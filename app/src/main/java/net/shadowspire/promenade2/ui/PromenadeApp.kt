package net.shadowspire.promenade2.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.shadowspire.promenade2.core.model.AutoMuteSettings
import net.shadowspire.promenade2.core.model.PlaybackDelaySettings
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
import net.shadowspire.promenade2.domain.instructions.MarkdownBlock
import net.shadowspire.promenade2.domain.instructions.MarkdownInline
import net.shadowspire.promenade2.domain.instructions.MarkdownInstructionDocument
import net.shadowspire.promenade2.domain.instructions.MarkdownInstructionParser
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
            var destinationName by rememberSaveable {
                mutableStateOf(PromenadeDestination.Player.name)
            }
            var editingPlaylistFileName by rememberSaveable {
                mutableStateOf<String?>(null)
            }
            val destination = PromenadeDestination.valueOf(destinationName)
            val editingPlaylist = library.playlists.firstOrNull { playlist ->
                playlist.playlist.id.fileName == editingPlaylistFileName
            }
            val currentTrack = currentTrackFor(
                library = library,
                activePlaylist = activePlaylist,
                playback = playback,
            )
            var restoredInitialSelection by rememberSaveable {
                mutableStateOf(false)
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

            LaunchedEffect(
                playback.isConnected,
                library.isScanning,
                library.playlists,
                library.tracks,
                preferences.lastPlaylistId,
                preferences.lastPlaylistEntryIndex,
                preferences.lastTrackId,
            ) {
                if (playback.isConnected && !library.isScanning && !restoredInitialSelection) {
                    restoreLastLoadedSelection(
                        preferences = preferences,
                        library = library,
                        connection = connection,
                    )
                    restoredInitialSelection = true
                }
            }

            PromenadeScreen(
                destination = destination,
                playback = playback,
                library = library,
                preferences = preferences,
                activePlaylist = activePlaylist,
                currentTrack = currentTrack,
                editingPlaylist = editingPlaylist,
                onNavigateToPlayer = { destinationName = PromenadeDestination.Player.name },
                onNavigateToPlaylists = { destinationName = PromenadeDestination.Playlists.name },
                onNavigateToSettings = { destinationName = PromenadeDestination.Settings.name },
                onNavigateToInstructions = { destinationName = PromenadeDestination.Instructions.name },
                onNavigateToDiagnostics = { destinationName = PromenadeDestination.Diagnostics.name },
                onChooseTracksFolder = { tracksFolderPicker.launch(null) },
                onChoosePlaylistsFolder = { playlistsFolderPicker.launch(null) },
                onRescan = appState::rescan,
                onCreatePlaylist = appState::createPlaylist,
                onDeletePlaylist = appState::deletePlaylist,
                onSelectPlaylist = { playlist ->
                    appState.selectPlaylist(playlist.playlist.id)
                    appState.saveLastPlaylistSelection(playlist.playlist.id, entryIndex = 0)
                    val resolvedTracks = playlist.resolvedTracks
                    if (resolvedTracks.isNotEmpty()) {
                        connection.loadQueue(resolvedTracks, startIndex = 0, autoplay = false)
                    }
                    destinationName = PromenadeDestination.Player.name
                },
                onEditPlaylist = { playlist ->
                    editingPlaylistFileName = playlist.playlist.id.fileName
                },
                onLoadPlaylist = { playlist ->
                    appState.selectPlaylist(playlist.playlist.id)
                    appState.saveLastPlaylistSelection(playlist.playlist.id, entryIndex = 0)
                    val resolvedTracks = playlist.resolvedTracks
                    if (resolvedTracks.isNotEmpty()) {
                        connection.loadQueue(resolvedTracks, startIndex = 0, autoplay = false)
                    }
                },
                onLoadPlaylistEntry = { playlist, entry ->
                    val track = entry.track
                    if (track != null) {
                        appState.selectPlaylist(playlist.playlist.id)
                        appState.saveLastPlaylistSelection(playlist.playlist.id, entry.index)
                        val resolvedIndex = playlist.entries
                            .take(entry.index + 1)
                            .count { playlistEntry -> playlistEntry.track != null } - 1
                        connection.loadQueue(playlist.resolvedTracks, resolvedIndex.coerceAtLeast(0), autoplay = false)
                    }
                },
                onAddTrackToPlaylist = { playlist, track ->
                    appState.addTrackToPlaylist(playlist, track)
                },
                onRemovePlaylistEntry = appState::removePlaylistEntry,
                onMovePlaylistEntry = appState::movePlaylistEntry,
                onLoadTrack = { track ->
                    val index = library.tracks.indexOf(track)
                    if (index >= 0) {
                        appState.saveLastTrackSelection(track)
                        connection.setCallsMuted(false)
                        appState.updatePreferences { current ->
                            current.copy(
                                playbackSettings = current.playbackSettings.copy(callsMuted = false),
                            )
                        }
                        connection.loadQueue(library.tracks, index, autoplay = false)
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
                onSetPlaybackDelay = { playbackDelay ->
                    connection.setPlaybackDelay(playbackDelay)
                    appState.updatePreferences { current ->
                        current.copy(
                            playbackSettings = current.playbackSettings.copy(playbackDelay = playbackDelay),
                        )
                    }
                },
            )
        }
    }
}

