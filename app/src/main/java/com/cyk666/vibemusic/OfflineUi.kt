package com.cyk666.vibemusic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun formatDownloadDate(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMs))
    } catch (_: Exception) {
        ""
    }
}

/**
 * 本地下载页。**不碰磁盘**：每首的字节数与合计由 MainActivity 在 IO 线程算好传进来
 * （`sizes` / `totalBytes`），这里只做展示、排序、多选与删除意图的上报。
 *
 * 播放下标：展示顺序可能被排序打乱，[buildOfflineRows] 把原始下标随行带下来，
 * 点行时传的是 `row.index`，所以换排序不会播错歌。
 */
@Composable
fun OfflineScreen(
    modifier: Modifier = Modifier,
    items: List<OfflineMeta>,
    sizes: Map<String, Long> = emptyMap(),
    totalBytes: Long = 0L,
    loading: Boolean = false,
    onPlayAt: (Int) -> Unit = {},
    onDelete: (OfflineMeta) -> Unit = {},
    onDeleteMany: (List<OfflineMeta>) -> Unit = {},
    onBack: () -> Unit = {},
    onGoSearch: () -> Unit = {}
) {
    var sort by remember { mutableStateOf(OfflineSort.RECENT) }
    // 管理态是**显式**状态，不再由「选中集合非空」派生：派生写法下点掉最后一个
    // 勾选就等于退出多选（用户反馈的「只能选一首」正是这个），而且点到 key 重复
    // 的第二行会互相抵消。
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var sheetFor by remember { mutableStateOf<OfflineMeta?>(null) }
    var confirmDelete by remember { mutableStateOf<OfflineMeta?>(null) }
    var confirmBatch by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }

    // 选中集合可能残留上一轮已删除的 key：以「仍然存在的选中项」为准，
    // 否则工具栏会写「已选 3 项」却只删得掉 1 首。
    val chosen = remember(items, selected) { selectedItems(items, selected) }
    val allSelected = isAllSelected(selected, items)
    val rows = remember(items, sizes, sort) { buildOfflineRows(items, sizes, sort) }
    // 退出管理态只有三条路：工具栏「取消」、系统返回键、列表被删空。
    val exitSelection = {
        selecting = false
        selected = emptySet()
    }
    LaunchedEffect(items.size) {
        if (shouldExitSelection(selecting, items.size)) exitSelection()
    }

    // 系统返回键：管理态先退出管理；普通态**完全不拦截**（交还系统默认返回）。
    BackHandler(enabled = selecting) { exitSelection() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (selecting) {
            SelectionToolbar(
                count = chosen.size,
                allSelected = allSelected,
                actionLabel = "删除",
                onCancel = exitSelection,
                onToggleAll = { selected = if (allSelected) emptySet() else offlineAllKeys(items) },
                onAction = { confirmBatch = true }
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BackNavButton(label = "我的", onClick = onBack)
                Text(
                    text = "本地下载 (${items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.padding(top = 4.dp))
        if (items.isNotEmpty() && !selecting) {
            OfflineSummaryLine(totalBytes = totalBytes, count = items.size)
            if (items.size > 1) {
                OfflineSortRow(sort = sort, onSortChange = { sort = it })
            }
        }
        when {
            loading -> {
                SearchSkeleton()
            }
            items.isEmpty() -> {
                EmptyStateLine(
                    // 次数不许写死：阈值改了文案必须跟着改（这里直接引常量）
                    text = "还没有下载，常听${HIGH_FREQ_THRESHOLD}次自动离线",
                    actionLabel = "去搜索",
                    onAction = onGoSearch
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        rows,
                        key = { _, row -> "${offlineKey(row.meta)}#${row.index}" }
                    ) { _, row ->
                        val key = offlineKey(row.meta)
                        SelectableSongRow(
                            model = buildSongRowModel(
                                row.meta.toSong(),
                                subtitleOverride = offlineRowSubtitle(
                                    row.meta.platform,
                                    row.sizeBytes,
                                    row.meta.downloadedAt,
                                    row.meta.artist
                                )
                            ),
                            meta = if (row.meta.durationSec > 0) {
                                formatDuration(row.meta.durationSec)
                            } else {
                                null
                            },
                            checked = key in selected,
                            selecting = selecting,
                            onClick = {
                                if (selecting) {
                                    selected = toggleSelected(selected, key)
                                } else {
                                    onPlayAt(row.index)
                                }
                            },
                            onLongClick = {
                                if (selecting) {
                                    selected = toggleSelected(selected, key)
                                } else {
                                    selecting = true
                                    selected = selected + key
                                }
                            },
                            onOverflow = { sheetFor = row.meta }
                        )
                    }
                    if (!selecting) {
                        item(key = "offline-clear-all") {
                            OfflineClearAllFooter(onClearAll = { confirmClearAll = true })
                        }
                    }
                }
            }
        }
    }
    sheetFor?.let { target ->
        val idx = items.indexOfFirst {
            it.platform == target.platform && it.sourceId == target.sourceId
        }
        SongMenuSheet(
            title = target.name.ifBlank { "(untitled)" },
            actions = listOf(
                SongMenuAction("play", "播放", enabled = idx >= 0),
                SongMenuAction("delete", "删除下载", danger = true)
            ),
            onAction = { id ->
                when (id) {
                    "play" -> if (idx >= 0) onPlayAt(idx)
                    "delete" -> confirmDelete = target
                }
                sheetFor = null
            },
            onDismiss = { sheetFor = null }
        )
    }
    confirmDelete?.let { target ->
        DangerConfirmDialog(
            title = "删除下载",
            text = "确定删除本地《${target.name}》吗？文件与记录一并清除。",
            confirmText = "删除",
            onConfirm = {
                confirmDelete = null
                onDelete(target)
            },
            onDismiss = { confirmDelete = null }
        )
    }
    if (confirmBatch && chosen.isNotEmpty()) {
        val targets = chosen
        DangerConfirmDialog(
            title = "删除下载",
            text = "将删除选中的 ${targets.size} 首下载，不可恢复。",
            confirmText = "删除",
            onConfirm = {
                confirmBatch = false
                exitSelection()
                onDeleteMany(targets)
            },
            onDismiss = { confirmBatch = false }
        )
    }
    if (confirmClearAll) {
        DangerConfirmDialog(
            title = "清除全部下载",
            text = "将删除全部 ${items.size} 首下载，不可恢复。",
            confirmText = "全部删除",
            onConfirm = {
                confirmClearAll = false
                exitSelection()
                onDeleteMany(items)
            },
            onDismiss = { confirmClearAll = false }
        )
    }
}

// ---- 下载页专属：纯逻辑 + 子组件（原 OfflineManageUi.kt，按「一个屏幕一个文件」并回） ----

// 多选工具栏 / 可勾选行在 SelectionUi.kt（三个页面共用），这里只留下载页自己的东西。

// ---- 1. 纯逻辑（无 Compose、无 Context，可直接单测） ----

/** 本地下载的排序方式。两种都是降序；不持久化，离开页面即回默认。 */
enum class OfflineSort { RECENT, SIZE }

fun offlineSortLabel(sort: OfflineSort): String = when (sort) {
    OfflineSort.RECENT -> "最近下载"
    OfflineSort.SIZE -> "按大小"
}

/**
 * 一行下载记录：meta + 它在**原始 items** 里的下标。
 * 排序只改展示顺序，[index] 始终指向原列表位置 —— 播放靠它，不靠展示下标。
 */
data class OfflineRow(val meta: OfflineMeta, val index: Int, val sizeBytes: Long)

/** 一首下载的唯一键；与 `OfflineStore` 的 size map key、选中集合 key 保持一致。 */
fun offlineKey(meta: OfflineMeta): String = playKey(meta.platform, meta.sourceId)

/**
 * 纯函数：字节 -> 「12.3 MB」/「820 KB」。
 * 0 或负数（文件读不到，例如刚删完还没刷新）返回空串，让调用方整段省略，
 * 而不是显示「0.0 MB」骗人。
 */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${kb.roundToInt().coerceAtLeast(1)} KB"
    return "%.1f MB".format(kb / 1024.0)
}

