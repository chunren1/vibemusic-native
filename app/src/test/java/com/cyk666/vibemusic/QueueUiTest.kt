package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueUiTest {

    private fun song(id: String, name: String = "n$id", dur: Int = 231) = Song(
        sourceId = id,
        name = name,
        artist = "a$id",
        album = "",
        coverUrl = "",
        durationSec = dur,
        platform = "netease"
    )

    @Test
    fun rows_mapTitleArtistDurationWithCurrentHighlight() {
        val rows = buildQueueRows(listOf(song("1"), song("2"), song("3")), 1)
        assertEquals(3, rows.size)
        assertEquals("n1", rows[0].title)
        assertEquals("a2", rows[1].artist)
        assertEquals("3:51", rows[1].durationLabel)
        assertFalse(rows[0].isCurrent)
        assertTrue(rows[1].isCurrent)
        assertFalse(rows[2].isCurrent)
    }

    @Test
    fun rows_blankNameFallsBackToUntitled() {
        val rows = buildQueueRows(listOf(song("1", name = "")), 0)
        assertEquals("(untitled)", rows[0].title)
        assertTrue(rows[0].isCurrent)
    }

    @Test
    fun rows_emptyListReturnsEmpty() {
        assertTrue(buildQueueRows(emptyList(), 0).isEmpty())
    }

    @Test
    fun rows_outOfRangeIndexHighlightsNothing() {
        val rows = buildQueueRows(listOf(song("1"), song("2")), 9)
        assertTrue(rows.none { it.isCurrent })
    }

    @Test
    fun rows_sourceIdsPreservedInOrder() {
        val rows = buildQueueRows(listOf(song("x"), song("y")), 0)
        assertEquals(listOf("x", "y"), rows.map { it.sourceId })
    }

    @Test
    fun guard_singleItemCannotBeRemoved() {
        assertFalse(canRemoveQueueItem(1))
        assertFalse(canRemoveQueueItem(0))
    }

    @Test
    fun guard_multiItemCanBeRemoved() {
        assertTrue(canRemoveQueueItem(2))
        assertTrue(canRemoveQueueItem(10))
    }
}
