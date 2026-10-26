package nebula.music.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_songs")
data class LocalSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    // A MediaStore content URI (content://media/external/audio/media/<id>), not a
    // filesystem path — see LocalMusicRepository for why DATA is unusable here.
    val filePath: String,
    val thumbnailUri: String,
    // MediaStore DATE_MODIFIED, seconds since the epoch. Backs the "Date updated" sort.
    val dateModified: Long = 0,
    // Where the file sits, for the Folders browse: RELATIVE_PATH ("Music/Albums")
    // or DATA's parent, whichever the device answers at scan. Blank = unknown
    // (file added before this column existed or the device gave no path).
    val folderPath: String = "",
    // The album-level artist (MediaStore ALBUM_ARTIST, R and up): whose shelf an
    // album sits on when a compilation's tracks each list their own performer.
    // Blank = the device had no tag — the grouper falls back to the track artist.
    val albumArtist: String = ""
)
