package com.cyk666.vibemusic

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MaterializeTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    // ---- needsMaterialize truth table: empty timeline + non-empty queue heals ----

    @Test
    fun emptyTimelineEmptyQueue_noMaterialize() {
        assertFalse(needsMaterialize(0, 0))
    }

    @Test
    fun emptyTimelineWithQueue_materializes() {
        assertTrue(needsMaterialize(0, 5))
    }

    @Test
    fun populatedTimeline_noMaterialize() {
        assertFalse(needsMaterialize(3, 5))
    }

    @Test
    fun singleQueuedSong_materializes() {
        assertTrue(needsMaterialize(0, 1))
    }

    @Test
    fun emptyQueueWithStaleTimeline_noMaterialize() {
        assertFalse(needsMaterialize(2, 0))
    }

    // ---- resolveMaterializePosition: known duration defers to the restore rule ----

    @Test
    fun knownDuration_midTrackRestores() {
        assertEquals(60_000L, resolveMaterializePosition(60_000L, 231_000L))
    }

    @Test
    fun knownDuration_introAndOutroClampToZero() {
        assertEquals(0L, resolveMaterializePosition(5_000L, 231_000L))
        assertEquals(0L, resolveMaterializePosition(2_000L, 231_000L))
        assertEquals(0L, resolveMaterializePosition(225_000L, 231_000L))
        assertEquals(0L, resolveMaterializePosition(0L, 231_000L))
    }

    // ---- resolveMaterializePosition: unknown duration → restore past intro ----

    @Test
    fun unknownDuration_restoresPastIntro() {
        assertEquals(60_000L, resolveMaterializePosition(60_000L, 0L))
        assertEquals(60_000L, resolveMaterializePosition(60_000L, -1L))
        assertEquals(5_001L, resolveMaterializePosition(5_001L, 0L))
    }

    @Test
    fun unknownDuration_introStaysAtZero() {
        assertEquals(0L, resolveMaterializePosition(5_000L, 0L))
        assertEquals(0L, resolveMaterializePosition(0L, 0L))
    }

    // ---- materialization prefers the downloaded file (local:// URI) ----

    @Test
    fun downloadedSong_materializesToLocalUri() {
        val ctx = context()
        val song = Song(
            sourceId = "mat-probe-local",
            name = "予以",
            artist = "队长",
            album = "予以",
            coverUrl = "",
            durationSec = 231,
            platform = "netease"
        )
        try {
            assertTrue(OfflineStore.saveBytes(ctx, song, fakeMp3(100_000), 1700000000000L))
            val item = song.toPlayMediaItem(ctx)
            assertTrue(item.mediaId.startsWith("local:"))
            assertEquals("file", item.localConfiguration?.uri?.scheme)
        } finally {
            try {
                OfflineStore.audioFile(ctx, song).delete()
            } catch (_: Exception) {
            }
            try {
                OfflineStore.metaFile(ctx, song).delete()
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun streamedSong_materializesToHttpUri() {
        val ctx = context()
        val song = Song(
            sourceId = "mat-probe-stream",
            name = "予以",
            artist = "队长",
            album = "予以",
            coverUrl = "",
            durationSec = 231,
            platform = "netease"
        )
        try {
            OfflineStore.audioFile(ctx, song).delete()
        } catch (_: Exception) {
        }
        try {
            OfflineStore.metaFile(ctx, song).delete()
        } catch (_: Exception) {
        }
        val item = song.toPlayMediaItem(ctx)
        assertFalse(item.mediaId.startsWith("local:"))
        assertEquals("https", item.localConfiguration?.uri?.scheme)
    }
}
