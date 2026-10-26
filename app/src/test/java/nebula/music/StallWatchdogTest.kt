package nebula.music

import nebula.music.viewmodel.StallAction
import nebula.music.viewmodel.StallWatchdog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The watchdog is the only thing standing between a stalled stream and a screen
 * frozen at 0:00 / 0:00 forever, so its budget arithmetic is the part that has to
 * be right: fire too early and it re-resolves every song, fire too often and it
 * hammers InnerTube all night.
 */
class StallWatchdogTest {

    private val stallMs = 10_000L

    @Test
    fun `a song that is not buffering never asks for anything`() {
        val watchdog = StallWatchdog(stallMs = stallMs, maxRetries = 3)
        repeat(10) { tick ->
            assertEquals(StallAction.None, watchdog.onTick(tick * 60_000L, buffering = false))
        }
    }

    @Test
    fun `buffering below the threshold does nothing`() {
        val watchdog = StallWatchdog(stallMs = stallMs, maxRetries = 3)
        assertEquals(StallAction.None, watchdog.onTick(0L, buffering = true))
        // One tick short of the threshold. 500ms is the real tick period, so this
        // is the last tick that must stay silent.
        assertEquals(StallAction.None, watchdog.onTick(9_500L, buffering = true))
    }

    @Test
    fun `a song that buffers past the threshold is retried`() {
        val watchdog = StallWatchdog(stallMs = stallMs, maxRetries = 3)
        assertEquals(StallAction.None, watchdog.onTick(0L, buffering = true))
        assertEquals(StallAction.Retry, watchdog.onTick(stallMs, buffering = true))
    }

    @Test
    fun `the retry budget runs out and it gives up instead of looping`() {
        val watchdog = StallWatchdog(stallMs = stallMs, maxRetries = 3)
        var now = 0L
        watchdog.onTick(now, buffering = true)
        repeat(3) { attempt ->
            now += stallMs
            assertEquals("retry $attempt", StallAction.Retry, watchdog.onTick(now, buffering = true))
            // Half a window later the song is still buffering, but the clock restarted
            // on the retry — so this must stay silent rather than fire again.
            now += stallMs / 2
            assertEquals(StallAction.None, watchdog.onTick(now, buffering = true))
        }
        now += stallMs
        // Budget spent. The bug this guards: sharing retryPlayback()'s counter, which
        // a successful re-resolve resets, would hand back another Retry here and the
        // app would re-resolve this song every 10s until the user closed it.
        assertEquals(StallAction.GiveUp, watchdog.onTick(now, buffering = true))
        // ...and it must STAY given up rather than flip back to Retry and oscillate.
        assertEquals(StallAction.GiveUp, watchdog.onTick(now + stallMs, buffering = true))
    }

    @Test
    fun `playing again clears the spent budget`() {
        val watchdog = StallWatchdog(stallMs = stallMs, maxRetries = 1)
        watchdog.onTick(0L, buffering = true)
        assertEquals(StallAction.Retry, watchdog.onTick(stallMs, buffering = true))
        assertEquals(StallAction.GiveUp, watchdog.onTick(stallMs + stallMs, buffering = true))

        // A song that recovers must not inherit the dead one's budget, or the NEXT
        // slow song would get one attempt instead of three.
        watchdog.onTick(stallMs + stallMs, buffering = false)
        assertEquals(StallAction.None, watchdog.onTick(stallMs + stallMs, buffering = true))
        assertEquals(StallAction.Retry, watchdog.onTick(stallMs + stallMs + stallMs, buffering = true))
    }

    @Test
    fun `an earlier stall does not cost the current song its retries`() {
        val watchdog = StallWatchdog(stallMs = stallMs, maxRetries = 2)
        watchdog.onTick(0L, buffering = true)
        watchdog.onTick(stallMs, buffering = true) // Retry — song A stalls
        watchdog.onTick(stallMs, buffering = false) // A recovers

        // Song B, fresh stall: it gets the FULL budget, not what A left behind.
        watchdog.onTick(stallMs, buffering = true)
        assertEquals(StallAction.Retry, watchdog.onTick(stallMs + stallMs, buffering = true))
        assertEquals(StallAction.Retry, watchdog.onTick(stallMs + stallMs + stallMs, buffering = true))
        assertEquals(StallAction.GiveUp, watchdog.onTick(stallMs + stallMs + stallMs + stallMs, buffering = true))
    }
}