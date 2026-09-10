package com.cyk666.vibemusic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLaunchTest {

    @Test
    fun blankQuery_neverAutoSearches() {
        assertFalse(shouldAutoSearchOnLaunch(""))
        assertFalse(shouldAutoSearchOnLaunch("   "))
        assertFalse(shouldAutoSearchOnLaunch("\t\n"))
    }

    @Test
    fun nonBlankQuery_mayAutoSearch() {
        assertTrue(shouldAutoSearchOnLaunch("予以"))
        assertTrue(shouldAutoSearchOnLaunch("  周杰伦  "))
        assertTrue(shouldAutoSearchOnLaunch("a"))
    }
}
