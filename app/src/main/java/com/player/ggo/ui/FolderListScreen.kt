package com.player.ggo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
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
import com.player.ggo.SortMode
import com.player.ggo.data.MusicFolder
import com.player.ggo.data.Song
import com.player.ggo.ui.components.AlbumArt
import com.player.ggo.ui.components.MiniPlayer
import com.player.ggo.ui.components.SongListItem
import com.player.ggo.ui.components.SortMenuButton
import com.player.ggo.ui.displayArtist

/**
 * Lista plana de TODAS las carpetas con musica dentro de /sdcard/Music,
 * con busqueda por titulo / artista / carpeta y orden configurable.
 *
 * - Con [searchQuery] vacio: lista de carpetas.
 * - Con query: seccion "Carpetas" (carpetas cuyo nombre matchea) +
 *   seccion "Canciones" (canciones cuyo titulo o artista matchea).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val query = state.searchQuery.trim()
    val q = query.lowercase()

    val matchedFolders: List<MusicFolder> = remember(query, state.folders, state.folderSort) {
        val base = if (q.isEmpty()) state.folders
            else state.folders.filter { it.name.contains(q, ignoreCase = true) }
        when (state.folderSort) {
            SortMode.NAME -> base.sortedBy { it.name.lowercase() }
            SortMode.COUNT -> base.sortedByDescending { it.songs.size }
            SortMode.DATE -> base.sortedByDescending { f ->
                f.songs.maxOfOrNull { it.dateModified } ?: 0L
            }
            SortMode.DURATION -> base.sortedByDescending { f ->
                f.songs.sumOf { it.durationMs }
            }
            SortMode.ARTIST -> base // no aplica a carpetas
        }
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
                actions = {
                    SortMenuButton(
                        current = state.folderSort,
                        options = listOf(SortMode.NAME, SortMode.COUNT, SortMode.DATE, SortMode.DURATION),
                        onSelect = { vm.setFolderSort(it) }
                    )
                },
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
            // Barra de búsqueda en forma de píldora (esquinas totalmente
            // redondeadas), contenedor tonal, lupa + X para limpiar.
            TextField(
                value = state.searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp),
                placeholder = { Text("Buscar por título, artista o carpeta") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.clearSearch() }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Limpiar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(percent = 50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                        AlbumArt(albumArtUri = previewArt, size = 48, corner = 12)
                                    } else {
                                        // Contenedor redondeado con primaryContainer y
                                        // icono de carpeta en onPrimaryContainer: siempre
                                        // se ve con buen contraste, sin gris plano.
                                        Surface(
                                            modifier = Modifier.size(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Box(
                                                modifier = Modifier.size(48.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                headlineContent = {
                                    Text(
                                        folder.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        "${folder.songs.size} canciones",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    if (matchedSongs.isNotEmpty()) {
                        item {
                            SectionHeader("Canciones")
                        }
                        itemsIndexed(matchedSongs, key = { _, s -> "s_${s.id}" }) { index, song ->
                            SongListItem(
                                song = song,
                                supportingText = "${displayArtist(song.artist)} · ${song.folderName}",
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
