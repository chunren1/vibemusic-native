package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我的歌单」多选态的纯逻辑真值表：全选判定、全选/全不选切换、列表变空自动退出。
 * 实现住在 SelectionUi.kt（三个页面共用），这里按「歌单 id 列表」的真实语义验证。
 * 纯 JUnit4，不依赖 Robolectric / Compose 运行时。
 */
class PlaylistsSelectionTest {

    @Test
    fun allCheckedWhenEveryIdSelected() {
        assertTrue(allSelected(listOf("a", "b"), setOf("a", "b")))
    }

    @Test
    fun emptyListIsNeverAllChecked() {
        // 空列表若判成「全选」，工具栏会显示「全不选」——本来就没什么可选
        assertFalse(allSelected(emptyList(), emptySet()))
    }

    @Test
    fun partialSelectionIsNotAllChecked() {
        assertFalse(allSelected(listOf("a", "b"), setOf("a")))
    }

    @Test
    fun staleIdsDoNotFakeAllChecked() {
        // 单选删除会从 checkedIds 里减 id，但顺序颠倒时可能残留已不存在的 id；
        // 只比 size 会被残留值骗过去，必须逐个比对列表本身
        assertFalse(allSelected(listOf("a", "b"), setOf("b", "gone")))
    }

    @Test
    fun toggleAllSelectsEverythingFromEmpty() {
        assertEquals(
            setOf("a", "b", "c"),
            toggleAllSelected(listOf("a", "b", "c"), emptySet())
        )
    }

    @Test
    fun toggleAllClearsWhenEverythingChecked() {
        assertEquals(
            emptySet<String>(),
            toggleAllSelected(listOf("a", "b"), setOf("a", "b"))
        )
    }

    @Test
    fun toggleAllFillsPartialSelectionThenClears() {
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
    fun exitsSelectionOnlyWhenSelectingAndListEmpty() {
        assertTrue(shouldExitSelection(selecting = true, rowCount = 0))
        assertFalse(shouldExitSelection(selecting = true, rowCount = 3))
        assertFalse(shouldExitSelection(selecting = false, rowCount = 0))
    }
}
