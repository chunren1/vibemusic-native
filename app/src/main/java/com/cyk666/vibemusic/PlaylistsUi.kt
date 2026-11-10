package com.cyk666.vibemusic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

/**
 * Standalone playlist management page (Obsidian Bloom dark).
 *
 * Reached from Mine via the 我的歌单 entry row. Hosts the full
 * management UI (create/import/list/multi-select/refresh/rename/
 * description/delete/reorder) that used to live inline under the
 * Mine profile header. Tapping a specific playlist opens its
 * PlaylistDetail page; the back row returns to Mine.
 */
@Composable
fun PlaylistsScreen(
    modifier: Modifier = Modifier,
    playlists: List<Playlist>,
    playlistsLoading: Boolean,
    error: String? = null,
    onBack: () -> Unit = {},
    onSelectPlaylist: (Playlist) -> Unit = {},
    onCreatePlaylist: (String, String) -> Unit = { _, _ -> },
    onRenamePlaylist: (Playlist, String) -> Unit = { _, _ -> },
    onUpdateDescription: (Playlist, String) -> Unit = { _, _ -> },
    onDeletePlaylist: (Playlist) -> Unit = {},
    onBatchDelete: (List<String>) -> Unit = {},
    onMovePlaylist: (Playlist, Int) -> Unit = { _, _ -> },
    onImportPlaylist: (String, String) -> Unit = { _, _ -> },
    onRetryPlaylists: () -> Unit = {},
    onGoSearch: () -> Unit = {}
) {
    var selecting by remember { mutableStateOf(false) }
    var showListMenu by remember { mutableStateOf(false) }
    var checkedIds by remember { mutableStateOf(setOf<String>()) }
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var descTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var showBatchConfirm by remember { mutableStateOf(false) }
    var menuSheetFor by remember { mutableStateOf<Playlist?>(null) }
    val scroll = rememberScrollState()
    // 退出多选的统一清理：返回键与标题行「取消」都走这里，避免有一条路径漏清勾选
    val exitSelection = {
        selecting = false
        checkedIds = emptySet()
    }
    // 批量删除或外部刷新可能把列表清空，此时多选态没有意义，自动退出
    LaunchedEffect(playlists.isEmpty()) {
        if (shouldExitSelection(selecting, playlists.size)) exitSelection()
    }
    // 系统返回键：多选态先退出多选；非多选态不拦截，交还系统默认返回
    BackHandler(enabled = selecting) { exitSelection() }
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
            .verticalScroll(scroll)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackNavButton(
                label = "我的",
                onClick = { if (selecting) exitSelection() else onBack() }
            )
            Text(
                text = "我的歌单 (${playlists.size})",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // 新建入口一（常驻，空态/失败态也在）。多选态隐藏：正在挑要删的歌单时不建新的。
            if (!selecting) PlaylistsCreateIconButton(onClick = { showCreate = true })
        }
        Spacer(Modifier.height(8.dp))
        if (showCreate) {
            PlaylistTextDialog(
                title = "新建歌单",
                label = "歌单名",
                secondLabel = "简介（可选）",
                confirmText = "创建",
                onConfirm = { name, desc ->
                    showCreate = false
                    onCreatePlaylist(name, desc)
                },
                onDismiss = { showCreate = false }
            )
        }
        if (showImport) {
            ImportDialog(
                onConfirm = { source, id ->
                    showImport = false
                    onImportPlaylist(source, id)
                },
                onDismiss = { showImport = false }
            )
        }
        renameTarget?.let { target ->
            PlaylistTextDialog(
                title = "重命名",
                initial = target.name,
                label = "新歌单名",
                confirmText = "保存",
                onConfirm = { name, _ ->
                    renameTarget = null
                    onRenamePlaylist(target, name)
                },
                onDismiss = { renameTarget = null }
            )
        }
        descTarget?.let { target ->
            PlaylistTextDialog(
                title = "改简介",
                label = "简介",
                confirmText = "保存",
                onConfirm = { desc, _ ->
                    descTarget = null
                    onUpdateDescription(target, desc)
                },
                onDismiss = { descTarget = null }
            )
        }
        deleteTarget?.let { target ->
            DangerConfirmDialog(
                title = "删除歌单",
                text = "确定删除「${target.name}」吗？组内歌曲一并移除，不可恢复。",
                confirmText = "删除",
                onConfirm = {
                    deleteTarget = null
                    if (selecting) {
                        checkedIds = checkedIds - target.id
                    }
                    onDeletePlaylist(target)
                },
                onDismiss = { deleteTarget = null }
            )
        }
        if (showBatchConfirm) {
            DangerConfirmDialog(
                title = "批量删除",
                text = "确定删除选中的 ${checkedIds.size} 个歌单吗？不可恢复。",
                confirmText = "删除所选",
                onConfirm = {
                    showBatchConfirm = false
                    selecting = false
                    val ids = checkedIds.toList()
                    checkedIds = emptySet()
                    onBatchDelete(ids)
                },
                onDismiss = { showBatchConfirm = false }
            )
        }
        Spacer(Modifier.height(4.dp))
        // 失败 ≠ 空：加载失败时若落进 empty 分支，用户会以为歌单被删光了
        when (
            selectListState(
                loading = playlistsLoading,
                error = error,
                isEmpty = playlists.isEmpty()
            )
        ) {
            ListState.LOADING -> SearchSkeleton()
            ListState.ERROR -> DiscoverRetryRow(
                message = error.orEmpty(),
                onRetry = onRetryPlaylists
            )
            ListState.EMPTY -> EmptyStateLine(
                text = "还没有歌单，新建一个开始收藏吧",
                actionLabel = "去搜索",
                onAction = onGoSearch
            )
            ListState.CONTENT -> MineSectionCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "全部歌单",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (!selecting) {
                            IconButton(
                                onClick = { showListMenu = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                AppIcon(AppIconKind.MORE, UiMuted)
                            }
                        }
                    }
                    if (selecting) {
                        val ids = playlists.map { it.id }
                        // 与下载页 / 歌单详情同一套工具栏：取消 · 已选 N 项 · 全选 · 破坏性动作
                        SelectionToolbar(
                            count = checkedIds.size,
                            allSelected = allSelected(ids, checkedIds),
                            actionLabel = "删除",
                            onCancel = exitSelection,
                            onToggleAll = { checkedIds = toggleAllSelected(ids, checkedIds) },
                            onAction = { showBatchConfirm = true }
                        )
                    }
                    playlists.forEach { pl ->
                        val checked = pl.id in checkedIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selecting) {
                                SelectionDot(checked = checked)
                            }
                            EntryRow(
                                modifier = Modifier.weight(1f),
                                title = pl.name.ifBlank { "(untitled)" },
                                subtitle = "${pl.songCount} 首",
                                coverUrl = pl.coverUrl,
                                onClick = {
                                    if (selecting) checkedIds = toggleSelected(checkedIds, pl.id)
                                    else onSelectPlaylist(pl)
                                },
                                // 与下载页、歌单详情同一套入口：长按任意行进入多选并选中该行。
                                // 未进入多选时 checkedIds 必为空，先置 selecting 再 toggle 即可
                                // 同时完成「进入 + 选中」；已多选时长按与单击一样是取反。
                                onLongClick = {
                                    selecting = true
                                    checkedIds = toggleSelected(checkedIds, pl.id)
                                },
                                trailing = if (selecting) {
                                    null
                                } else {
                                    {
                                        IconButton(
                                            onClick = { menuSheetFor = pl },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            AppIcon(AppIconKind.MORE, UiMuted)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    // 新建入口二：卡片内列表末尾，跟着「全部歌单」走；多选态隐藏。
                    if (!selecting) {
                        PlaylistsCreateFooterRow(onClick = { showCreate = true })
                    }
                }
            }
        }
        menuSheetFor?.let { pl ->
            SongMenuSheet(
                title = pl.name.ifBlank { "(untitled)" },
                actions = listOf(
                    SongMenuAction("rename", "重命名"),
                    SongMenuAction("desc", "改简介"),
                    SongMenuAction("up", "上移"),
                    SongMenuAction("down", "下移"),
                    SongMenuAction("delete", "删除", danger = true)
                ),
                onAction = { id ->
                    when (id) {
                        "rename" -> renameTarget = pl
                        "desc" -> descTarget = pl
                        "up" -> onMovePlaylist(pl, -1)
                        "down" -> onMovePlaylist(pl, 1)
                        "delete" -> deleteTarget = pl
                    }
                    menuSheetFor = null
                },
                onDismiss = { menuSheetFor = null }
            )
        }
        if (showListMenu) {
            SongMenuSheet(
                title = "我的歌单",
                actions = listOf(
                    SongMenuAction("import", "导入外部歌单"),
                    SongMenuAction("select", if (selecting) "取消多选" else "多选"),
                    SongMenuAction("refresh", "刷新")
                ),
                onAction = { id ->
                    when (id) {
                        "import" -> showImport = true
                        "select" -> {
                            selecting = !selecting
                            if (!selecting) checkedIds = emptySet()
                        }
                        "refresh" -> onRetryPlaylists()
                    }
                    showListMenu = false
                },
                onDismiss = { showListMenu = false }
            )
        }
    }
}
