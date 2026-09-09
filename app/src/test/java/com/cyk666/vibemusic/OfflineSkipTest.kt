package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineSkipTest {

    private fun songs(n: Int) = List(n) { i ->
        Song("id$i", "n$i", "a", "", "", 100, "netease")
    }

    @Test
    fun advancesToNextPlayable() {
        val q = songs(4)
        assertEquals(1, selectNextOfflineIndex(q, 0, { it == 1 }, false))
    }

    @Test
    fun skipsHolesAhead() {
        val q = songs(5)
        assertEquals(3, selectNextOfflineIndex(q, 0, { it == 3 || it == 4 }, false))
    }

    @Test
    fun noneAheadWithoutRepeatStops() {
        val q = songs(3)
        assertEquals(-1, selectNextOfflineIndex(q, 1, { false }, false))
    }

    @Test
    fun noneAheadWithRepeatWrapsOnce() {
        val q = songs(4)
        assertEquals(0, selectNextOfflineIndex(q, 3, { it == 0 }, true))
    }

    @Test
    fun wrapSkipsUnplayableBefore() {
        val q = songs(4)
        assertEquals(2, selectNextOfflineIndex(q, 3, { it == 2 }, true))
    }

    @Test
    fun wrapExcludesFailedCurrent() {
        val q = songs(1)
        assertEquals(-1, selectNextOfflineIndex(q, 0, { it == 0 }, true))
    }

    @Test
    fun wrapFindsNothingStops() {
        val q = songs(3)
        assertEquals(-1, selectNextOfflineIndex(q, 2, { false }, true))
    }

    @Test
    fun emptyQueueStops() {
        assertEquals(-1, selectNextOfflineIndex(emptyList(), 0, { true }, true))
    }

    @Test
    fun aheadBeatsWrap() {
        val q = songs(4)
        assertEquals(3, selectNextOfflineIndex(q, 1, { it == 0 || it == 3 }, true))
    }
}
