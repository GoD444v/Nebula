package com.example.nebula.data.download

import com.example.nebula.data.db.dao.PlaylistDao
import com.example.nebula.data.db.entities.PlaylistSongEntity
import com.example.nebula.data.models.SearchResult
import kotlinx.coroutines.flow.first

/**
 * A song as the download index knows it: identity plus the metadata needed to render
 * a playlist row. Deliberately not a Media3 type — the sync must be testable without
 * a real [androidx.media3.exoplayer.offline.DownloadManager].
 */
data class DownloadedSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?
) {
    companion object {
        fun from(song: SearchResult) = DownloadedSong(
            videoId = song.videoId,
            title = song.title,
            artist = song.artist,
            thumbnailUrl = song.thumbnailUrl.takeIf { it.isNotBlank() }
        )
    }
}

/**
 * Keeps the built-in Downloaded playlist equal to the set of completed downloads.
 *
 * Two paths, on purpose:
 *  - events ([onCompleted] / [onRemoved]) so the playlist is correct immediately
 *  - [reconcile] on startup, because a process kill between a download completing and
 *    its event being delivered leaves the playlist permanently wrong, and nothing
 *    would ever correct it
 *
 * Finds its playlist by the isSystem flag, never by name, so a user rename cannot
 * break sync.
 */
class DownloadedPlaylistSync(private val dao: PlaylistDao) {

    suspend fun ensureExists(): Long {
        dao.getSystemPlaylistBlocking()?.let { return it.id }
        return dao.insertSystemPlaylist(DEFAULT_NAME)
    }

    suspend fun onCompleted(song: SearchResult) {
        val id = ensureExists()
        dao.insertSongs(
            listOf(
                PlaylistSongEntity(
                    playlistId = id,
                    videoId = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl.takeIf { it.isNotBlank() },
                    position = dao.nextPosition(id)
                )
            )
        )
    }

    suspend fun onRemoved(videoId: String) {
        val id = dao.getSystemPlaylistBlocking()?.id ?: return
        val current = dao.observeSongs(id).first()
        if (current.none { it.videoId == videoId }) return
        dao.removeSong(id, videoId)
        renumber(id)
    }

    /**
     * Rewrites positions to 0..n-1 in the current order.
     *
     * Every path that removes a row must call this, because a hole in the positions is
     * not cosmetic: `PlaylistDao.moveSong` takes position VALUES, while the screen hands
     * it row INDEXES. The two agree only while positions are dense. With a hole they
     * diverge, the `BETWEEN MIN(..) AND MAX(..)` window covers the wrong rows, and the
     * reorder becomes a silent no-op — the list looks like it reset itself. Proven by
     * `ReorderGapDiagnosticTest.reorderMovesTheRightRowsWhenPositionsHaveAHole`, which
     * failed with the order unchanged before this call existed on the prune path.
     */
    private suspend fun renumber(id: Long) {
        dao.observeSongs(id).first().forEachIndexed { index, song ->
            if (song.position != index) dao.updatePosition(id, song.videoId, index)
        }
    }

    /**
     * Makes the playlist match [indexed] exactly: adds anything missed, drops anything
     * whose download is gone. Safe to run repeatedly.
     */
    suspend fun reconcile(indexed: List<DownloadedSong>) {
        val id = ensureExists()
        val present = dao.observeSongs(id).first()
        if (indexed.isEmpty()) {
            dao.deleteSongsNotIn(emptyList(), id)
            return
        }
        val haveVideoIds = present.mapTo(HashSet()) { it.videoId }
        var position = dao.nextPosition(id)
        val missing = indexed
            .filterNot { it.videoId in haveVideoIds }
            .map { song ->
                PlaylistSongEntity(
                    playlistId = id,
                    videoId = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                    position = position++
                )
            }
        if (missing.isNotEmpty()) dao.insertSongs(missing)
        dao.deleteSongsNotIn(indexed.map { it.videoId }, id)
        // The prune above can leave a hole in the positions. See [renumber] for why that
        // is not cosmetic.
        renumber(id)
    }

    companion object {
        const val DEFAULT_NAME = "Downloaded"
    }
}
