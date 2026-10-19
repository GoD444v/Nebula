package nebula.music

import nebula.music.data.db.entities.PlayHistoryEntity
import nebula.music.ui.screens.historyBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Day grouping for the History screen: labels, order, per-day dedup. */
class HistoryBucketsTest {

    // "Now" = noon today, local time — a stable anchor for every case below.
    private val zone = ZoneId.systemDefault()
    private val nowMs = LocalDate.now(zone)
        .atTime(12, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun at(dayOffset: Long, id: Long = 0L) =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            .plusDays(dayOffset)
            .atTime(15, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun entry(videoId: String, playedAt: Long, id: Long = 0L) =
        PlayHistoryEntity(
            id = id,
            videoId = videoId,
            title = "Title $videoId",
            artist = "Artist",
            playedAt = playedAt
        )

    @Test
    fun `plays today and yesterday get their named buckets`() {
        val result = historyBuckets(
            listOf(
                entry("a", at(-1)),
                entry("b", at(0))
            ),
            nowMs
        )
        assertEquals(listOf("Today", "Yesterday"), result.map { it.first })
        assertEquals(listOf("b"), result[0].second.map { it.videoId })
        assertEquals(listOf("a"), result[1].second.map { it.videoId })
    }

    @Test
    fun `older plays are grouped by their day and stay newest first`() {
        val result = historyBuckets(
            listOf(
                entry("old", at(-9)),
                entry("mid", at(-3)),
                entry("new", at(-2))
            ),
            nowMs
        )
        assertEquals(3, result.size)
        // Newest first: -2 before -3 before -9.
        assertEquals(listOf("new", "mid", "old"), result.flatMap { it.second }.map { it.videoId })
        // Older buckets carry an absolute date, never Today/Yesterday.
        assertTrue(result.all { it.first != "Today" && it.first != "Yesterday" })
    }

    @Test
    fun `same song played twice today shows once, newest play wins`() {
        val first = entry("a", at(0), id = 1)
        val second = entry("a", at(0), id = 2)
        val result = historyBuckets(listOf(second, first), nowMs)
        assertEquals(1, result.size)
        assertEquals(listOf(2L), result[0].second.map { it.id })
    }

    @Test
    fun `same song on two days appears in both days`() {
        val result = historyBuckets(
            listOf(entry("a", at(0)), entry("a", at(-1))),
            nowMs
        )
        assertEquals(2, result.size)
        assertTrue(result.all { group -> group.second.any { it.videoId == "a" } })
    }

    @Test
    fun `empty history yields no buckets`() {
        assertTrue(historyBuckets(emptyList(), nowMs).isEmpty())
    }
}
