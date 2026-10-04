package nebula.music

import nebula.music.player.NextAction
import nebula.music.player.PlaybackModes
import nebula.music.player.PlaybackModes.QueueState
import nebula.music.player.PlaybackModes.SongInfo
import org.junit.Test
import org.junit.Assert.*

class PlaybackModeTest {

    @Test
    fun sequential_advances_in_order() {
        val a = PlaybackModes.next(
            queueSize = 3, queueIndex = 0, upNextSize = 0,
            repeatMode = PlaybackModes.REPEAT_OFF, shuffleOn = false
        )
        assertEquals(NextAction.PlayQueue(1), a)
    }

    @Test
    fun sequential_stops_at_end_with_no_upnext() {
        val a = PlaybackModes.next(
            queueSize = 3, queueIndex = 2, upNextSize = 0,
            repeatMode = PlaybackModes.REPEAT_OFF, shuffleOn = false
        )
        assertEquals(NextAction.Stop, a)
    }

    @Test
    fun sequential_continues_into_upnext_at_end() {
        val a = PlaybackModes.next(
            queueSize = 2, queueIndex = 1, upNextSize = 5,
            repeatMode = PlaybackModes.REPEAT_OFF, shuffleOn = false
        )
        assertEquals(NextAction.PlayUpNext(0), a)
    }

    @Test
    fun loop_queue_wraps_around() {
        val a = PlaybackModes.next(
            queueSize = 3, queueIndex = 2, upNextSize = 0,
            repeatMode = PlaybackModes.REPEAT_ALL, shuffleOn = false
        )
        assertEquals(NextAction.PlayQueue(0), a)
    }

    @Test
    fun loop_one_replays_current() {
        val a = PlaybackModes.next(
            queueSize = 3, queueIndex = 1, upNextSize = 4,
            repeatMode = PlaybackModes.REPEAT_ONE, shuffleOn = false
        )
        assertEquals(NextAction.ReplayCurrent, a)
    }

    @Test
    fun shuffle_picks_random_queue_item() {
        val a = PlaybackModes.next(
            queueSize = 3, queueIndex = 0, upNextSize = 5,
            repeatMode = PlaybackModes.REPEAT_OFF, shuffleOn = true,
            randomIdx = { size -> 2 }
        )
        assertEquals(NextAction.PlayQueue(2), a)
    }

    @Test
    fun shuffle_falls_back_to_upnext_when_queue_single() {
        val a = PlaybackModes.next(
            queueSize = 1, queueIndex = 0, upNextSize = 5,
            repeatMode = PlaybackModes.REPEAT_OFF, shuffleOn = true,
            randomIdx = { _ -> 0 }
        )
        // Single-item queue: random pick would replay itself, so fall to Up Next.
        assertEquals(NextAction.PlayUpNext(0), a)
    }

    @Test
    fun radio_keeps_current_and_drops_old_upnext() {
        val merged = PlaybackModes.mergeRadio(
            queue = listOf("a", "b", "c"),
            queueIndex = 1,
            radio = listOf("r1", "r2")
        )
        assertEquals(listOf("a", "b", "r1", "r2"), merged)
    }

    @Test
    fun radio_dedupes_against_queue_and_itself() {
        val merged = PlaybackModes.mergeRadio(
            queue = listOf("a", "b"),
            queueIndex = 0,
            radio = listOf("b", "r1", "r1", "r2")
        )
        assertEquals(listOf("a", "b", "r1", "r2"), merged)
    }

    @Test
    fun radio_empty_when_nothing_new() {
        val merged = PlaybackModes.mergeRadio(
            queue = listOf("a", "b"),
            queueIndex = 0,
            radio = listOf("a", "b")
        )
        assertEquals(listOf("a", "b"), merged)
    }

    @Test
    fun queue_roundtrip_preserves_all_songs_index_and_position() {
        val songs = listOf(
            SongInfo("v1", "Song One", "Artist A", "http://img/1.jpg"),
            SongInfo("v2", "Song Two", "Artist B", "http://img/2.jpg"),
            SongInfo("v3", "Song Three", "Artist C", "http://img/3.jpg")
        )
        val encoded = PlaybackModes.encodeQueue(songs, queueIndex = 1, positionMs = 42000L)
        val decoded = PlaybackModes.decodeQueue(encoded)!!
        assertEquals(1, decoded.currentIndex)
        assertEquals(42000L, decoded.positionMs)
        assertEquals(songs, decoded.songs)
    }

    @Test
    fun queue_roundtrip_handles_special_characters() {
        val songs = listOf(
            SongInfo("v1", "Song\nNewline", "Artist\tTab", "http://img/1.jpg")
        )
        val encoded = PlaybackModes.encodeQueue(songs, queueIndex = 0, positionMs = 0L)
        val decoded = PlaybackModes.decodeQueue(encoded)!!
        assertEquals(songs, decoded.songs)
    }

    @Test
    fun queue_decode_rejects_garbage() {
        val decoded = PlaybackModes.decodeQueue("not-a-valid-queue")
        assertNull(decoded)
    }

    @Test
    fun queue_decode_rejects_empty() {
        val decoded = PlaybackModes.decodeQueue("")
        assertNull(decoded)
    }
}
