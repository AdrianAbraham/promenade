package net.shadowspire.promenade2.core.model

data class PlaybackDelaySettings(
    val delaySeconds: Int = 0,
) {
    val isEnabled: Boolean
        get() = delaySeconds > 0
}
