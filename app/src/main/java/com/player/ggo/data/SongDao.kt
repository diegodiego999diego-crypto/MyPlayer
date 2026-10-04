package com.player.ggo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    /** Lista completa, ordenada por carpeta y titulo. Se emite en vivo. */
    @Query(
        """
        SELECT * FROM songs
        ORDER BY relativePath COLLATE NOCASE ASC,
                 title COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAll(): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id NOT IN (:ids)")
    suspend fun deleteStale(ids: List<Long>)

    @Query("DELETE FROM songs")
    suspend fun clear()

    /**
     * Reemplaza toda la tabla atómicamente: usado tras un re-escaneo
     * completo de MediaStore, donde la nueva lista es la fuente de verdad.
     */
    @Transaction
    suspend fun replaceAll(songs: List<SongEntity>) {
        if (songs.isEmpty()) {
            clear()
            return
        }
        upsertAll(songs)
        deleteStale(songs.map { it.id })
    }
}
