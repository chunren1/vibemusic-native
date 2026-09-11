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
}
