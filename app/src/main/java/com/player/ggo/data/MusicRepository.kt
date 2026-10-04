package com.player.ggo.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/** Una cancion con todo lo que la UI necesita (incluida la caratula). */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val albumName: String,
    val durationMs: Long,
    val contentUri: String,
    val albumArtUri: String?,
    val relativePath: String,
    val folderName: String
)

/** Una carpeta con musica. [path] es la ruta relativa dentro de Music/. */
data class MusicFolder(
    val name: String,
    val path: String,
    val songs: List<Song>
)

/**
 * Lee el audio del almacenamiento compartido via MediaStore y lo
 * cachea en Room. Expone [foldersFlow] para que la UI cargue al
 * instante desde cache mientras [refreshFromMediaStore] reconsulta
 * MediaStore en segundo plano.
 */
class MusicRepository(private val context: Context) {

    companion object {
        /** Raiz nativa de musica en almacenamiento compartido: /sdcard/Music */
        val musicRoot: File =
            File(Environment.getExternalStorageDirectory(), "Music")

        /** URI base para resolver caratulas por albumId. */
        private val ALBUM_ART_BASE: Uri =
            Uri.parse("content://media/external/audio/albumart")

        fun albumArtUri(albumId: Long): Uri =
            ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)
    }

    private val dao = AppDatabase.getInstance(context).songDao()

    /**
     * Lista PLANA de carpetas derivada del cache Room.
     * Cada carpeta que contiene canciones aparece al mismo nivel,
     * sin importar su profundidad.
     * Ej.: Music/A y Music/A/B/C generan dos entradas hermanas.
     */
    val foldersFlow: Flow<List<MusicFolder>> = dao.observeAll()
        .map { rows ->
            rows.groupBy { it.relativePath }
                .toSortedMap(compareBy { it.lowercase() })
                .map { (relPath, list) ->
                    MusicFolder(
                        name = relPath.trimEnd('/').substringAfterLast('/'),
                        path = relPath,
                        songs = list.map { it.toSong() }
                    )
                }
        }
        .flowOn(Dispatchers.IO)

    /** Lista plana de todas las canciones (para busqueda global). */
    val allSongsFlow: Flow<List<Song>> = dao.observeAll()
        .map { rows -> rows.map { it.toSong() } }
        .flowOn(Dispatchers.IO)

    /**
     * Reconsulta MediaStore y reescribe el cache Room.
     * Tras este call, [foldersFlow] emite la lista nueva automaticamente.
     */
    suspend fun refreshFromMediaStore() = withContext(Dispatchers.IO) {
        queryAndCache()
    }

    /**
     * Pide a MediaScanner que indexe [paths] (archivos nuevos o
     * modificados detectados por FileObserver) y luego reconsulta
     * MediaStore y reescribe el cache Room.
     */
    suspend fun scanFilesThenRefresh(paths: List<String>) = withContext(Dispatchers.IO) {
        if (paths.isNotEmpty()) {
            // scanFile es async; el callback se dispara por path. Lanzamos
            // todos a la vez y esperamos a que termine el escaneo global
            // con un pequeno sleep defensivo (el callback no garantiza
            // orden ni atomicidad). Es aceptable: refreshFromMediaStore
            // reconsulta MediaStore justo despues.
            runCatching {
                val unique = paths.filter { it.isNotEmpty() }.distinct()
                if (unique.isNotEmpty()) {
                    MediaScannerConnection.scanFile(
                        context.applicationContext,
                        unique.toTypedArray(),
                        null,
                        null
                    )
                    // Pequena ventana para que MediaStore procese los
                    // eventos de escaneo antes de reconsultar.
                    delay(150)
                }
            }
        }
        queryAndCache()
    }

    private fun queryAndCache() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("Music/%")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val out = ArrayList<SongEntity>(64)
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, sortOrder
        )?.use { c ->
            val idCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val tCol    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val arCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val alCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val alNCol  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dCol    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pCol    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val dmCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val relPath = c.getString(pCol) ?: continue
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                val albumId = c.getLong(alCol)
                out.add(
                    SongEntity(
                        id = id,
                        title = c.getString(tCol) ?: "Desconocida",
                        artist = c.getString(arCol) ?: "Desconocido",
                        albumId = albumId,
                        albumName = c.getString(alNCol) ?: "Desconocido",
                        durationMs = c.getLong(dCol),
                        contentUri = uri.toString(),
                        albumArtUri = if (albumId > 0) albumArtUri(albumId).toString() else null,
                        relativePath = relPath,
                        folderName = relPath.trimEnd('/').substringAfterLast('/'),
                        dateModified = c.getLong(dmCol)
                    )
                )
            }
        }
        dao.replaceAll(out)
    }
}

private fun SongEntity.toSong() = Song(
    id = id,
    title = title,
    artist = artist,
    albumId = albumId,
    albumName = albumName,
    durationMs = durationMs,
    contentUri = contentUri,
    albumArtUri = albumArtUri,
    relativePath = relativePath,
    folderName = folderName
)
