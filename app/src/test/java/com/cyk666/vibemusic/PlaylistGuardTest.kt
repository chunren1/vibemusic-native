package com.cyk666.vibemusic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistGuardTest {

    @Test
    fun currentPlaylistSameGeneration_lands() {
        assertFalse(isStalePlaylistSongs(3, 3, "42", "42"))
    }

    @Test
    fun supersededGeneration_droppedEvenForSamePlaylist() {
        assertTrue(isStalePlaylistSongs(2, 3, "42", "42"))
    }

    @Test
    fun sameGenerationButSwitchedPlaylist_dropped() {
        assertTrue(isStalePlaylistSongs(3, 3, "43", "42"))
    }

    @Test
    fun selectionClearedMidLoad_dropped() {
        assertTrue(isStalePlaylistSongs(3, 3, null, "42"))
    }

    @Test
    fun bothStale_dropped() {
        assertTrue(isStalePlaylistSongs(1, 3, "other", "42"))
    }
}
