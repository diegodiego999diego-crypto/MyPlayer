package com.player.ggo.data

import android.content.ContentUris
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val contentUri: String
)

/** Una carpeta con musica. [path] es la ruta relativa dentro de Music/. */
data class MusicFolder(
    val name: String,
    val path: String,
    val songs: List<Song>
)

/**
 * Lee el audio del almacenamiento compartido via MediaStore.
 * No usa escaneo propio: el sistema ya mantiene el indice, y un
 * FileObserver recursivo dispara el re-escaneo cuando algo cambia.
 */
class MusicRepository(private val context: Context) {

    companion object {
        /** Raiz nativa de musica en almacenamiento compartido: /sdcard/Music */
        val musicRoot: File =
            File(Environment.getExternalStorageDirectory(), "Music")
    }

    /**
     * Devuelve una lista PLANA de carpetas: cada carpeta que contiene
     * canciones aparece al mismo nivel, sin importar su profundidad.
     * Ej.: Music/A y Music/A/B/C generan dos entradas hermanas.
     */
    suspend fun scanMusicFolders(): List<MusicFolder> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH
        )
        // Solo audio ubicado dentro de Music/ del almacenamiento compartido
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("Music/%")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val byFolder = linkedMapOf<String, MutableList<Song>>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val relPath = cursor.getString(pathCol) ?: continue
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                val song = Song(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Desconocida",
                    artist = cursor.getString(artistCol) ?: "Desconocido",
                    durationMs = cursor.getLong(durCol),
                    contentUri = uri.toString()
                )
                byFolder.getOrPut(relPath) { mutableListOf() }.add(song)
            }
        }

        byFolder.entries
            .sortedBy { it.key.lowercase() }
            .map { (relPath, songs) ->
                MusicFolder(
                    name = relPath.trimEnd('/').substringAfterLast('/'),
                    path = relPath,
                    songs = songs
                )
            }
    }
}
