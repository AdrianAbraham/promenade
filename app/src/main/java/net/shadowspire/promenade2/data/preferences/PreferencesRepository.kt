package net.shadowspire.promenade2.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.shadowspire.promenade2.core.model.FolderRef

private val Context.appDataStore by preferencesDataStore(name = "promenade_preferences")

class PreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.appDataStore

    val preferences: Flow<AppPreferences> = dataStore.data.map { values ->
        AppPreferences(
            tracksFolder = values.folderRef(Keys.TracksFolderName, Keys.TracksFolderUri),
            playlistsFolder = values.folderRef(Keys.PlaylistsFolderName, Keys.PlaylistsFolderUri),
        )
    }

    suspend fun setTracksFolder(folder: FolderRef?) {
        dataStore.edit { values ->
            values.setFolderRef(Keys.TracksFolderName, Keys.TracksFolderUri, folder)
        }
    }

    suspend fun setPlaylistsFolder(folder: FolderRef?) {
        dataStore.edit { values ->
            values.setFolderRef(Keys.PlaylistsFolderName, Keys.PlaylistsFolderUri, folder)
        }
    }

    private fun androidx.datastore.preferences.core.Preferences.folderRef(
        nameKey: androidx.datastore.preferences.core.Preferences.Key<String>,
        uriKey: androidx.datastore.preferences.core.Preferences.Key<String>,
    ): FolderRef? {
        val name = this[nameKey]
        val uri = this[uriKey]
        return if (name != null && uri != null) {
            FolderRef(displayName = name, treeUriString = uri)
        } else {
            null
        }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.setFolderRef(
        nameKey: androidx.datastore.preferences.core.Preferences.Key<String>,
        uriKey: androidx.datastore.preferences.core.Preferences.Key<String>,
        folder: FolderRef?,
    ) {
        if (folder == null) {
            remove(nameKey)
            remove(uriKey)
        } else {
            this[nameKey] = folder.displayName
            this[uriKey] = folder.treeUriString
        }
    }

    private object Keys {
        val TracksFolderName = stringPreferencesKey("tracks_folder_name")
        val TracksFolderUri = stringPreferencesKey("tracks_folder_uri")
        val PlaylistsFolderName = stringPreferencesKey("playlists_folder_name")
        val PlaylistsFolderUri = stringPreferencesKey("playlists_folder_uri")
    }
}
