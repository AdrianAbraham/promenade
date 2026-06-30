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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.shadowspire.promenade2.core.model.AutoMuteSettings
import net.shadowspire.promenade2.core.model.Playlist
import net.shadowspire.promenade2.core.model.PlaylistId
import net.shadowspire.promenade2.core.model.PlaybackSettings
import net.shadowspire.promenade2.core.model.Track
import net.shadowspire.promenade2.data.diagnostics.LibraryDiagnostic
import net.shadowspire.promenade2.data.diagnostics.Severity
import net.shadowspire.promenade2.data.library.FolderSummary
import net.shadowspire.promenade2.data.library.LibraryState
import net.shadowspire.promenade2.data.playlist.PlaylistEntryResolution
import net.shadowspire.promenade2.data.playlist.ResolvedPlaylist
import net.shadowspire.promenade2.data.preferences.AppPreferences
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
            val preferences by appState.preferencesState.collectAsStateWithLifecycle()
            val activePlaylistId by appState.activePlaylistId.collectAsStateWithLifecycle()
            val activePlaylist = library.playlists.firstOrNull { playlist ->
                playlist.playlist.id == activePlaylistId
            }
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

            LaunchedEffect(playback.isConnected, preferences.playbackSettings) {
                if (playback.isConnected) {
                    connection.setBalance(preferences.playbackSettings.balance)
                    connection.setCallsMuted(preferences.playbackSettings.callsMuted)
                    connection.setAutoMute(preferences.playbackSettings.autoMute)
                    connection.setPlaybackDelay(preferences.playbackSettings.playbackDelay)
                }
            }

            PromenadeScreen(
                playback = playback,
                library = library,
                preferences = preferences,
                activePlaylist = activePlaylist,
                onChooseTracksFolder = { tracksFolderPicker.launch(null) },
                onChoosePlaylistsFolder = { playlistsFolderPicker.launch(null) },
                onRescan = appState::rescan,
                onCreatePlaylist = appState::createPlaylist,
                onSelectPlaylist = appState::selectPlaylist,
                onDeletePlaylist = appState::deletePlaylist,
                onLoadPlaylist = { playlist ->
                    val resolvedTracks = playlist.resolvedTracks
                    if (resolvedTracks.isNotEmpty()) {
                        connection.loadQueue(resolvedTracks, startIndex = 0, autoplay = false)
                    }
                },
                onPlayPlaylistEntry = { playlist, entry ->
                    val track = entry.track
                    if (track != null) {
                        val resolvedIndex = playlist.entries
                            .take(entry.index + 1)
                            .count { playlistEntry -> playlistEntry.track != null } - 1
                        connection.loadQueue(playlist.resolvedTracks, resolvedIndex.coerceAtLeast(0), autoplay = true)
                    }
                },
                onAddTrackToPlaylist = { playlist, track ->
                    appState.addTrackToPlaylist(playlist, track)
                },
                onRemovePlaylistEntry = appState::removePlaylistEntry,
                onMovePlaylistEntry = appState::movePlaylistEntry,
                onPlayTrack = { track ->
                    val index = library.tracks.indexOf(track)
                    if (index >= 0) {
                        connection.setCallsMuted(false)
                        appState.updatePreferences { current ->
                            current.copy(
                                playbackSettings = current.playbackSettings.copy(callsMuted = false),
                            )
                        }
                        connection.loadQueue(library.tracks, index, autoplay = true)
                    }
                },
                onPlay = connection::play,
                onPause = connection::pause,
                onStop = connection::stop,
                onSeek = connection::seekTo,
                onPrevious = connection::skipPrevious,
                onNext = connection::skipNext,
                onSetBalance = { balance ->
                    connection.setBalance(balance)
                    appState.updatePreferences { current ->
                        current.copy(
                            playbackSettings = current.playbackSettings.copy(balance = balance),
                        )
                    }
                },
                onSetCallsMuted = { muted ->
                    connection.setCallsMuted(muted)
                    appState.updatePreferences { current ->
                        current.copy(
                            playbackSettings = current.playbackSettings.copy(callsMuted = muted),
                        )
                    }
                },
                onSetAutoMute = { autoMute ->
                    connection.setAutoMute(autoMute)
                    appState.updatePreferences { current ->
                        current.copy(
                            playbackSettings = current.playbackSettings.copy(autoMute = autoMute),
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromenadeScreen(
    playback: PlaybackSnapshot,
    library: LibraryState,
    preferences: AppPreferences,
    activePlaylist: ResolvedPlaylist?,
    onChooseTracksFolder: () -> Unit,
    onChoosePlaylistsFolder: () -> Unit,
    onRescan: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (PlaylistId?) -> Unit,
    onDeletePlaylist: (PlaylistId) -> Unit,
    onLoadPlaylist: (ResolvedPlaylist) -> Unit,
    onPlayPlaylistEntry: (ResolvedPlaylist, PlaylistEntryResolution) -> Unit,
    onAddTrackToPlaylist: (Playlist, Track) -> Unit,
    onRemovePlaylistEntry: (Playlist, Int) -> Unit,
    onMovePlaylistEntry: (Playlist, Int, Int) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSetBalance: (Float) -> Unit,
    onSetCallsMuted: (Boolean) -> Unit,
    onSetAutoMute: (AutoMuteSettings) -> Unit,
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
                PlaylistSection(
                    library = library,
                    activePlaylist = activePlaylist,
                    playback = playback,
                    onCreatePlaylist = onCreatePlaylist,
                    onSelectPlaylist = onSelectPlaylist,
                    onDeletePlaylist = onDeletePlaylist,
                    onLoadPlaylist = onLoadPlaylist,
                    onPlayPlaylistEntry = onPlayPlaylistEntry,
                    onRemovePlaylistEntry = onRemovePlaylistEntry,
                    onMovePlaylistEntry = onMovePlaylistEntry,
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
                    onPrevious = onPrevious,
                    onNext = onNext,
                )
            }

            item {
                PracticeControls(
                    playback = playback,
                    settings = preferences.playbackSettings,
                    onSetBalance = onSetBalance,
                    onSetCallsMuted = onSetCallsMuted,
                    onSetAutoMute = onSetAutoMute,
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
                    items = library.tracks.withIndex().toList(),
                    key = { indexedTrack -> indexedTrack.value.id.jsonFileName },
                ) { indexedTrack ->
                    TrackRow(
                        track = indexedTrack.value,
                        isCurrent = playback.currentIndex == indexedTrack.index,
                        activePlaylist = activePlaylist?.playlist,
                        onPlayTrack = onPlayTrack,
                        onAddTrackToPlaylist = onAddTrackToPlaylist,
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
private fun PlaylistSection(
    library: LibraryState,
    activePlaylist: ResolvedPlaylist?,
    playback: PlaybackSnapshot,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (PlaylistId?) -> Unit,
    onDeletePlaylist: (PlaylistId) -> Unit,
    onLoadPlaylist: (ResolvedPlaylist) -> Unit,
    onPlayPlaylistEntry: (ResolvedPlaylist, PlaylistEntryResolution) -> Unit,
    onRemovePlaylistEntry: (Playlist, Int) -> Unit,
    onMovePlaylistEntry: (Playlist, Int, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Playlists",
            supportingText = if (library.playlistsFolder?.available == true) {
                "${library.playlists.size} playlist${if (library.playlists.size == 1) "" else "s"}"
            } else {
                "Choose a playlists folder to create and edit playlists."
            },
        )
        Button(
            onClick = onCreatePlaylist,
            enabled = library.playlistsFolder?.available == true,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "New playlist")
        }
        library.playlists.forEach { playlist ->
            PlaylistRow(
                playlist = playlist,
                isActive = activePlaylist?.playlist?.id == playlist.playlist.id,
                onSelectPlaylist = onSelectPlaylist,
                onDeletePlaylist = onDeletePlaylist,
            )
        }
        if (activePlaylist != null) {
            ActivePlaylistEditor(
                playlist = activePlaylist,
                playback = playback,
                onLoadPlaylist = onLoadPlaylist,
                onPlayPlaylistEntry = onPlayPlaylistEntry,
                onRemovePlaylistEntry = onRemovePlaylistEntry,
                onMovePlaylistEntry = onMovePlaylistEntry,
            )
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: ResolvedPlaylist,
    isActive: Boolean,
    onSelectPlaylist: (PlaylistId?) -> Unit,
    onDeletePlaylist: (PlaylistId) -> Unit,
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
                text = if (isActive) "Editing: ${playlist.playlist.name}" else playlist.playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.entries.size} entries, ${playlist.unresolvedCount} unresolved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = { onSelectPlaylist(playlist.playlist.id) }) {
            Text(text = if (isActive) "Selected" else "Edit")
        }
        IconButton(onClick = { onDeletePlaylist(playlist.playlist.id) }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete ${playlist.playlist.name}",
            )
        }
    }
}

@Composable
private fun ActivePlaylistEditor(
    playlist: ResolvedPlaylist,
    playback: PlaybackSnapshot,
    onLoadPlaylist: (ResolvedPlaylist) -> Unit,
    onPlayPlaylistEntry: (ResolvedPlaylist, PlaylistEntryResolution) -> Unit,
    onRemovePlaylistEntry: (Playlist, Int) -> Unit,
    onMovePlaylistEntry: (Playlist, Int, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(
                title = playlist.playlist.name,
                supportingText = "Duplicate and unresolved entries are preserved.",
            )
            OutlinedButton(
                onClick = { onLoadPlaylist(playlist) },
                enabled = playlist.resolvedTracks.isNotEmpty(),
            ) {
                Text(text = "Load")
            }
        }
        if (playlist.entries.isEmpty()) {
            Text(
                text = "Add tracks from the track list below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            playlist.entries.forEach { entry ->
                PlaylistEntryRow(
                    playlist = playlist.playlist,
                    entry = entry,
                    isCurrent = playback.currentIndex == resolvedIndexFor(playlist, entry),
                    onPlayPlaylistEntry = { onPlayPlaylistEntry(playlist, entry) },
                    onRemovePlaylistEntry = onRemovePlaylistEntry,
                    onMovePlaylistEntry = onMovePlaylistEntry,
                )
            }
        }
    }
}

@Composable
private fun PlaylistEntryRow(
    playlist: Playlist,
    entry: PlaylistEntryResolution,
    isCurrent: Boolean,
    onPlayPlaylistEntry: () -> Unit,
    onRemovePlaylistEntry: (Playlist, Int) -> Unit,
    onMovePlaylistEntry: (Playlist, Int, Int) -> Unit,
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
                text = when {
                    entry.track != null && isCurrent -> "Now: ${entry.track.name}"
                    entry.track != null -> entry.track.name
                    else -> "Missing: ${entry.trackId.jsonFileName}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.track == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Entry ${entry.index + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = { onMovePlaylistEntry(playlist, entry.index, entry.index - 1) },
            enabled = entry.index > 0,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Move entry up",
            )
        }
        IconButton(
            onClick = { onMovePlaylistEntry(playlist, entry.index, entry.index + 1) },
            enabled = entry.index < playlist.entries.lastIndex,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Move entry down",
            )
        }
        IconButton(
            onClick = onPlayPlaylistEntry,
            enabled = entry.track != null,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play playlist entry",
            )
        }
        IconButton(onClick = { onRemovePlaylistEntry(playlist, entry.index) }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove playlist entry",
            )
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
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = snapshot.canPlay,
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Previous track",
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
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
            onClick = onNext,
            enabled = snapshot.canPlay,
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next track",
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
private fun PracticeControls(
    playback: PlaybackSnapshot,
    settings: PlaybackSettings,
    onSetBalance: (Float) -> Unit,
    onSetCallsMuted: (Boolean) -> Unit,
    onSetAutoMute: (AutoMuteSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Practice",
            supportingText = playback.practiceStatus(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Music / calls balance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = settings.balance,
                onValueChange = onSetBalance,
                valueRange = 0f..1f,
                modifier = Modifier.semantics {
                    contentDescription = "Music calls balance"
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "Music")
                Text(text = "Calls")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onSetCallsMuted(!settings.callsMuted) },
                enabled = playback.hasCalls || settings.callsMuted,
            ) {
                Text(text = if (settings.callsMuted) "Unmute calls" else "Mute calls")
            }
        }
        AutoMuteControls(
            settings = settings.autoMute,
            onSetAutoMute = onSetAutoMute,
        )
    }
}

