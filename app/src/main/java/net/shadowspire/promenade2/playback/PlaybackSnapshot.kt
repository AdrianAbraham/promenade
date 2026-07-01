package net.shadowspire.promenade2.playback

data class PlaybackSnapshot(
    val isConnected: Boolean = false,
    val title: String = "No audio selected",
    val status: String = "Choose an audio file to test service-backed playback.",
    val isPlaying: Boolean = false,
    val canPlay: Boolean = false,
    val currentIndex: Int? = null,
    val queueSize: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercentage: Int = 0,
    val currentRepetition: Int = 0,
    val totalRepetitions: Int = 0,
    val hasCalls: Boolean = false,
    val playbackDelayActive: Boolean = false,
    val playbackDelayRemainingSeconds: Int = 0,
)
