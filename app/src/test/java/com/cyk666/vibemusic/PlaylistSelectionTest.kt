package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 歌单详情多选的真值表：共享的选中集合运算、批量移除前的 sourceId 去重、
 * 选中集合与当前歌单求交、清空范围是全集而非筛选可见集。
 * 纯 JUnit4，不依赖 Robolectric / Compose 运行时。
 */
class PlaylistSelectionTest {

    private fun song(sourceId: String, name: String = "n$sourceId", platform: String = "netease") =
        Song(sourceId, name, "歌手", "", "", 200, platform)

    // ---- 共享的选中集合运算（SelectionUi.kt） ----

    @Test
    fun toggleAddsThenRemoves() {
        var checked: Set<String> = emptySet()
        checked = toggleSelected(checked, "a")
        assertEquals(setOf("a"), checked)
        checked = toggleSelected(checked, "a")
        assertTrue(checked.isEmpty())
    }

    @Test
    fun toggleOnlyTouchesOneKey() {
        val checked = toggleSelected(setOf("a", "b"), "c")
        assertEquals(setOf("a", "b", "c"), checked)
        assertEquals(setOf("b", "c"), toggleSelected(checked, "a"))
    }

    @Test
    fun allSelectedRequiresEveryRow() {
        assertTrue(allSelected(listOf("a", "b"), setOf("a", "b")))
        assertFalse(allSelected(listOf("a", "b"), setOf("a")))
    }

    @Test
    fun emptyRowListIsNeverAllSelected() {
        assertFalse(allSelected(emptyList(), emptySet()))
    }

    @Test
    fun staleKeysDoNotFakeAllSelected() {
        // 只比 size 会被残留 key 骗过去，必须逐项比对列表本身
        assertFalse(allSelected(listOf("a", "b"), setOf("b", "gone")))
    }

    @Test
    fun toggleAllFillsThenClears() {
        val ids = listOf("a", "b", "c")
        val filled = toggleAllSelected(ids, setOf("b"))
        assertEquals(setOf("a", "b", "c"), filled)
        assertEquals(emptySet<String>(), toggleAllSelected(ids, filled))
    }

    @Test
    fun toggleAllOnEmptyListStaysEmpty() {
        assertEquals(emptySet<String>(), toggleAllSelected(emptyList(), emptySet()))
    }

    @Test
    fun exitSelectionOnlyWhenSelectingAndNoRows() {
        assertTrue(shouldExitSelection(selecting = true, rowCount = 0))
        assertFalse(shouldExitSelection(selecting = true, rowCount = 3))
        assertFalse(shouldExitSelection(selecting = false, rowCount = 0))
    }

    // ---- 歌单详情的纯逻辑 ----

    @Test
    fun dedupeKeepsFirstOccurrenceAndOrder() {
        val songs = listOf(song("c"), song("a"), song("c"), song("b"))
        val out = dedupeBySourceId(songs)
        assertEquals(listOf("c", "a", "b"), out.map { it.sourceId })
        assertEquals("nc", out.first().name)
    }

    @Test
    fun dedupeOnEmptyListIsEmpty() {
        assertTrue(dedupeBySourceId(emptyList()).isEmpty())
    }

    @Test
    fun selectedSongsFollowsPlaylistOrder() {
        val songs = listOf(song("a"), song("b"), song("c"))
        assertEquals(
            listOf("a", "c"),
            selectedSongs(songs, setOf("c", "a")).map { it.sourceId }
        )
    }

    @Test
    fun selectedSongsDropsStaleKeys() {
        val songs = listOf(song("a"))
        assertTrue(selectedSongs(songs, setOf("gone")).isEmpty())
        // 残留 key 不影响计数：交集只有 1 首
        assertEquals(1, selectedSongs(songs, setOf("a", "gone")).size)
    }

    @Test
    fun clearAllTargetsWholePlaylistNotFilteredView() {
        val all = listOf(song("a"), song("b"), song("c"))
        val filtered = listOf(song("b"))
        val targets = songsForClearAll(all, filtered)
        assertEquals(listOf("a", "b", "c"), targets.map { it.sourceId })
    }

    @Test
    fun clearAllAlsoDedupes() {
        val all = listOf(song("a"), song("a"), song("b"))
        assertEquals(listOf("a", "b"), songsForClearAll(all, emptyList()).map { it.sourceId })
    }
}
