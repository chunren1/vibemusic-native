package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SongTest {

    private val chinese = Song(
        sourceId = "1895330088",
        name = "予以",
        artist = "队长",
        album = "予以",
        coverUrl = "https://cover.example/x.jpg",
        durationSec = 231,
        platform = "netease"
    )

    // ---- JSON 缓存往返（CODE-9：songFromJsonObject 此前零直测） ----

    @Test
    fun songsJson_roundTripPreservesEveryFieldIncludingVip() {
        val vip = chinese.copy(vip = true)
        val list = listOf(chinese, vip)
        assertEquals(list, songsFromJson(songsToJson(list)))
    }

    @Test
    fun songFromJsonObject_blankPlatformFallsBackToNetease() {
        // 老缓存/老队列里可能没有 platform 字段：读回来必须仍是 netease，
        // 否则 streamUrl 会拼出 platform= 空值、后端 400。
        val o = org.json.JSONObject().put("sourceId", "1").put("name", "n")
        val song = songFromJsonObject(o)
        assertEquals("netease", song.platform)
        assertEquals(0, song.durationSec)
        assertEquals(false, song.vip)
    }

    @Test
    fun songsFromJson_malformedIsNull() {
        // 调用方按"缓存未命中"处理：必须是 null，不能抛、也不能静默返回空表
        // （空表会让 UI 显示"没有歌曲"而不是重拉）。
        assertEquals(null, songsFromJson("nope"))
        // 顶层是对象而不是数组 / 数组里不是对象 —— 都属于"缓存坏了"
        assertEquals(null, songsFromJson("{\"a\":1}"))
        assertEquals(null, songsFromJson("[1,2]"))
    }

    @Test
    fun streamUrl_encodesChineseAndKeepsIdsVerbatim() {
        assertEquals(
            "https://vibe.cyk666.top/api/songs/stream" +
                "?sourceId=1895330088" +
                "&name=%E4%BA%88%E4%BB%A5" +
                "&artist=%E9%98%9F%E9%95%BF" +
                "&platform=netease",
            chinese.streamUrl()
        )
    }

    @Test
    fun streamUrl_keepsSourceIdAndPlatformVerbatim() {
        val s = chinese.copy(sourceId = "abc 123?&=", platform = "qq")
        val url = s.streamUrl()
        assertEquals(
            "https://vibe.cyk666.top/api/songs/stream" +
                "?sourceId=abc%20123%3F%26%3D" +
                "&name=%E4%BA%88%E4%BB%A5" +
                "&artist=%E9%98%9F%E9%95%BF" +
                "&platform=qq",
            url
        )
    }

    @Test
    fun streamUrl_nonNeteasePlatformPassesThroughWithCorrectAuthorityAndPath() {
        val s = chinese.copy(platform = "qq")
        val url = android.net.Uri.parse(s.streamUrl())
        assertEquals("vibe.cyk666.top", url.authority)
        assertEquals("/api/songs/stream", url.path)
        assertEquals("qq", url.getQueryParameter("platform"))
        assertEquals("1895330088", url.getQueryParameter("sourceId"))
        assertEquals("予以", url.getQueryParameter("name"))
        assertEquals("队长", url.getQueryParameter("artist"))
    }

    @Test
    fun mediaItem_roundTripPreservesAllFields() {
        val back = songFromMediaItem(chinese.toMediaItem())
        assertEquals(chinese.sourceId, back.sourceId)
        assertEquals(chinese.name, back.name)
        assertEquals(chinese.artist, back.artist)
        assertEquals(chinese.album, back.album)
        assertEquals(chinese.coverUrl, back.coverUrl)
        assertEquals(chinese.platform, back.platform)
    }

    @Test
    fun mediaItem_roundTripBlankFieldsFallBackToDefaults() {
        val blank = Song(
            sourceId = "1",
            name = "",
            artist = "",
            album = "",
            coverUrl = "",
            durationSec = 0,
            platform = ""
        )
        val back = songFromMediaItem(blank.toMediaItem())
        assertEquals("1", back.sourceId)
        assertEquals("", back.name)
        assertEquals("", back.artist)
        assertEquals("", back.album)
        assertEquals("", back.coverUrl)
        assertEquals("netease", back.platform)
    }
}
