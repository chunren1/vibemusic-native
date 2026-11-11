package com.cyk666.vibemusic

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Valid-shaped fake audio: header + zero body to the requested size. */
fun fakeAudioBytes(header: ByteArray, size: Int): ByteArray {
    val out = ByteArray(size)
    header.copyInto(out, 0, 0, minOf(header.size, size))
    return out
}

fun fakeMp3(size: Int = MIN_AUDIO_BYTES): ByteArray =
    fakeAudioBytes(byteArrayOf(0x49, 0x44, 0x33, 0x04), size)

@RunWith(RobolectricTestRunner::class)
class AudioValidationTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    @Test
    fun magic_id3IsAudio() {
        assertTrue(looksLikeAudio(fakeAudioBytes(byteArrayOf(0x49, 0x44, 0x33), 16)))
    }

    @Test
    fun magic_mpegFrameSyncIsAudio() {
        assertTrue(looksLikeAudio(fakeAudioBytes(byteArrayOf(0xFF.toByte(), 0xFB.toByte()), 16)))
        assertTrue(looksLikeAudio(fakeAudioBytes(byteArrayOf(0xFF.toByte(), 0xE0.toByte()), 16)))
    }

    @Test
    fun magic_isoBmffIsAudio() {
        // B 站音轨是 fMP4：前 4 字节是 box 长度，`ftyp` 落在 4..7（2026-11-03 线上实测
        // `00 00 00 20 66 74 79 70 69 73 6f 35`）。只看第 0-3 字节会让每一首 B 站歌曲
        // 下载失败，并把已下好的判成损坏。
        val mp4 = byteArrayOf(0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x35)
        assertTrue(looksLikeAudio(fakeAudioBytes(mp4, 16)))
        // 只有 4 字节（长度 + 半截 box 类型）不足以判断 → 不能算音频
        assertFalse(looksLikeAudio(mp4.copyOfRange(0, 4)))
    }

    @Test
    fun magic_flacIsAudio() {
        assertTrue(
            looksLikeAudio(
                fakeAudioBytes(byteArrayOf(0x66, 0x4C, 0x61, 0x43), 16)
            )
        )
    }

    @Test
    fun magic_oggIsAudio() {
        assertTrue(
            looksLikeAudio(
                fakeAudioBytes(byteArrayOf(0x4F, 0x67, 0x67, 0x53), 16)
            )
        )
    }

    @Test
    fun magic_wavRiffIsAudio() {
        assertTrue(
            looksLikeAudio(
                fakeAudioBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46), 16)
            )
        )
    }

    @Test
    fun magic_jsonErrorBodyIsNotAudio() {
        val json = """{"code":500,"message":"stream failed"}""".toByteArray()
        assertFalse(looksLikeAudio(json))
    }

    @Test
    fun magic_plainTextIsNotAudio() {
        assertFalse(looksLikeAudio("hello world, this is not audio".toByteArray()))
    }

    @Test
    fun magic_emptyIsNotAudio() {
        assertFalse(looksLikeAudio(ByteArray(0)))
    }

    @Test
    fun magic_truncatedHeaderIsNotAudio() {
        assertFalse(looksLikeAudio(byteArrayOf(0x49, 0x44)))
        assertFalse(looksLikeAudio(byteArrayOf(0x49, 0x44, 0x33)))
        assertFalse(looksLikeAudio(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x00)))
    }

    @Test
    fun magic_frameSyncSecondByteMustHaveTopBits() {
        assertFalse(looksLikeAudio(fakeAudioBytes(byteArrayOf(0xFF.toByte(), 0x00), 16)))
        assertFalse(looksLikeAudio(fakeAudioBytes(byteArrayOf(0xFE.toByte(), 0xFB.toByte()), 16)))
    }

    @Test
    fun validDownload_floorBoundary() {
        assertFalse(isValidDownload(fakeMp3(MIN_AUDIO_BYTES - 1)))
        assertTrue(isValidDownload(fakeMp3(MIN_AUDIO_BYTES)))
        assertTrue(isValidDownload(fakeMp3(MIN_AUDIO_BYTES + 1)))
    }

    @Test
    fun validDownload_bigJsonStillRejected() {
        val big = ByteArray(MIN_AUDIO_BYTES + 500)
        """{"code":500}""".toByteArray().copyInto(big)
        assertFalse(isValidDownload(big))
    }

    @Test
    fun validDownload_emptyRejected() {
        assertFalse(isValidDownload(ByteArray(0)))
    }

    @Test
    fun saveBytes_rejectsEmpty() {
        val ctx = context()
        val song = Song("rej-empty", "n", "a", "", "", 100, "netease")
        assertFalse(OfflineStore.saveBytes(ctx, song, ByteArray(0), 1700000000000L))
        assertFalse(OfflineStore.isDownloaded(ctx, song))
    }

    @Test
    fun saveBytes_rejectsJsonErrorBody() {
        val ctx = context()
        val song = Song("rej-json", "n", "a", "", "", 100, "netease")
        val body = """{"code":500,"message":"stream failed"}""".toByteArray()
        assertFalse(OfflineStore.saveBytes(ctx, song, body, 1700000000000L))
        assertFalse(OfflineStore.isDownloaded(ctx, song))
    }

    @Test
    fun saveBytes_rejectsUndersizedAudioHeader() {
        val ctx = context()
        val song = Song("rej-small", "n", "a", "", "", 100, "netease")
        assertFalse(OfflineStore.saveBytes(ctx, song, fakeMp3(1024), 1700000000000L))
        assertFalse(OfflineStore.isDownloaded(ctx, song))
    }

    @Test
    fun saveBytes_acceptsValidAudioRoundTrip() {
        val ctx = context()
        val song = Song("acc-valid", "n", "a", "", "", 100, "netease")
        try {
            OfflineStore.audioFile(ctx, song).delete()
            OfflineStore.metaFile(ctx, song).delete()
        } catch (_: Exception) {
        }
        assertTrue(OfflineStore.saveBytes(ctx, song, fakeMp3(), 1700000000000L))
        assertTrue(OfflineStore.isDownloaded(ctx, song))
        val listed = OfflineStore.listDownloads(ctx)
        assertTrue(listed.any { it.sourceId == "acc-valid" })
        assertTrue(OfflineStore.delete(ctx, listed.first { it.sourceId == "acc-valid" }))
        assertFalse(OfflineStore.isDownloaded(ctx, song))
    }

    @Test
    fun floor_matchesSpec() {
        assertEquals(100_000, MIN_AUDIO_BYTES)
    }

    // ---- durationMatches：下载内容与歌曲元数据的时长一致性（防换源错配/试听片段入库）----

    @Test
    fun durationMatches_exactMatch() {
        assertTrue(durationMatches(190, 190_063L))
    }

    @Test
    fun durationMatches_withinToleranceForVersionEdits() {
        // 前奏剪辑/编码误差：190s 的歌实际 200s（+10s，在 15s 容差内）
        assertTrue(durationMatches(190, 200_000L))
        // 190s 实际 175s（-15s，边界）
        assertTrue(durationMatches(190, 175_000L))
    }

    @Test
    fun durationMatches_rejectsWrongSongOrSnippet() {
        // 试听片段：190s 的歌只下到 60s
        assertFalse(durationMatches(190, 60_000L))
        // 另一首歌：190s 的歌实际 240s
        assertFalse(durationMatches(190, 240_000L))
    }

    @Test
    fun durationMatches_missingMetadataPasses() {
        // 元数据缺失或时长读不出时不拦（宁可入库也不误拒）
        assertTrue(durationMatches(0, 190_000L))
        assertTrue(durationMatches(-1, null))
        assertTrue(durationMatches(190, null))
        assertTrue(durationMatches(190, 0L))
    }

    // ---- fixTagMojibake：GBK 标签乱码修复 ----

    @Test
    fun fixTagMojibake_repairsGbkMojibake() {
        // "还是会想你" 的 GBK 字节被按 ISO-8859-1 读出后的形态
        val gbkBytes = "还是会想你".toByteArray(charset("GBK"))
        val mojibake = String(gbkBytes, Charsets.ISO_8859_1)
        assertEquals("还是会想你", fixTagMojibake(mojibake))
    }

    @Test
    fun fixTagMojibake_keepsNormalChinese() {
        assertEquals("晴天", fixTagMojibake("晴天"))
        assertEquals("还是会想你 (Live版)", fixTagMojibake("还是会想你 (Live版)"))
    }

    @Test
    fun fixTagMojibake_keepsAscii() {
        assertEquals("Shape of You", fixTagMojibake("Shape of You"))
        assertEquals("", fixTagMojibake(""))
    }
}
