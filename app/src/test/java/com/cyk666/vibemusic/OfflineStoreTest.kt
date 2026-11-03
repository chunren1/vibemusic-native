package com.cyk666.vibemusic

import android.content.Context
import com.cyk666.vibemusic.OfflineStore.toLocalMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OfflineStoreTest {

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

    @Test
    fun orphanScanReclaimsUnreferencedAudioAndTmp() {
        val dir = java.nio.file.Files.createTempDirectory("offline-orphan").toFile()
        try {
            // 这条 meta 由 [demo] 推导：音频名 = netease_1895330088.mp3
            java.io.File(dir, "netease_1895330088.meta")
                .writeText(OfflineMeta.of(demo, 100L).toJson())
            val keptAudio = java.io.File(dir, "netease_1895330088.mp3").apply { writeBytes(ByteArray(8)) }
            val orphanAudio = java.io.File(dir, "old_rule_1895330088.mp3").apply { writeBytes(ByteArray(8)) }
            val staleTmp = java.io.File(dir, "qq_7.mp3.tmp").apply { writeBytes(ByteArray(8)) }
            val now = 10_000_000L
            listOf(keptAudio, orphanAudio, staleTmp)
                .forEach { assertTrue(it.setLastModified(now - ORPHAN_GRACE_MS - 1)) }

            val found = orphanDownloads(dir, now).map { it.name }.toSet()
            assertEquals(setOf("old_rule_1895330088.mp3", "qq_7.mp3.tmp"), found)
            // 被 meta 引用的那份音频必须留着
            assertTrue(keptAudio.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun threshold_belowSixDoesNotAutoSave() {
        // 2026-11-03 起阈值 3 → 6（用户要求）：1~5 次都不许自动存。
        assertFalse(shouldAutoSave(0))
        assertFalse(shouldAutoSave(1))
        assertFalse(shouldAutoSave(2))
        assertFalse(shouldAutoSave(3))
        assertFalse(shouldAutoSave(4))
        assertFalse(shouldAutoSave(5))
    }

    @Test
    fun threshold_sixAutoSaves() {
        assertTrue(shouldAutoSave(6))
    }

    @Test
    fun threshold_aboveSixStillRetries() {
        // 回归：`count == THRESHOLD` 只有一个窗口 —— 命中那次断网或内容校验失败后
        // 计数就跨过去了，此后再也不会尝试。`>=` 保证每次播放都重试（isDownloaded 挡重复）。
        assertTrue(shouldAutoSave(7))
        assertTrue(shouldAutoSave(100))
    }

    @Test
    fun threshold_matchesDeclaredConstant() {
        // 阈值只有一处来源：改常量必须连带改上面三条真值表，不许只改一处。
        assertEquals(6, HIGH_FREQ_THRESHOLD)
    }

    @Test
    fun playKey_combinesPlatformAndSourceId() {
        assertEquals("netease:1895330088", playKey(demo))
        assertEquals("qq:42", playKey("qq", "42"))
        assertEquals("netease:42", playKey("", "42"))
    }

    @Test
    fun playCounts_jsonRoundTrip() {
        val rendered = renderPlayCounts(mapOf("netease:1" to 2, "qq:9" to 3))
        val parsed = parsePlayCounts(rendered)
        assertEquals(2, parsed["netease:1"])
        assertEquals(3, parsed["qq:9"])
    }

    @Test
    fun playCounts_blankAndCorruptAreEmpty() {
        assertTrue(parsePlayCounts("").isEmpty())
        assertTrue(parsePlayCounts("not-json{{{").isEmpty())
    }

    @Test
    fun bumpPlayCount_incrementsOnce() {
        val (m1, c1) = bumpPlayCount(emptyMap(), "netease:1")
        assertEquals(1, c1)
        val (m2, c2) = bumpPlayCount(m1, "netease:1")
        assertEquals(2, c2)
        assertEquals(2, m2["netease:1"])
    }

    @Test
    fun filename_weirdSourceIdsSanitized() {
        assertEquals("netease_abc", offlineBaseName("netease", "abc"))
        val weird = offlineBaseName("netease/", "a/b?c:d*e f.mp3")
        assertFalse(weird.contains("/"))
        assertFalse(weird.contains("?"))
        assertFalse(weird.contains(":"))
        assertFalse(weird.contains("*"))
        assertFalse(weird.contains(" "))
        assertTrue(weird.startsWith("netease__"))
    }

    @Test
    fun filename_platformFallsBackToNetease() {
        assertEquals("netease_1", offlineBaseName("", "1"))
        assertEquals("netease_1895330088", offlineBaseName(demo.copy(platform = "")))
    }

    @Test
    fun meta_roundTrip() {
        val meta = OfflineMeta.of(demo, 1700000000000L)
        val back = OfflineMeta.fromJson(meta.toJson())!!
        assertEquals(demo.sourceId, back.sourceId)
        assertEquals(demo.name, back.name)
        assertEquals(demo.artist, back.artist)
        assertEquals(demo.album, back.album)
        assertEquals(demo.coverUrl, back.coverUrl)
        assertEquals(demo.durationSec, back.durationSec)
        assertEquals("netease", back.platform)
        assertEquals(1700000000000L, back.downloadedAt)
    }

    @Test
    fun meta_corruptIsNull() {
        assertEquals(null, OfflineMeta.fromJson("garbage{{{"))
        assertEquals(null, OfflineMeta.fromJson(""))
    }

    @Test
    fun decision_downloadedAlwaysPlaysLocal() {
        assertEquals(LocalDecision.PLAY_LOCAL, offlineFileDecision(true, true))
        assertEquals(LocalDecision.PLAY_LOCAL, offlineFileDecision(true, false))
    }

    @Test
    fun decision_missingOnlineStreams() {
        assertEquals(LocalDecision.STREAM, offlineFileDecision(false, true))
    }

    @Test
    fun decision_missingOfflineBlocks() {
        assertEquals(LocalDecision.BLOCK_WITH_MESSAGE, offlineFileDecision(false, false))
    }

    @Test
    fun dedup_notDownloadedBeforeSave() {
        val ctx = context()
        val song = demo.copy(sourceId = "dedup-probe-1")
        try {
            OfflineStore.audioFile(ctx, song).delete()
            OfflineStore.metaFile(ctx, song).delete()
        } catch (_: Exception) {
        }
        assertFalse(OfflineStore.isDownloaded(ctx, song))
    }

    @Test
    fun dedup_saveThenDeleteRoundTrip() {
        val ctx = context()
        val song = demo.copy(sourceId = "dedup-probe-2")
        try {
            OfflineStore.audioFile(ctx, song).delete()
            OfflineStore.metaFile(ctx, song).delete()
        } catch (_: Exception) {
        }
        assertTrue(OfflineStore.saveBytes(ctx, song, fakeMp3(100_000), 1700000000000L))
        assertTrue(OfflineStore.isDownloaded(ctx, song))
        val listed = OfflineStore.listDownloads(ctx)
        assertTrue(listed.any { it.sourceId == "dedup-probe-2" })
        assertTrue(OfflineStore.delete(ctx, listed.first { it.sourceId == "dedup-probe-2" }))
        assertFalse(OfflineStore.isDownloaded(ctx, song))
    }

    @Test
    fun dedup_partialFileWithoutMetaIsNotDownloaded() {
        val ctx = context()
        val song = demo.copy(sourceId = "dedup-probe-3")
        try {
            OfflineStore.metaFile(ctx, song).delete()
            OfflineStore.audioFile(ctx, song).writeBytes(byteArrayOf(9))
        } catch (_: Exception) {
        }
        assertFalse(OfflineStore.isDownloaded(ctx, song))
        try {
            OfflineStore.audioFile(ctx, song).delete()
        } catch (_: Exception) {
        }
    }

    @Test
    fun localMediaItem_carriesLocalIdAndFileUri() {
        val ctx = context()
        val item = demo.toLocalMediaItem(ctx)
        assertEquals("local:netease:1895330088", item.mediaId)
        assertEquals("file", item.localConfiguration?.uri?.scheme)
        assertTrue(item.localConfiguration?.uri.toString().endsWith("netease_1895330088.mp3"))
        val back = songFromMediaItem(item)
        assertEquals("1895330088", back.sourceId)
        assertEquals("netease", back.platform)
        assertEquals("予以", back.name)
    }

    @Test
    fun songFromMediaItem_legacyLocalIdWithoutPlatform() {
        val item = androidx.media3.common.MediaItem.Builder()
            .setUri(demo.streamUrl())
            .setMediaId("local:1895330088")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle("予以")
                    .setExtras(android.os.Bundle().apply { putString("platform", "qq") })
                    .build()
            )
            .build()
        val back = songFromMediaItem(item)
        assertEquals("1895330088", back.sourceId)
        assertEquals("qq", back.platform)
    }
}
