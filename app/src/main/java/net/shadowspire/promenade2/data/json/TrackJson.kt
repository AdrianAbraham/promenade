package net.shadowspire.promenade2.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrackJson(
    val name: String? = null,
    @SerialName("music_file")
    val musicFile: String? = null,
    @SerialName("calls_file")
    val callsFile: String? = null,
    @SerialName("instructions_file")
    val instructionsFile: String? = null,
    val intro: String? = null,
    val repetitions: List<RepetitionJson>? = null,
)

@Serializable
data class RepetitionJson(
    val start: Double? = null,
)
