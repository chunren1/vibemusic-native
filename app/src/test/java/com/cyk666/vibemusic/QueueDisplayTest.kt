package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueDisplayTest {

    private fun song(id: String, name: String = "n$id", dur: Int = 231) = Song(
        sourceId = id,
        name = name,
        artist = "a$id",
        album = "",
        coverUrl = "",
        durationSec = dur,
        platform = "netease"
    )

    private fun timelineSong(id: String, name: String = "n$id") = song(id, name, 0)

    @Test
    fun resolve_prefersActivityDurationOverTimeline() {
        val row = resolveQueueRowDisplay(
            activityQueue = listOf(song("1", dur = 231)),
            timelineSong = timelineSong("1"),
            isCurrent = false
        )
        assertEquals("3:51", row.durationText)
        assertEquals("n1", row.title)
        assertEquals("a1", row.artist)
        assertEquals("1", row.sourceId)
        assertFalse(row.isCurrent)
    }

    @Test
    fun resolve_unknownEverywhere_rendersFallback() {
        val row = resolveQueueRowDisplay(
            activityQueue = emptyList(),
            timelineSong = timelineSong("9"),
            isCurrent = false
        )
        assertEquals("--:--", row.durationText)
    }

    @Test
    fun resolve_noActivityMatchButTimelineKnows_rendersTimeline() {
        val row = resolveQueueRowDisplay(
            activityQueue = listOf(song("1", dur = 200)),
            timelineSong = song("2", dur = 125),
            isCurrent = false
        )
        assertEquals("2:05", row.durationText)
    }

    @Test
    fun resolve_blankNameFallsBackToUntitled() {
        val row = resolveQueueRowDisplay(
            activityQueue = emptyList(),
            timelineSong = timelineSong("1", name = ""),
            isCurrent = false
        )
        assertEquals("(untitled)", row.title)
        assertEquals("--:--", row.durationText)
    }

    @Test
    fun resolve_currentRowAttachesLiveProgress() {
        val row = resolveQueueRowDisplay(
            activityQueue = listOf(song("1", dur = 231)),
            timelineSong = timelineSong("1"),
            isCurrent = true,
            livePositionMs = 65_000L,
            liveDurationMs = 231_000L
        )
        assertTrue(row.isCurrent)
        assertEquals(65_000L, row.livePositionMs)
        assertEquals(231_000L, row.liveDurationMs)
    }

    @Test
    fun resolve_nonCurrentRowDropsLiveProgress() {
        val row = resolveQueueRowDisplay(
            activityQueue = listOf(song("1", dur = 231)),
            timelineSong = timelineSong("1"),
            isCurrent = false,
            livePositionMs = 65_000L,
            liveDurationMs = 231_000L
        )
        assertEquals(0L, row.livePositionMs)
        assertEquals(0L, row.liveDurationMs)
    }

    @Test
    fun build_marksCurrentAndPassesLiveThrough() {
        val activity = listOf(song("x", dur = 200), song("y", dur = 100))
        val timeline = listOf(timelineSong("x"), timelineSong("y"))
        val rows = buildQueueRowDisplays(activity, timeline, 1, 5_000L, 100_000L)
        assertEquals(2, rows.size)
        assertEquals(listOf("x", "y"), rows.map { it.sourceId })
        assertFalse(rows[0].isCurrent)
        assertTrue(rows[1].isCurrent)
        assertEquals("3:20", rows[0].durationText)
        assertEquals("1:40", rows[1].durationText)
        assertEquals(0L, rows[0].livePositionMs)
        assertEquals(5_000L, rows[1].livePositionMs)
        assertEquals(100_000L, rows[1].liveDurationMs)
    }

    @Test
    fun build_emptyTimelineReturnsEmpty() {
        assertTrue(buildQueueRowDisplays(listOf(song("1")), emptyList(), 0).isEmpty())
    }

    @Test
    fun merge_keepsActivityDurationsInTimelineOrder() {
        val activity = listOf(song("a", dur = 200), song("b", dur = 100))
        val merged = mergeQueueDurations(activity, listOf(timelineSong("b"), timelineSong("a")))
        assertEquals(listOf("b", "a"), merged.map { it.sourceId })
        assertEquals(100, merged[0].durationSec)
        assertEquals(200, merged[1].durationSec)
    }

    @Test
    fun merge_dropsRemovedAndKeepsUnknownAsFallback() {
        val activity = listOf(song("a", dur = 200), song("gone", dur = 50))
        val merged = mergeQueueDurations(activity, listOf(timelineSong("a"), timelineSong("new")))
        assertEquals(listOf("a", "new"), merged.map { it.sourceId })
        assertEquals(200, merged[0].durationSec)
        assertEquals(0, merged[1].durationSec)
    }

    @Test
    fun merge_blankIdPassesThrough() {
        val t = song("", dur = 0)
        val merged = mergeQueueDurations(listOf(song("a", dur = 200)), listOf(t))
        assertEquals(1, merged.size)
        assertEquals("", merged[0].sourceId)
    }
}
