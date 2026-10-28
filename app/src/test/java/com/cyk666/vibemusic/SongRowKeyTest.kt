package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 钉死 LazyColumn 的稳定 key 契约（CODE-REVIEW-2026-10-28 UI-2）。
 *
 * 背景：`randomSongs` / 多源聚合搜索不保证 `sourceId + platform` 在同一列表内唯一，
 * 而重复 key 会让 `LazyColumn` 抛 `IllegalStateException: Key was already used`。
 * 修复前 4 个调用点用的是无 index 的裸拼接，只有 1 处带 index —— 所以把规则收成
 * 纯函数并在这里钉住：**index 必须参与 key**。
 */
class SongRowKeyTest {

    private fun song(
        sourceId: String = "s1",
        platform: String = "netease",
        name: String = "歌名"
    ) = Song(
        sourceId = sourceId,
        name = name,
        artist = "歌手",
        album = "",
        coverUrl = "",
        durationSec = 0,
        platform = platform
    )

    @Test
    fun sameSongAtDifferentIndexGetsDifferentKey() {
        val s = song()
        // 同一首歌在一屏里出现两次（多源命中 / 后端重复返回）：index 不同 → key 不同。
        assertNotEquals(songRowKey(0, s), songRowKey(1, s))
        assertEquals("song-0-s1-netease", songRowKey(0, s))
        assertEquals("song-1-s1-netease", songRowKey(1, s))
    }

    @Test
    fun differentSongsAtSameIndexGetDifferentKey() {
        assertNotEquals(
            songRowKey(3, song(sourceId = "a")),
            songRowKey(3, song(sourceId = "b"))
        )
        assertNotEquals(
            songRowKey(3, song(platform = "netease")),
            songRowKey(3, song(platform = "qq"))
        )
    }

    @Test
    fun prefixSeparatesListsInOneScreen() {
        val s = song()
        // 同一屏可能有「猜你喜欢」和「每日推荐」两份列表，前缀必须隔离。
        assertNotEquals(
            songRowKey(0, s, "guess"),
            songRowKey(0, s, "daily")
        )
        assertEquals("guess-0-s1-netease", songRowKey(0, s, "guess"))
        assertEquals("encounter-2-s1-netease", songRowKey(2, s, "encounter"))
    }

    @Test
    fun defaultPrefixIsSong() {
        assertEquals("song-7-s1-netease", songRowKey(7, song()))
    }
}
