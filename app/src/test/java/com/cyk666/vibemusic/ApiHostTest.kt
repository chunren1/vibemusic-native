package com.cyk666.vibemusic

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CODE-6 / CODE-4 回归（CODE-REVIEW-2026-10-28）。
 *
 * 两条都是"同一规则写了两份、改一处漏一处"：域名（BASE_URL 与 streamUrl 各写一份）
 * 与播放记账判据（上报 / 计数各写一份）。
 */
@RunWith(RobolectricTestRunner::class)
class ApiHostTest {

    private fun song(
        sourceId: String = "1895330088",
        platform: String = "netease"
    ) = Song(
        sourceId = sourceId,
        name = "予以",
        artist = "队长",
        album = "",
        coverUrl = "",
        durationSec = 200,
        platform = platform
    )

    // ---- CODE-6：域名唯一来源 ----

    @Test
    fun streamUrlHostMatchesBaseUrlHost() {
        // 漏改任意一处就是"登录/列表正常，所有播放流 404"：流地址不走 Retrofit
        // 的 baseUrl，是自己用 Uri.Builder 拼的。
        val streamHost = Uri.parse(song().streamUrl()).host
        val apiHost = Uri.parse(VibeApi.BASE_URL).host
        assertEquals(API_HOST, apiHost)
        assertEquals(API_HOST, streamHost)
    }

    @Test
    fun baseUrlKeepsTrailingSlash() {
        // Retrofit 要求 baseUrl 以 / 结尾，否则相对路径会把最后一段吃掉。
        assertTrue(VibeApi.BASE_URL.endsWith("/"))
        assertTrue(VibeApi.BASE_URL.startsWith("https://"))
    }

    // ---- CODE-4：曲库身份判据 ----

    @Test
    fun librarySong_acceptsNormalSongs() {
        val s = song()
        assertSame(s, librarySongOrNull(s))
    }

    @Test
    fun librarySong_rejectsNullAndBlankSourceId() {
        assertNull(librarySongOrNull(null))
        assertNull(librarySongOrNull(song(sourceId = "")))
        assertNull(librarySongOrNull(song(sourceId = "   ")))
    }

    @Test
    fun librarySong_rejectsExternalImports() {
        // 外部导入（微信「用其他应用打开」）没有曲库身份：上报会把 content://
        // 写进「最近播放」，计数也没有意义。
        assertNull(librarySongOrNull(song(sourceId = "content://media/1", platform = "file")))
        assertNull(librarySongOrNull(song(sourceId = "file:///sdcard/a.mp3", platform = "file")))
    }

    @Test
    fun librarySong_keepsPlatformFileWithLibraryId() {
        // platform=file 但 sourceId 不是 content:/file:（手工导入的曲库条目）仍算曲库歌曲。
        val s = song(sourceId = "9001", platform = "file")
        assertSame(s, librarySongOrNull(s))
    }

    // ---- CODE-4："同一首只记一次" ----

    @Test
    fun recordOnce_firstTimeOnly() {
        assertTrue(VibeApi.shouldRecordOnce("netease:1", null))
        assertFalse(VibeApi.shouldRecordOnce("netease:1", "netease:1"))
        assertTrue(VibeApi.shouldRecordOnce("netease:2", "netease:1"))
    }

    @Test
    fun recordOnce_blankKeyNeverRecords() {
        assertFalse(VibeApi.shouldRecordOnce("", null))
        assertFalse(VibeApi.shouldRecordOnce("   ", "netease:1"))
    }

    @Test
    fun reportPlay_addsLoggedInGateOnTopOfRecordOnce() {
        assertTrue(VibeApi.shouldReportPlay(true, "netease:1", null))
        assertFalse(VibeApi.shouldReportPlay(false, "netease:1", null))
        assertFalse(VibeApi.shouldReportPlay(true, "netease:1", "netease:1"))
        assertFalse(VibeApi.shouldReportPlay(true, "", null))
    }
}
