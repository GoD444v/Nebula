package nebula.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import nebula.music.data.models.SearchResult
import nebula.music.viewmodel.PlayerViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test: tapping a song in a playlist played the wrong song.
 *
 * The screen called `playAll(rest + song)`, which appended the tapped song to the end of
 * the queue. `playAll` starts `queue[0]`, so tapping the second song in a three-song
 * playlist played the first one. Reported as: reorder to [lital funk, luz roja, beliver],
 * tap luz roja (2nd), hear lital funk (1st).
 *
 * Asserts on the resulting queue, not on what is "playing": playback itself needs a
 * MediaController and a network, neither of which a unit test can provide, but the queue
 * order is exactly the thing that was wrong and it is fully observable here.
 */
@RunWith(AndroidJUnit4::class)
class PlayFromHereTest {

    private val vm = PlayerViewModel()

    private fun song(id: String) = SearchResult(id, "Title $id", "Artist $id", "")

    @Test
    fun tappedSongIsFirstSoItIsWhatPlays() {
        val a = song("lital-funk")
        val b = song("luz-roja")
        val c = song("beliver")

        vm.playFromHere(b, listOf(a, c))

        assertEquals(listOf("luz-roja", "lital-funk", "beliver"), vm.queueList.map { it.videoId })
    }

    @Test
    fun tappingTheFirstSongStillPlaysIt() {
        val a = song("a")
        val b = song("b")

        vm.playFromHere(a, listOf(b))

        assertEquals(listOf("a", "b"), vm.queueList.map { it.videoId })
    }

    @Test
    fun tappingTheLastSongKeepsTheEarlierOrderIntact() {
        val a = song("a")
        val b = song("b")
        val c = song("c")

        vm.playFromHere(c, listOf(a, b))

        assertEquals(listOf("c", "a", "b"), vm.queueList.map { it.videoId })
    }

    @Test
    fun aSongAlreadyInRestIsNotQueuedTwice() {
        val a = song("a")
        val b = song("b")

        vm.playFromHere(a, listOf(a, b))

        assertEquals(listOf("a", "b"), vm.queueList.map { it.videoId })
    }

    @Test
    fun playingFromTheMiddleReplacesTheQueueRatherThanAppending() {
        vm.playFromHere(song("old-1"), listOf(song("old-2")))

        vm.playFromHere(song("new"), listOf(song("a"), song("b")))

        assertEquals(listOf("new", "a", "b"), vm.queueList.map { it.videoId })
    }

    /**
     * Regression test: tapping a song in a playlist played the first song on Next.
     *
     * The screen passed the whole playlist (minus the tapped song) as `rest`, so tapping
     * #2 of [a, b, c, d] built [b, a, c, d] and Next played `a`. The screen contract is now
     * tapped + only the songs after it in displayed order; Next must play the immediate
     * successor. `currentVideoId` is set synchronously by `resolveAndPlay`, so no
     * controller or network is needed to observe the advance.
     */
    @Test
    fun nextAfterTappingMiddlePlaysImmediateSuccessor() {
        val songs = listOf(song("a"), song("b"), song("c"), song("d"))

        vm.playFromHere(songs[1], songs.drop(2))

        assertEquals(listOf("b", "c", "d"), vm.queueList.map { it.videoId })
        vm.next()
        assertEquals("c", vm.currentVideoId)
    }
}