package com.cyk666.vibemusic

import android.content.Context
import com.cyk666.vibemusic.MediaCache.toCachedMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CachePolicyTest {

    private val demo = Song(
        sourceId = "1895330088",
        name = "予以",
        artist = "队长",
        album = "予以",
        coverUrl = "https://cover.example/x.jpg",
        durationSec = 231,
        platform = "netease"
    )

    private fun context(): Context = RuntimeEnvironment.getApplication()

    // ---- offlinePlayDecision truth table ----

    @Test
    fun decision_onlineStreamsRegardlessOfCache() {
        assertEquals(OfflineDecision.STREAM, offlinePlayDecision(true, 0L))
        assertEquals(OfflineDecision.STREAM, offlinePlayDecision(true, 999L))
    }

    @Test
    fun decision_offlineWithBytesPlaysCached() {
        assertEquals(OfflineDecision.PLAY_CACHED, offlinePlayDecision(false, 1L))
        assertEquals(OfflineDecision.PLAY_CACHED, offlinePlayDecision(false, Long.MAX_VALUE))
    }

    @Test
    fun decision_offlineEmptyBlocks() {
        assertEquals(OfflineDecision.BLOCK_WITH_MESSAGE, offlinePlayDecision(false, 0L))
        assertEquals(OfflineDecision.BLOCK_WITH_MESSAGE, offlinePlayDecision(false, -5L))
    }

    // ---- cache-key stability ----

    @Test
    fun cacheKey_stableAcrossCallsForSameSong() {
        assertEquals(MediaCache.cacheKey(demo), MediaCache.cacheKey(demo))
        assertEquals(MediaCache.cacheKey(demo), MediaCache.cacheKey(demo.copy()))
    }

    @Test
    fun cacheKey_differsAcrossSongs() {
        assertTrue(MediaCache.cacheKey(demo) != MediaCache.cacheKey(demo.copy(sourceId = "1")))
        assertTrue(
            MediaCache.cacheKey(demo) !=
                MediaCache.cacheKey(demo.copy(sourceId = "1895330088", platform = "qq"))
        )
    }

    @Test
    fun cacheKey_equalsStreamUrl() {
        assertEquals(demo.streamUrl(), MediaCache.cacheKey(demo))
    }

    // ---- evictor budget ----

    @Test
    fun evictor_budgetIs150MBRolling() {
        assertEquals(150L * 1024L * 1024L, MediaCache.MAX_BYTES)
    }

    // ---- cached MediaItem mapping ----

    @Test
    fun cachedMediaItem_carriesKeyAndKeepsMetadata() {
        val item = demo.toCachedMediaItem()
        assertEquals(demo.sourceId, item.mediaId)
        assertEquals(demo.streamUrl(), item.localConfiguration?.customCacheKey)
        val back = songFromMediaItem(item)
        assertEquals("1895330088", back.sourceId)
        assertEquals("予以", back.name)
        assertEquals("队长", back.artist)
        assertEquals("netease", back.platform)
    }

    // ---- singleton + empty-cache behavior (Robolectric, no device) ----

    @Test
    fun singleton_sameInstanceAcrossCalls() {
        val ctx = context()
        assertSame(MediaCache.get(ctx), MediaCache.get(ctx))
    }

    @Test
    fun freshCache_reportsZeroBytesAndNotCached() {
        val ctx = context()
        MediaCache.clear(ctx)
        assertEquals(0L, MediaCache.cachedBytes(ctx, demo.streamUrl()))
        assertFalse(MediaCache.isCached(ctx, demo.streamUrl()))
        assertTrue(MediaCache.sizeBytes(ctx) >= 0L)
    }

    @Test
    fun cachedBytes_blankUrlIsZero() {
        assertEquals(0L, MediaCache.cachedBytes(context(), ""))
    }

    @Test
    fun clear_isIdempotent() {
        val ctx = context()
        MediaCache.clear(ctx)
        MediaCache.clear(ctx)
        assertEquals(0L, MediaCache.cachedBytes(ctx, demo.streamUrl()))
    }

}
