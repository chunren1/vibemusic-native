package com.cyk666.vibemusic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
            .verticalScroll(scroll)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                AppIcon(AppIconKind.CHEVRON_LEFT, UiMuted, size = 20.dp)
                Text("我的")
            }
            Text(
                text = "我的歌单 (${playlists.size})",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { showCreate = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("新建歌单")
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
        if (playlistsLoading) {
            SearchSkeleton()
        } else if (playlists.isEmpty()) {
            EmptyStateLine(
                text = "还没有歌单，新建一个开始收藏吧",
                actionLabel = "去搜索",
                onAction = onGoSearch
            )
        } else {
            MineSectionCard {
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
                        IconButton(
                            onClick = { showListMenu = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            AppIcon(AppIconKind.MORE, UiMuted)
                        }
                    }
                    if (selecting) {
                        val allChecked = playlists.isNotEmpty() &&
                            checkedIds.size == playlists.size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                checkedIds =
                                    if (allChecked) emptySet()
                                    else playlists.map { it.id }.toSet()
                            }) {
                                Text(if (allChecked) "全不选" else "全选")
                            }
                            Text(
                                text = "已选 ${checkedIds.size} 项",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.weight(1f))
                            Button(
                                onClick = { showBatchConfirm = true },
                                enabled = checkedIds.isNotEmpty()
                            ) {
                                Text("删除所选")
                            }
                        }
                    }
                    playlists.forEach { pl ->
                        val checked = pl.id in checkedIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selecting) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        checkedIds =
                                            if (checked) checkedIds - pl.id
                                            else checkedIds + pl.id
                                    }
                                )
                            }
                            EntryRow(
                                modifier = Modifier.weight(1f),
                                title = pl.name.ifBlank { "(untitled)" },
                                subtitle = "${pl.songCount} 首",
                                coverUrl = pl.coverUrl,
                                onClick = {
                                    if (selecting) {
                                        checkedIds =
                                            if (checked) checkedIds - pl.id
                                            else checkedIds + pl.id
                                    } else {
                                        onSelectPlaylist(pl)
                                    }
                                },
                                trailing = {
                                    if (selecting) {
                                        AppIcon(AppIconKind.CHEVRON_RIGHT, UiMuted)
                                    } else {
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
