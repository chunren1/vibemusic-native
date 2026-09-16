package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeCoverGateTest {

    private fun song(id: String, cover: String) =
        Song(id, "n$id", "a", "al", cover, 180, "netease")

    private fun playlist(id: String, pic: String) =
        RecommendPlaylist(id, "p$id", pic, "", 0L)

    @Test
    fun `有封面_判定通过`() {
        assertTrue(hasCover(song("1", "https://cdn/x.jpg")))
    }

    @Test
    fun `空封面_判定拦截`() {
        assertFalse(hasCover(song("1", "")))
    }

    @Test
    fun `首页歌单_过滤无封面并保持顺序`() {
        val songs = listOf(
            song("1", "https://cdn/1.jpg"),
            song("2", ""),
            song("3", "https://cdn/3.jpg")
        )
        val visible = homeVisibleSongs(songs)
        assertEquals(listOf(songs[0], songs[2]), visible)
    }

    @Test
    fun `首页歌单_全无封面时返回空`() {
        assertTrue(homeVisibleSongs(listOf(song("1", ""))).isEmpty())
    }

    @Test
    fun `首页歌单_不修改原列表`() {
        val songs = listOf(song("1", ""), song("2", "https://cdn/2.jpg"))
        homeVisibleSongs(songs)
        assertEquals(2, songs.size)
    }

    @Test
    fun `宝藏歌单_过滤无封面并保持顺序`() {
        val pls = listOf(
            playlist("1", "https://cdn/1.jpg"),
            playlist("2", ""),
            playlist("3", "https://cdn/3.jpg")
        )
        val visible = homeVisiblePlaylists(pls)
        assertEquals(listOf(pls[0], pls[2]), visible)
    }

    @Test
    fun `宝藏歌单_空列表返回空`() {
        assertTrue(homeVisiblePlaylists(emptyList()).isEmpty())
    }
}
