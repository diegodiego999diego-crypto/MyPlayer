package com.player.ggo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.player.ggo.MainViewModel
import com.player.ggo.ui.components.AlbumArt
import com.player.ggo.ui.components.MiniPlayer

/** Lista plana de TODAS las carpetas con musica dentro de /sdcard/Music. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()

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
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.folders.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No se encontró música en la carpeta Music",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.folders, key = { it.path }) { folder ->
                    // Primera cancion con caratula, para previsualizar
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
                        headlineContent = { Text(folder.name) },
                        supportingContent = {
                            Text("${folder.songs.size} canciones · ${folder.path}")
                        }
                    )
                }
            }
        }
    }
}
