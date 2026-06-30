package net.shadowspire.promenade2.data.library

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import net.shadowspire.promenade2.core.model.ContentRef
import net.shadowspire.promenade2.core.model.FolderRef
import net.shadowspire.promenade2.core.model.RepetitionMark
import net.shadowspire.promenade2.core.model.Seconds
import net.shadowspire.promenade2.core.model.Track
import net.shadowspire.promenade2.core.model.TrackId
import net.shadowspire.promenade2.data.diagnostics.LibraryDiagnostic
import net.shadowspire.promenade2.data.diagnostics.Severity
import net.shadowspire.promenade2.data.json.TrackJson

class SafLibraryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun scan(
        tracksFolder: FolderRef?,
        playlistsFolder: FolderRef?,
    ): LibraryState = withContext(Dispatchers.IO) {
        val diagnostics = mutableListOf<LibraryDiagnostic>()
        val tracksScan = scanTracksFolder(tracksFolder, diagnostics)
        val playlistsSummary = scanFolderSummary(playlistsFolder)

        LibraryState(
            tracksFolder = tracksScan.summary,
            playlistsFolder = playlistsSummary,
            tracks = tracksScan.tracks,
            diagnostics = diagnostics,
        )
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

    private fun scanFolderSummary(folder: FolderRef?): FolderSummary? {
        if (folder == null) {
            return null
        }

        val document = folder.toDocumentFile()
        if (document == null || !document.canRead()) {
            return FolderSummary(folder, available = false, itemCount = 0)
        }

        val jsonFileCount = document.listFiles().count { child ->
            child.isFile && child.name?.endsWith(JSON_EXTENSION, ignoreCase = true) == true
        }
        return FolderSummary(folder, available = true, itemCount = jsonFileCount)
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

    private data class TrackScan(
        val summary: FolderSummary?,
        val tracks: List<Track>,
    )

    private companion object {
        const val JSON_EXTENSION = ".json"
    }
}
