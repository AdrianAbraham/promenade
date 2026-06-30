package net.shadowspire.promenade2.core.model

data class Track(
    val id: TrackId,
    val name: String,
    val musicRef: ContentRef,
    val callsRef: ContentRef?,
    val instructionsRef: ContentRef?,
    val intro: String,
    val repetitions: List<RepetitionMark>,
)