@Composable
private fun AutoMuteControls(
    settings: AutoMuteSettings,
    onSetAutoMute: (AutoMuteSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Auto-mute",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onSetAutoMute(AutoMuteSettings()) },
                enabled = settings.isEnabled,
            ) {
                Text(text = "Off")
            }
            OutlinedButton(
                onClick = { onSetAutoMute(AutoMuteSettings(muteAfterRepetition = 2)) },
                enabled = settings.muteAfterRepetition != 2,
            ) {
                Text(text = "After 2")
            }
            OutlinedButton(
                onClick = { onSetAutoMute(AutoMuteSettings(muteWithRepetitionsRemaining = 1)) },
                enabled = settings.muteWithRepetitionsRemaining != 1,
            ) {
                Text(text = "Last rep")
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    activePlaylist: Playlist?,
    onPlayTrack: (Track) -> Unit,
    onAddTrackToPlaylist: (Playlist, Track) -> Unit,
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
                text = if (isCurrent) "Now: ${track.name}" else track.name,
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
        if (activePlaylist != null) {
            TextButton(onClick = { onAddTrackToPlaylist(activePlaylist, track) }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Add")
            }
        }
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
    return "$intro - ${repetitions.size} repetition${if (repetitions.size == 1) "" else "s"} - $calls"
}

private fun resolvedIndexFor(
    playlist: ResolvedPlaylist,
    entry: PlaylistEntryResolution,
): Int? {
    if (entry.track == null) {
        return null
    }
    return playlist.entries
        .take(entry.index + 1)
        .count { playlistEntry -> playlistEntry.track != null } - 1
}

private fun PlaybackSnapshot.practiceStatus(): String {
    val queueText = if (currentIndex == null || queueSize == 0) {
        "No queue"
    } else {
        "Track ${currentIndex + 1} of $queueSize"
    }
    val repetitionText = if (totalRepetitions == 0) {
        "no repetitions"
    } else {
        "rep $currentRepetition of $totalRepetitions"
    }
    val callsText = if (hasCalls) "calls available" else "music only"
    return "$queueText - $repetitionText - $callsText"
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
