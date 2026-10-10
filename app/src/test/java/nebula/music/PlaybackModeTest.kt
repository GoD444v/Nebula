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

    @Test
    fun removal_before_current_shifts_index_down() {
        assertEquals(1, PlaybackModes.newIndexAfterRemoval(
            queueSizeBeforeRemoval = 4, queueIndex = 2, removedIndex = 0))
    }

    @Test
    fun removal_after_current_leaves_index_alone() {
        assertEquals(1, PlaybackModes.newIndexAfterRemoval(
            queueSizeBeforeRemoval = 4, queueIndex = 1, removedIndex = 3))
    }

    @Test
    fun removal_of_current_keeps_index_slot_refilled() {
        assertEquals(2, PlaybackModes.newIndexAfterRemoval(
            queueSizeBeforeRemoval = 4, queueIndex = 2, removedIndex = 2))
    }

    @Test
    fun removal_of_last_playing_song_steps_index_back() {
        assertEquals(2, PlaybackModes.newIndexAfterRemoval(
            queueSizeBeforeRemoval = 4, queueIndex = 3, removedIndex = 3))
    }

    @Test
    fun removal_of_only_song_yields_minus_one() {
        assertEquals(-1, PlaybackModes.newIndexAfterRemoval(
            queueSizeBeforeRemoval = 1, queueIndex = 0, removedIndex = 0))
    }

    // The two-song case is where passing the POST-removal size instead of the pre-removal
    // one does the most damage: sizeAfter lands on 0, the empty-queue guard fires, and the
    // index becomes -1 while a song is still queued. Deleting the playing song of [A,B]
    // must leave index 0 on B, not -1.
    @Test
    fun two_song_queue_removing_the_playing_song_keeps_the_other_indexed() {
        assertEquals(0, PlaybackModes.newIndexAfterRemoval(
            queueSizeBeforeRemoval = 2, queueIndex = 0, removedIndex = 0))
    }

    @Test
    fun two_song_queue_removing_the_playing_tail_keeps_the_other_indexed() {
        assertEquals(0, PlaybackModes.newIndexAfterRemoval(
            queueSizeBeforeRemoval = 2, queueIndex = 1, removedIndex = 1))
    }

    @Test
    fun two_song_queue_removing_the_other_song_leaves_the_playing_index() {
        assertEquals(0, PlaybackModes.newIndexAfterRemoval(
            queueSizeBeforeRemoval = 2, queueIndex = 0, removedIndex = 1))
    }

    // Removing a playing song from a 4-song queue must land on the song that took its
    // slot (index 2 -> D), never one further back (index 1 -> B).
    @Test
    fun four_song_queue_removing_the_playing_song_advances_not_rewinds() {
        assertEquals(2, PlaybackModes.newIndexAfterRemoval(
            queueSizeBeforeRemoval = 4, queueIndex = 2, removedIndex = 2))
    }

    @Test
    fun history_keeps_entries_before_the_removed_slot() {
        assertEquals(listOf(0, 1), PlaybackModes.repairHistory(
            history = listOf(0, 1), queueSizeAfterRemoval = 4, removedIndex = 3))
    }

    @Test
    fun history_drops_entry_pointing_at_removed_slot() {
        // history entry 2 is the removed slot itself: history=[0,2,3], removing index 2
        // drops that entry and shifts 3 down to 2.
        assertEquals(listOf(0, 2), PlaybackModes.repairHistory(
            history = listOf(0, 2, 3), queueSizeAfterRemoval = 3, removedIndex = 2))
    }

    @Test
    fun history_shifts_entries_after_the_removed_slot_down() {
        assertEquals(listOf(0, 2, 3), PlaybackModes.repairHistory(
            history = listOf(0, 3, 4), queueSizeAfterRemoval = 5, removedIndex = 1))
    }

    @Test
    fun history_clamps_entries_past_the_new_end() {
        // A queue of 2 can only address indices 0 and 1. Entry 5 shifts to 4 and entry 9 to
        // 8 — both past the end, so both are dropped rather than left to be popped blind.
        assertEquals(listOf(0), PlaybackModes.repairHistory(
            history = listOf(0, 5, 9), queueSizeAfterRemoval = 2, removedIndex = 1))
    }

    @Test
    fun history_survives_emptying_the_queue() {
        assertEquals(emptyList<Int>(), PlaybackModes.repairHistory(
            history = listOf(0, 1), queueSizeAfterRemoval = 0, removedIndex = 0))
    }
}
