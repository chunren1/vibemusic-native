package com.cyk666.vibemusic

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Review item 5: the in-memory locally-available index. Pure gate/lookup
 * verdicts need no Context; snapshot-build + media-item routing use a
 * Robolectric app context (same convention as MaterializeTest).
 */
@RunWith(RobolectricTestRunner::class)
class OfflineAvailabilityTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private fun song(id: String) = Song(id, "n$id", "a", "", "", 100, "netease")

    // ---- pure gate: online streams, offline needs local or cache ----

    @Test
    fun emptySnapshot_onlinePlays() {
        assertTrue(OfflineAvailability().isGatePlayable(song("s1"), true))
    }

    @Test
    fun emptySnapshot_offlineBlocks() {
        val avail = OfflineAvailability()
        assertFalse(avail.isGatePlayable(song("s1"), false))
        assertFalse(avail.isOfflinePlayable(song("s1")))
    }

    @Test
    fun blankSourceId_neverOfflinePlayable() {
        val avail = OfflineAvailability(
            localBases = setOf(offlineBaseName(song(""))),
            cachedKeys = setOf(MediaCache.cacheKey(song("")))
        )
        assertFalse(avail.isOfflinePlayable(Song("", "n", "a", "", "", 0, "")))
    }

    @Test
    fun localEntry_offlinePlayable() {
        val s = song("local-1")
        val avail = OfflineAvailability(localBases = setOf(offlineBaseName(s)))
        assertTrue(avail.isLocal(s))
        assertFalse(avail.isCached(s))
        assertTrue(avail.isOfflinePlayable(s))
        assertTrue(avail.isGatePlayable(s, false))
    }

    @Test
    fun cachedEntry_offlinePlayableWithoutLocal() {
        val s = song("cached-1")
        val avail = OfflineAvailability(cachedKeys = setOf(MediaCache.cacheKey(s)))
        assertFalse(avail.isLocal(s))
        assertTrue(avail.isCached(s))
        assertTrue(avail.isOfflinePlayable(s))
        assertTrue(avail.isGatePlayable(s, false))
    }

    @Test
    fun unknownSong_notPlayable() {
        val s = song("ghost")
        val avail = OfflineAvailability(
            localBases = setOf(offlineBaseName(song("other"))),
            cachedKeys = setOf(MediaCache.cacheKey(song("other2")))
        )
        assertFalse(avail.isOfflinePlayable(s))
        assertFalse(avail.isGatePlayable(s, false))
        assertTrue(avail.isGatePlayable(s, true))
    }

    // ---- pure: snapshot drives the single-owner offline-next rule ----

    @Test
    fun snapshotPredicate_skipsHolesAndStops() {
        val q = List(4) { i -> song("q$i") }
        val avail = OfflineAvailability(
            localBases = setOf(offlineBaseName(q[1]), offlineBaseName(q[3]))
        )
        val at = { idx: Int -> avail.isOfflinePlayable(q[idx]) }
        assertEquals(1, selectNextOfflineIndex(q, 0, at, false))
        assertEquals(3, selectNextOfflineIndex(q, 1, at, false))
        assertEquals(-1, selectNextOfflineIndex(q, 3, at, false))
        assertEquals(1, selectNextOfflineIndex(q, 3, at, true))
    }

    // ---- snapshot build: one pass, same triage bar as the old loops ----

    @Test
    fun build_emptyList_isEmpty() {
        val avail = buildOfflineAvailability(context(), emptyList())
        assertTrue(avail.localBases.isEmpty())
        assertTrue(avail.cachedKeys.isEmpty())
    }

    @Test
    fun build_downloadedSong_isLocal() {
        val ctx = context()
        val s = song("avail-build-local")
        try {
            assertTrue(OfflineStore.saveBytes(ctx, s, fakeMp3(100_000), 1700000000000L))
            val avail = buildOfflineAvailability(ctx, listOf(s))
            assertTrue(avail.isLocal(s))
            assertTrue(avail.isOfflinePlayable(s))
        } finally {
            try {
                OfflineStore.audioFile(ctx, s).delete()
            } catch (_: Exception) {
            }
            try {
                OfflineMeta.of(s, 0L).let { OfflineStore.delete(ctx, it) }
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun build_missingSong_notLocal() {
        val ctx = context()
        val s = song("avail-build-missing")
        try {
            OfflineStore.audioFile(ctx, s).delete()
        } catch (_: Exception) {
        }
        try {
            OfflineStore.metaFile(ctx, s).delete()
        } catch (_: Exception) {
        }
        val avail = buildOfflineAvailability(ctx, listOf(s))
        assertFalse(avail.isLocal(s))
        assertFalse(avail.isOfflinePlayable(s))
    }

    // ---- snapshot routing matches the legacy per-song routing ----

    @Test
    fun routing_localSnapshot_matchesLegacyLocal() {
        val ctx = context()
        val s = song("avail-route-local")
        try {
            assertTrue(OfflineStore.saveBytes(ctx, s, fakeMp3(100_000), 1700000000000L))
            val avail = buildOfflineAvailability(ctx, listOf(s))
            val viaSnapshot = s.toPlayMediaItem(ctx, avail)
            val viaLegacy = s.toPlayMediaItem(ctx)
            assertTrue(viaSnapshot.mediaId.startsWith("local:"))
            assertEquals(viaLegacy.mediaId, viaSnapshot.mediaId)
            assertEquals("file", viaSnapshot.localConfiguration?.uri?.scheme)
        } finally {
            try {
                OfflineStore.audioFile(ctx, s).delete()
            } catch (_: Exception) {
            }
            try {
                OfflineMeta.of(s, 0L).let { OfflineStore.delete(ctx, it) }
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun routing_emptySnapshot_streams() {
        val ctx = context()
        val s = song("avail-route-stream")
        val item = s.toPlayMediaItem(ctx, OfflineAvailability())
        assertFalse(item.mediaId.startsWith("local:"))
        assertEquals("https", item.localConfiguration?.uri?.scheme)
    }
}
