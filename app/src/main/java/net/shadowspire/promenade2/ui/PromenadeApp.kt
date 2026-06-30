package net.shadowspire.promenade2.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.shadowspire.promenade2.core.model.Track
import net.shadowspire.promenade2.data.diagnostics.LibraryDiagnostic
import net.shadowspire.promenade2.data.diagnostics.Severity
import net.shadowspire.promenade2.data.library.FolderSummary
import net.shadowspire.promenade2.data.library.LibraryState
import net.shadowspire.promenade2.playback.PlaybackSnapshot
import net.shadowspire.promenade2.playback.PromenadeControllerConnection

@Composable
fun PromenadeApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val connection = remember {
                PromenadeControllerConnection(context.applicationContext)
            }
            val appState = remember {
                PromenadeAppState(context.applicationContext, scope)
            }
            val playback by connection.snapshot.collectAsStateWithLifecycle()
            val library by appState.libraryState.collectAsStateWithLifecycle()
            val tracksFolderPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri != null) {
                    appState.setTracksFolder(uri)
                }
            }
            val playlistsFolderPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri != null) {
                    appState.setPlaylistsFolder(uri)
                }
            }

            DisposableEffect(connection, appState) {
                connection.connect()
                appState.start()
                onDispose {
                    appState.stop()
                    connection.disconnect()
                }
            }

            PromenadeScreen(
                playback = playback,
                library = library,
                onChooseTracksFolder = { tracksFolderPicker.launch(null) },
                onChoosePlaylistsFolder = { playlistsFolderPicker.launch(null) },
                onRescan = appState::rescan,
                onPlayTrack = { track -> connection.loadAndPlay(track.musicRef.uriString.toUri()) },
                onPlay = connection::play,
                onPause = connection::pause,
                onStop = connection::stop,
                onSeek = connection::seekTo,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromenadeScreen(
    playback: PlaybackSnapshot,
    library: LibraryState,
    onChooseTracksFolder: () -> Unit,
    onChoosePlaylistsFolder: () -> Unit,
    onRescan: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Promenade 2") },
                actions = {
                    IconButton(
                        onClick = onRescan,
                        enabled = !library.isScanning,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Rescan folders",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp),
        ) {
            item {
                FolderSetupSection(
                    library = library,
                    onChooseTracksFolder = onChooseTracksFolder,
                    onChoosePlaylistsFolder = onChoosePlaylistsFolder,
                )
            }

            item {
                PlayerSummary(snapshot = playback)
            }

            item {
                PlaybackProgress(
                    snapshot = playback,
                    onSeek = onSeek,
                )
            }

            item {
                PlaybackControls(
                    snapshot = playback,
                    onPlay = onPlay,
                    onPause = onPause,
                    onStop = onStop,
                )
            }

            item {
                SectionHeader(
                    title = "Tracks",
                    supportingText = if (library.isScanning) {
                        "Scanning folders"
                    } else {
                        "${library.tracks.size} available"
                    },
                )
            }

            if (library.tracks.isEmpty()) {
                item {
                    Text(
                        text = "Choose a tracks folder containing Promenade track JSON and audio files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    items = library.tracks,
                    key = { track -> track.id.jsonFileName },
                ) { track ->
                    TrackRow(
                        track = track,
                        onPlayTrack = onPlayTrack,
                    )
                }
            }

            if (library.diagnostics.isNotEmpty()) {
                item {
                    DiagnosticsSection(diagnostics = library.diagnostics)
                }
            }
        }
    }
}

@Composable
private fun FolderSetupSection(
    library: LibraryState,
    onChooseTracksFolder: () -> Unit,
    onChoosePlaylistsFolder: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Folders",
            supportingText = "Tracks are read-only; playlists are stored separately.",
        )
        FolderRow(
            label = "Tracks",
            summary = library.tracksFolder,
            emptyText = "No tracks folder selected",
            onChooseFolder = onChooseTracksFolder,
        )
        FolderRow(
            label = "Playlists",
            summary = library.playlistsFolder,
            emptyText = "No playlists folder selected",
            onChooseFolder = onChoosePlaylistsFolder,
        )
    }
}

@Composable
private fun FolderRow(
    label: String,
    summary: FolderSummary?,
    emptyText: String,
    onChooseFolder: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary?.ref?.displayName ?: emptyText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary.statusText(),
                style = MaterialTheme.typography.bodySmall,
                color = if (summary?.available == false) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedButton(onClick = onChooseFolder) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Choose")
        }
    }
}

@Composable
private fun PlayerSummary(snapshot: PlaybackSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(
            title = "Now Playing",
            supportingText = if (snapshot.isConnected) snapshot.status else "Connecting to playback service",
        )
        Text(
            text = snapshot.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaybackProgress(
    snapshot: PlaybackSnapshot,
    onSeek: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val hasDuration = snapshot.durationMs > 0L
        Slider(
            value = if (hasDuration) {
                snapshot.positionMs.toFloat()
            } else {
                0f
            },
            onValueChange = { position -> onSeek(position.toLong()) },
            valueRange = 0f..snapshot.durationMs.coerceAtLeast(1L).toFloat(),
            enabled = hasDuration,
            modifier = Modifier.semantics {
                contentDescription = "Playback position"
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = formatDuration(snapshot.positionMs))
            Text(text = formatDuration(snapshot.durationMs))
        }

        LinearProgressIndicator(
            progress = { snapshot.bufferedPercentage / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .semantics {
                    contentDescription = "Buffered ${snapshot.bufferedPercentage} percent"
                },
        )
    }
}

@Composable
private fun PlaybackControls(
    snapshot: PlaybackSnapshot,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = if (snapshot.isPlaying) onPause else onPlay,
            enabled = snapshot.canPlay,
        ) {
            Icon(
                imageVector = if (snapshot.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (snapshot.isPlaying) "Pause" else "Play",
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(
            onClick = onStop,
            enabled = snapshot.canPlay,
        ) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "Stop",
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    onPlayTrack: (Track) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.trackSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        TextButton(onClick = { onPlayTrack(track) }) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Play")
        }
    }
}

@Composable
private fun DiagnosticsSection(diagnostics: List<LibraryDiagnostic>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = "Diagnostics",
            supportingText = "${diagnostics.size} issue${if (diagnostics.size == 1) "" else "s"}",
        )
        diagnostics.take(MAX_VISIBLE_DIAGNOSTICS).forEach { diagnostic ->
            Text(
                text = diagnostic.displayText(),
                style = MaterialTheme.typography.bodySmall,
                color = if (diagnostic.severity == Severity.Error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (diagnostics.size > MAX_VISIBLE_DIAGNOSTICS) {
            Text(
                text = "${diagnostics.size - MAX_VISIBLE_DIAGNOSTICS} more issues",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    supportingText: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun FolderSummary?.statusText(): String =
    when {
        this == null -> "Required"
        !available -> "Folder unavailable"
        else -> "$itemCount item${if (itemCount == 1) "" else "s"}"
    }

private fun Track.trackSummary(): String {
    val calls = if (callsRef == null) "music only" else "music + calls"
    return "$intro · ${repetitions.size} repetition${if (repetitions.size == 1) "" else "s"} · $calls"
}

private fun LibraryDiagnostic.displayText(): String {
    val prefix = when (severity) {
        Severity.Error -> "Error"
        Severity.Warning -> "Warning"
    }
    val file = fileName?.let { " [$it]" }.orEmpty()
    return "$prefix$file: $message"
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private const val MAX_VISIBLE_DIAGNOSTICS = 8
