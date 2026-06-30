package net.shadowspire.promenade2.data.preferences

import net.shadowspire.promenade2.core.model.FolderRef
import net.shadowspire.promenade2.core.model.PlaybackSettings

data class AppPreferences(
    val tracksFolder: FolderRef? = null,
    val playlistsFolder: FolderRef? = null,
    val playbackSettings: PlaybackSettings = PlaybackSettings(),
)
