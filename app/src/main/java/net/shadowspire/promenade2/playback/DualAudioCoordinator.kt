package net.shadowspire.promenade2.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.shadowspire.promenade2.core.model.AutoMuteSettings
import net.shadowspire.promenade2.core.model.PlaybackDelaySettings
import net.shadowspire.promenade2.domain.automute.AutoMutePolicy
import net.shadowspire.promenade2.domain.balance.BalanceCurve

class DualAudioCoordinator(
    context: Context,
    private val musicPlayer: Player,
    private val scope: CoroutineScope,
) : Player.Listener {
    private val callsPlayer = ExoPlayer.Builder(context)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var balance = 0.5f
    private var manualCallsMuted = false
    private var autoMuteSettings = AutoMuteSettings()
    private var playbackDelaySettings = PlaybackDelaySettings()
    private var repetitionStarts = DoubleArray(0)
    private var callsLoaded = false
    private var driftJob: Job? = null
    private var delayJob: Job? = null
    private var pausingForDelay = false
    private var skipNextDelayStart = false

    fun attach() {
        musicPlayer.addListener(this)
        loadCallsFor(musicPlayer.currentMediaItem)
        applyVolumes()
        startDriftCorrection()
    }

    fun release() {
        cancelPlaybackDelay()
        driftJob?.cancel()
        musicPlayer.removeListener(this)
        callsPlayer.release()
    }

    fun setBalance(value: Float) {
        balance = value.coerceIn(0f, 1f)
        applyVolumes()
    }

    fun setCallsMuted(value: Boolean) {
        manualCallsMuted = value
        applyVolumes()
    }

    fun setAutoMute(settings: AutoMuteSettings) {
        autoMuteSettings = settings
        applyVolumes()
    }

    fun setPlaybackDelay(settings: PlaybackDelaySettings) {
        playbackDelaySettings = settings
        if (!settings.isEnabled) {
            cancelPlaybackDelay()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        cancelPlaybackDelay()
        manualCallsMuted = false
        loadCallsFor(mediaItem)
        applyVolumes()

        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            musicPlayer.pause()
            callsPlayer.pause()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying && shouldStartPlaybackDelay()) {
            startPlaybackDelay()
            return
        }

        if (!isPlaying && !pausingForDelay) {
            cancelPlaybackDelay()
        }

        if (isPlaying && callsLoaded && callsPlayer.volume > 0f) {
            callsPlayer.play()
        } else {
            callsPlayer.pause()
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        cancelPlaybackDelay()
        if (callsLoaded) {
            callsPlayer.seekTo(newPosition.positionMs)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            callsPlayer.pause()
        }
        applyVolumes()
    }

    private fun shouldStartPlaybackDelay(): Boolean =
        if (skipNextDelayStart) {
            skipNextDelayStart = false
            false
        } else {
            playbackDelaySettings.isEnabled &&
                delayJob == null &&
                musicPlayer.playbackState == Player.STATE_READY
        }

    private fun startPlaybackDelay() {
        val seconds = playbackDelaySettings.delaySeconds.coerceAtLeast(0)
        if (seconds == 0) {
            return
        }

        delayJob?.cancel()
        callsPlayer.pause()
        pausingForDelay = true
        musicPlayer.pause()
        pausingForDelay = false

        delayJob = scope.launch {
            for (remaining in seconds downTo 1) {
                PlaybackDelayStatusStore.update(
                    PlaybackDelayStatus(
                        isCountingDown = true,
                        remainingSeconds = remaining,
                    ),
                )
                delay(ONE_SECOND_MS)
            }

            delayJob = null
            PlaybackDelayStatusStore.update(PlaybackDelayStatus())
            skipNextDelayStart = true
            musicPlayer.play()
        }
    }

    private fun cancelPlaybackDelay() {
        val hadDelay = delayJob != null
        delayJob?.cancel()
        delayJob = null
        if (hadDelay || PlaybackDelayStatusStore.status.value.isCountingDown) {
            PlaybackDelayStatusStore.update(PlaybackDelayStatus())
        }
    }

    private fun loadCallsFor(mediaItem: MediaItem?) {
        callsPlayer.stop()
        callsPlayer.clearMediaItems()
        callsLoaded = false
        repetitionStarts = mediaItem?.mediaMetadata?.extras
            ?.getDoubleArray(PracticeMediaMetadata.RepetitionStarts)
            ?: DoubleArray(0)

        val callsUri = mediaItem?.mediaMetadata?.extras
            ?.getString(PracticeMediaMetadata.CallsUri)
            ?.takeIf { value -> value.isNotBlank() }
            ?: return

        callsPlayer.setMediaItem(MediaItem.fromUri(callsUri.toUri()))
        callsPlayer.prepare()
        callsPlayer.seekTo(musicPlayer.currentPosition.coerceAtLeast(0L))
        callsLoaded = true
    }

    private fun applyVolumes() {
        val autoMuted = AutoMutePolicy.shouldMute(
            currentRepetition = currentRepetition(),
            totalRepetitions = repetitionStarts.size,
            settings = autoMuteSettings,
        )
        val volumes = BalanceCurve.volumes(
            balance = balance,
            callsMuted = manualCallsMuted || autoMuted,
        )
        musicPlayer.volume = volumes.music
        callsPlayer.volume = volumes.calls

        if (callsPlayer.volume == 0f) {
            callsPlayer.pause()
        } else if (musicPlayer.isPlaying && callsLoaded) {
            callsPlayer.play()
        }
    }

    private fun currentRepetition(): Int {
        val positionSeconds = musicPlayer.currentPosition.coerceAtLeast(0L) / MILLIS_PER_SECOND
        val index = repetitionStarts.indexOfLast { start -> start <= positionSeconds }
        return if (index < 0) 0 else index + 1
    }

    private fun startDriftCorrection() {
        driftJob?.cancel()
        driftJob = scope.launch {
            while (isActive) {
                if (callsLoaded && musicPlayer.isPlaying && callsPlayer.volume > 0f) {
                    val driftMs = kotlin.math.abs(callsPlayer.currentPosition - musicPlayer.currentPosition)
                    if (driftMs > DRIFT_CORRECTION_MS) {
                        callsPlayer.seekTo(musicPlayer.currentPosition)
                    }
                    applyVolumes()
                }
                delay(DRIFT_CHECK_MS)
            }
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0
        const val ONE_SECOND_MS = 1_000L
        const val DRIFT_CHECK_MS = 750L
        const val DRIFT_CORRECTION_MS = 200L
    }
}
