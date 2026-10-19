package nebula.music.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * One playlist pinned to the speed dial — the quick-access row at the top of Home.
 *
 * Echo-Music's concept (`speed_dial_item`, pinned from a menu with "Pin to speed dial" /
 * "Unpin from speed dial"). Echo stores a whole item snapshot per type; this stores only
 * playlists, and only their id, so a renamed playlist shows its new name on the dial
 * instead of a stale copy frozen at pin time. The join back to `playlists` is in
 * [nebula.music.data.db.dao.SpeedDialDao].
 *
 * `playlistId` is the primary key, which is what makes pinning idempotent — pinning the
 * same playlist twice is one row, and the paired delete is an unpin. `ON DELETE CASCADE`
 * means deleting a playlist takes its pin with it, so a dial can never point at a row that
 * no longer exists.
 *
 * shortcut: playlists only. Songs, artists and albums are the same three lines here plus a
 * type column and a per-type open path — add them when a menu actually offers to pin one.
 */
@Entity(
    tableName = "speed_dial",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SpeedDialItem(
    @PrimaryKey val playlistId: Long,
    /** Pin order, oldest first — the order the user chose to add them. */
    val createdAt: Long = System.currentTimeMillis()
)