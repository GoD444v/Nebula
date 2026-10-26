package nebula.music.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import nebula.music.data.db.entities.SpeedDialItem

/** A playlist as the speed dial shows it: the pin, plus what the playlist is called now. */
data class PinnedPlaylist(
    val playlistId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val songCount: Int
)

/**
 * The speed dial: playlists the user pinned for one-tap access from Home.
 *
 * The JOIN is the point. Storing a title snapshot would go stale the moment a playlist is
 * renamed, and the dial is exactly where a stale name is most obvious — it sits above
 * everything else on Home. Reading the live row costs one query.
 *
 * INNER JOIN, not LEFT: a pin whose playlist was deleted should disappear, and the
 * CASCADE means it cannot normally exist, but a dial that shows a blank tile because a row
 * went missing is worse than one that silently drops it.
 */
@Dao
interface SpeedDialDao {

    @Query(
        """
        SELECT p.id AS playlistId,
               p.name AS title,
               p.thumbnailUrl AS thumbnailUrl,
               (SELECT COUNT(*) FROM playlist_songs WHERE playlistId = p.id) AS songCount
        FROM playlists p
        INNER JOIN speed_dial s ON s.playlistId = p.id
        ORDER BY s.createdAt ASC
        """
    )
    fun observePinned(): Flow<List<PinnedPlaylist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pin(item: SpeedDialItem)

    @Query("DELETE FROM speed_dial WHERE playlistId = :playlistId")
    suspend fun unpin(playlistId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM speed_dial WHERE playlistId = :playlistId)")
    suspend fun isPinned(playlistId: Long): Boolean
}