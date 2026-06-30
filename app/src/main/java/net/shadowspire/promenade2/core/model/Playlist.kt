package net.shadowspire.promenade2.core.model

data class Playlist(
    val id: PlaylistId,
    val name: String,
    val entries: List<TrackId>,
)
