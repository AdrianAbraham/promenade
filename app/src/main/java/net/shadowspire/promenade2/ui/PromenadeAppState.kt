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

    fun start() {
        if (preferencesJob != null) {
            return
        }

        preferencesJob = scope.launch {
            preferencesRepository.preferences.collectLatest { latestPreferences ->
                preferences = latestPreferences
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

    private suspend fun scan(preferences: AppPreferences) {
        _libraryState.value = _libraryState.value.copy(isScanning = true)
        _libraryState.value = libraryRepository.scan(
            tracksFolder = preferences.tracksFolder,
            playlistsFolder = preferences.playlistsFolder,
        )
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
}
