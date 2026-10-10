package nebula.music.player

import java.net.URLDecoder
import java.net.URLEncoder

// Pure queue-navigation decisions for shuffle / loop / sequential modes.
// No Android imports: covered by PlaybackModeTest on the JVM. PlayerViewModel
// maps Media3 repeat constants onto REPEAT_* here and executes the action.
sealed interface NextAction {
    data class PlayQueue(val index: Int) : NextAction
    data class PlayUpNext(val index: Int) : NextAction
    data object ReplayCurrent : NextAction
    data object Stop : NextAction
}

object PlaybackModes {
    const val REPEAT_OFF = 0
    const val REPEAT_ONE = 1
    const val REPEAT_ALL = 2

    /** One song in the persisted queue. */
    data class SongInfo(
        val videoId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String
    )

    /** Decoded queue: songs + where playback was. */
    data class QueueState(
        val songs: List<SongInfo>,
        val currentIndex: Int,
        val positionMs: Long
    )

    // Queue persistence (Echo's saveQueueToDisk concept, prefs instead of file).
    // Format: line1=currentIndex, line2=positionMs, lines3+=tab-separated
    // URL-encoded videoId/title/artist/thumbnailUrl. URL-encoding keeps newlines
    // and tabs inside titles from breaking the format.
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")

    fun encodeQueue(songs: List<SongInfo>, queueIndex: Int, positionMs: Long): String {
        val lines = mutableListOf(queueIndex.toString(), positionMs.toString())
        songs.forEach { s ->
            lines.add(
                listOf(s.videoId, s.title, s.artist, s.thumbnailUrl)
                    .joinToString("\t") { enc(it) }
            )
        }
        return lines.joinToString("\n")
    }

    fun decodeQueue(raw: String): QueueState? {
        if (raw.isBlank()) return null
        val lines = raw.split("\n")
        if (lines.size < 2) return null
        val index = lines[0].toIntOrNull() ?: return null
        val position = lines[1].toLongOrNull() ?: return null
        val songs = lines.drop(2).mapNotNull { line ->
            val f = line.split("\t")
            if (f.size < 4) null
            else SongInfo(
                videoId = dec(f[0]),
                title = dec(f[1]),
                artist = dec(f[2]),
                thumbnailUrl = dec(f[3])
            )
        }
        if (songs.isEmpty()) return null
        return QueueState(songs, index.coerceIn(0, songs.lastIndex), position)
    }

    // Start Radio (Echo concept): keep the current song, drop everything after
    // it, append the radio songs deduped against the queue and themselves.
    fun mergeRadio(queue: List<String>, queueIndex: Int, radio: List<String>): List<String> {
        val kept = queue.subList(0, (queueIndex + 1).coerceAtMost(queue.size))
        val seen = kept.toMutableSet()
        val fresh = radio.filter { seen.add(it) }
        return kept + fresh
    }

    fun next(
        queueSize: Int,
        queueIndex: Int,
        upNextSize: Int,
        repeatMode: Int,
        shuffleOn: Boolean,
        randomIdx: (Int) -> Int = { (0 until it).random() }
    ): NextAction {
        if (repeatMode == REPEAT_ONE) return NextAction.ReplayCurrent
        if (shuffleOn) {
            if (queueSize > 1) {
                var pick = randomIdx(queueSize).mod(queueSize)
                if (pick == queueIndex) pick = (pick + 1).mod(queueSize)
                return NextAction.PlayQueue(pick)
            }
            if (upNextSize > 0) return NextAction.PlayUpNext(randomIdx(upNextSize).mod(upNextSize))
            if (repeatMode == REPEAT_ALL && queueSize > 0) return NextAction.ReplayCurrent
            return NextAction.Stop
        }
        if (queueIndex + 1 < queueSize) return NextAction.PlayQueue(queueIndex + 1)
        // End of queue: loop-all wraps instead of drifting into autoplay (Echo's
        // DisableLoadMoreWhenRepeatAll concept); sequential continues into Up Next.
        if (repeatMode == REPEAT_ALL && queueSize > 0) return NextAction.PlayQueue(0)
        if (upNextSize > 0) return NextAction.PlayUpNext(0)
        return NextAction.Stop
    }

    /**
     * The queueIndex after [removedIndex] is deleted from a queue of [queueSize].
     *
     * Removing before the current song shifts it down one. Removing the current song
     * leaves the slot refilled by the next song, so the index holds — unless it was the
     * last song, where it steps back to the new tail. Emptying the queue yields -1.
     */
    fun newIndexAfterRemoval(
        queueSizeBeforeRemoval: Int,
        queueIndex: Int,
        removedIndex: Int
    ): Int {
        // The size the queue will have AFTER the deletion, and the last index that size
        // can address. These are different numbers: a queue of 4 becomes 3, whose highest
        // valid index is 2.
        val sizeAfter = queueSizeBeforeRemoval - 1
        if (sizeAfter <= 0) return -1
        return when {
            removedIndex < queueIndex -> queueIndex - 1
            removedIndex == queueIndex -> minOf(queueIndex, sizeAfter - 1)
            else -> queueIndex
        }
    }

    /**
     * [history] rewritten for the deletion of [removedIndex] from a queue that now holds
     * [queueSizeAfterRemoval] songs.
     *
     * Entries before the removed slot still point where they did. The entry ON the removed
     * slot is dropped — it names a song that is gone. Entries after it shift down by one.
     * Anything past the new end is dropped: PlayerViewModel.previous() pops history before
     * it bounds-checks the index, so a stale entry is swallowed silently and lost.
     */
    fun repairHistory(
        history: List<Int>,
        queueSizeAfterRemoval: Int,
        removedIndex: Int
    ): List<Int> = history.mapNotNull { entry ->
        when {
            entry == removedIndex -> null
            entry > removedIndex -> (entry - 1).takeIf { it in 0 until queueSizeAfterRemoval }
            else -> entry.takeIf { it in 0 until queueSizeAfterRemoval }
        }
    }
}
