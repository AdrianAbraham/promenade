package net.shadowspire.promenade2.data.playlist

import net.shadowspire.promenade2.core.model.Track
import net.shadowspire.promenade2.core.model.TrackId

data class PlaylistEntryResolution(
    val index: Int,
    val trackId: TrackId,
    val track: Track?,
)
