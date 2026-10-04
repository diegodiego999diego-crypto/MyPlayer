package com.player.ggo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.player.ggo.MainViewModel
import com.player.ggo.ui.displayArtist
import com.player.ggo.ui.formatDuration

/**
 * Mini-reproductor persistente al pie de la lista.
 *
 * Muestra caratula, titulo y artista de la cancion actual, boton
 * play/pausa y boton siguiente. Al tocar el cuerpo (no los botones)
 * abre el reproductor completo.
 *
 * El llamador decide si mostrarlo o no (normalmente: cuando hay una
 * cancion cargada y el reproductor completo no esta visible).
 */
@Composable
fun MiniPlayer(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val song = state.currentSong ?: return

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.showPlayer() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(
                albumArtUri = song.albumArtUri,
                size = 44,
                corner = 8
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Column {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = displayArtist(song.artist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = { vm.togglePlayPause() }) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pausar" else "Reproducir"
                )
            }
            IconButton(onClick = { vm.next() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Siguiente")
            }
        }
    }
}
