package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnqueueSingleTest {

    private fun song(id: String) = Song(id, "n$id", "a", "al", "", 180, "netease")

    @Test
    fun emptyQueueStartsSingleItemAtZero() {
        val placed = enqueueSingleAfterCurrent(emptyList(), 0, song("x"))
        assertEquals(listOf(song("x")), placed.queue)
        assertEquals(0, placed.index)
    }

    @Test
    fun tapInsertsRightAfterCurrentIndex() {
        val queue = listOf(song("1"), song("2"), song("3"))
        val placed = enqueueSingleAfterCurrent(queue, 0, song("x"))
        assertEquals(
            listOf(song("1"), song("x"), song("2"), song("3")),
            placed.queue
        )
        assertEquals(1, placed.index)
    }

    @Test
    fun tapAtTailAppendsAtEnd() {
        val queue = listOf(song("1"), song("2"))
        val placed = enqueueSingleAfterCurrent(queue, 1, song("x"))
        assertEquals(
            listOf(song("1"), song("2"), song("x")),
            placed.queue
        )
        assertEquals(2, placed.index)
    }

    @Test
    fun existingItemsPreservedInOrder() {
        val queue = listOf(song("1"), song("2"), song("3"), song("4"))
        val tapped = song("x")
        val placed = enqueueSingleAfterCurrent(queue, 2, tapped)
        assertEquals(queue.size + 1, placed.queue.size)
        assertEquals(tapped, placed.queue[placed.index])
        assertEquals(queue, placed.queue.filter { it.sourceId != "x" })
    }

    @Test
    fun outOfRangeIndexClampsToTail() {
        val queue = listOf(song("1"), song("2"))
        val placed = enqueueSingleAfterCurrent(queue, 99, song("x"))
        assertEquals(
            listOf(song("1"), song("2"), song("x")),
            placed.queue
        )
        assertEquals(2, placed.index)
    }

    @Test
    fun negativeIndexClampsToHead() {
        val queue = listOf(song("1"), song("2"))
        val placed = enqueueSingleAfterCurrent(queue, -5, song("x"))
        assertEquals(
            listOf(song("1"), song("x"), song("2")),
            placed.queue
        )
        assertEquals(1, placed.index)
    }

    @Test
    fun doesNotReplaceWholeQueue() {
        val queue = listOf(song("1"), song("2"))
        val placed = enqueueSingleAfterCurrent(queue, 0, song("x"))
        assertTrue(placed.queue.containsAll(queue))
        assertEquals(3, placed.queue.size)
    }
}
