package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotCacheTest {

    // ---- isStale ----

    @Test
    fun stale_neverLoadedIsStale() {
        assertTrue(isStale(0L, HOTSPOT_DISCOVER_TTL_MS, 1_000L))
        assertTrue(isStale(-5L, HOTSPOT_DISCOVER_TTL_MS, 1_000L))
    }

    @Test
    fun stale_freshWithinTtl() {
        val now = 10_000_000L
        assertFalse(isStale(now - 1_000L, HOTSPOT_DISCOVER_TTL_MS, now))
        assertFalse(isStale(now - HOTSPOT_DISCOVER_TTL_MS + 1L, HOTSPOT_DISCOVER_TTL_MS, now))
    }

    @Test
    fun stale_boundaryIsStale() {
        val now = 10_000_000L
        assertTrue(isStale(now - HOTSPOT_DISCOVER_TTL_MS, HOTSPOT_DISCOVER_TTL_MS, now))
        assertTrue(isStale(now - HOTSPOT_DISCOVER_TTL_MS - 1L, HOTSPOT_DISCOVER_TTL_MS, now))
    }

    @Test
    fun stale_futureTimestampIsFresh() {
        val now = 1_000_000L
        assertFalse(isStale(now + 60_000L, HOTSPOT_DISCOVER_TTL_MS, now))
    }

    @Test
    fun stale_nonPositiveTtlAlwaysStale() {
        assertTrue(isStale(999L, 0L, 1_000L))
        assertTrue(isStale(999L, -10L, 1_000L))
    }

    // ---- TTL table ----

    @Test
    fun ttl_discoverSectionsAre30Min() {
        assertEquals(30 * 60 * 1000L, HOTSPOT_DISCOVER_TTL_MS)
        assertEquals(HOTSPOT_DISCOVER_TTL_MS, hotspotTtlMs(HotspotSection.DAILY))
        assertEquals(HOTSPOT_DISCOVER_TTL_MS, hotspotTtlMs(HotspotSection.GUESS))
        assertEquals(HOTSPOT_DISCOVER_TTL_MS, hotspotTtlMs(HotspotSection.HOT))
    }

    @Test
    fun ttl_playlistsAre5Min() {
        assertEquals(5 * 60 * 1000L, HOTSPOT_PLAYLISTS_TTL_MS)
        assertEquals(HOTSPOT_PLAYLISTS_TTL_MS, hotspotTtlMs(HotspotSection.PLAYLISTS))
    }

    // ---- shouldBackgroundRefresh (SWR matrix) ----

    @Test
    fun swr_noCacheNeverBackgroundRefreshes() {
        val now = 2_000_000L
        assertFalse(
            shouldBackgroundRefresh(false, 0L, HOTSPOT_DISCOVER_TTL_MS, now)
        )
        assertFalse(
            shouldBackgroundRefresh(false, now - 2 * HOTSPOT_DISCOVER_TTL_MS, HOTSPOT_DISCOVER_TTL_MS, now)
        )
    }

    @Test
    fun swr_freshCacheShowsInstantlyNoRefresh() {
        val now = 2_000_000L
        assertFalse(
            shouldBackgroundRefresh(true, now - 1_000L, HOTSPOT_DISCOVER_TTL_MS, now)
        )
    }

    @Test
    fun swr_staleCacheRefreshesSilently() {
        val now = 2_000_000L
        assertTrue(
            shouldBackgroundRefresh(
                true,
                now - HOTSPOT_DISCOVER_TTL_MS - 1L,
                HOTSPOT_DISCOVER_TTL_MS,
                now
            )
        )
        assertTrue(
            shouldBackgroundRefresh(true, 0L, HOTSPOT_PLAYLISTS_TTL_MS, now)
        )
    }

    @Test
    fun swr_playlistsExpireFasterThanDiscover() {
        val now = 2_000_000L
        val sixMinAgo = now - 6 * 60 * 1000L
        assertTrue(shouldBackgroundRefresh(true, sixMinAgo, HOTSPOT_PLAYLISTS_TTL_MS, now))
        assertFalse(shouldBackgroundRefresh(true, sixMinAgo, HOTSPOT_DISCOVER_TTL_MS, now))
    }

    // ---- needsBlockingLoad ----

    @Test
    fun blocking_onlyWhenNothingCached() {
        assertTrue(needsBlockingLoad(false))
        assertFalse(needsBlockingLoad(true))
    }
}
