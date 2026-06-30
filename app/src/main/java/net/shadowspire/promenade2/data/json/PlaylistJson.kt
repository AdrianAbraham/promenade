package net.shadowspire.promenade2.data.json

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistJson(
    val name: String? = null,
    val entries: List<String>? = null,
)
