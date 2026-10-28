package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SelfHealTest {

    @Test
    fun localOnline_deletesAndStreams() {
        assertEquals(
            LocalErrorAction.DELETE_AND_STREAM,
            localErrorDecision("local:netease:1895330088", true)
        )
    }

    @Test
    fun localOffline_showsMessage() {
        assertEquals(
            LocalErrorAction.ERROR_MESSAGE,
            localErrorDecision("local:netease:1895330088", false)
        )
    }

    @Test
    fun localLegacyIdWithoutPlatform_healsWhenOnline() {
        assertEquals(
            LocalErrorAction.DELETE_AND_STREAM,
            localErrorDecision("local:1895330088", true)
        )
        assertEquals(
            LocalErrorAction.ERROR_MESSAGE,
            localErrorDecision("local:1895330088", false)
        )
    }

    @Test
    fun streamItem_keepsExistingSkipPath() {
        assertEquals(LocalErrorAction.SKIP, localErrorDecision("1895330088", true))
        assertEquals(LocalErrorAction.SKIP, localErrorDecision("1895330088", false))
    }

    @Test
    fun blankId_keepsExistingSkipPath() {
        assertEquals(LocalErrorAction.SKIP, localErrorDecision("", true))
        assertEquals(LocalErrorAction.SKIP, localErrorDecision("", false))
    }

    @Test
    fun nonLocalPrefix_keepsExistingSkipPath() {
        assertEquals(LocalErrorAction.SKIP, localErrorDecision("cached:netease:1", true))
        assertEquals(LocalErrorAction.SKIP, localErrorDecision("LOCAL:netease:1", true))
    }

    /**
     * PB-1：服务端只在"在线 + local 项"时放手（自愈能真做事）；离线必须自己推进，
     * 否则一个截断的下载会让整条离线队列停住 —— 而自愈在离线什么也做不了。
     */
    @Test
    fun serviceDefersToHeal_onlyForLocalItemsWhileOnline() {
        assertEquals(true, defersLocalErrorToHeal("local:netease:1", true))
        assertEquals(false, defersLocalErrorToHeal("local:netease:1", false))
        assertEquals(false, defersLocalErrorToHeal("1895330088", true))
        assertEquals(false, defersLocalErrorToHeal("1895330088", false))
        assertEquals(false, defersLocalErrorToHeal("", true))
    }

    @Test
    fun playSource_healthyDownloadPlaysLocal() {
        assertEquals(
            PlaySource.LOCAL,
            selectPlaySource(isDownloaded = true, fileExists = true, fileSize = MIN_AUDIO_BYTES.toLong())
        )
        assertEquals(
            PlaySource.LOCAL,
            selectPlaySource(isDownloaded = true, fileExists = true, fileSize = 5_000_000L)
        )
    }

    @Test
    fun playSource_externallyDeletedFileFallsBackToStream() {
        assertEquals(
            PlaySource.STREAM,
            selectPlaySource(isDownloaded = true, fileExists = false, fileSize = 0L)
        )
    }

    @Test
    fun playSource_poisonedUndersizedFileFallsBackToStream() {
        assertEquals(
            PlaySource.STREAM,
            selectPlaySource(
                isDownloaded = true,
                fileExists = true,
                fileSize = (MIN_AUDIO_BYTES - 1).toLong()
            )
        )
    }

    @Test
    fun playSource_neverDownloadedStreams() {
        assertEquals(
            PlaySource.STREAM,
            selectPlaySource(isDownloaded = false, fileExists = false, fileSize = 0L)
        )
        assertEquals(
            PlaySource.STREAM,
            selectPlaySource(isDownloaded = false, fileExists = true, fileSize = 5_000_000L)
        )
    }
}
