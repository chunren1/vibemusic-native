package com.cyk666.vibemusic

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineManageTest {

    private fun meta(id: String, at: Long, artist: String = "歌手", platform: String = "netease") =
        OfflineMeta(
            sourceId = id,
            name = "n$id",
            artist = artist,
            album = "",
            coverUrl = "",
            durationSec = 100,
            platform = platform,
            downloadedAt = at
        )

    /** 磁盘记录：meta 内容 + 它自己的 `.meta` 文件名（内容与文件名可以不一致）。 */
    private fun entry(
        id: String,
        at: Long,
        platform: String = "netease",
        fileName: String = "${platform}_$id.meta"
    ) = OfflineDownloadEntry(meta(id, at, platform = platform), fileName)

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

    // ---- 副标题（平台 · 大小 · 日期 · 歌手） ----

    @Test
    fun subtitleJoinsPlatformSizeDateAndArtist() {
        // 用 0 的时间戳避开时区依赖：日期段为空时直接省略
        assertEquals(
            "网易云 · 12.3 MB · 周杰伦",
            offlineRowSubtitle("netease", 12_897_485L, 0L, "周杰伦")
        )
    }

    @Test
    fun subtitleOmitsMissingSegments() {
        // 平台段永远在（空值按网易云），其余缺失段省略
        assertEquals("网易云", offlineRowSubtitle("netease", 0L, 0L, ""))
        assertEquals("QQ · 林俊杰", offlineRowSubtitle("qq", 0L, 0L, "  林俊杰 "))
    }

    @Test
    fun subtitleIncludesDateWhenPresent() {
        val text = offlineRowSubtitle("netease", 0L, 1_761_772_800_000L, "陈奕迅")
        assertTrue(text.startsWith("网易云 · 20"))
        assertTrue(text.endsWith(" · 陈奕迅"))
    }

    @Test
    fun platformLabelCoversAllFiveSources() {
        assertEquals("网易云", offlinePlatformLabel("netease"))
        assertEquals("QQ", offlinePlatformLabel("qq"))
        assertEquals("咪咕", offlinePlatformLabel("migu"))
        assertEquals("酷狗", offlinePlatformLabel("kugou"))
        assertEquals("B站", offlinePlatformLabel("bilibili"))
    }

    @Test
    fun platformLabelKeepsUnknownAndDefaultsBlank() {
        // 未知平台原样显示（宁可露出 "joox" 也不吞掉来源）
        assertEquals("joox", offlinePlatformLabel("joox"))
        assertEquals("网易云", offlinePlatformLabel(""))
        assertEquals("网易云", offlinePlatformLabel("   "))
    }

    // ---- 磁盘去重（同 key 只留最新；同名不同平台都留） ----

    @Test
    fun dedupeKeepsNewestMetaPerKey() {
        val old = entry("42", 100L, fileName = "old_rule_42.meta")
        val new = entry("42", 300L, fileName = "netease_42.meta")
        val picked = pickNewestDownloads(listOf(old, new))
        assertEquals(listOf("netease_42.meta"), picked.map { it.metaFileName })
        // 被淘汰的那条 meta 要删掉，否则下次扫盘又「复活」成重复记录
        assertEquals(listOf("old_rule_42.meta"), duplicateMetaFileNames(listOf(old, new)))
    }

    @Test
    fun dedupeKeepsSameSongFromDifferentPlatforms() {
        val netease = entry("42", 100L, platform = "netease")
        val qq = entry("42", 300L, platform = "qq")
        val entries = listOf(netease, qq)
        // playKey 不同 → 两条都留（这正是「同名但来源不同」，靠副标题的标签区分）
        assertEquals(2, pickNewestDownloads(entries).size)
        assertTrue(duplicateMetaFileNames(entries).isEmpty())
    }

    @Test
    fun dedupeTieIsDeterministic() {
        // downloadedAt 并列（legacy meta 常为 0）时用文件名做次级键：与传进来的顺序无关，
        // 否则「留哪条」取决于 listFiles 的目录哈希序，展示字段可能留到旧的那条。
        val a = entry("7", 500L, fileName = "a_7.meta")
        val b = entry("7", 500L, fileName = "b_7.meta")
        assertEquals(listOf("a_7.meta"), pickNewestDownloads(listOf(a, b)).map { it.metaFileName })
        assertEquals(listOf("a_7.meta"), pickNewestDownloads(listOf(b, a)).map { it.metaFileName })
    }

    // ---- 孤儿对账（无 meta 的 mp3 / 被杀进程留下的 tmp） ----

    @Test
    fun orphanScanSparesFreshFiles() {
        // 音频已 rename、meta 还没写的那一瞬间：文件很新，不能被当垃圾删掉
        val dir = Files.createTempDirectory("offline-fresh").toFile()
        try {
            val fresh = File(dir, "netease_9.mp3").apply { writeBytes(ByteArray(8)) }
            assertTrue(fresh.setLastModified(9_999_999L))
            assertTrue(orphanDownloads(dir, 10_000_000L).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun atomicWriteLeavesNoTmpBehind() {
        val dir = Files.createTempDirectory("offline-meta-write").toFile()
        try {
            val target = File(dir, "netease_1.meta")
            writeTextAtomically(target, "{\"sourceId\":\"1\"}")
            assertEquals("{\"sourceId\":\"1\"}", target.readText())
            assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun dedupeDegenerateInputs() {
        assertTrue(pickNewestDownloads(emptyList()).isEmpty())
        assertTrue(duplicateMetaFileNames(emptyList()).isEmpty())
        val single = listOf(entry("1", 1L))
        assertEquals(1, pickNewestDownloads(single).size)
        assertTrue(duplicateMetaFileNames(single).isEmpty())
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
    fun zeroSelectionKeepsManageModeUntilListIsEmpty() {
        // 回归（用户反馈「最少都要选择一首，不然就退出多选」）：管理态改成显式状态后，
        // 选中集合清空**不**再触发退出；只有列表被删空（rowCount == 0）才退出。
        assertFalse(shouldExitSelection(selecting = true, rowCount = 3))
        assertTrue(shouldExitSelection(selecting = true, rowCount = 0))
        assertFalse(shouldExitSelection(selecting = false, rowCount = 0))
    }

    @Test
    fun sortLabelsAreChinese() {
        assertEquals("最近下载", offlineSortLabel(OfflineSort.RECENT))
        assertEquals("按大小", offlineSortLabel(OfflineSort.SIZE))
    }
}
