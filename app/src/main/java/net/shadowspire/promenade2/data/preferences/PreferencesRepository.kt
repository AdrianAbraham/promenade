package net.shadowspire.promenade2.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.shadowspire.promenade2.core.model.AutoMuteSettings
import net.shadowspire.promenade2.core.model.FolderRef
import net.shadowspire.promenade2.core.model.PlaybackDelaySettings
import net.shadowspire.promenade2.core.model.PlaybackSettings
import net.shadowspire.promenade2.core.model.PlaylistId
import net.shadowspire.promenade2.core.model.TrackId

private val Context.appDataStore by preferencesDataStore(name = "promenade_preferences")

class PreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.appDataStore

    val preferences: Flow<AppPreferences> = dataStore.data.map { values ->
        AppPreferences(
            tracksFolder = values.folderRef(Keys.TracksFolderName, Keys.TracksFolderUri),
            playlistsFolder = values.folderRef(Keys.PlaylistsFolderName, Keys.PlaylistsFolderUri),
            lastPlaylistId = values[Keys.LastPlaylistId]?.let(::PlaylistId),
            lastPlaylistEntryIndex = values[Keys.LastPlaylistEntryIndex]?.takeIf { index -> index >= 0 },
            lastTrackId = values[Keys.LastTrackId]?.let(::TrackId),
            playbackSettings = PlaybackSettings(
                balance = values[Keys.Balance] ?: PlaybackSettings.DEFAULT_BALANCE,
                callsMuted = values[Keys.CallsMuted] ?: false,
                autoMute = AutoMuteSettings(
                    muteAfterRepetition = values[Keys.MuteAfterRepetition].positiveOrNull(),
                    muteWithRepetitionsRemaining = values[Keys.MuteWithRepetitionsRemaining].positiveOrNull(),
                ),
                playbackDelay = PlaybackDelaySettings(
                    delaySeconds = values[Keys.PlaybackDelaySeconds]?.coerceAtLeast(0) ?: 0,
                ),
            ),
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

    suspend fun setPlaybackSettings(settings: PlaybackSettings) {
        dataStore.edit { values ->
            values[Keys.Balance] = settings.balance.coerceIn(0f, 1f)
            values[Keys.CallsMuted] = settings.callsMuted
            values.setOptionalPositiveInt(Keys.MuteAfterRepetition, settings.autoMute.muteAfterRepetition)
            values.setOptionalPositiveInt(
                Keys.MuteWithRepetitionsRemaining,
                settings.autoMute.muteWithRepetitionsRemaining,
            )
            values[Keys.PlaybackDelaySeconds] = settings.playbackDelay.delaySeconds.coerceAtLeast(0)
        }
    }

    suspend fun setLastPlaylistSelection(
        playlistId: PlaylistId,
        entryIndex: Int,
    ) {
        dataStore.edit { values ->
            values[Keys.LastPlaylistId] = playlistId.fileName
            values[Keys.LastPlaylistEntryIndex] = entryIndex.coerceAtLeast(0)
            values.remove(Keys.LastTrackId)
        }
    }

    suspend fun setLastTrackSelection(trackId: TrackId) {
        dataStore.edit { values ->
            values.remove(Keys.LastPlaylistId)
            values.remove(Keys.LastPlaylistEntryIndex)
            values[Keys.LastTrackId] = trackId.jsonFileName
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

    private fun androidx.datastore.preferences.core.MutablePreferences.setOptionalPositiveInt(
        key: androidx.datastore.preferences.core.Preferences.Key<Int>,
        value: Int?,
    ) {
        if (value == null || value <= 0) {
            remove(key)
        } else {
            this[key] = value
        }
    }

    private fun Int?.positiveOrNull(): Int? =
        this?.takeIf { value -> value > 0 }

    private object Keys {
        val TracksFolderName = stringPreferencesKey("tracks_folder_name")
        val TracksFolderUri = stringPreferencesKey("tracks_folder_uri")
        val PlaylistsFolderName = stringPreferencesKey("playlists_folder_name")
        val PlaylistsFolderUri = stringPreferencesKey("playlists_folder_uri")
        val LastPlaylistId = stringPreferencesKey("last_playlist_id")
        val LastPlaylistEntryIndex = intPreferencesKey("last_playlist_entry_index")
        val LastTrackId = stringPreferencesKey("last_track_id")
        val Balance = floatPreferencesKey("playback_balance")
        val CallsMuted = booleanPreferencesKey("calls_muted")
        val MuteAfterRepetition = intPreferencesKey("auto_mute_after_repetition")
        val MuteWithRepetitionsRemaining = intPreferencesKey("auto_mute_with_repetitions_remaining")
        val PlaybackDelaySeconds = intPreferencesKey("playback_delay_seconds")
    }
}
