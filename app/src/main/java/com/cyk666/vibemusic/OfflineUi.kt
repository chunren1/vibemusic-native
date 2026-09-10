package com.cyk666.vibemusic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDownloadDate(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMs))
    } catch (_: Exception) {
        ""
    }
}

@Composable
fun OfflineScreen(
    modifier: Modifier = Modifier,
    items: List<OfflineMeta>,
    loading: Boolean = false,
    onPlayAt: (Int) -> Unit = {},
    onDelete: (OfflineMeta) -> Unit = {},
    onBack: () -> Unit = {},
    onGoSearch: () -> Unit = {}
) {
    var confirmDelete by remember { mutableStateOf<OfflineMeta?>(null) }
    var sheetFor by remember { mutableStateOf<OfflineMeta?>(null) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                AppIcon(AppIconKind.CHEVRON_LEFT, UiMuted, size = 20.dp)
                Text("我的")
            }
            Text(
                text = "本地下载 (${items.size})",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.padding(top = 4.dp))
        when {
            loading -> {
                SearchSkeleton()
            }
            items.isEmpty() -> {
                EmptyStateLine(
                    text = "还没有下载，常听3次自动离线",
                    actionLabel = "去搜索",
                    onAction = onGoSearch
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        items,
                        key = { idx, m -> "${m.platform}:${m.sourceId}#$idx" }
                    ) { index, meta ->
                        val date = formatDownloadDate(meta.downloadedAt)
                        SongRow(
                            model = buildSongRowModel(
                                Song(
                                    sourceId = meta.sourceId,
                                    name = meta.name,
                                    artist = meta.artist,
                                    album = "",
                                    coverUrl = meta.coverUrl,
                                    durationSec = meta.durationSec,
                                    platform = meta.platform
                                ),
                                subtitleOverride = if (date.isNotEmpty()) {
                                    "$date · ${meta.artist}"
                                } else {
                                    meta.artist
                                }
                            ),
                            meta = if (meta.durationSec > 0) {
                                formatDuration(meta.durationSec)
                            } else {
                                null
                            },
                            onClick = { onPlayAt(index) },
                            onLongClick = { sheetFor = meta },
                            onOverflow = { sheetFor = meta }
                        )
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
}