/**
 * 纯函数：平台短标签，用在下载行的副标题上 —— 同一首歌从两个平台各下一份时
 * 行看起来完全一样（用户反馈的「为什么有重名」），标出来源才能分辨。
 * 未知平台原样返回（宁可显示 "joox" 也不吞掉），空值按网易云（与
 * [playKey] / [OfflineMeta.fromJson] 的默认口径一致）。
 */
fun offlinePlatformLabel(platform: String): String {
    val key = platform.trim().ifBlank { "netease" }
    return SEARCH_PLATFORMS.firstOrNull { it.first == key }?.second ?: key
}

/** 纯函数：副标题「平台 · 大小 · 日期 · 歌手」，缺失的段省略；长歌手名交给单行 Ellipsis。 */
fun offlineRowSubtitle(
    platform: String,
    sizeBytes: Long,
    downloadedAt: Long,
    artist: String
): String = listOf(
    offlinePlatformLabel(platform),
    formatFileSize(sizeBytes),
    formatDownloadDate(downloadedAt),
    artist.trim()
).filter { it.isNotEmpty() }.joinToString(" · ")

/** 纯函数：选中项的落盘总字节（缺失/异常值按 0 算，不抛）。 */
fun sumOfflineBytes(sizes: Map<String, Long>): Long =
    sizes.values.sumOf { it.coerceAtLeast(0L) }

