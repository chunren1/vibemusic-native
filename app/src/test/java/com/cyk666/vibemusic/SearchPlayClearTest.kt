package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchPlayClearTest {

    private fun song(id: String) = Song(id, "n$id", "a", "al", "", 180, "netease")

    private fun dirtyState() = SearchViewState(
        query = "周杰伦",
        results = listOf(song("1"), song("2")),
        total = 2,
        searched = true,
        liveQuery = "周杰伦",
        artistFilter = "周杰伦",
        suggestVisible = true,
        error = "网络连接断开"
    )

    @Test
    fun `播放搜索结果_清空查询串`() {
        assertEquals("", clearedSearchAfterPlay(dirtyState()).query)
    }

    @Test
    fun `播放搜索结果_清空结果与总数`() {
        val cleared = clearedSearchAfterPlay(dirtyState())
        assertTrue(cleared.results.isEmpty())
        assertEquals(0, cleared.total)
        assertFalse(cleared.searched)
    }

    @Test
    fun `播放搜索结果_隐藏联想并清除筛选`() {
        val cleared = clearedSearchAfterPlay(dirtyState())
        assertEquals("", cleared.liveQuery)
        assertNull(cleared.artistFilter)
        assertFalse(cleared.suggestVisible)
    }

    // ---- R4-A7: 清空快照必须连错误态一起清，否则"干净搜索页"会残留旧报错 ----

    @Test
    fun `播放搜索结果_清空错误态（不留旧报错）`() {
        assertNull(clearedSearchAfterPlay(dirtyState()).error)
    }

    @Test
    fun `播放在线可播_应用后错误态一并清空`() {
        val target = song("1")
        val gatePlayable = OfflineAvailability().isGatePlayable(target, true)
        val after = searchStateAfterPlayTap(dirtyState(), gatePlayable, isStale = false)
        assertNull(after.error)
    }

    @Test
    fun `离线不可播_错误态随快照保留`() {
        val target = song("1")
        val gatePlayable = OfflineAvailability().isGatePlayable(target, false)
        assertFalse(gatePlayable)
        val after = searchStateAfterPlayTap(dirtyState(), gatePlayable, isStale = false)
        assertEquals("网络连接断开", after.error)
    }

    @Test
    fun `播放搜索结果_干净状态保持干净`() {
        val cleared = clearedSearchAfterPlay(SearchViewState())
        assertEquals(SearchViewState(suggestVisible = false), cleared)
    }

    // ---- R4-A1 gate-before-clear: ordering, not just copy semantics ----

    @Test
    fun `离线点播不可播_搜索快照原样保留`() {
        val before = dirtyState()
        val target = song("1")
        // 按 handler 顺序先过 gate:空快照 + 离线 → 不可播
        val gatePlayable = OfflineAvailability().isGatePlayable(target, false)
        assertFalse(gatePlayable)
        val after = searchStateAfterPlayTap(before, gatePlayable, isStale = false)
        assertEquals(before, after)
        assertEquals("周杰伦", after.query)
        assertEquals(2, after.results.size)
        assertEquals(2, after.total)
        assertTrue(after.searched)
        assertEquals("周杰伦", after.liveQuery)
        assertEquals("周杰伦", after.artistFilter)
        assertTrue(after.suggestVisible)
    }

    @Test
    fun `在线点播可播且新鲜_才清空快照`() {
        val before = dirtyState()
        val target = song("1")
        val gatePlayable = OfflineAvailability().isGatePlayable(target, true)
        assertTrue(gatePlayable)
        val after = searchStateAfterPlayTap(before, gatePlayable, isStale = false)
        assertEquals(clearedSearchAfterPlay(before), after)
        assertTrue(after.results.isEmpty())
        assertFalse(after.searched)
    }

    @Test
    fun `离线点播已缓存可播且新鲜_清空快照`() {
        val target = song("1")
        val avail = OfflineAvailability(cachedKeys = setOf(MediaCache.cacheKey(target)))
        assertTrue(avail.isGatePlayable(target, false))
        val after = searchStateAfterPlayTap(dirtyState(), true, isStale = false)
        assertTrue(after.results.isEmpty())
        assertEquals("", after.query)
    }

    @Test
    fun `点播已过期_即使可播也不清空`() {
        val before = dirtyState()
        assertTrue(isStalePlayGen(completedGen = 1, latestGen = 2))
        val after = searchStateAfterPlayTap(before, gatePlayable = true, isStale = true)
        assertEquals(before, after)
    }

    @Test
    fun `点播过期且不可播_快照保留`() {
        val before = dirtyState()
        val after = searchStateAfterPlayTap(before, gatePlayable = false, isStale = true)
        assertEquals(before, after)
    }
}
