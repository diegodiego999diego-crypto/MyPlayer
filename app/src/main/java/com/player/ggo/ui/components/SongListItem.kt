package com.player.ggo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.player.ggo.data.Song
import com.player.ggo.ui.formatDuration

/**
 * Item de cancion con menu contextual al mantener pulsado.
 *
 * - Toque normal: [onClick] (normalmente reproducir).
 * - Mantener pulsado: abre menu con "Reproducir a continuacion"
 *   (la inserta justo despues de la cancion actual).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: Song,
    supportingText: String,
    onClick: () -> Unit,
    onPlayNext: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true }
                ),
            leadingContent = {
                AlbumArt(albumArtUri = song.albumArtUri, size = 48, corner = 8)
            },
            headlineContent = {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(
                    supportingText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = { Text(formatDuration(song.durationMs)) }
        )

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("Reproducir a continuación") },
                leadingIcon = {
                    Icon(Icons.Default.Queue, contentDescription = null)
                },
                onClick = {
                    menuOpen = false
                    onPlayNext()
                }
            )
        }
    }
}
