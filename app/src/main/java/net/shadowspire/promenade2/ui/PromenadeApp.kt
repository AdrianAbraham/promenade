package net.shadowspire.promenade2.ui

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.shadowspire.promenade2.playback.PlaybackSnapshot
import net.shadowspire.promenade2.playback.PromenadeControllerConnection

@Composable
fun PromenadeApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val connection = remember {
                PromenadeControllerConnection(context.applicationContext)
            }
            val snapshot by connection.snapshot.collectAsStateWithLifecycle()
            val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    connection.loadAndPlay(uri)
                }
            }

            DisposableEffect(connection) {
                connection.connect()
                onDispose {
                    connection.disconnect()
                }
            }

            PromenadePhaseOneScreen(
                snapshot = snapshot,
                onChooseAudio = { audioPicker.launch(arrayOf("audio/*")) },
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
private fun PromenadePhaseOneScreen(
    snapshot: PlaybackSnapshot,
    onChooseAudio: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Promenade 2") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PlayerSummary(snapshot = snapshot)

            Button(
                onClick = onChooseAudio,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Choose audio")
            }

            PlaybackProgress(
                snapshot = snapshot,
                onSeek = onSeek,
            )

            PlaybackControls(
                snapshot = snapshot,
                onPlay = onPlay,
                onPause = onPause,
                onStop = onStop,
            )
        }
    }
}

@Composable
private fun PlayerSummary(snapshot: PlaybackSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = snapshot.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (snapshot.isConnected) snapshot.status else "Connecting to playback service",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
