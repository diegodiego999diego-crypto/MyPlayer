package com.player.ggo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.player.ggo.MainViewModel
import com.player.ggo.data.MusicFolder
import com.player.ggo.data.Song
import com.player.ggo.ui.components.AlbumArt
import com.player.ggo.ui.components.MiniPlayer
import com.player.ggo.ui.components.SongListItem

/**
 * Lista plana de TODAS las carpetas con musica dentro de /sdcard/Music,
 * con busqueda por titulo / artista / carpeta.
 *
 * - Con [searchQuery] vacio: lista de carpetas.
 * - Con query: seccion "Carpetas" (carpetas cuyo nombre matchea) +
 *   seccion "Canciones" (canciones cuyo titulo o artista matchea).
 *   Mantener pulsada una cancion ofrece "Reproducir a continuacion".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val query = state.searchQuery.trim()
    val q = query.lowercase()

    val matchedFolders: List<MusicFolder> = remember(query, state.folders) {
        if (q.isEmpty()) state.folders
        else state.folders.filter { it.name.contains(q, ignoreCase = true) }
    }
    val matchedSongs: List<Song> = remember(query, state.allSongs) {
        if (q.isEmpty()) emptyList()
        else state.allSongs.filter {
            it.title.contains(q, ignoreCase = true) ||
            it.artist.contains(q, ignoreCase = true) ||
            it.albumName.contains(q, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi música") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            if (state.currentSong != null) {
                MiniPlayer(vm)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Barra de búsqueda
            TextField(
                value = state.searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar por título, artista o carpeta") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.clearSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )

            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                q.isEmpty() && state.folders.isEmpty() -> EmptyState(
                    "No se encontró música en la carpeta Music"
                )

                q.isNotEmpty() && matchedFolders.isEmpty() && matchedSongs.isEmpty() -> EmptyState(
                    "Sin resultados para \"$query\""
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (matchedFolders.isNotEmpty()) {
                        item {
                            SectionHeader("Carpetas")
                        }
                        items(matchedFolders, key = { "f_${it.path}" }) { folder ->
                            val previewArt = folder.songs.firstNotNullOfOrNull { it.albumArtUri }
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.openFolder(folder) },
                                leadingContent = {
                                    if (previewArt != null) {
                                        AlbumArt(albumArtUri = previewArt, size = 48, corner = 8)
                                    } else {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                headlineContent = {
                                    Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(
                                        "${folder.songs.size} canciones · ${folder.path}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }

                    if (matchedSongs.isNotEmpty()) {
                        item {
                            SectionHeader("Canciones")
                        }
                        itemsIndexed(matchedSongs, key = { _, s -> "s_${s.id}" }) { index, song ->
                            SongListItem(
                                song = song,
                                supportingText = "${song.artist} · ${song.folderName}",
                                onClick = { vm.playFromSearch(matchedSongs, index) },
                                onPlayNext = { vm.playNext(song) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
