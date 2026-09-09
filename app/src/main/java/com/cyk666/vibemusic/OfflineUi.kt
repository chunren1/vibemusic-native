package com.cyk666.vibemusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfflineScreen(
    modifier: Modifier = Modifier,
    items: List<OfflineMeta>,
    loading: Boolean = false,
    onPlayAt: (Int) -> Unit = {},
    onDelete: (OfflineMeta) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var confirmDelete by remember { mutableStateOf<OfflineMeta?>(null) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹ 我的")
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
                Row(modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                }
            }
            items.isEmpty() -> {
                Text(
                    text = "还没有下载，在搜索页点 ⋯ → 下载，或常听3次自动离线",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(items, key = { idx, m -> "${m.platform}:${m.sourceId}#$idx" }) { index, meta ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onPlayAt(index) },
                                    onLongClick = { confirmDelete = meta }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = meta.coverUrl.ifBlank { null },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meta.name.ifBlank { "(untitled)" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = meta.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val date = formatDownloadDate(meta.downloadedAt)
                                if (date.isNotEmpty()) {
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (meta.durationSec > 0) {
                                Text(
                                    text = formatDuration(meta.durationSec),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            IconButton(onClick = { confirmDelete = meta }) {
                                Text("✕")
                            }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除下载") },
            text = { Text("确定删除本地《${target.name}》吗？文件与记录一并清除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    onDelete(target)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            }
        )
    }
}
