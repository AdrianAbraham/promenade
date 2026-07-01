package net.shadowspire.promenade2.data.library

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Clock
import net.shadowspire.promenade2.core.model.ContentRef
import net.shadowspire.promenade2.core.model.FolderRef
import net.shadowspire.promenade2.core.model.Playlist
import net.shadowspire.promenade2.core.model.PlaylistId
import net.shadowspire.promenade2.core.model.RepetitionMark
import net.shadowspire.promenade2.core.model.Seconds
import net.shadowspire.promenade2.core.model.Track
import net.shadowspire.promenade2.core.model.TrackId
import net.shadowspire.promenade2.data.diagnostics.LibraryDiagnostic
import net.shadowspire.promenade2.data.diagnostics.Severity
import net.shadowspire.promenade2.data.json.PlaylistJson
import net.shadowspire.promenade2.data.json.TrackJson
import net.shadowspire.promenade2.data.playlist.PlaylistEntryResolution
import net.shadowspire.promenade2.data.playlist.ResolvedPlaylist

class SafLibraryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val clock = Clock.systemUTC()

    suspend fun scan(
        tracksFolder: FolderRef?,
        playlistsFolder: FolderRef?,
    ): LibraryState = withContext(Dispatchers.IO) {
        val diagnostics = mutableListOf<LibraryDiagnostic>()
        val tracksScan = scanTracksFolder(tracksFolder, diagnostics)
        val playlistScan = scanPlaylistsFolder(playlistsFolder, tracksScan.tracks, diagnostics)

        LibraryState(
            tracksFolder = tracksScan.summary,
            playlistsFolder = playlistScan.summary,
            tracks = tracksScan.tracks,
            playlists = playlistScan.playlists,
            diagnostics = diagnostics,
        )
    }

    suspend fun createPlaylist(
        playlistsFolder: FolderRef?,
        name: String,
    ): Playlist? = withContext(Dispatchers.IO) {
        val folder = playlistsFolder?.toDocumentFile()?.takeIf { document -> document.canWrite() }
            ?: return@withContext null
        val playlistName = name.trim().ifBlank { "New Playlist" }
        val fileName = generatePlaylistFileName(folder, playlistName)
        val document = folder.createFile(JSON_MIME_TYPE, fileName) ?: return@withContext null
        val playlist = Playlist(
            id = PlaylistId(document.name ?: fileName),
            name = playlistName,
            entries = emptyList(),
        )
        runCatching {
            document.writeText(playlist.toJsonText())
            playlist
        }.getOrNull()
    }

    suspend fun savePlaylist(
        playlistsFolder: FolderRef?,
        playlist: Playlist,
    ): Boolean = withContext(Dispatchers.IO) {
        val folder = playlistsFolder?.toDocumentFile()?.takeIf { document -> document.canWrite() }
            ?: return@withContext false
        val document = folder.findFile(playlist.id.fileName) ?: return@withContext false
        runCatching {
            document.writeText(playlist.toJsonText())
            true
        }.getOrDefault(false)
    }

    suspend fun deletePlaylist(
        playlistsFolder: FolderRef?,
        playlistId: PlaylistId,
    ): Boolean = withContext(Dispatchers.IO) {
        val folder = playlistsFolder?.toDocumentFile()?.takeIf { document -> document.canWrite() }
            ?: return@withContext false
        folder.findFile(playlistId.fileName)?.delete() == true
    }

    fun folderRef(uri: Uri): FolderRef {
        val document = DocumentFile.fromTreeUri(appContext, uri)
        return FolderRef(
            displayName = document?.name ?: uri.lastPathSegment ?: "Selected folder",
            treeUriString = uri.toString(),
        )
    }

    private fun scanTracksFolder(
        folder: FolderRef?,
        diagnostics: MutableList<LibraryDiagnostic>,
    ): TrackScan {
        if (folder == null) {
            return TrackScan(summary = null, tracks = emptyList())
        }

        val document = folder.toDocumentFile()
        if (document == null || !document.canRead()) {
            diagnostics += LibraryDiagnostic(
                severity = Severity.Error,
                fileName = null,
                message = "Promenade can no longer access the tracks folder. Choose it again.",
            )
            return TrackScan(
                summary = FolderSummary(folder, available = false, itemCount = 0),
                tracks = emptyList(),
            )
        }

        val children = document.listFiles().filter { it.isFile }
        val childrenByName = children.mapNotNull { child ->
            child.name?.let { name -> name to child }
        }.toMap()

        val tracks = children
            .filter { child -> child.name?.endsWith(JSON_EXTENSION, ignoreCase = true) == true }
            .mapNotNull { child -> parseTrack(child, childrenByName, diagnostics) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        return TrackScan(
            summary = FolderSummary(folder, available = true, itemCount = children.size),
            tracks = tracks,
        )
    }

    private fun scanPlaylistsFolder(
        folder: FolderRef?,
        tracks: List<Track>,
        diagnostics: MutableList<LibraryDiagnostic>,
    ): PlaylistScan {
        if (folder == null) {
            return PlaylistScan(summary = null, playlists = emptyList())
        }

        val document = folder.toDocumentFile()
        if (document == null || !document.canRead() || !document.canWrite()) {
            diagnostics += LibraryDiagnostic(
                severity = Severity.Error,
                fileName = null,
                message = "Promenade can no longer read and write the playlists folder. Choose it again.",
            )
            return PlaylistScan(
                summary = FolderSummary(folder, available = false, itemCount = 0),
                playlists = emptyList(),
            )
        }

        val tracksById = tracks.associateBy { track -> track.id }
        val playlistFiles = document.listFiles()
            .filter { child -> child.isFile && child.name?.endsWith(JSON_EXTENSION, ignoreCase = true) == true }
        val playlists = playlistFiles
            .mapNotNull { child -> parsePlaylist(child, tracksById, diagnostics) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { resolved -> resolved.playlist.name })

        return PlaylistScan(
            summary = FolderSummary(folder, available = true, itemCount = playlistFiles.size),
            playlists = playlists,
        )
    }

    private fun parsePlaylist(
        jsonFile: DocumentFile,
        tracksById: Map<TrackId, Track>,
        diagnostics: MutableList<LibraryDiagnostic>,
    ): ResolvedPlaylist? {
        val fileName = jsonFile.name ?: return null
        val parsed = try {
            json.decodeFromString<PlaylistJson>(jsonFile.readText())
        } catch (error: SerializationException) {
            diagnostics += LibraryDiagnostic(
                severity = Severity.Error,
                fileName = fileName,
                message = "Skipped playlist `$fileName`: invalid JSON.",
            )
            return null
        } catch (error: IllegalArgumentException) {
            diagnostics += LibraryDiagnostic(
                severity = Severity.Error,
                fileName = fileName,
                message = "Skipped playlist `$fileName`: could not read file.",
            )
            return null
        }

        val fieldErrors = mutableListOf<String>()
        val name = parsed.name.requiredText("name", fieldErrors)
        val entryNames = parsed.entries
        if (entryNames == null) {
            fieldErrors.addMissing("entries")
        }
        val entries = entryNames
            ?.mapIndexedNotNull { index, entry ->
                val value = entry.trim()
                if (value.isBlank()) {
                    fieldErrors += "entries[$index] must be a non-blank track filename"
                    null
                } else {
                    TrackId(value)
                }
            }
            .orEmpty()

        if (fieldErrors.isNotEmpty()) {
            diagnostics += LibraryDiagnostic(
                severity = Severity.Error,
                fileName = fileName,
                message = "Skipped playlist `$fileName`: ${fieldErrors.joinToString("; ")}.",
            )
            return null
        }

        val playlist = Playlist(
            id = PlaylistId(fileName),
            name = name,
            entries = entries,
        )
        val resolvedEntries = entries.mapIndexed { index, trackId ->
            val track = tracksById[trackId]
            if (track == null) {
                diagnostics += LibraryDiagnostic(
                    severity = Severity.Warning,
                    fileName = fileName,
                    message = "`$name` references missing track `${trackId.jsonFileName}`.",
                )
            }
            PlaylistEntryResolution(index = index, trackId = trackId, track = track)
        }
        return ResolvedPlaylist(
            playlist = playlist,
            entries = resolvedEntries,
        )
    }

    private fun parseTrack(
        jsonFile: DocumentFile,
        folderChildren: Map<String, DocumentFile>,
        diagnostics: MutableList<LibraryDiagnostic>,
    ): Track? {
        val fileName = jsonFile.name ?: return null
        val parsed = try {
            json.decodeFromString<TrackJson>(jsonFile.readText())
        } catch (error: SerializationException) {
            diagnostics += LibraryDiagnostic(
                severity = Severity.Error,
                fileName = fileName,
                message = "Skipped `$fileName`: invalid JSON.",
            )
            return null
        } catch (error: IllegalArgumentException) {
            diagnostics += LibraryDiagnostic(
                severity = Severity.Error,
                fileName = fileName,
                message = "Skipped `$fileName`: could not read file.",
            )
            return null
        }

        val fieldErrors = mutableListOf<String>()
        val name = parsed.name.requiredText("name", fieldErrors)
        val musicFileName = parsed.musicFile.requiredText("music_file", fieldErrors)
        val intro = parsed.intro
        if (intro == null) {
            fieldErrors.addMissing("intro")
        }
        val repetitionInputs = parsed.repetitions
        if (repetitionInputs == null) {
            fieldErrors.addMissing("repetitions")
        }

        val repetitions = repetitionInputs
            ?.mapIndexedNotNull { index, repetition ->
                val start = repetition.start
                if (start == null || !start.isFinite() || start < 0.0) {
                    fieldErrors += "repetitions[$index].start must be a non-negative finite number"
                    null
                } else {
                    RepetitionMark(start = Seconds(start))
                }
            }
            .orEmpty()

        if (fieldErrors.isNotEmpty()) {
            diagnostics += LibraryDiagnostic(
                severity = Severity.Error,
                fileName = fileName,
                message = "Skipped `$fileName`: ${fieldErrors.joinToString("; ")}.",
            )
            return null
        }
        val validIntro = intro.orEmpty()

        val musicFile = folderChildren[musicFileName]
        if (musicFile == null || !musicFile.canRead()) {
            diagnostics += LibraryDiagnostic(
                severity = Severity.Error,
                fileName = fileName,
                message = "Skipped `$fileName`: music file `$musicFileName` was not found.",
            )
            return null
        }

        val callsRef = optionalContentRef(
            jsonFileName = fileName,
            fieldValue = parsed.callsFile,
            folderChildren = folderChildren,
            diagnostics = diagnostics,
            missingMessage = { trackName, missingFile ->
                "`$trackName` loaded without calls because `$missingFile` was not found."
            },
            trackName = name,
        )
        val instructionsRef = optionalContentRef(
            jsonFileName = fileName,
            fieldValue = parsed.instructionsFile,
            folderChildren = folderChildren,
            diagnostics = diagnostics,
            missingMessage = { trackName, missingFile ->
                "`$trackName` has no instructions because `$missingFile` was not found."
            },
            trackName = name,
        )

        val sortedRepetitions = repetitions.sortedBy { it.start.value }
        if (sortedRepetitions != repetitions) {
            diagnostics += LibraryDiagnostic(
                severity = Severity.Warning,
                fileName = fileName,
                message = "`$fileName` repetitions were sorted by start time.",
            )
        }

        return Track(
            id = TrackId(fileName),
            name = name,
            musicRef = musicFile.toContentRef(musicFileName),
            callsRef = callsRef,
            instructionsRef = instructionsRef,
            intro = validIntro,
            repetitions = sortedRepetitions,
        )
    }

    private fun optionalContentRef(
        jsonFileName: String,
        fieldValue: String?,
        folderChildren: Map<String, DocumentFile>,
        diagnostics: MutableList<LibraryDiagnostic>,
        missingMessage: (trackName: String, missingFile: String) -> String,
        trackName: String,
    ): ContentRef? {
        val fileName = fieldValue?.trim().orEmpty()
        if (fileName.isBlank()) {
            return null
        }

        val document = folderChildren[fileName]
        if (document == null || !document.canRead()) {
            diagnostics += LibraryDiagnostic(
                severity = Severity.Warning,
                fileName = jsonFileName,
                message = missingMessage(trackName, fileName),
            )
            return null
        }

        return document.toContentRef(fileName)
    }

    private fun FolderRef.toDocumentFile(): DocumentFile? =
        DocumentFile.fromTreeUri(appContext, treeUriString.toUri())

    private fun DocumentFile.readText(): String {
        val uri = uri
        try {
            return appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: throw IllegalArgumentException("Unable to open $uri")
        } catch (error: IOException) {
            throw IllegalArgumentException("Unable to read $uri", error)
        } catch (error: SecurityException) {
            throw IllegalArgumentException("Unable to read $uri", error)
        }
    }

    private fun DocumentFile.writeText(text: String) {
        val uri = uri
        try {
            appContext.contentResolver.openOutputStream(uri, WRITE_MODE)?.bufferedWriter()?.use { writer ->
                writer.write(text)
            } ?: throw IllegalArgumentException("Unable to open $uri")
        } catch (error: IOException) {
            throw IllegalArgumentException("Unable to write $uri", error)
        } catch (error: SecurityException) {
            throw IllegalArgumentException("Unable to write $uri", error)
        }
    }

    private fun DocumentFile.toContentRef(fallbackName: String): ContentRef =
        ContentRef(
            displayName = name ?: fallbackName,
            uriString = uri.toString(),
        )

    private fun String?.requiredText(
        fieldName: String,
        fieldErrors: MutableList<String>,
    ): String {
        val value = this?.trim().orEmpty()
        if (value.isBlank()) {
            fieldErrors.addMissing(fieldName)
        }
        return value
    }

    private fun MutableList<String>.addMissing(fieldName: String): Nothing? {
        this += "$fieldName is required"
        return null
    }

    private fun Playlist.toJsonText(): String =
        json.encodeToString(
            PlaylistJson(
                name = name,
                entries = entries.map { entry -> entry.jsonFileName },
            ),
        )

    private fun generatePlaylistFileName(
        folder: DocumentFile,
        playlistName: String,
    ): String {
        val baseName = playlistName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "playlist" }
        var candidate = "$baseName$JSON_EXTENSION"
        var index = 2
        while (folder.findFile(candidate) != null) {
            candidate = "${baseName}_${index}$JSON_EXTENSION"
            index += 1
        }
        return candidate.ifBlank { "playlist_${clock.millis()}$JSON_EXTENSION" }
    }

    private data class TrackScan(
        val summary: FolderSummary?,
        val tracks: List<Track>,
    )

    private data class PlaylistScan(
        val summary: FolderSummary?,
        val playlists: List<ResolvedPlaylist>,
    )

    private companion object {
        const val JSON_EXTENSION = ".json"
        const val JSON_MIME_TYPE = "application/json"
        const val WRITE_MODE = "wt"
    }
}
