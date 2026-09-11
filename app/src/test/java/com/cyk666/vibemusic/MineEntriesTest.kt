package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MineEntriesTest {

    @Test
    fun loggedIn_exactlyFourEntriesWithCounts() {
        val entries = buildMineEntries(
            favCount = 7,
            playlistCount = 3,
            historyCount = 12,
            offlineCount = 5,
            loggedIn = true
        )
        assertEquals(4, entries.size)
        assertEquals("我的收藏", entries[0].title)
        assertEquals("7 首", entries[0].subtitle)
        assertEquals("我的歌单", entries[1].title)
        assertEquals("3 个", entries[1].subtitle)
        assertEquals("最近播放", entries[2].title)
        assertEquals("12 首", entries[2].subtitle)
        assertEquals("本地下载", entries[3].title)
        assertEquals("5 首", entries[3].subtitle)
    }

    @Test
    fun guest_accountEntriesPromptLogin_offlineKeepsCount() {
        val entries = buildMineEntries(
            favCount = 0,
            playlistCount = 0,
            historyCount = 0,
            offlineCount = 5,
            loggedIn = false
        )
        assertEquals(4, entries.size)
        assertEquals("登录后查看", entries[0].subtitle)
        assertEquals("登录后查看", entries[1].subtitle)
        assertEquals("登录后查看", entries[2].subtitle)
        assertEquals("5 首", entries[3].subtitle)
    }

    @Test
    fun entryIds_stableForDispatch() {
        val entries = buildMineEntries(1, 2, 3, 4, loggedIn = true)
        assertEquals(
            listOf("favorites", "playlists", "history", "offline"),
            entries.map { it.id }
        )
    }
}
