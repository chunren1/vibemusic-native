package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineManageTest {

    private fun meta(id: String, at: Long, artist: String = "歌手") = OfflineMeta(
        sourceId = id,
        name = "n$id",
        artist = artist,
        album = "",
        coverUrl = "",
        durationSec = 100,
        platform = "netease",
        downloadedAt = at
    )

    // ---- 大小格式化 ----

    @Test
    fun sizeZeroOrNegativeIsEmpty() {
        assertEquals("", formatFileSize(0L))
        assertEquals("", formatFileSize(-4096L))
    }

    @Test
    fun sizeUnderOneMbUsesKb() {
        assertEquals("512 KB", formatFileSize(512L * 1024L))
    }

    @Test
    fun sizeOneMbAndUpUsesMb() {
        assertEquals("1.0 MB", formatFileSize(1024L * 1024L))
        assertEquals("12.3 MB", formatFileSize(12_897_485L))
    }

    @Test
    fun summaryUsesDownloadedBytesAndCount() {
        assertEquals("下载 12.3 MB · 共 3 首", offlineSummary(12_897_485L, 3))
        assertEquals("下载 0.0 MB · 共 0 首", offlineSummary(0L, 0))
    }

    // ---- 副标题（大小 · 日期 · 歌手） ----

    @Test
    fun subtitleJoinsSizeDateAndArtist() {
        // 用 0 的时间戳避开时区依赖：日期段为空时直接省略
        assertEquals("12.3 MB · 周杰伦", offlineRowSubtitle(12_897_485L, 0L, "周杰伦"))
    }

    @Test
    fun subtitleOmitsMissingSegments() {
        assertEquals("", offlineRowSubtitle(0L, 0L, ""))
        assertEquals("林俊杰", offlineRowSubtitle(0L, 0L, "  林俊杰 "))
    }

    @Test
    fun subtitleIncludesDateWhenPresent() {
        val text = offlineRowSubtitle(0L, 1_761_772_800_000L, "陈奕迅")
        assertTrue(text.startsWith("20"))
        assertTrue(text.endsWith(" · 陈奕迅"))
    }

    // ---- 汇总 ----

    @Test
    fun sumIgnoresMissingAndClampsNegative() {
        val sizes = mapOf("netease:a" to 100L, "netease:b" to 250L, "netease:c" to -5L)
        assertEquals(350L, sumOfflineBytes(sizes))
        assertEquals(0L, sumOfflineBytes(emptyMap()))
    }

    @Test
    fun offlineKeyMatchesPlayKeyContract() {
        assertEquals(playKey("netease", "42"), offlineKey(meta("42", 0L)))
        assertEquals(playKey("bilibili", "BV1"), offlineKey(meta("BV1", 0L).copy(platform = "bilibili")))
    }

    // ---- 排序 + 原始下标 ----

    @Test
    fun recentSortKeepsOriginalOrderAndIndex() {
        val items = listOf(meta("c", 300L), meta("b", 200L), meta("a", 100L))
        val rows = buildOfflineRows(items, emptyMap(), OfflineSort.RECENT)
        assertEquals(listOf("c", "b", "a"), rows.map { it.meta.sourceId })
        assertEquals(listOf(0, 1, 2), rows.map { it.index })
    }

    @Test
    fun sizeSortIsDescendingAndCarriesOriginalIndex() {
        val items = listOf(meta("a", 300L), meta("b", 200L), meta("c", 100L))
        val sizes = mapOf(
            "netease:c" to 9_000_000L,
            "netease:a" to 2_000_000L,
            "netease:b" to 5_000_000L
        )
        val rows = buildOfflineRows(items, sizes, OfflineSort.SIZE)
        assertEquals(listOf("c", "b", "a"), rows.map { it.meta.sourceId })
        // 下标必须是原列表位置，不是展示位置 —— 否则点第一行会播错歌
        assertEquals(listOf(2, 1, 0), rows.map { it.index })
        assertEquals(listOf(9_000_000L, 5_000_000L, 2_000_000L), rows.map { it.sizeBytes })
    }

    @Test
    fun sizeSortPutsUnknownSizesLast() {
        val items = listOf(meta("a", 300L), meta("b", 200L))
        val rows = buildOfflineRows(items, mapOf("netease:a" to 10L), OfflineSort.SIZE)
        assertEquals(listOf("a", "b"), rows.map { it.meta.sourceId })
        assertEquals(0L, rows.last().sizeBytes)
    }

    @Test
    fun sizeSortIsStableOnTies() {
        val items = listOf(meta("new", 300L), meta("old", 100L))
        val sizes = mapOf("netease:new" to 500L, "netease:old" to 500L)
        val rows = buildOfflineRows(items, sizes, OfflineSort.SIZE)
        assertEquals(listOf("new", "old"), rows.map { it.meta.sourceId })
    }

    @Test
    fun emptyListProducesEmptyRows() {
        assertTrue(buildOfflineRows(emptyList(), emptyMap(), OfflineSort.SIZE).isEmpty())
    }

    // ---- 多选 ----

    @Test
    fun toggleAddsThenRemoves() {
        var selected: Set<String> = emptySet()
        selected = toggleSelected(selected, "netease:a")
        assertEquals(setOf("netease:a"), selected)
        selected = toggleSelected(selected, "netease:a")
        assertTrue(selected.isEmpty())
    }

    @Test
    fun allKeysUseStableKeys() {
        val items = listOf(meta("a", 1L), meta("b", 2L))
        assertEquals(setOf("netease:a", "netease:b"), offlineAllKeys(items))
    }

    @Test
    fun allSelectedRequiresNonEmptyAndFullCoverage() {
        val items = listOf(meta("a", 1L), meta("b", 2L))
        assertFalse(isAllSelected(emptySet(), items))
        assertFalse(isAllSelected(emptySet(), emptyList()))
        assertFalse(isAllSelected(setOf("netease:a"), items))
        assertTrue(isAllSelected(setOf("netease:a", "netease:b"), items))
        // 选中里多出来的 key 不影响「全选」判定
        assertTrue(isAllSelected(setOf("netease:a", "netease:b", "netease:z"), items))
    }

    @Test
    fun selectedItemsFollowsListOrder() {
        val items = listOf(meta("a", 300L), meta("b", 200L), meta("c", 100L))
        val picked = selectedItems(items, setOf("netease:c", "netease:a"))
        assertEquals(listOf("a", "c"), picked.map { it.sourceId })
        assertTrue(selectedItems(items, emptySet()).isEmpty())
    }

    @Test
    fun sortLabelsAreChinese() {
        assertEquals("最近下载", offlineSortLabel(OfflineSort.RECENT))
        assertEquals("按大小", offlineSortLabel(OfflineSort.SIZE))
    }
}