/**
 * 纯函数：生成展示行并排序。
 * 走 stable sort（Kotlin `sortedBy*` 稳定）：大小相同 / 时间相同时保持原始顺序，
 * 而原始 items 已按下载时间倒序，所以「按大小」里的并列项仍是新的在前。
 */
fun buildOfflineRows(
    items: List<OfflineMeta>,
    sizes: Map<String, Long>,
    sort: OfflineSort
): List<OfflineRow> {
    val rows = items.mapIndexed { i, m -> OfflineRow(m, i, sizes[offlineKey(m)] ?: 0L) }
    return when (sort) {
        OfflineSort.RECENT -> rows.sortedByDescending { it.meta.downloadedAt }
        OfflineSort.SIZE -> rows.sortedByDescending { it.sizeBytes }
    }
}

/** 纯函数：当前列表里所有 key（全选用）。 */
fun offlineAllKeys(items: List<OfflineMeta>): Set<String> =
    items.map { offlineKey(it) }.toSet()

fun isAllSelected(selected: Set<String>, items: List<OfflineMeta>): Boolean =
    allSelected(items.map { offlineKey(it) }, selected)

/** 纯函数：把选中 key 还原成 meta，顺序跟随列表本身（删除顺序稳定可预期）。 */
fun selectedItems(items: List<OfflineMeta>, selected: Set<String>): List<OfflineMeta> =
    items.filter { offlineKey(it) in selected }

/** 纯函数：顶部摘要「下载 12.3 MB · 共 7 首」。缓存占用不在这里（设置页已有）。 */
fun offlineSummary(totalBytes: Long, count: Int): String =
    "下载 ${formatStorageMb(totalBytes)} · 共 $count 首"

// ---- 2. 子组件 ----

/** 顶部存储摘要一行。 */
@Composable
fun OfflineSummaryLine(totalBytes: Long, count: Int, modifier: Modifier = Modifier) {
    Text(
        text = offlineSummary(totalBytes, count),
        style = MaterialTheme.typography.bodySmall,
        color = UiMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = 2.dp, bottom = 8.dp)
    )
}

/** 排序切换（与搜索页同款 FilterChip，保持设计系统一致）。 */
@Composable
fun OfflineSortRow(
    sort: OfflineSort,
    onSortChange: (OfflineSort) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OfflineSort.entries.forEach { option ->
            FilterChip(
                selected = option == sort,
                onClick = { onSortChange(option) },
                label = { Text(offlineSortLabel(option)) }
            )
        }
    }
}

/** 列表底部的危险动作（全部清除），确认对话框由调用方弹。 */
@Composable
fun OfflineClearAllFooter(onClearAll: () -> Unit) {
    TextButton(
        onClick = onClearAll,
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_DP.dp)
    ) {
        Text("清除全部下载", color = MaterialTheme.colorScheme.error)
    }
}
