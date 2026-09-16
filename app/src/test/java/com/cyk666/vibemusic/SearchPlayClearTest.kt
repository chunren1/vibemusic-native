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
        suggestVisible = true
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

    @Test
    fun `播放搜索结果_干净状态保持干净`() {
        val cleared = clearedSearchAfterPlay(SearchViewState())
        assertEquals(SearchViewState(suggestVisible = false), cleared)
    }
}
