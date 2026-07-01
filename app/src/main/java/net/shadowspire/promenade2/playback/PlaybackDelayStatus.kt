package net.shadowspire.promenade2.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackDelayStatus(
    val isCountingDown: Boolean = false,
    val remainingSeconds: Int = 0,
)

object PlaybackDelayStatusStore {
    private val _status = MutableStateFlow(PlaybackDelayStatus())
    val status: StateFlow<PlaybackDelayStatus> = _status.asStateFlow()

    fun update(status: PlaybackDelayStatus) {
        _status.value = status
    }
}
