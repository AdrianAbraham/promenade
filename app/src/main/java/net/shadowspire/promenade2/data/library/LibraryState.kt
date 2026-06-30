package net.shadowspire.promenade2.data.library

import net.shadowspire.promenade2.core.model.Track
import net.shadowspire.promenade2.data.diagnostics.LibraryDiagnostic

data class LibraryState(
    val tracksFolder: FolderSummary? = null,
    val playlistsFolder: FolderSummary? = null,
    val tracks: List<Track> = emptyList(),
    val diagnostics: List<LibraryDiagnostic> = emptyList(),
    val isScanning: Boolean = false,
)
