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
