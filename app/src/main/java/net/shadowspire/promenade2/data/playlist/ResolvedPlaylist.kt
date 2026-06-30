package net.shadowspire.promenade2.data.playlist

import net.shadowspire.promenade2.core.model.Playlist
import net.shadowspire.promenade2.core.model.Track

data class ResolvedPlaylist(
    val playlist: Playlist,
    val entries: List<PlaylistEntryResolution>,
) {
    val resolvedTracks: List<Track>
        get() = entries.mapNotNull { entry -> entry.track }

    val unresolvedCount: Int
        get() = entries.count { entry -> entry.track == null }
}
