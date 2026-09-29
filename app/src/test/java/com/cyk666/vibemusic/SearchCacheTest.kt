package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchCacheTest {

    private fun song(id: String) = Song(
        sourceId = id,
        name = "n-$id",
        artist = "artist",
        album = "",
        coverUrl = "",
        durationSec = 200,
        platform = "netease"
    )

    @Test
    fun `missOnEmpty`() {
        assertNull(SearchResultCache().get("青花瓷", nowMs = 1_000L))
    }

    @Test
    fun `putThenHitReturnsSongsAndTotal`() {
        val c = SearchResultCache()
        c.put("青花瓷", listOf(song("1"), song("2")), total = 42, nowMs = 1_000L)
        val hit = c.get("青花瓷", nowMs = 2_000L)
        assertEquals(listOf("1", "2"), hit?.songs?.map { it.sourceId })
        assertEquals(42, hit?.total)
    }

    @Test
    fun `keyIsTrimmed`() {
        val c = SearchResultCache()
        c.put("  青花瓷  ", listOf(song("1")), total = 1, nowMs = 1_000L)
        assertEquals(1, c.get("青花瓷", nowMs = 2_000L)?.songs?.size)
    }

    @Test
    fun `expiredEntryIsMiss`() {
        val c = SearchResultCache(ttlMs = 5 * 60 * 1000L)
        c.put("青花瓷", listOf(song("1")), total = 1, nowMs = 1_000L)
        assertNull(c.get("青花瓷", nowMs = 1_000L + 5 * 60 * 1000L + 1L))
    }

    @Test
    fun `lruEvictsOldestBeyondCap`() {
        val c = SearchResultCache(maxEntries = 2)
        c.put("a", listOf(song("a")), total = 1, nowMs = 1_000L)
        c.put("b", listOf(song("b")), total = 1, nowMs = 2_000L)
        c.put("c", listOf(song("c")), total = 1, nowMs = 3_000L)
        assertNull(c.get("a", nowMs = 4_000L))
        assertEquals(1, c.get("b", nowMs = 4_000L)?.songs?.size)
        assertEquals(1, c.get("c", nowMs = 4_000L)?.songs?.size)
    }

    @Test
    fun `blankKeywordNeverCached`() {
        val c = SearchResultCache()
        c.put("   ", listOf(song("1")), total = 1, nowMs = 1_000L)
        assertEquals(0, c.size())
    }
}
