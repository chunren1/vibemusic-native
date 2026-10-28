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
class HealActionTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    @Test
    fun threshold_deleteOnSecondStrike() {
        assertEquals(2, LOCAL_HEAL_DELETE_THRESHOLD)
    }

    @Test
    fun validFile_streamsOnceUntilProbeStopsBeingTrusted() {
        assertEquals(HealAction.KEEP_STREAM_ONCE, healAction(true, 1, true))
        assertEquals(HealAction.KEEP_STREAM_ONCE, healAction(true, 2, true))
        assertEquals(HealAction.KEEP_STREAM_ONCE, healAction(true, 4, true))
    }

    /**
     * PB-2 的真值表：`isAudioFileIntact` 只读 4 字节 + 大小门，**截断体能通过**，
     * 所以"探针说没问题"不能无限可信 —— 连续失败越线必须强制删，否则这种文件
     * 永远删不掉、文案承诺的"联网后重新下载"永不发生。
     */
    @Test
    fun validFile_forcedDeleteAfterQuietThreshold() {
        assertEquals(5, LOCAL_HEAL_FORCE_DELETE_AFTER)
        assertEquals(HealAction.DELETE_AND_STREAM, healAction(true, 5, true))
        assertEquals(HealAction.DELETE_AND_STREAM, healAction(true, 9, true))
    }

    @Test
    fun invalidFile_firstStrikeStreamsOnce() {
        assertEquals(HealAction.KEEP_STREAM_ONCE, healAction(false, 0, true))
        assertEquals(HealAction.KEEP_STREAM_ONCE, healAction(false, 1, true))
    }

    @Test
    fun invalidFile_secondStrikeDeletes() {
        assertEquals(HealAction.DELETE_AND_STREAM, healAction(false, 2, true))
        assertEquals(HealAction.DELETE_AND_STREAM, healAction(false, 3, true))
    }

    @Test
    fun offline_neverDeletes() {
        assertEquals(HealAction.ERROR_MESSAGE, healAction(true, 1, false))
        assertEquals(HealAction.ERROR_MESSAGE, healAction(false, 1, false))
        assertEquals(HealAction.ERROR_MESSAGE, healAction(false, 2, false))
    }

    private val probe = Song(
        sourceId = "heal-probe-1",
        name = "予以",
        artist = "队长",
        album = "予以",
        coverUrl = "",
        durationSec = 231,
        platform = "netease"
    )

    private fun scrub(ctx: Context) {
        try {
            OfflineStore.audioFile(ctx, probe).delete()
        } catch (_: Exception) {
        }
    }

    @Test
    fun intactFile_validatesTrue() {
        val ctx = context()
        try {
            scrub(ctx)
            OfflineStore.audioFile(ctx, probe).apply {
                parentFile?.mkdirs()
                writeBytes(fakeMp3(100_000))
            }
            assertTrue(OfflineStore.isAudioFileIntact(ctx, probe))
        } finally {
            scrub(ctx)
        }
    }

    @Test
    fun truncatedFile_validatesFalse() {
        val ctx = context()
        try {
            scrub(ctx)
            OfflineStore.audioFile(ctx, probe).apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(0x49, 0x44, 0x33, 0x04))
            }
            assertFalse(OfflineStore.isAudioFileIntact(ctx, probe))
        } finally {
            scrub(ctx)
        }
    }

    @Test
    fun fullSizeNonAudio_validatesFalse() {
        val ctx = context()
        try {
            scrub(ctx)
            OfflineStore.audioFile(ctx, probe).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(100_000) { 0x41 })
            }
            assertFalse(OfflineStore.isAudioFileIntact(ctx, probe))
        } finally {
            scrub(ctx)
        }
    }

    @Test
    fun missingFile_validatesFalse() {
        val ctx = context()
        scrub(ctx)
        assertFalse(OfflineStore.isAudioFileIntact(ctx, probe))
    }
}
