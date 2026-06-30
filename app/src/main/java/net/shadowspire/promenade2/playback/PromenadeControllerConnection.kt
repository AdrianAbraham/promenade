package net.shadowspire.promenade2.playback

import android.content.ComponentName
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PromenadeControllerConnection(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateFrom(player)
        }
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    fun connect() {
        if (controllerFuture != null) {
            return
        }

        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PromenadePlaybackService::class.java),
        )
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val mediaController = future.get()
                controller = mediaController
                mediaController.addListener(playerListener)
                updateFrom(mediaController)
                startProgressUpdates()
            },
            mainExecutor,
        )
    }

    fun disconnect() {
        progressJob?.cancel()
        progressJob = null

        controller?.removeListener(playerListener)
        controller = null

        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        _snapshot.value = PlaybackSnapshot()
    }

    fun loadAndPlay(uri: Uri) {
        val title = appContext.contentResolver.displayName(uri) ?: uri.lastPathSegment ?: "Selected audio"
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .build(),
            )
            .build()

        controller?.run {
            setMediaItem(mediaItem)
            prepare()
            play()
            updateFrom(this)
        }
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun stop() {
        controller?.run {
            stop()
            seekTo(0L)
            updateFrom(this)
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                controller?.let(::updateFrom)
                delay(PROGRESS_UPDATE_MS)
            }
        }
    }

    private fun updateFrom(player: Player) {
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val title = player.mediaMetadata.title?.toString()
            ?: player.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: "No audio selected"

        _snapshot.value = PlaybackSnapshot(
            isConnected = true,
            title = title,
            status = player.playbackStatus(),
            isPlaying = player.isPlaying,
            canPlay = player.mediaItemCount > 0,
            positionMs = positionMs.coerceAtMost(durationMs.takeIf { it > 0L } ?: positionMs),
            durationMs = durationMs,
            bufferedPercentage = player.bufferedPercentage.coerceIn(0, 100),
        )
    }

    private fun Player.playbackStatus(): String =
        when (playbackState) {
            Player.STATE_BUFFERING -> "Buffering"
            Player.STATE_READY -> if (isPlaying) "Playing" else "Ready"
            Player.STATE_ENDED -> "Completed"
            Player.STATE_IDLE -> if (mediaItemCount > 0) "Stopped" else "Idle"
            else -> "Unknown"
        }

    private fun android.content.ContentResolver.displayName(uri: Uri): String? {
        val cursor: Cursor? = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) it.getString(index) else null
            } else {
                null
            }
        }
    }

    private companion object {
        const val PROGRESS_UPDATE_MS = 250L
    }
}
