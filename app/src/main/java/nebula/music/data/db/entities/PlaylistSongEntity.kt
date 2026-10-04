package nebula.music.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One song inside a playlist.
 *
 * The composite primary key `(playlistId, videoId)` is what makes re-adding a song a
 * no-op instead of a second row, so an insert uses OnConflictStrategy.IGNORE and the
 * caller can count the `-1` rows Room reports for skipped inserts.
 *
 * Title/artist/thumbnail are stored rather than resolved from `videoId` on demand, so
 * the detail screen renders instantly and works with no network. A playlist is a
 * snapshot of the songs as they were when added.
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)
