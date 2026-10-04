package com.player.ggo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.player.ggo.MainViewModel
import com.player.ggo.ui.components.AlbumArt

/**
 * Reproductor a pantalla completa.
 * Controles inferiores: retroceder 10s, cancion anterior, play/pausa,
 * siguiente, adelantar 10s y ciclo de repeticion (nada / carpeta / una).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()

    BackHandler { vm.hidePlayer() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reproduciendo") },
                navigationIcon = {
                    IconButton(onClick = { vm.hidePlayer() }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Cerrar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // Caratula grande del album (con imagen por defecto si no hay)
            AlbumArt(
                albumArtUri = state.currentSong?.albumArtUri,
                size = 240,
                corner = 24
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = state.currentSong?.title ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.currentSong?.artist ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            state.currentSong?.albumName?.takeIf { it.isNotBlank() }?.let { album ->
                Text(
                    text = album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(24.dp))

            Slider(
                value = if (state.durationMs > 0)
                    state.positionMs.toFloat() / state.durationMs else 0f,
                onValueChange = { fraction ->
                    if (state.durationMs > 0)
                        vm.seekTo((fraction * state.durationMs).toLong())
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    formatDuration(state.positionMs),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatDuration(state.durationMs),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(Modifier.weight(1f))

            // ---------- Controles ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Modo de repeticion
                IconButton(onClick = { vm.cycleRepeatMode() }) {
                    when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icon(
                            Icons.Default.RepeatOneOn,
                            contentDescription = "Repetir una",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Player.REPEAT_MODE_ALL -> Icon(
                            Icons.Default.RepeatOn,
                            contentDescription = "Repetir carpeta",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        else -> Icon(
                            Icons.Default.Repeat,
                            contentDescription = "Sin repetir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { vm.back10() }) {
                    Icon(Icons.Default.Replay10, contentDescription = "Retroceder 10 s")
                }
                IconButton(onClick = { vm.previous() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior")
                }

                FilledIconButton(
                    onClick = { vm.togglePlayPause() },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Siguiente")
                }
                IconButton(onClick = { vm.forward10() }) {
                    Icon(Icons.Default.Forward10, contentDescription = "Adelantar 10 s")
                }
            }
        }
    }
}
