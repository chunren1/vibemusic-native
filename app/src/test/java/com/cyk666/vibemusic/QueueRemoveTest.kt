package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueRemoveTest {

    @Test
    fun removeBeforeCurrent_shiftsCurrentDown() {
        assertEquals(2, nextIndexAfterRemove(size = 5, removedIdx = 1, currentIdx = 3))
    }

    @Test
    fun removeCurrentMiddle_nextSlidesIntoSlot() {
        assertEquals(2, nextIndexAfterRemove(size = 5, removedIdx = 2, currentIdx = 2))
    }

    @Test
    fun removeCurrentTail_clampsToNewLast() {
        assertEquals(3, nextIndexAfterRemove(size = 5, removedIdx = 4, currentIdx = 4))
    }

    @Test
    fun removeAfterCurrent_keepsIndex() {
        assertEquals(1, nextIndexAfterRemove(size = 5, removedIdx = 4, currentIdx = 1))
    }

    @Test
    fun twoItems_removeCurrent_resolvesToSoleSurvivor() {
        assertEquals(0, nextIndexAfterRemove(size = 2, removedIdx = 0, currentIdx = 0))
        assertEquals(0, nextIndexAfterRemove(size = 2, removedIdx = 1, currentIdx = 1))
    }

    @Test
    fun twoItems_removeOther_currentShifts() {
        assertEquals(0, nextIndexAfterRemove(size = 2, removedIdx = 0, currentIdx = 1))
    }

    @Test
    fun singleItem_returnsZero() {
        assertEquals(0, nextIndexAfterRemove(size = 1, removedIdx = 0, currentIdx = 0))
    }

    @Test
    fun removedPlayingItem_flagsCurrentOnly() {
        assertTrue(removedPlayingItem(removedIdx = 2, currentIdx = 2))
        assertFalse(removedPlayingItem(removedIdx = 1, currentIdx = 2))
        assertFalse(removedPlayingItem(removedIdx = 3, currentIdx = 2))
    }
}
