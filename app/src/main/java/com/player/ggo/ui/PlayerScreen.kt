package com.player.ggo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import android.content.res.Configuration
import com.player.ggo.MainViewModel
import com.player.ggo.UiState
import com.player.ggo.ui.components.AlbumArt
import com.player.ggo.ui.displayArtist
import com.player.ggo.ui.hasArtist

/**
 * Reproductor a pantalla completa.
 * Controles inferiores: retroceder 10s, cancion anterior, play/pausa,
 * siguiente, adelantar 10s y ciclo de repeticion (nada / carpeta / una).
 *
 * En vertical: caratula arriba, controles abajo.
 * En horizontal: caratula a la izquierda, info y controles a la derecha.
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
        if (state.currentSong == null) {
            PlayerEmptyState(
                onBack = { vm.hidePlayer() },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@Scaffold
        }

        val landscape = LocalConfiguration.current.orientation ==
            Configuration.ORIENTATION_LANDSCAPE

        if (landscape) {
            PlayerLandscape(state, vm, Modifier.fillMaxSize().padding(padding))
        } else {
            PlayerPortrait(state, vm, Modifier.fillMaxSize().padding(padding))
        }
    }
}

/** Layout vertical clásico. */
@Composable
private fun PlayerPortrait(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        AlbumArt(
            albumArtUri = state.currentSong?.albumArtUri,
            size = 240,
            corner = 24
        )

        Spacer(Modifier.height(32.dp))
        SongTitles(state)
        Spacer(Modifier.height(24.dp))
        ProgressBlock(state, vm)
        Spacer(Modifier.weight(1f))
        ControlsRow(state, vm, Modifier.padding(bottom = 40.dp))
    }
}

/** Layout horizontal: caratula izquierda, resto a la derecha. */
@Composable
private fun PlayerLandscape(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            AlbumArt(
                albumArtUri = state.currentSong?.albumArtUri,
                size = 180,
                corner = 20
            )
        }

        Spacer(Modifier.width(32.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            SongTitles(state)
            Spacer(Modifier.height(16.dp))
            ProgressBlock(state, vm)
            Spacer(Modifier.height(16.dp))
            ControlsRow(state, vm, Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun SongTitles(state: UiState) {
    val title = state.currentSong?.title?.takeIf { it.isNotBlank() } ?: "—"
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    val artist = state.currentSong?.artist
    if (artist != null && hasArtist(artist)) {
        Text(
            text = displayArtist(artist),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    state.currentSong?.albumName?.takeIf { it.isNotBlank() }?.let { album ->
        Text(
            text = album,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProgressBlock(state: UiState, vm: MainViewModel) {
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
}

@Composable
private fun ControlsRow(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
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

/** Estado vacío del reproductor: se abrió sin ninguna canción cargada. */
@Composable
private fun PlayerEmptyState(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Nada sonando por aquí",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Elige una canción de tu biblioteca para empezar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) {
            Text("Ir a mi música")
        }
    }
}
