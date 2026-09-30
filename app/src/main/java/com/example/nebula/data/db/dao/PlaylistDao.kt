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

    /**
     * The one built-in playlist, found by flag rather than by name so a user rename
     * cannot break whatever keeps it in sync. Null until [insertSystemPlaylist] runs.
     */
    @Query("SELECT * FROM playlists WHERE isSystem = 1 LIMIT 1")
    fun observeSystemPlaylist(): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE isSystem = 1 LIMIT 1")
    suspend fun getSystemPlaylistBlocking(): PlaylistEntity?

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    /**
     * Idempotent: if a system playlist already exists its id is returned and nothing
     * is inserted. A blind insert would let a second caller create a duplicate, and
     * since both lookups are `LIMIT 1` the duplicate's songs would then be orphaned
     * — invisible to the sync and unreachable from the UI.
     */
    suspend fun insertSystemPlaylist(name: String): Long {
        getSystemPlaylistBlocking()?.let { return it.id }
        return insert(PlaylistEntity(name = name, isSystem = true))
    }

    /**
     * `now` is an explicit parameter with no Kotlin default: Room's generated
     * implementation and default arguments on abstract interface members do not
     * mix reliably. Callers pass System.currentTimeMillis().
     */
    @Query("UPDATE playlists SET name = :name, lastUpdatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long): Int

    /**
     * The `isSystem = 0` predicate is the delete guard, and it lives in SQL rather
     * than in the ViewModel so no caller can bypass it.
     */
    @Query("DELETE FROM playlists WHERE id = :id AND isSystem = 0")
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

    /**
     * Drops rows whose videoId is not in [videoIds] — the prune half of reconcile.
     * An empty [videoIds] means the download index is empty, so the whole playlist
     * goes; `NOT IN ()` is not valid SQL and would throw.
     */
    suspend fun deleteSongsNotIn(videoIds: List<String>, playlistId: Long) {
        if (videoIds.isEmpty()) {
            deleteAllSongs(playlistId)
        } else {
            deleteSongsNotInQuery(videoIds, playlistId)
        }
    }

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteAllSongs(playlistId: Long): Int

    @Query(
        "DELETE FROM playlist_songs WHERE playlistId = :playlistId " +
            "AND videoId NOT IN (:videoIds)"
    )
    suspend fun deleteSongsNotInQuery(videoIds: List<String>, playlistId: Long): Int

    @Query(
        "UPDATE playlist_songs SET position = :position " +
            "WHERE playlistId = :playlistId AND videoId = :videoId"
    )
    suspend fun updatePosition(playlistId: Long, videoId: String, position: Int): Int
}
