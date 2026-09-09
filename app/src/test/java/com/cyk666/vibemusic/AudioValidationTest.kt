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
}
