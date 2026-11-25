package com.cyk666.vibemusic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 其他版本的完整宿主：自己拿状态、自己发请求、自己渲染。
 *
 * <p>放这里而不是塞进 MainActivity：根组件体量有守卫（check-file-size.sh），
 * 「查一次接口 + 一个弹层」属于这个功能自己的内聚逻辑，不该让根组件背。</p>
 */
@Composable
fun AlternativesHost(
    original: Song,
    onDismiss: () -> Unit,
    onPlay: (AlternativeCandidate) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<AlternativeCandidate>>(emptyList()) }
    LaunchedEffect(original.platform, original.sourceId) {
        loading = true
        error = null
        try {
            candidates = withContext(Dispatchers.IO) {
                fetchAlternatives(original.name, original.artist, original.durationSec * 1000L)
            }
        } catch (e: Exception) {
            error = diagnosableError("查询其他版本失败", e)
        } finally {
            loading = false
        }
    }
    AlternativesSheet(
        original = original,
        loading = loading,
        error = error,
        candidates = candidates,
        onPick = onPlay,
        onDismiss = onDismiss
    )
}

/**
 * 「原版不可播」时的其他版本列表。
 *
 * 只展示服务端筛过的候选（标题一致、已排除 DJ/伴奏/翻唱），每条把差异写在副标题里：
 * 歌手不同、时长差 56s、低于 320k。用户点哪条就播哪条，且播放提示条会显示
 * 「正在播放其他版本」——不会假装是原曲。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AlternativesSheet(
    original: Song,
    loading: Boolean,
    error: String?,
    candidates: List<AlternativeCandidate>,
    onPick: (AlternativeCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = "《${original.name.ifBlank { "(untitled)" }}》的其他版本",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "原版当前没有可播资源。以下候选不是同一录音，播放前请确认差异。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }

                error != null -> Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }

                candidates.isEmpty() -> Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text("没有找到其他可播版本", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }

                else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(candidates, key = { it.platform + ":" + it.sourceId }) { c ->
                        CandidateRow(c) { onPick(c) }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** 播放页操作条上的「找其他版本」入口（图标与触达尺寸与相邻按钮一致）。 */
@Composable
fun FindAlternativesButton(onClick: () -> Unit) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        AppIcon(AppIconKind.SEARCH, InkOnDark)
    }
}

@Composable
private fun CandidateRow(c: AlternativeCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${platformLabel(c.platform)} · ${c.artists.ifBlank { "未知歌手" }}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val dur = if (c.durationMs > 0) formatDurationMs(c.durationMs) else ""
            val q = qualityLabel(c.quality).removePrefix(" · ")
            val meta = listOf(dur, q, c.warningText()).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 毫秒 → m:ss（列表里只做粗略展示）。 */
fun formatDurationMs(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60, total % 60)
}
