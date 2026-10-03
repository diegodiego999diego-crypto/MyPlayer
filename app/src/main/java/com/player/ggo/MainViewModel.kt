package com.player.ggo

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.player.ggo.data.MusicFolder
import com.player.ggo.data.MusicRepository
import com.player.ggo.data.RecursiveFileObserver
import com.player.ggo.data.Song
import com.player.ggo.service.MusicPlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la UI principal. */
data class UiState(
    val permissionGranted: Boolean = false,
    val loading: Boolean = true,
    /** Lista plana: TODAS las carpetas que contienen música, anidadas o no. */
    val folders: List<MusicFolder> = emptyList(),
    val selectedFolder: MusicFolder? = null,
    val playerVisible: Boolean = false,
    val isPlaying: Boolean = false,
    val currentSong: Song? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    @Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_OFF
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicRepository(app)
    private var controller: MediaController? = null
    private var fileObserver: RecursiveFileObserver? = null
    private var ticker: Job? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        if (!granted) return
        connectPlayer()
        scanMusic()
        startObserving()
    }

    /** Conecta con el servicio de reproduccion (MediaSession). */
    private fun connectPlayer() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get().apply {
                setSeekBackIncrementMs(10_000)
                setSeekForwardIncrementMs(10_000)
                addListener(playerListener)
                repeatMode = _uiState.value.repeatMode
            }
            startTicker()
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val meta = mediaItem?.mediaMetadata
            _uiState.update {
                it.copy(
                    currentSong = Song(
                        id = mediaItem?.mediaId?.toLongOrNull() ?: -1,
                        title = meta?.title?.toString() ?: "Desconocida",
                        artist = meta?.artist?.toString() ?: "Desconocido",
                        durationMs = meta?.extras?.getLong("durationMs") ?: 0L,
                        contentUri = mediaItem?.localConfiguration?.uri.toString()
                    ),
                    durationMs = controller?.duration?.takeIf { d -> d > 0 } ?: 0L
                )
            }
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.update { it.copy(repeatMode = repeatMode) }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update {
                it.copy(durationMs = controller?.duration?.takeIf { d -> d > 0 } ?: it.durationMs)
            }
        }
    }

    /** Escanea /sdcard/Music con MediaStore y aplana las carpetas. */
    fun scanMusic() {
        viewModelScope.launch(Dispatchers.IO) {
            val folders = repo.scanMusicFolders()
            _uiState.update { state ->
                // Si la carpeta seleccionada sigue existiendo, refresca sus canciones.
                val refreshed = state.selectedFolder?.let { sel ->
                    folders.firstOrNull { it.path == sel.path }
                }
                state.copy(
                    loading = false,
                    folders = folders,
                    selectedFolder = refreshed
                )
            }
        }
    }

    /**
     * FileObserver recursivo sobre /sdcard/Music.
     * Ante cualquier alta, baja o movimiento de archivos/carpetas se re-escanea
     * (con debounce), asi la lista siempre refleja el almacenamiento real.
     */
    private fun startObserving() {
        fileObserver?.stopWatching()
        fileObserver = RecursiveFileObserver(MusicRepository.musicRoot) {
            viewModelScope.launch {
                delay(800) // debounce para ráfagas de eventos
                scanMusic()
            }
        }.also { it.startWatching() }
    }

    // ---------- Navegacion ----------

    fun openFolder(folder: MusicFolder) = _uiState.update { it.copy(selectedFolder = folder) }
    fun closeFolder() = _uiState.update { it.copy(selectedFolder = null) }
    fun hidePlayer() = _uiState.update { it.copy(playerVisible = false) }
    fun showPlayer() = _uiState.update { it.copy(playerVisible = true) }

    // ---------- Reproduccion ----------

    /** Reproduce una cancion y salta automaticamente al reproductor completo. */
    fun playSong(folder: MusicFolder, index: Int) {
        val c = controller ?: return
        val items = folder.songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setExtras(android.os.Bundle().apply {
                            putLong("durationMs", song.durationMs)
                        })
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
        _uiState.update { it.copy(playerVisible = true) }
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()
    fun forward10() = controller?.seekForward()
    fun back10() = controller?.seekBack()

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    /** Cicla: sin repetir -> repetir carpeta completa -> repetir una cancion. */
    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                delay(500)
                val c = controller ?: continue
                _uiState.update {
                    it.copy(positionMs = c.currentPosition.coerceAtLeast(0L))
                }
            }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        fileObserver?.stopWatching()
        controller?.release()
        super.onCleared()
    }
}
