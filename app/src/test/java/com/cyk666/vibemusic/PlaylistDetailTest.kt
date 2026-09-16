package com.cyk666.vibemusic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistDetailTest {

    private fun song(name: String, vip: Boolean = false) = Song(
        sourceId = "id-$name",
        name = name,
        artist = "artist",
        album = "",
        coverUrl = "",
        durationSec = 200,
        platform = "netease",
        vip = vip
    )

    @Test
    fun countVipSongs_countsOnlyFlagged() {
        val songs = listOf(song("a"), song("b", vip = true), song("c", vip = true))
        assertEquals(2, countVipSongs(songs))
    }

    @Test
    fun countVipSongs_emptyIsZero() {
        assertEquals(0, countVipSongs(emptyList()))
    }

    @Test
    fun buildPlayAllLabel_noVipShowsCountOnly() {
        assertEquals("10首", buildPlayAllLabel(10, 0))
        assertEquals("0首", buildPlayAllLabel(0, 0))
    }

    @Test
    fun buildPlayAllLabel_withVipAppendsCount() {
        assertEquals("10首 · 含3首VIP", buildPlayAllLabel(10, 3))
    }

    @Test
    fun resolveDetailSongCount_loadedListWins() {
        val songs = listOf(song("a"), song("b"))
        assertEquals(2, resolveDetailSongCount(songs, songsLoading = false, storedCount = 9))
        assertEquals(0, resolveDetailSongCount(emptyList(), songsLoading = false, storedCount = 9))
    }

    @Test
    fun resolveDetailSongCount_loadingFallsBackToStored() {
        assertEquals(9, resolveDetailSongCount(emptyList(), songsLoading = true, storedCount = 9))
        assertEquals(0, resolveDetailSongCount(emptyList(), songsLoading = true, storedCount = -1))
    }

    @Test
    fun buildShareText_listsFirstSongs() {
        val text = buildShareText(
            "深夜",
            listOf(song("a"), Song("id-x", "", "artist", "", "", 0, "netease"), song("b"))
        )
        assertTrue(text.contains("深夜"))
        assertTrue(text.contains("a - artist"))
        assertTrue(text.contains("b - artist"))
    }

    @Test
    fun buildShareText_emptyListSharesTitleOnly() {
        assertEquals("分享歌单「深夜」", buildShareText("深夜", emptyList()))
        assertEquals("分享歌单「(untitled)」", buildShareText("", emptyList()))
    }

    @Test
    fun buildShareText_capsPreviewAtFive() {
        val songs = (1..8).map { song("s$it") }
        val text = buildShareText("长歌单", songs)
        assertTrue(text.contains("s5 - artist"))
        assertFalse(text.contains("s6 - artist"))
    }

    @Test
    fun filterInPlaylistSongs_blankRestoresFullList() {
        val songs = listOf(song("夜曲"), song("晴天"))
        assertEquals(songs, filterInPlaylistSongs(songs, ""))
        assertEquals(songs, filterInPlaylistSongs(songs, "   "))
    }

    @Test
    fun filterInPlaylistSongs_matchesNameCaseInsensitive() {
        val songs = listOf(
            Song("1", "Hello World", "a", "", "", 0, "netease"),
            Song("2", "夜曲", "周杰伦", "", "", 0, "netease")
        )
        assertEquals(listOf(songs[0]), filterInPlaylistSongs(songs, "hello"))
        assertEquals(listOf(songs[1]), filterInPlaylistSongs(songs, "夜"))
    }

    @Test
    fun filterInPlaylistSongs_matchesArtist() {
        val songs = listOf(
            Song("1", "夜曲", "周杰伦", "", "", 0, "netease"),
            Song("2", "晴天", "周杰伦", "", "", 0, "netease"),
            Song("3", "孤勇者", "陈奕迅", "", "", 0, "netease")
        )
        assertEquals(listOf(songs[0], songs[1]), filterInPlaylistSongs(songs, "周杰伦"))
        assertEquals(listOf(songs[2]), filterInPlaylistSongs(songs, " 陈奕迅 "))
    }

    @Test
    fun filterInPlaylistSongs_noMatchIsEmpty() {
        val songs = listOf(song("夜曲"), song("晴天"))
        assertEquals(emptyList<Song>(), filterInPlaylistSongs(songs, "不存在的歌"))
    }

    @Test
    fun parseVipFlag_absentDefaultsFalse() {
        val o = JSONObject().put("sourceId", "1").put("name", "n")
        assertFalse(VibeApi.parseVipFlag(o))
        val s = VibeApi.parsePlaylistSong(o)
        assertFalse(s.vip)
    }

    @Test
    fun parseVipFlag_boolAndFeeShapes() {
        assertTrue(VibeApi.parseVipFlag(JSONObject().put("vip", true)))
        assertTrue(VibeApi.parseVipFlag(JSONObject().put("paywall", true)))
        assertTrue(VibeApi.parseVipFlag(JSONObject().put("fee", 1)))
        assertTrue(VibeApi.parseVipFlag(JSONObject().put("fee", "1")))
        assertFalse(VibeApi.parseVipFlag(JSONObject().put("fee", 0)))
        assertFalse(VibeApi.parseVipFlag(JSONObject().put("fee", 8)))
    }

    @Test
    fun parsePlaylistSong_carriesVipFlag() {
        val o = JSONObject()
            .put("sourceId", "9")
            .put("name", "予以")
            .put("platform", "netease")
            .put("vip", true)
        assertTrue(VibeApi.parsePlaylistSong(o).vip)
    }

    @Test
    fun `推荐详情路径_拼接source与id`() {
        assertEquals(
            "api/playlists/detail?source=netease&id=12345",
            VibeApi.buildRecommendDetailPath("netease", "12345")
        )
    }

    private fun recommendDetailEnvelope(): String = """
        {
          "code": 200,
          "message": "ok",
          "data": {
            "id": "12345",
            "name": "宝藏",
            "coverUrl": "https://cover.example/x.jpg",
            "songCount": 2,
            "source": "netease",
            "songs": [
              {
                "id": "1895330088",
                "name": "予以",
                "artist": "队长",
                "album": "予以",
                "coverUrl": "https://cover.example/x.jpg",
                "duration": 231
              },
              {
                "id": "999",
                "name": "bare",
                "artist": "anon"
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `推荐详情解析_songs映射为Song`() {
        val songs = VibeApi.parseRecommendDetailSongs(recommendDetailEnvelope())
        assertEquals(2, songs.size)
        val full = songs[0]
        assertEquals("1895330088", full.sourceId)
        assertEquals("予以", full.name)
        assertEquals("队长", full.artist)
        assertEquals("予以", full.album)
        assertEquals("https://cover.example/x.jpg", full.coverUrl)
        assertEquals(231, full.durationSec)
        assertEquals("netease", full.platform)
        val bare = songs[1]
        assertEquals("999", bare.sourceId)
        assertEquals("bare", bare.name)
        assertEquals("netease", bare.platform)
    }

    @Test
    fun `推荐详情解析_缺songs为空列表`() {
        val json = """{"code":200,"message":"ok","data":{"id":"1","name":"空"}}"""
        assertEquals(emptyList<Song>(), VibeApi.parseRecommendDetailSongs(json))
    }

    @Test
    fun `推荐详情解析_非200抛错不吞成空列表`() {
        val json = """{"code":404,"message":"歌单不存在","data":null}"""
        try {
            VibeApi.parseRecommendDetailSongs(json)
            org.junit.Assert.fail("404 必须抛错")
        } catch (e: RuntimeException) {
            assertTrue(e.message.orEmpty().contains("404"))
        }
    }
}
