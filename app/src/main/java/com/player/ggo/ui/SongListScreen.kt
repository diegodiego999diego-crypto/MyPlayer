package com.player.ggo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.player.ggo.MainViewModel
import com.player.ggo.ui.components.MiniPlayer
import com.player.ggo.ui.components.SongListItem

/**
 * Canciones de la carpeta seleccionada.
 * - Toque: reproduce y abre el player.
 * - Mantener pulsado: menu con "Reproducir a continuacion".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val folder = state.selectedFolder ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder.name) },
                navigationIcon = {
                    IconButton(onClick = { vm.closeFolder() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
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
            itemsIndexed(folder.songs, key = { _, s -> s.id }) { index, song ->
                SongListItem(
                    song = song,
                    supportingText = "${song.artist} · ${song.albumName}",
                    onClick = { vm.playSong(folder, index) },
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
