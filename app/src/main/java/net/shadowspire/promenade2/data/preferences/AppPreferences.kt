package net.shadowspire.promenade2.data.preferences

import net.shadowspire.promenade2.core.model.FolderRef

data class AppPreferences(
    val tracksFolder: FolderRef? = null,
    val playlistsFolder: FolderRef? = null,
)
