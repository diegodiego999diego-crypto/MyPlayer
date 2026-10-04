package com.player.ggo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cancion cacheada en Room. Se mantiene en BD para que la lista
 * cargue al instante desde cache mientras MediaStore se re-consulta.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val albumName: String,
    val durationMs: Long,
    val contentUri: String,
    /** URI de la caratula del album (content://media/external/audio/albumart/<albumId>). */
    val albumArtUri: String?,
    /** RELATIVE_PATH de MediaStore, ej: "Music/Rock/". */
    val relativePath: String,
    /** Nombre legible de la carpeta (ultima componente de relativePath). */
    val folderName: String,
    /** Fecha de modificacion en MediaStore; sirve para detectar cambios. */
    val dateModified: Long
)