private enum class PromenadeDestination {
    Player,
    Playlists,
    Settings,
    Instructions,
    Diagnostics,
}

private fun restoreLastLoadedSelection(
    preferences: AppPreferences,
    library: LibraryState,
    connection: PromenadeControllerConnection,
) {
    val playlist = preferences.lastPlaylistId?.let { playlistId ->
        library.playlists.firstOrNull { resolvedPlaylist ->
            resolvedPlaylist.playlist.id == playlistId
        }
    }
    if (playlist != null && playlist.resolvedTracks.isNotEmpty()) {
        val savedEntry = preferences.lastPlaylistEntryIndex?.let { entryIndex ->
            playlist.entries.getOrNull(entryIndex)
        }?.takeIf { entry -> entry.track != null }
        val startIndex = savedEntry
            ?.let { entry -> resolvedIndexFor(playlist, entry) }
            ?.coerceIn(0, playlist.resolvedTracks.lastIndex)
            ?: 0
        connection.loadQueue(playlist.resolvedTracks, startIndex, autoplay = false)
        return
    }

    val trackIndex = preferences.lastTrackId?.let { trackId ->
        library.tracks.indexOfFirst { track -> track.id == trackId }
    } ?: -1
    if (trackIndex >= 0) {
        connection.loadQueue(library.tracks, trackIndex, autoplay = false)
    }
}

