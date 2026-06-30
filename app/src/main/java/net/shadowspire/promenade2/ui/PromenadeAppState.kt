package net.shadowspire.promenade2.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.shadowspire.promenade2.core.model.Playlist
import net.shadowspire.promenade2.core.model.PlaylistId
import net.shadowspire.promenade2.core.model.Track
import net.shadowspire.promenade2.data.diagnostics.LibraryDiagnostic
import net.shadowspire.promenade2.data.diagnostics.Severity
import net.shadowspire.promenade2.data.library.LibraryState
import net.shadowspire.promenade2.data.library.SafLibraryRepository
import net.shadowspire.promenade2.data.preferences.AppPreferences
import net.shadowspire.promenade2.data.preferences.PreferencesRepository

class PromenadeAppState(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val preferencesRepository = PreferencesRepository(appContext)
    private val libraryRepository = SafLibraryRepository(appContext)
    private var preferences = AppPreferences()
    private var preferencesJob: Job? = null

    private val _libraryState = MutableStateFlow(LibraryState(isScanning = true))
    val libraryState: StateFlow<LibraryState> = _libraryState.asStateFlow()

    private val _preferencesState = MutableStateFlow(AppPreferences())
    val preferencesState: StateFlow<AppPreferences> = _preferencesState.asStateFlow()

    private val _activePlaylistId = MutableStateFlow<PlaylistId?>(null)
    val activePlaylistId: StateFlow<PlaylistId?> = _activePlaylistId.asStateFlow()

    fun start() {
        if (preferencesJob != null) {
            return
        }

        preferencesJob = scope.launch {
            preferencesRepository.preferences.collectLatest { latestPreferences ->
                preferences = latestPreferences
                _preferencesState.value = latestPreferences
                scan(latestPreferences)
            }
        }
    }

    fun stop() {
        preferencesJob?.cancel()
        preferencesJob = null
    }

    fun setTracksFolder(uri: Uri) {
        scope.launch {
            if (persistFolderGrant(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)) {
                preferencesRepository.setTracksFolder(libraryRepository.folderRef(uri))
            }
        }
    }

    fun setPlaylistsFolder(uri: Uri) {
        scope.launch {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            if (persistFolderGrant(uri, flags)) {
                preferencesRepository.setPlaylistsFolder(libraryRepository.folderRef(uri))
            }
        }
    }

    fun rescan() {
        scope.launch {
            scan(preferences)
        }
    }

    fun selectPlaylist(playlistId: PlaylistId?) {
        _activePlaylistId.value = playlistId
    }

    fun saveLastPlaylistSelection(
        playlistId: PlaylistId,
        entryIndex: Int,
    ) {
        scope.launch {
            preferencesRepository.setLastPlaylistSelection(playlistId, entryIndex)
        }
    }

    fun saveLastTrackSelection(track: Track) {
        scope.launch {
            preferencesRepository.setLastTrackSelection(track.id)
        }
    }

    fun createPlaylist() {
        scope.launch {
            val playlist = libraryRepository.createPlaylist(
                playlistsFolder = preferences.playlistsFolder,
                name = "New Playlist",
            )
            if (playlist == null) {
                addActionError("Could not create playlist.")
            } else {
                _activePlaylistId.value = playlist.id
                scan(preferences)
            }
        }
    }

    fun deletePlaylist(playlistId: PlaylistId) {
        scope.launch {
            val deleted = libraryRepository.deletePlaylist(
                playlistsFolder = preferences.playlistsFolder,
                playlistId = playlistId,
            )
            if (!deleted) {
                addActionError("Could not delete playlist.")
            } else {
                if (_activePlaylistId.value == playlistId) {
                    _activePlaylistId.value = null
                }
                scan(preferences)
            }
        }
    }

    fun addTrackToPlaylist(
        playlist: Playlist,
        track: Track,
    ) {
        savePlaylist(
            playlist = playlist.copy(entries = playlist.entries + track.id),
            errorMessage = "Could not add track to playlist.",
        )
    }

    fun removePlaylistEntry(
        playlist: Playlist,
        entryIndex: Int,
    ) {
        if (entryIndex !in playlist.entries.indices) {
            return
        }
        savePlaylist(
            playlist = playlist.copy(
                entries = playlist.entries.filterIndexed { index, _ -> index != entryIndex },
            ),
            errorMessage = "Could not remove playlist entry.",
        )
    }

    fun movePlaylistEntry(
        playlist: Playlist,
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (fromIndex !in playlist.entries.indices || toIndex !in playlist.entries.indices) {
            return
        }
        val entries = playlist.entries.toMutableList()
        val moved = entries.removeAt(fromIndex)
        entries.add(toIndex, moved)
        savePlaylist(
            playlist = playlist.copy(entries = entries),
            errorMessage = "Could not reorder playlist.",
        )
    }

    fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        scope.launch {
            preferencesRepository.setPlaybackSettings(transform(preferences).playbackSettings)
        }
    }

    private suspend fun scan(preferences: AppPreferences) {
        _libraryState.value = _libraryState.value.copy(isScanning = true)
        _libraryState.value = libraryRepository.scan(
            tracksFolder = preferences.tracksFolder,
            playlistsFolder = preferences.playlistsFolder,
        )
        val library = _libraryState.value
        val preferredPlaylistId = preferences.lastPlaylistId
        if (preferredPlaylistId != null && library.playlists.any { playlist ->
                playlist.playlist.id == preferredPlaylistId
            }
        ) {
            _activePlaylistId.value = preferredPlaylistId
        } else if (_activePlaylistId.value == null && library.playlists.isNotEmpty()) {
            _activePlaylistId.value = library.playlists.first().playlist.id
        } else if (_activePlaylistId.value != null && library.playlists.none { playlist ->
                playlist.playlist.id == _activePlaylistId.value
            }
        ) {
            _activePlaylistId.value = library.playlists.firstOrNull()?.playlist?.id
        }
    }

    private fun persistFolderGrant(uri: Uri, flags: Int): Boolean =
        try {
            appContext.contentResolver.takePersistableUriPermission(uri, flags)
            true
        } catch (error: SecurityException) {
            _libraryState.value = _libraryState.value.copy(
                diagnostics = listOf(
                    LibraryDiagnostic(
                        severity = Severity.Error,
                        fileName = null,
                        message = "Promenade could not keep access to that folder. Choose it again.",
                    ),
                ),
                isScanning = false,
            )
            false
        }

    private fun savePlaylist(
        playlist: Playlist,
        errorMessage: String,
    ) {
        scope.launch {
            val saved = libraryRepository.savePlaylist(preferences.playlistsFolder, playlist)
            if (!saved) {
                addActionError(errorMessage)
            } else {
                scan(preferences)
            }
        }
    }

    private fun addActionError(message: String) {
        _libraryState.value = _libraryState.value.copy(
            diagnostics = listOf(
                LibraryDiagnostic(
                    severity = Severity.Error,
                    fileName = null,
                    message = message,
                ),
            ) + _libraryState.value.diagnostics,
            isScanning = false,
        )
    }
}
