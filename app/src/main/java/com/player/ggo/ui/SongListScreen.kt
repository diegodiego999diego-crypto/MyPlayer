package com.player.ggo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.player.ggo.MainViewModel
import com.player.ggo.SortMode
import com.player.ggo.ui.components.MiniPlayer
import com.player.ggo.ui.components.SongListItem
import com.player.ggo.ui.components.SortMenuButton
import com.player.ggo.ui.displayArtist

/**
 * Canciones de la carpeta seleccionada.
 * - Toque: reproduce y abre el player.
 * - Mantener pulsado: menu con "Reproducir a continuacion".
 * - Icono de orden: A-Z, artista, mas recientes o duracion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val folder = state.selectedFolder ?: return

    // Aplica el orden elegido; la reproduccion usa ESTA lista ordenada.
    val sortedSongs = remember(folder, state.songSort) {
        when (state.songSort) {
            SortMode.NAME -> folder.songs.sortedBy { it.title.lowercase() }
            SortMode.ARTIST -> folder.songs.sortedBy { it.artist.lowercase() }
            SortMode.DATE -> folder.songs.sortedByDescending { it.dateModified }
            SortMode.DURATION -> folder.songs.sortedByDescending { it.durationMs }
            SortMode.COUNT -> folder.songs // no aplica a canciones
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder.name) },
                navigationIcon = {
                    IconButton(onClick = { vm.closeFolder() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    SortMenuButton(
                        current = state.songSort,
                        options = listOf(SortMode.NAME, SortMode.ARTIST, SortMode.DATE, SortMode.DURATION),
                        onSelect = { vm.setSongSort(it) }
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
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            itemsIndexed(sortedSongs, key = { _, s -> s.id }) { index, song ->
                SongListItem(
                    song = song,
                    supportingText = "${displayArtist(song.artist)} · ${song.albumName}",
                    onClick = { vm.playSongs(sortedSongs, index) },
                    onPlayNext = { vm.playNext(song) }
                )
            }
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
