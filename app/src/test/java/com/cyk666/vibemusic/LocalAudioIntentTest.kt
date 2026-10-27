package com.cyk666.vibemusic

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 外部音频 intent 解析（LocalAudioIntent）。
 *
 * 覆盖的是「哪些 URI 会被接受」这条唯一有分支的逻辑 —— 元信息读取依赖
 * MediaMetadataRetriever，只能在真机验证（测试里走的是失败回退路径）。
 */
@RunWith(RobolectricTestRunner::class)
class LocalAudioIntentTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun uri(raw: String): Uri = Uri.parse(raw)

    private val wechatFile = uri("content://com.tencent.mm.external.fileprovider/audio/晴天.mp3")
    private val secondFile = uri("content://com.tencent.mm.external.fileprovider/audio/夜曲.m4a")

    @Test
    fun mediaIdUsesLocalFilePrefix() {
        assertEquals(
            "local:file:content://com.tencent.mm.external.fileprovider/audio/晴天.mp3",
            LocalAudioIntent.mediaIdFor(wechatFile)
        )
    }

    @Test
    fun viewIntentWithContentUriIsAccepted() {
        val intent = Intent(Intent.ACTION_VIEW, wechatFile)
        assertEquals(listOf(wechatFile), LocalAudioIntent.urisFrom(intent))
    }

    @Test
    fun sendIntentReadsExtraStream() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, wechatFile)
        }
        assertEquals(listOf(wechatFile), LocalAudioIntent.urisFrom(intent))
    }

    @Test
    fun sendMultipleReadsAllAndKeepsOrder() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(wechatFile, secondFile)
            )
        }
        assertEquals(listOf(wechatFile, secondFile), LocalAudioIntent.urisFrom(intent))
    }

    @Test
    fun clipDataIsUsedWhenNoExtraStream() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            clipData = ClipData.newRawUri("audio", wechatFile)
        }
        assertEquals(listOf(wechatFile), LocalAudioIntent.urisFrom(intent))
    }

    @Test
    fun duplicatesCollapseToOne() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, wechatFile)
            clipData = ClipData.newRawUri("audio", wechatFile)
        }
        assertEquals(listOf(wechatFile), LocalAudioIntent.urisFrom(intent))
    }

    @Test
    fun httpUriIsRejected() {
        val intent = Intent(Intent.ACTION_VIEW, uri("https://example.com/a.mp3"))
        assertTrue(LocalAudioIntent.urisFrom(intent).isEmpty())
    }

    @Test
    fun nullOrActionlessIntentYieldsEmpty() {
        assertTrue(LocalAudioIntent.urisFrom(null).isEmpty())
        assertTrue(LocalAudioIntent.urisFrom(Intent()).isEmpty())
    }

    @Test
    fun isExternalOnlyMatchesOurPrefix() {
        assertTrue(LocalAudioIntent.isExternal("local:file:content://x/y.mp3"))
        assertFalse(LocalAudioIntent.isExternal("local:netease:1895330088"))
        assertFalse(LocalAudioIntent.isExternal("1895330088"))
        assertFalse(LocalAudioIntent.isExternal(null))
    }

    @Test
    fun fallbackTitleStripsExtension() {
        assertEquals(
            "晴天",
            LocalAudioIntent.fallbackTitle(context, uri("file:///sdcard/Download/晴天.mp3"))
        )
    }

    @Test
    fun fallbackTitleNeverBlank() {
        assertEquals("本地音频", LocalAudioIntent.fallbackTitle(context, uri("content://x")))
    }

    @Test
    fun buildMediaItemSurvivesUnreadableFile() {
        // 假 content URI 读不到元信息：必须回退成「文件名标题 + 正确 mediaId」而不是抛异常
        val item = LocalAudioIntent.buildMediaItem(context, wechatFile)
        assertNotNull(item)
        assertEquals(LocalAudioIntent.mediaIdFor(wechatFile), item?.mediaId)
        assertEquals(wechatFile, item?.localConfiguration?.uri)
        assertEquals("晴天", item?.mediaMetadata?.title?.toString())
    }

    @Test
    fun pendingIsOneShot() {
        LocalAudioIntent.submit(listOf(wechatFile))
        assertEquals(listOf(wechatFile), LocalAudioIntent.pending.value)
        LocalAudioIntent.consume()
        assertNull(LocalAudioIntent.pending.value)
    }

    @Test
    fun submitEmptyDoesNotOverwritePending() {
        LocalAudioIntent.submit(listOf(wechatFile))
        LocalAudioIntent.submit(emptyList())
        assertEquals(listOf(wechatFile), LocalAudioIntent.pending.value)
        LocalAudioIntent.consume()
    }
}
