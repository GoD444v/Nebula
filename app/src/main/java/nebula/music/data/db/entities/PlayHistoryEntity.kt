package nebula.music.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One play of one song, shown on the History screen.
 *
 * Title/artist/thumbnail are stored on the row rather than resolved through a
 * song table — there is no song table, and the row must render offline and
 * survive the video being deleted upstream. A history entry is a snapshot of
 * the song as it played.
 *
 * Every play is its own row; the History screen dedups by videoId per day for
 * display, so a song played five times today shows once but keeps its count
 * in the raw data for later.
 */
@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    /** Epoch millis of the play. */
    val playedAt: Long = System.currentTimeMillis()
)
