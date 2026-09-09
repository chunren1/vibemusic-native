package com.cyk666.vibemusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Display row derived from the controller timeline. */
data class QueueRow(
    val sourceId: String,
    val title: String,
    val artist: String,
    val durationLabel: String,
    val isCurrent: Boolean
)

/** Pure mapping: queue songs + current index → display rows (current highlighted). */
fun buildQueueRows(songs: List<Song>, currentIndex: Int): List<QueueRow> =
    songs.mapIndexed { i, s ->
        QueueRow(
            sourceId = s.sourceId,
            title = s.name.ifBlank { "(untitled)" },
            artist = s.artist,
            durationLabel = formatDuration(s.durationSec),
            isCurrent = i == currentIndex
        )
    }

/** Guard: never remove the last remaining item. */
fun canRemoveQueueItem(size: Int): Boolean = size > 1

/** Queue row ready for display: duration resolved, live progress attached. */
data class QueueRowDisplay(
    val sourceId: String,
    val title: String,
    val artist: String,
    val durationText: String,
    val isCurrent: Boolean,
    val livePositionMs: Long = 0L,
    val liveDurationMs: Long = 0L
)

/**
 * Resolve one queue row for display. Duration prefers the Activity queue Song
 * (real durationSec) matched by sourceId; the timeline-rebuilt Song (which
 * hardcodes durationSec=0) is only a fallback, rendered as "--:--" when neither
 * side knows the length. Live progress is attached to the current row only.
 */
fun resolveQueueRowDisplay(
    activityQueue: List<Song>,
    timelineSong: Song,
    isCurrent: Boolean,
    livePositionMs: Long = 0L,
    liveDurationMs: Long = 0L
): QueueRowDisplay {
    val match = timelineSong.sourceId.takeIf { it.isNotBlank() }?.let { id ->
        activityQueue.firstOrNull { it.sourceId == id }
    }
    val secs = listOfNotNull(
        match?.durationSec?.takeIf { it > 0 },
        timelineSong.durationSec.takeIf { it > 0 }
    ).firstOrNull()
    return QueueRowDisplay(
        sourceId = timelineSong.sourceId,
        title = timelineSong.name.ifBlank { "(untitled)" },
        artist = timelineSong.artist,
        durationText = if (secs != null) formatDuration(secs) else "--:--",
        isCurrent = isCurrent,
        livePositionMs = if (isCurrent) livePositionMs.coerceAtLeast(0L) else 0L,
        liveDurationMs = if (isCurrent) liveDurationMs.coerceAtLeast(0L) else 0L
    )
}

/** Build display rows for the whole controller timeline in order. */
fun buildQueueRowDisplays(
    activityQueue: List<Song>,
    timelineSongs: List<Song>,
    currentIndex: Int,
    livePositionMs: Long = 0L,
    liveDurationMs: Long = 0L
): List<QueueRowDisplay> =
    timelineSongs.mapIndexed { i, s ->
        resolveQueueRowDisplay(activityQueue, s, i == currentIndex, livePositionMs, liveDurationMs)
    }

/**
 * Merge controller-timeline order with Activity durations: each timeline item
 * keeps the Activity queue Song when the sourceId matches, so a timeline
 * rebuild never clobbers real durations back to 0:00. Order always follows
 * the timeline (removals/clear stay in sync).
 */
fun mergeQueueDurations(activityQueue: List<Song>, timeline: List<Song>): List<Song> =
    timeline.map { t ->
        val id = t.sourceId
        if (id.isNotBlank()) activityQueue.firstOrNull { it.sourceId == id } ?: t else t
    }

/**
 * Pure index math for queue removal (pinned by QueueRemoveTest).
 *
 * Real bug fixed: removing the currently-playing item left the player idling
 * silently (controller kept a stale currentMediaItemIndex, nothing re-seeked).
 * The new index is the item that slid into the removed slot (= the next song),
 * clamped to the new last index when the removed item was the tail; removing an
 * item before current shifts current down by one.
 */
fun nextIndexAfterRemove(size: Int, removedIdx: Int, currentIdx: Int): Int {
    if (size <= 1) return 0
    val last = size - 2
    return when {
        removedIdx < currentIdx -> (currentIdx - 1).coerceIn(0, last)
        removedIdx == currentIdx -> currentIdx.coerceIn(0, last)
        else -> currentIdx.coerceIn(0, last)
    }
}

/** True when the removed row was the playing one → caller must re-seek + play. */
fun removedPlayingItem(removedIdx: Int, currentIdx: Int): Boolean = removedIdx == currentIdx

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    modifier: Modifier = Modifier,
    rows: List<QueueRowDisplay>,
    modeLabel: String,
    onPlayAt: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val currentPos = rows.indexOfFirst { it.isCurrent }
    LaunchedEffect(currentPos) {
        if (currentPos >= 0) {
            try {
                listState.scrollToItem(currentPos)
            } catch (_: Exception) {
            }
        }
    }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text("‹ 返回播放")
            }
            Text(
                text = "播放队列 (${rows.size})",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "模式：$modeLabel",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onClear, enabled = rows.size > 1) {
                Text("清空（保留当前）")
            }
        }
        Spacer(Modifier.height(4.dp))
        if (rows.isEmpty()) {
            Text("队列是空的，去搜索页点一首歌吧")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                itemsIndexed(rows, key = { idx, r -> "${r.sourceId}#$idx" }) { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onPlayAt(index) },
                                onLongClick = { onRemove(index) }
                            )
                            .background(
                                if (row.isCurrent) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (row.isCurrent) "▶ " else "${index + 1}. ",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = if (row.isCurrent) {
                                    MaterialTheme.typography.titleMedium
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                }
                            )
                            Text(
                                text = row.artist,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = row.durationText,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (row.isCurrent && row.liveDurationMs > 0) {
                                Text(
                                    text = formatDuration((row.livePositionMs / 1000).toInt()) +
                                        " / " +
                                        formatDuration((row.liveDurationMs / 1000).toInt()),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        IconButton(onClick = { onRemove(index) }) {
                            Text("✕")
                        }
                    }
                }
            }
        }
    }
}
