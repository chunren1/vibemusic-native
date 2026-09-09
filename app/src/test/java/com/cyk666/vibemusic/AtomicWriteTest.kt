package com.cyk666.vibemusic

import android.content.Context
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AtomicWriteTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private fun clean(ctx: Context, song: Song) {
        try {
            OfflineStore.audioFile(ctx, song).delete()
        } catch (_: Exception) {
        }
        try {
            OfflineStore.metaFile(ctx, song).delete()
        } catch (_: Exception) {
        }
        try {
            File(
                OfflineStore.dir(ctx),
                OfflineStore.audioFile(ctx, song).name + ".tmp"
            ).delete()
        } catch (_: Exception) {
        }
    }

    @Test
    fun happyPath_bytesIdenticalAndNoTmpLeft() {
        val ctx = context()
        val song = Song("atom-happy", "n", "a", "", "", 100, "netease")
        clean(ctx, song)
        val bytes = fakeMp3()
        assertTrue(OfflineStore.saveBytes(ctx, song, bytes, 1700000000000L))
        assertArrayEquals(bytes, OfflineStore.audioFile(ctx, song).readBytes())
        assertTrue(OfflineStore.metaFile(ctx, song).exists())
        assertFalse(
            File(
                OfflineStore.dir(ctx),
                OfflineStore.audioFile(ctx, song).name + ".tmp"
            ).exists()
        )
        clean(ctx, song)
    }

    @Test
    fun interruptedWrite_staleTmpCleanedAndFinalValid() {
        val ctx = context()
        val song = Song("atom-stale", "n", "a", "", "", 100, "netease")
        clean(ctx, song)
        // Simulate a process killed mid-write: garbage temp, no final file.
        val tmp = File(
            OfflineStore.dir(ctx),
            OfflineStore.audioFile(ctx, song).name + ".tmp"
        )
        tmp.parentFile?.mkdirs()
        tmp.writeBytes("half-written garbage".toByteArray())
        assertFalse(OfflineStore.audioFile(ctx, song).exists())
        // Next successful save must sweep the stale temp and land a valid file.
        assertTrue(OfflineStore.saveBytes(ctx, song, fakeMp3(), 1700000000000L))
        assertFalse(tmp.exists())
        assertTrue(OfflineStore.isDownloaded(ctx, song))
        clean(ctx, song)
    }

    @Test
    fun rejectedSave_leavesNoHalfFiles() {
        val ctx = context()
        val song = Song("atom-reject", "n", "a", "", "", 100, "netease")
        clean(ctx, song)
        val json = """{"code":500,"message":"truncated"}""".toByteArray()
        assertFalse(OfflineStore.saveBytes(ctx, song, json, 1700000000000L))
        assertFalse(OfflineStore.audioFile(ctx, song).exists())
        assertFalse(OfflineStore.metaFile(ctx, song).exists())
        assertFalse(
            File(
                OfflineStore.dir(ctx),
                OfflineStore.audioFile(ctx, song).name + ".tmp"
            ).exists()
        )
        assertFalse(OfflineStore.isDownloaded(ctx, song))
    }

    @Test
    fun rejectedUndersized_leavesNoHalfFiles() {
        val ctx = context()
        val song = Song("atom-small", "n", "a", "", "", 100, "netease")
        clean(ctx, song)
        assertFalse(OfflineStore.saveBytes(ctx, song, fakeMp3(1024), 1700000000000L))
        assertFalse(OfflineStore.audioFile(ctx, song).exists())
        assertFalse(OfflineStore.metaFile(ctx, song).exists())
        assertFalse(
            File(
                OfflineStore.dir(ctx),
                OfflineStore.audioFile(ctx, song).name + ".tmp"
            ).exists()
        )
    }

    @Test
    fun metaOnlyAfterAudio_verifiedOnDisk() {
        val ctx = context()
        val song = Song("atom-meta", "n", "a", "", "", 100, "netease")
        clean(ctx, song)
        // Empty input fails before any disk touch: no meta without audio.
        assertFalse(OfflineStore.saveBytes(ctx, song, ByteArray(0), 1700000000000L))
        assertFalse(OfflineStore.audioFile(ctx, song).exists())
        assertFalse(OfflineStore.metaFile(ctx, song).exists())
    }
}
