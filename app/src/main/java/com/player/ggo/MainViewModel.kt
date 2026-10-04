package com.player.ggo

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.player.ggo.data.MediaStoreObserver
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la UI principal. */
data class UiState(
    val permissionGranted: Boolean = false,
    val loading: Boolean = true,
    /** Lista plana: TODAS las carpetas que contienen música, anidadas o no. */
    val folders: List<MusicFolder> = emptyList(),
    /** Lista plana de TODAS las canciones (para búsqueda). */
    val allSongs: List<Song> = emptyList(),
    val selectedFolder: MusicFolder? = null,
    val playerVisible: Boolean = false,
    val isPlaying: Boolean = false,
    val currentSong: Song? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** Texto de búsqueda activo (vacío = sin búsqueda). */
    val searchQuery: String = "",
    @Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_OFF
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicRepository(app)
    private var controller: MediaController? = null
    private var fileObserver: RecursiveFileObserver? = null
    private var mediaStoreObserver: MediaStoreObserver? = null
    private var ticker: Job? = null
    /** Debounce de eventos de FileObserver/ContentObserver. */
    private var rescanJob: Job? = null
    /** Buffer de rutas pendientes de scanFile (se vacia en cada rescan). */
    private val pendingScanPaths = mutableSetOf<String>()

    /** Mapa mediaId -> Song para reconstruir currentSong al cambiar de pista. */
    private var songByMediaId: Map<String, Song> = emptyMap()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Colecciona el cache Room: la lista carga al instante desde BD
        // y se actualiza sola cada vez que refreshFromMediaStore() escribe.
        viewModelScope.launch {
            repo.foldersFlow.collect { folders ->
                _uiState.update { it.copy(loading = false, folders = folders) }
            }
        }
        viewModelScope.launch {
            repo.allSongsFlow.collect { songs ->
                _uiState.update { it.copy(allSongs = songs) }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        if (!granted) return
        connectPlayer()
        // Re-escaneo completo al abrir la app: cubre cambios con la app cerrada.
        refreshFromMediaStore()
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
            val song = mediaItem?.mediaId?.let { songByMediaId[it] }
            _uiState.update {
                it.copy(
                    currentSong = song,
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

    /** Reconsulta MediaStore y reescribe el cache Room. */
    fun refreshFromMediaStore() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.refreshFromMediaStore() }
        }
    }

    /**
     * Observa /sdcard/Music con FileObserver recursivo (primario) y
     * MediaStore.Audio con ContentObserver (respaldo). Ante cualquier
     * alta, baja o movimiento se re-escanea con debounce; si FileObserver
     * trae una ruta absoluta, se llama a MediaScannerConnection.scanFile()
     * sobre ella antes de re-consultar, para que MediaStore indexe el
     * archivo nuevo.
     */
    private fun startObserving() {
        if (fileObserver == null) {
            fileObserver = RecursiveFileObserver(MusicRepository.musicRoot) { absolutePath ->
                if (absolutePath != null) {
                    synchronized(pendingScanPaths) { pendingScanPaths.add(absolutePath) }
                }
                scheduleDebouncedRescan()
            }.also { it.startWatching() }
        }
        if (mediaStoreObserver == null) {
            mediaStoreObserver = MediaStoreObserver(getApplication()) {
                scheduleDebouncedRescan()
            }.also { it.register() }
        }
    }

    fun stopObservers() {
        fileObserver?.stopWatching()
        fileObserver = null
        mediaStoreObserver?.unregister()
        mediaStoreObserver = null
        rescanJob?.cancel()
        rescanJob = null
    }

    /**
     * Re-escaneo con debounce: junta eventos de FileObserver y
     * ContentObserver durante [DEBOUNCE_MS] y luego ejecuta un unico
     * scanFile()+refresh. Evita relevar MediaStore en cada evento de
     * una rafaga (por ejemplo, al copiar muchos archivos).
     */
    private fun scheduleDebouncedRescan() {
        rescanJob?.cancel()
        rescanJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val paths = synchronized(pendingScanPaths) {
                val copy = pendingScanPaths.toList()
                pendingScanPaths.clear()
                copy
            }
            runCatching { repo.scanFilesThenRefresh(paths) }
        }
    }

    // ---------- Navegacion ----------

    fun openFolder(folder: MusicFolder) = _uiState.update { it.copy(selectedFolder = folder) }
    fun closeFolder() = _uiState.update { it.copy(selectedFolder = null) }
    fun hidePlayer() = _uiState.update { it.copy(playerVisible = false) }
    fun showPlayer() = _uiState.update { it.copy(playerVisible = true) }

    // ---------- Búsqueda ----------

    /** Actualiza el texto de búsqueda; la UI filtra en vivo. */
    fun setSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun clearSearch() = _uiState.update { it.copy(searchQuery = "") }

    /**
     * Reproduce una cancion desde resultados de búsqueda. Construye una
     * lista de reproducción con todas las canciones que matchearon (no
     * solo la carpeta origen), de modo que "siguiente" avance dentro de
     * los resultados.
     */
    fun playFromSearch(results: List<Song>, index: Int) {
        playSongs(results, index)
    }

    // ---------- Reproduccion ----------

    /** Reproduce una cancion y salta automaticamente al reproductor completo. */
    fun playSong(folder: MusicFolder, index: Int) {
        playSongs(folder.songs, index)
    }

    /** Reproduce una lista arbitraria de canciones desde [index]. */
    fun playSongs(songs: List<Song>, index: Int) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        songByMediaId = songs.associateBy { it.id.toString() }
        val items = songs.map { it.toMediaItem() }
        c.setMediaItems(items, index.coerceIn(0, items.lastIndex), 0L)
        c.prepare()
        c.play()
        _uiState.update {
            it.copy(
                playerVisible = true,
                currentSong = songs[index.coerceIn(0, songs.lastIndex)]
            )
        }
    }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(albumName)
                    .setArtworkUri(albumArtUri?.let { Uri.parse(it) })
                    .setExtras(android.os.Bundle().apply {
                        putLong("durationMs", durationMs)
                    })
                    .build()
            )
            .build()

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
                kotlinx.coroutines.delay(500)
                val c = controller ?: continue
                _uiState.update {
                    it.copy(positionMs = c.currentPosition.coerceAtLeast(0L))
                }
            }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        stopObservers()
        controller?.release()
        controller = null
        super.onCleared()
    }

    companion object {
        /** Debounce para rafagas de eventos de FileObserver/ContentObserver. */
        private const val DEBOUNCE_MS = 800L
    }
}
