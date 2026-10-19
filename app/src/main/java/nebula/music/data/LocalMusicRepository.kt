package nebula.music.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import nebula.music.data.db.NebulaDatabase
import nebula.music.data.db.entities.LocalSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Filters applied at scan time, EchoMusic's `LocalSongScanConfig` reduced to the two
 * settings that actually change which files land in the table.
 *
 * Applied during the scan rather than after it: a song shorter than the minimum must
 * never enter `local_songs`, because every later read would have to re-filter.
 */
data class LocalSongScanConfig(
    val minimumDurationSeconds: Int = 30,
    val excludedFolders: Set<String> = emptySet()
) {
    /** A folder set with slashes and case noise normalised, so "Music//" == "music". */
    val normalizedFolders: Set<String> = excludedFolders.mapNotNull {
        val cleaned = it.trim().replace('\\', '/').replace(Regex("/+"), "/").trim('/')
        cleaned.lowercase(Locale.ROOT).takeIf { c -> c.isNotEmpty() }
    }.toSet()
}

/** What the last scan found and removed — EchoMusic's `LocalSongScanSummary`. */
data class LocalSongScanSummary(
    val scannedSongs: Int = 0,
    val removedSongs: Int = 0
)

class LocalMusicRepository {

    /**
     * Rescans the device and replaces the table. Returns what was found and what the
     * previous scan had that this one does not, which is the "N songs found, M removed"
     * line EchoMusic shows.
     */
    suspend fun scanLocalMusic(
        context: Context,
        config: LocalSongScanConfig = LocalSongScanConfig()
    ): LocalSongScanSummary = withContext(Dispatchers.IO) {
        val songs = mutableListOf<LocalSong>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.ALBUM_ID)
            // DATA is dead as a playable handle under scoped storage, but folder
            // exclusion cannot work without it and nothing else exposes the path.
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            // The Folders browse: only named on Q and up.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
            // The Album-artists browse: ALBUM_ARTIST only exists on R and up.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
            }
        }.toTypedArray()

        // The duration floor belongs in the query, not a Kotlin filter: a row that never
        // lands in the cursor cannot leak into the table.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} > ${config.minimumDurationSeconds.coerceAtLeast(0) * 1000L}"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val relativePathColumn =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1
            val dateModifiedColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val albumArtistColumn =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                } else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown"
                val album = cursor.getString(albumColumn) ?: "Unknown"
                // Blank when the device had no tag, which is common — TPE2 is the one
                // tag a ripper is allowed to forget. The grouper falls back to the
                // track artist, so the view stays populated either way.
                val albumArtist = if (albumArtistColumn >= 0) {
                    cursor.getString(albumArtistColumn).orEmpty()
                } else {
                    ""
                }
                val duration = cursor.getLong(durationColumn)
                // The content URI, NOT MediaStore.DATA. DATA is deprecated and returns an empty
                // string under scoped storage (Android 11+), which left filePath blank and
                // handed the player setUri("") — so songs listed but never played. The
                // MediaStore _ID is the only durable handle on scoped storage, and it is
                // what RetroMusic plays from.
                val filePath = "content://media/external/audio/media/$id"

                // Folder for the Folders browse: RELATIVE_PATH ("Music/Albums/")
                // is the scoped-storage answer; DATA's parent covers the devices
                // where it is blank. Trailing slash stripped; blank → split into
                // the "Unknown" bucket in the UI.
                val relativePath = if (relativePathColumn >= 0) {
                    cursor.getString(relativePathColumn)
                } else {
                    null
                }
                val folderPath = when {
                    !relativePath.isNullOrBlank() -> relativePath.trim().trimEnd('/')
                    dataColumn >= 0 && !cursor.getString(dataColumn).isNullOrBlank() ->
                        File(cursor.getString(dataColumn)).parent ?: ""
                    else -> ""
                }
                // Album art is keyed on the ALBUM id, not the audio track id — the
                // ".../media/<trackId>/albumart" form is legacy and does not resolve on
                // modern Android. Same shape RetroMusic uses.
                val albumId = cursor.getLong(albumIdColumn)
                val thumbnailUri = "content://media/external/audio/albumart/$albumId"

                // Folder exclusion matches the parent of the file path, so excluding
                // "Music/Podcasts" drops everything under it too. DATA can be null on
                // scoped storage, in which case the file simply is not excluded.
                if (dataColumn >= 0 && config.normalizedFolders.isNotEmpty()) {
                    val path = cursor.getString(dataColumn).orEmpty()
                        .replace('\\', '/').lowercase(Locale.ROOT)
                    if (config.normalizedFolders.any { path.contains("/$it/") || path.startsWith("$it/") }) {
                        continue
                    }
                }

                songs.add(
                    LocalSong(
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        filePath = filePath,
                        thumbnailUri = thumbnailUri,
                        dateModified = cursor.getLong(dateModifiedColumn),
                        folderPath = folderPath,
                        albumArtist = albumArtist
                    )
                )
            }
        }

        // Replace wholesale — the table is a cache of the device, not an accumulation.
        // Removed = what the old scan held that this one did not.
        val db = NebulaDatabase.getDatabase(context)
        val previousIds = db.localSongDao().getAllSongs().map { it.filePath }.toSet()
        db.localSongDao().deleteAll()
        db.localSongDao().insertAll(songs)

        LocalSongScanSummary(
            scannedSongs = songs.size,
            removedSongs = previousIds.count { it !in songs.map { s -> s.filePath } }
        )
    }

    /** Everything scanned so far, title order. */
    suspend fun all(context: Context): List<LocalSong> = withContext(Dispatchers.IO) {
        NebulaDatabase.getDatabase(context).localSongDao().getAllSongs()
    }

    /** Title/artist substring match — the dao query that existed but was never called. */
    suspend fun search(context: Context, query: String): List<LocalSong> =
        withContext(Dispatchers.IO) {
            NebulaDatabase.getDatabase(context).localSongDao().searchSongs(query.trim())
        }

    suspend fun count(context: Context): Int = withContext(Dispatchers.IO) {
        NebulaDatabase.getDatabase(context).localSongDao().getSongCount()
    }
}