private fun currentTrackFor(
    library: LibraryState,
    activePlaylist: ResolvedPlaylist?,
    playback: PlaybackSnapshot,
): Track? {
    val index = playback.currentIndex ?: return null
    val title = playback.title
    val playlistTrack = activePlaylist?.resolvedTracks?.getOrNull(index)
    if (playlistTrack?.name == title) {
        return playlistTrack
    }
    return library.tracks.getOrNull(index)?.takeIf { track -> track.name == title }
        ?: playlistTrack
        ?: library.tracks.getOrNull(index)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromenadeScreen(
    destination: PromenadeDestination,
    playback: PlaybackSnapshot,
    library: LibraryState,
    preferences: AppPreferences,
    activePlaylist: ResolvedPlaylist?,
    currentTrack: Track?,
    editingPlaylist: ResolvedPlaylist?,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToInstructions: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onChooseTracksFolder: () -> Unit,
    onChoosePlaylistsFolder: () -> Unit,
    onRescan: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (ResolvedPlaylist) -> Unit,
    onEditPlaylist: (ResolvedPlaylist) -> Unit,
    onDeletePlaylist: (PlaylistId) -> Unit,
    onLoadPlaylist: (ResolvedPlaylist) -> Unit,
    onLoadPlaylistEntry: (ResolvedPlaylist, PlaylistEntryResolution) -> Unit,
    onAddTrackToPlaylist: (Playlist, Track) -> Unit,
    onRemovePlaylistEntry: (Playlist, Int) -> Unit,
    onMovePlaylistEntry: (Playlist, Int, Int) -> Unit,
    onLoadTrack: (Track) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSetBalance: (Float) -> Unit,
    onSetCallsMuted: (Boolean) -> Unit,
    onSetAutoMute: (AutoMuteSettings) -> Unit,
    onSetPlaybackDelay: (PlaybackDelaySettings) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (destination) {
                            PromenadeDestination.Player -> "Promenade 2"
                            PromenadeDestination.Playlists -> "Playlists"
                            PromenadeDestination.Settings -> "Settings"
                            PromenadeDestination.Instructions -> "Instructions"
                            PromenadeDestination.Diagnostics -> "Diagnostics"
                        },
                    )
                },
                actions = {
                    if (destination != PromenadeDestination.Player) {
                        IconButton(onClick = onNavigateToPlayer) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Open player",
                            )
                        }
                    }
                    if (destination != PromenadeDestination.Playlists) {
                        IconButton(onClick = onNavigateToPlaylists) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Open playlists",
                            )
                        }
                    }
                    if (destination != PromenadeDestination.Instructions) {
                        IconButton(onClick = onNavigateToInstructions) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = "Open instructions",
                            )
                        }
                    }
                    if (destination != PromenadeDestination.Diagnostics) {
                        IconButton(onClick = onNavigateToDiagnostics) {
                            BadgedBox(
                                badge = {
                                    if (library.diagnostics.isNotEmpty()) {
                                        Badge {
                                            Text(text = library.diagnostics.size.coerceAtMost(99).toString())
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = diagnosticsContentDescription(library.diagnostics.size),
                                    tint = if (library.diagnostics.isEmpty()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            }
                        }
                    }
                    if (destination != PromenadeDestination.Settings) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Open settings",
                            )
                        }
                    }
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
            when (destination) {
                PromenadeDestination.Player -> {
                    playerContent(
                        library = library,
                        activePlaylist = activePlaylist,
                        currentTrack = currentTrack,
                        playback = playback,
                        preferences = preferences,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToInstructions = onNavigateToInstructions,
                        onLoadPlaylistEntry = onLoadPlaylistEntry,
                        onLoadTrack = onLoadTrack,
                        onAddTrackToPlaylist = onAddTrackToPlaylist,
                        onPlay = onPlay,
                        onPause = onPause,
                        onStop = onStop,
                        onSeek = onSeek,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onSetBalance = onSetBalance,
                        onSetCallsMuted = onSetCallsMuted,
                        onSetAutoMute = onSetAutoMute,
                        onSetPlaybackDelay = onSetPlaybackDelay,
                    )
                }

                PromenadeDestination.Playlists -> {
                    playlistsContent(
                        library = library,
                        activePlaylist = activePlaylist,
                        editingPlaylist = editingPlaylist,
                        playback = playback,
                        onCreatePlaylist = onCreatePlaylist,
                        onSelectPlaylist = onSelectPlaylist,
                        onEditPlaylist = onEditPlaylist,
                        onDeletePlaylist = onDeletePlaylist,
                        onLoadPlaylist = onLoadPlaylist,
                        onLoadPlaylistEntry = onLoadPlaylistEntry,
                        onAddTrackToPlaylist = onAddTrackToPlaylist,
                        onRemovePlaylistEntry = onRemovePlaylistEntry,
                        onMovePlaylistEntry = onMovePlaylistEntry,
                        onLoadTrack = onLoadTrack,
                    )
                }

                PromenadeDestination.Settings -> {
                    item {
                        FolderSetupSection(
                            library = library,
                            onChooseTracksFolder = onChooseTracksFolder,
                            onChoosePlaylistsFolder = onChoosePlaylistsFolder,
                        )
                    }
                    diagnosticsContent(library.diagnostics)
                }

                PromenadeDestination.Instructions -> {
                    item {
                        InstructionsScreen(currentTrack = currentTrack)
                    }
                }

                PromenadeDestination.Diagnostics -> {
                    diagnosticsScreenContent(library.diagnostics)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.playerContent(
    library: LibraryState,
    activePlaylist: ResolvedPlaylist?,
    currentTrack: Track?,
    playback: PlaybackSnapshot,
    preferences: AppPreferences,
    onNavigateToSettings: () -> Unit,
    onNavigateToInstructions: () -> Unit,
    onLoadPlaylistEntry: (ResolvedPlaylist, PlaylistEntryResolution) -> Unit,
    onLoadTrack: (Track) -> Unit,
    onAddTrackToPlaylist: (Playlist, Track) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSetBalance: (Float) -> Unit,
    onSetCallsMuted: (Boolean) -> Unit,
    onSetAutoMute: (AutoMuteSettings) -> Unit,
    onSetPlaybackDelay: (PlaybackDelaySettings) -> Unit,
) {
    if (library.tracksFolder?.available != true || library.playlistsFolder?.available != true) {
        item {
            FolderRepairBanner(
                library = library,
                onNavigateToSettings = onNavigateToSettings,
            )
        }
    }

    item {
        CurrentTrackPanel(
            track = currentTrack,
            snapshot = playback,
            onNavigateToInstructions = onNavigateToInstructions,
        )
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
            onSetPlaybackDelay = onSetPlaybackDelay,
        )
    }

    if (activePlaylist != null) {
        item {
            SectionHeader(
                title = activePlaylist.playlist.name,
                supportingText = "Tap a track to load it paused.",
            )
        }
        playlistEntryItems(
            playlist = activePlaylist,
            playback = playback,
            onLoadPlaylistEntry = onLoadPlaylistEntry,
            onRemovePlaylistEntry = null,
            onMovePlaylistEntry = null,
        )
    }

    trackListContent(
        library = library,
        playback = playback,
        activePlaylist = activePlaylist?.playlist,
        onLoadTrack = onLoadTrack,
        onAddTrackToPlaylist = onAddTrackToPlaylist,
    )

    diagnosticsContent(library.diagnostics)
}

private fun androidx.compose.foundation.lazy.LazyListScope.playlistsContent(
    library: LibraryState,
    activePlaylist: ResolvedPlaylist?,
    editingPlaylist: ResolvedPlaylist?,
    playback: PlaybackSnapshot,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (ResolvedPlaylist) -> Unit,
    onEditPlaylist: (ResolvedPlaylist) -> Unit,
    onDeletePlaylist: (PlaylistId) -> Unit,
    onLoadPlaylist: (ResolvedPlaylist) -> Unit,
    onLoadPlaylistEntry: (ResolvedPlaylist, PlaylistEntryResolution) -> Unit,
    onAddTrackToPlaylist: (Playlist, Track) -> Unit,
    onRemovePlaylistEntry: (Playlist, Int) -> Unit,
    onMovePlaylistEntry: (Playlist, Int, Int) -> Unit,
    onLoadTrack: (Track) -> Unit,
) {
    item {
        PlaylistPickerSection(
            library = library,
            activePlaylist = activePlaylist,
            onCreatePlaylist = onCreatePlaylist,
            onSelectPlaylist = onSelectPlaylist,
            onEditPlaylist = onEditPlaylist,
            onDeletePlaylist = onDeletePlaylist,
        )
    }

    if (editingPlaylist != null) {
        item {
            ActivePlaylistEditor(
                playlist = editingPlaylist,
                playback = playback,
                onLoadPlaylist = onLoadPlaylist,
                onLoadPlaylistEntry = onLoadPlaylistEntry,
                onRemovePlaylistEntry = onRemovePlaylistEntry,
                onMovePlaylistEntry = onMovePlaylistEntry,
            )
        }

        item {
            SectionHeader(
                title = "Add Tracks",
                supportingText = "Available tracks from the tracks folder.",
            )
        }

        trackRows(
            tracks = library.tracks,
            playback = playback,
            activePlaylist = editingPlaylist.playlist,
            onLoadTrack = onLoadTrack,
            onAddTrackToPlaylist = onAddTrackToPlaylist,
        )
    }

    diagnosticsContent(library.diagnostics)
}

@Composable
private fun PlaylistPickerSection(
    library: LibraryState,
    activePlaylist: ResolvedPlaylist?,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (ResolvedPlaylist) -> Unit,
    onEditPlaylist: (ResolvedPlaylist) -> Unit,
    onDeletePlaylist: (PlaylistId) -> Unit,
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
            PlaylistPickerRow(
                playlist = playlist,
                isActive = activePlaylist?.playlist?.id == playlist.playlist.id,
                onSelectPlaylist = onSelectPlaylist,
                onEditPlaylist = onEditPlaylist,
                onDeletePlaylist = onDeletePlaylist,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistPickerRow(
    playlist: ResolvedPlaylist,
    isActive: Boolean,
    onSelectPlaylist: (ResolvedPlaylist) -> Unit,
    onEditPlaylist: (ResolvedPlaylist) -> Unit,
    onDeletePlaylist: (PlaylistId) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = "Select playlist",
                onClick = { onSelectPlaylist(playlist) },
                onLongClickLabel = "Edit playlist",
                onLongClick = { onEditPlaylist(playlist) },
            )
            .semantics {
                contentDescription = "Playlist ${playlist.playlist.name}. Tap to select. Long press to edit."
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (isActive) "Selected: ${playlist.playlist.name}" else playlist.playlist.name,
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
        TextButton(onClick = { onEditPlaylist(playlist) }) {
            Text(text = "Edit")
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
    onLoadPlaylistEntry: (ResolvedPlaylist, PlaylistEntryResolution) -> Unit,
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
                    onLoadPlaylistEntry = { onLoadPlaylistEntry(playlist, entry) },
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
    onLoadPlaylistEntry: () -> Unit,
    onRemovePlaylistEntry: ((Playlist, Int) -> Unit)?,
    onMovePlaylistEntry: ((Playlist, Int, Int) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = entry.track != null,
                onClickLabel = "Load playlist entry paused",
                onClick = onLoadPlaylistEntry,
            )
            .semantics {
                contentDescription = if (entry.track == null) {
                    "Missing playlist entry ${entry.trackId.jsonFileName}"
                } else {
                    "Load ${entry.track.name} paused"
                }
            },
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
        if (onMovePlaylistEntry != null) {
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
        }
        if (onRemovePlaylistEntry != null) {
            IconButton(onClick = { onRemovePlaylistEntry(playlist, entry.index) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove playlist entry",
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.playlistEntryItems(
    playlist: ResolvedPlaylist,
    playback: PlaybackSnapshot,
    onLoadPlaylistEntry: (ResolvedPlaylist, PlaylistEntryResolution) -> Unit,
    onRemovePlaylistEntry: ((Playlist, Int) -> Unit)?,
    onMovePlaylistEntry: ((Playlist, Int, Int) -> Unit)?,
) {
    if (playlist.entries.isEmpty()) {
        item {
            Text(
                text = "No tracks in this playlist.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(
            items = playlist.entries,
            key = { entry -> "${playlist.playlist.id.fileName}:${entry.index}" },
        ) { entry ->
            PlaylistEntryRow(
                playlist = playlist.playlist,
                entry = entry,
                isCurrent = playback.currentIndex == resolvedIndexFor(playlist, entry),
                onLoadPlaylistEntry = { onLoadPlaylistEntry(playlist, entry) },
                onRemovePlaylistEntry = onRemovePlaylistEntry,
                onMovePlaylistEntry = onMovePlaylistEntry,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trackListContent(
    library: LibraryState,
    playback: PlaybackSnapshot,
    activePlaylist: Playlist?,
    onLoadTrack: (Track) -> Unit,
    onAddTrackToPlaylist: (Playlist, Track) -> Unit,
) {
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
        trackRows(
            tracks = library.tracks,
            playback = playback,
            activePlaylist = activePlaylist,
            onLoadTrack = onLoadTrack,
            onAddTrackToPlaylist = onAddTrackToPlaylist,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trackRows(
    tracks: List<Track>,
    playback: PlaybackSnapshot,
    activePlaylist: Playlist?,
    onLoadTrack: (Track) -> Unit,
    onAddTrackToPlaylist: (Playlist, Track) -> Unit,
) {
    items(
        items = tracks.withIndex().toList(),
        key = { indexedTrack -> indexedTrack.value.id.jsonFileName },
    ) { indexedTrack ->
        TrackRow(
            track = indexedTrack.value,
            isCurrent = playback.currentIndex == indexedTrack.index,
            activePlaylist = activePlaylist,
            onLoadTrack = onLoadTrack,
            onAddTrackToPlaylist = onAddTrackToPlaylist,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.diagnosticsContent(
    diagnostics: List<LibraryDiagnostic>,
) {
    if (diagnostics.isNotEmpty()) {
        item {
            DiagnosticsSection(diagnostics = diagnostics)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.diagnosticsScreenContent(
    diagnostics: List<LibraryDiagnostic>,
) {
    item {
        SectionHeader(
            title = "Diagnostics",
            supportingText = if (diagnostics.isEmpty()) {
                "No library or playback issues."
            } else {
                "${diagnostics.size} issue${if (diagnostics.size == 1) "" else "s"} found."
            },
        )
    }
    if (diagnostics.isEmpty()) {
        item {
            Text(
                text = "Everything scanned cleanly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(
            items = diagnostics.withIndex().toList(),
            key = { indexedDiagnostic -> "${indexedDiagnostic.index}:${indexedDiagnostic.value.displayText()}" },
        ) { indexedDiagnostic ->
            DiagnosticRow(diagnostic = indexedDiagnostic.value)
        }
    }
}

@Composable
private fun FolderRepairBanner(
    library: LibraryState,
    onNavigateToSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = "Folder attention needed",
            supportingText = listOfNotNull(
                "Tracks".takeIf { library.tracksFolder?.available != true },
                "Playlists".takeIf { library.playlistsFolder?.available != true },
            ).joinToString(prefix = "Open Settings to repair: "),
        )
        OutlinedButton(onClick = onNavigateToSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Settings")
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
private fun CurrentTrackPanel(
    track: Track?,
    snapshot: PlaybackSnapshot,
    onNavigateToInstructions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(
            title = "Current Track",
            supportingText = if (snapshot.isConnected) snapshot.status else "Connecting to playback service",
        )
        Text(
            text = track?.name ?: snapshot.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (track != null) {
            Text(
                text = track.trackSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(
                onClick = onNavigateToInstructions,
                enabled = track.instructionsRef != null,
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Instructions")
            }
        }
    }
}

@Composable
private fun InstructionsScreen(currentTrack: Track?) {
    val context = LocalContext.current
    var instructionText by remember(currentTrack?.id) {
        mutableStateOf<String?>(null)
    }
    var instructionError by remember(currentTrack?.id) {
        mutableStateOf<String?>(null)
    }
    val instructionsUri = currentTrack?.instructionsRef?.uriString

    LaunchedEffect(instructionsUri) {
        instructionText = null
        instructionError = null
        if (instructionsUri != null) {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(instructionsUri.toUri())
                        ?.bufferedReader()
                        ?.use { reader -> reader.readText() }
                }
            }
            instructionText = result.getOrNull()
            instructionError = result.exceptionOrNull()?.message
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = currentTrack?.name ?: "No track loaded",
            supportingText = currentTrack?.instructionsRef?.displayName ?: "Load a track with instructions.",
        )
        when {
            currentTrack == null -> {
                Text(
                    text = "Load a track to view its instructions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            currentTrack.instructionsRef == null -> {
                Text(
                    text = "This track does not include an instructions file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            instructionError != null -> {
                Text(
                    text = "Could not load instructions: $instructionError",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            instructionText == null -> {
                Text(
                    text = "Loading instructions...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                MarkdownInstructionContent(text = instructionText.orEmpty())
            }
        }
    }
}

@Composable
private fun MarkdownInstructionContent(text: String) {
    val parsed = remember(text) {
        runCatching {
            MarkdownInstructionParser.parse(text)
        }
    }
    val document = parsed.getOrNull()
    if (document == null) {
        PlainInstructionText(text = text)
    } else {
        MarkdownInstructionDocumentView(document = document)
    }
}

@Composable
private fun MarkdownInstructionDocumentView(document: MarkdownInstructionDocument) {
    if (document.blocks.isEmpty()) {
        PlainInstructionText(text = "")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        document.blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> MarkdownInlineText(
                    content = block.content,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                )

                is MarkdownBlock.Paragraph -> MarkdownInlineText(
                    content = block.content,
                    style = MaterialTheme.typography.bodyMedium,
                )

                is MarkdownBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "-",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MarkdownInlineText(
                                content = item,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                is MarkdownBlock.BlockQuote -> MarkdownInlineText(
                    content = listOf(MarkdownInline.Text("| ")) + block.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is MarkdownBlock.CodeBlock -> Text(
                    text = block.code.ifBlank { " " },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun MarkdownInlineText(
    content: List<MarkdownInline>,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    Text(
        text = buildAnnotatedString {
            content.forEach { inline ->
                when (inline) {
                    is MarkdownInline.Text -> append(inline.value)
                    is MarkdownInline.Emphasis -> pushAndAppend(
                        text = inline.value,
                        style = SpanStyle(fontStyle = FontStyle.Italic),
                    )

                    is MarkdownInline.Strong -> pushAndAppend(
                        text = inline.value,
                        style = SpanStyle(fontWeight = FontWeight.Bold),
                    )

                    is MarkdownInline.Code -> pushAndAppend(
                        text = inline.value,
                        style = SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )

                    is MarkdownInline.Link -> pushAndAppend(
                        text = "${inline.label} (${inline.url})",
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    )
                }
            }
        },
        style = style,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        color = color,
    )
}

@Composable
private fun PlainInstructionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.pushAndAppend(
    text: String,
    style: SpanStyle,
) {
    pushStyle(style)
    append(text)
    pop()
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
    onSetPlaybackDelay: (PlaybackDelaySettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Practice",
            supportingText = playback.practiceStatus(),
        )
        if (playback.playbackDelayActive) {
            Text(
                text = "Starting in ${playback.playbackDelayRemainingSeconds}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    contentDescription = "Playback starts in ${playback.playbackDelayRemainingSeconds} seconds"
                },
            )
        }
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
        PlaybackDelayControls(
            settings = settings.playbackDelay,
            onSetPlaybackDelay = onSetPlaybackDelay,
        )
    }
}

@Composable
private fun PlaybackDelayControls(
    settings: PlaybackDelaySettings,
    onSetPlaybackDelay: (PlaybackDelaySettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Count-in delay",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            delayOptions.forEach { seconds ->
                OutlinedButton(
                    onClick = { onSetPlaybackDelay(PlaybackDelaySettings(delaySeconds = seconds)) },
                    enabled = settings.delaySeconds != seconds,
                ) {
                    Text(text = if (seconds == 0) "Off" else "${seconds}s")
                }
            }
        }
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
    onLoadTrack: (Track) -> Unit,
    onAddTrackToPlaylist: (Playlist, Track) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "Load track paused",
                onClick = { onLoadTrack(track) },
            )
            .semantics {
                contentDescription = "Load track ${track.name} paused"
            },
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
private fun DiagnosticRow(diagnostic: LibraryDiagnostic) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = when (diagnostic.severity) {
                Severity.Error -> "Error"
                Severity.Warning -> "Warning"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (diagnostic.severity == Severity.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        diagnostic.fileName?.let { fileName ->
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = diagnostic.message,
            style = MaterialTheme.typography.bodyMedium,
        )
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
    val delayText = if (playbackDelayActive) {
        "delay ${playbackDelayRemainingSeconds}s"
    } else {
        "ready"
    }
    return "$queueText - $repetitionText - $callsText - $delayText"
}

private fun LibraryDiagnostic.displayText(): String {
    val prefix = when (severity) {
        Severity.Error -> "Error"
        Severity.Warning -> "Warning"
    }
    val file = fileName?.let { " [$it]" }.orEmpty()
    return "$prefix$file: $message"
}

private fun diagnosticsContentDescription(count: Int): String =
    if (count == 0) {
        "Open diagnostics. No issues."
    } else {
        "Open diagnostics. $count issue${if (count == 1) "" else "s"}."
    }

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private const val MAX_VISIBLE_DIAGNOSTICS = 8
private val delayOptions = listOf(0, 3, 5, 10)
