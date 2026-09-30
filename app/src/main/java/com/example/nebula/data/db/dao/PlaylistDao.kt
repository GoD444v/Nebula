package com.example.nebula.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nebula.data.db.entities.PlaylistEntity
import com.example.nebula.data.db.entities.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

/** A playlist plus how many songs it holds, counted in SQL rather than in Kotlin. */
data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val songCount: Int
)

@Dao
interface PlaylistDao {

    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM playlist_songs WHERE playlistId = p.id) AS songCount " +
            "FROM playlists p ORDER BY p.lastUpdatedAt DESC"
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeSongs(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    /**
     * `now` is an explicit parameter with no Kotlin default: Room's generated
     * implementation and default arguments on abstract interface members do not
     * mix reliably. Callers pass System.currentTimeMillis().
     */
    @Query("UPDATE playlists SET name = :name, lastUpdatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long): Int

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long): Int

    /**
     * IGNORE, not REPLACE: the primary key is (playlistId, videoId), so a duplicate
     * add is skipped rather than overwriting the existing row's position. Room
     * reports -1 for each skipped row, which is how callers learn nothing was added.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(songs: List<PlaylistSongEntity>): List<Long>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeSong(playlistId: Long, videoId: String): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Query(
        "UPDATE playlist_songs SET position = :position " +
            "WHERE playlistId = :playlistId AND videoId = :videoId"
    )
    suspend fun updatePosition(playlistId: Long, videoId: String, position: Int): Int
}
