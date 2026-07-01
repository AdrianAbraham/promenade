package net.shadowspire.promenade2.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
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
import net.shadowspire.promenade2.core.model.AutoMuteSettings
import net.shadowspire.promenade2.core.model.PlaybackDelaySettings
import net.shadowspire.promenade2.core.model.Track
import net.shadowspire.promenade2.domain.playlist.PracticeQueueBuilder

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

    fun loadQueue(
        tracks: List<Track>,
        startIndex: Int,
        autoplay: Boolean,
    ) {
        if (tracks.isEmpty()) {
            return
        }

        val mediaItems = PracticeQueueBuilder.build(tracks)
        val boundedIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
        controller?.run {
            setMediaItems(mediaItems, boundedIndex, C.TIME_UNSET)
            prepare()
            if (autoplay) {
                play()
            } else {
                pause()
            }
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

    fun skipPrevious() {
        controller?.run {
            if (hasPreviousMediaItem()) {
                seekToPreviousMediaItem()
            } else {
                seekTo(0L)
            }
        }
    }

    fun skipNext() {
        controller?.run {
            if (hasNextMediaItem()) {
                seekToNextMediaItem()
            }
        }
    }

    fun setBalance(balance: Float) {
        val args = Bundle().apply {
            putFloat(PracticeSessionCommands.Balance, balance.coerceIn(0f, 1f))
        }
        controller?.sendCustomCommand(PracticeSessionCommands.BalanceCommand, args)
    }

    fun setCallsMuted(muted: Boolean) {
        val args = Bundle().apply {
            putBoolean(PracticeSessionCommands.CallsMuted, muted)
        }
        controller?.sendCustomCommand(PracticeSessionCommands.CallsMutedCommand, args)
    }

    fun setAutoMute(settings: AutoMuteSettings) {
        val args = Bundle().apply {
            putInt(PracticeSessionCommands.MuteAfterRepetition, settings.muteAfterRepetition ?: 0)
            putInt(
                PracticeSessionCommands.MuteWithRepetitionsRemaining,
                settings.muteWithRepetitionsRemaining ?: 0,
            )
        }
        controller?.sendCustomCommand(PracticeSessionCommands.AutoMuteCommand, args)
    }

    fun setPlaybackDelay(settings: PlaybackDelaySettings) {
        val args = Bundle().apply {
            putInt(PracticeSessionCommands.PlaybackDelaySeconds, settings.delaySeconds.coerceAtLeast(0))
        }
        controller?.sendCustomCommand(PracticeSessionCommands.PlaybackDelayCommand, args)
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
        val extras = player.currentMediaItem?.mediaMetadata?.extras
        val repetitionStarts = extras?.getDoubleArray(PracticeMediaMetadata.RepetitionStarts) ?: DoubleArray(0)
        val currentIndex = player.currentMediaItemIndex.takeIf { index -> index != C.INDEX_UNSET }
        val title = player.mediaMetadata.title?.toString()
            ?: player.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: "No audio selected"

        _snapshot.value = PlaybackSnapshot(
            isConnected = true,
            title = title,
            status = player.playbackStatus(),
            isPlaying = player.isPlaying,
            canPlay = player.mediaItemCount > 0,
            currentIndex = currentIndex,
            queueSize = player.mediaItemCount,
            positionMs = positionMs.coerceAtMost(durationMs.takeIf { it > 0L } ?: positionMs),
            durationMs = durationMs,
            bufferedPercentage = player.bufferedPercentage.coerceIn(0, 100),
            currentRepetition = currentRepetition(positionMs, repetitionStarts),
            totalRepetitions = repetitionStarts.size,
            hasCalls = !extras?.getString(PracticeMediaMetadata.CallsUri).isNullOrBlank(),
        )
    }

    private fun currentRepetition(
        positionMs: Long,
        repetitionStarts: DoubleArray,
    ): Int {
        if (repetitionStarts.isEmpty()) {
            return 0
        }

        val positionSeconds = positionMs / MILLIS_PER_SECOND
        val index = repetitionStarts.indexOfLast { start -> start <= positionSeconds }
        return if (index < 0) 0 else index + 1
    }

    private fun Player.playbackStatus(): String =
        when (playbackState) {
            Player.STATE_BUFFERING -> "Buffering"
            Player.STATE_READY -> if (isPlaying) "Playing" else "Ready"
            Player.STATE_ENDED -> "Completed"
            Player.STATE_IDLE -> if (mediaItemCount > 0) "Stopped" else "Idle"
            else -> "Unknown"
        }

    private companion object {
        const val PROGRESS_UPDATE_MS = 250L
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
