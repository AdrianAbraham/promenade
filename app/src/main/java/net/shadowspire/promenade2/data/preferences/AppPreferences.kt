package net.shadowspire.promenade2.data.preferences

import net.shadowspire.promenade2.core.model.FolderRef
import net.shadowspire.promenade2.core.model.PlaybackSettings
import net.shadowspire.promenade2.core.model.PlaylistId
import net.shadowspire.promenade2.core.model.TrackId

data class AppPreferences(
    val tracksFolder: FolderRef? = null,
    val playlistsFolder: FolderRef? = null,
    val lastPlaylistId: PlaylistId? = null,
    val lastPlaylistEntryIndex: Int? = null,
    val lastTrackId: TrackId? = null,
    val themePreference: ThemePreference = ThemePreference.System,
    val playbackSettings: PlaybackSettings = PlaybackSettings(),
)

enum class ThemePreference {
    System,
    Light,
    Dark,
}
