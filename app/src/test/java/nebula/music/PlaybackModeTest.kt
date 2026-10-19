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
    fun autoplay_wraps_to_top_at_end_of_queue() {
        val a = PlaybackModes.next(
            queueSize = 3, queueIndex = 2, upNextSize = 0,
            repeatMode = PlaybackModes.REPEAT_OFF, shuffleOn = false, autoplayOn = true
        )
        assertEquals(NextAction.PlayQueue(0), a)
    }

    @Test
    fun autoplay_does_not_wrap_before_the_end() {
        val a = PlaybackModes.next(
            queueSize = 3, queueIndex = 0, upNextSize = 0,
            repeatMode = PlaybackModes.REPEAT_OFF, shuffleOn = false, autoplayOn = true
        )
        assertEquals(NextAction.PlayQueue(1), a)
    }

    @Test
    fun autoplay_off_still_stops_at_the_end() {
        val a = PlaybackModes.next(
            queueSize = 3, queueIndex = 2, upNextSize = 0,
            repeatMode = PlaybackModes.REPEAT_OFF, shuffleOn = false, autoplayOn = false
        )
        assertEquals(NextAction.Stop, a)
    }

    // A lone song from Home or Search: wrapping would replay that one song forever, so
    // the queue has to fall through to Stop and let PlayerViewModel start a radio.
    @Test
    fun autoplay_does_not_wrap_a_lone_song_queue() {
        val a = PlaybackModes.next(
            queueSize = 1, queueIndex = 0, upNextSize = 0,
            repeatMode = PlaybackModes.REPEAT_OFF, shuffleOn = false, autoplayOn = true
        )
        assertEquals(NextAction.Stop, a)
    }

    @Test
    fun autoplay_wraps_a_two_song_queue() {
        val a = PlaybackModes.next(
            queueSize = 2, queueIndex = 1, upNextSize = 0,
            repeatMode = PlaybackModes.REPEAT_OFF, shuffleOn = false, autoplayOn = true
        )
        assertEquals(NextAction.PlayQueue(0), a)
    }

    @Test
    fun autoplay_on_an_empty_queue_stops() {
        val a = PlaybackModes.next(
            queueSize = 0, queueIndex = -1, upNextSize = 0,
            repeatMode = PlaybackModes.REPEAT_OFF, shuffleOn = false, autoplayOn = true
        )
        assertEquals(NextAction.Stop, a)
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
    fun append_radio_keeps_everything_and_drops_seen() {
        val a = PlaybackModes.appendRadio(listOf("a", "b", "c"), listOf("b", "d", "e"))
        assertEquals(listOf("a", "b", "c", "d", "e"), a)
    }

    // The radio is seeded by one song, so refetching it returns the same tracks. Returning
    // null is what stops the top-up from looping forever without ever growing the queue.
    @Test
    fun append_radio_returns_null_when_nothing_is_new() {
        val a = PlaybackModes.appendRadio(listOf("a", "b", "c"), listOf("a", "b"))
        assertNull(a)
    }

    @Test
    fun append_radio_also_drops_duplicates_inside_more() {
        val a = PlaybackModes.appendRadio(listOf("a"), listOf("d", "d", "e"))
        assertEquals(listOf("a", "d", "e"), a)
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

    // ---- Scope: shuffle and repeat belong to the queue that is playing ----
    // Shuffle/repeat used to be one global pair persisted in SharedPreferences, so a
    // shuffle switched on inside one playlist stayed on for every other playlist and
    // survived a restart. These pin the replacement rule: entering a DIFFERENT source
    // clears both; entering the SAME one keeps them, because tapping a second song
    // inside the playlist you are already playing from must not discard what you just
    // set.

    @Test
    fun entering_a_different_scope_clears_shuffle_and_repeat() {
        val (shuffle, repeat) = PlaybackModes.onEnterScope(
            currentScope = "playlist:7", newScope = "local", shuffle = true, repeat = REPEAT_ALL_MODE)
        assertFalse("shuffle leaked into another playlist", shuffle)
        assertEquals(REPEAT_OFF_MODE, repeat)
    }

    @Test
    fun entering_the_same_scope_keeps_both_modes() {
        val (shuffle, repeat) = PlaybackModes.onEnterScope(
            currentScope = "playlist:7", newScope = "playlist:7", shuffle = true, repeat = REPEAT_ONE_MODE)
        assertTrue(shuffle)
        assertEquals(REPEAT_ONE_MODE, repeat)
    }

    @Test
    fun leaving_a_playlist_for_a_plain_tap_clears_both_modes() {
        // null is "not a set" — a search result, Home, a radio. It must read as off,
        // or the playlist you just left keeps shaping playback app-wide.
        val (shuffle, repeat) = PlaybackModes.onEnterScope(
            currentScope = "playlist:7", newScope = null, shuffle = true, repeat = REPEAT_ALL_MODE)
        assertFalse(shuffle)
        assertEquals(REPEAT_OFF_MODE, repeat)
    }

    @Test
    fun the_first_play_of_the_session_starts_from_off_regardless_of_scope() {
        val (shuffle, repeat) = PlaybackModes.onEnterScope(
            currentScope = null, newScope = "playlist:7", shuffle = false, repeat = REPEAT_OFF_MODE)
        assertFalse(shuffle)
        assertEquals(REPEAT_OFF_MODE, repeat)
    }

    // Media3's constants, spelled out rather than importing Media3 into a JVM test.
    private companion object {
        const val REPEAT_OFF_MODE = 0
        const val REPEAT_ONE_MODE = 1
        const val REPEAT_ALL_MODE = 2
    }
}
