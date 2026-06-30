package net.shadowspire.promenade2.core.model

data class PlaybackSettings(
    val balance: Float = DEFAULT_BALANCE,
    val callsMuted: Boolean = false,
    val autoMute: AutoMuteSettings = AutoMuteSettings(),
    val playbackDelay: PlaybackDelaySettings = PlaybackDelaySettings(),
) {
    companion object {
        const val DEFAULT_BALANCE = 0.5f
    }
}
