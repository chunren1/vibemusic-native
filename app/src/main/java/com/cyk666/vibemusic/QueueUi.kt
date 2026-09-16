package com.cyk666.vibemusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val liveDurationMs: Long = 0L,
    val coverUrl: String = "",
    // Full-song passthrough: queue sheets rebuild a Song from the row, so the
    // real platform/duration/album must travel with the display model instead
    // of UI-layer defaults (see toRowSong).
    val platform: String = "",
    val durationSec: Int = 0,
    val album: String = ""
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
        liveDurationMs = if (isCurrent) liveDurationMs.coerceAtLeast(0L) else 0L,
        coverUrl = match?.coverUrl ?: timelineSong.coverUrl,
        platform = match?.platform?.takeIf { it.isNotBlank() } ?: timelineSong.platform,
        durationSec = secs ?: 0,
        album = match?.album ?: timelineSong.album
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

/** Pure: queue display row back to a Song for fav/add-to-playlist (no defaults). */
fun QueueRowDisplay.toRowSong(): Song = Song(
    sourceId = sourceId,
    name = title,
    artist = artist,
    album = album,
    coverUrl = coverUrl,
    durationSec = durationSec,
    platform = platform
)

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

@Composable
fun QueueScreen(
    modifier: Modifier = Modifier,
    rows: List<QueueRowDisplay>,
    modeLabel: String,
    onPlayAt: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onGoSearch: () -> Unit = {},
    favIds: Set<String> = emptySet(),
    onToggleFav: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val currentPos = rows.indexOfFirst { it.isCurrent }
    var sheetFor by remember { mutableStateOf<Int?>(null) }
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
                AppIcon(AppIconKind.CHEVRON_LEFT, UiMuted, size = 20.dp)
                Text("返回播放")
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
            EmptyStateLine(
                text = "队列是空的，去搜一首放进来吧",
                actionLabel = "去搜索",
                onAction = onGoSearch
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                itemsIndexed(rows, key = { idx, r -> "${r.sourceId}#$idx" }) { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (row.isCurrent) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .padding(start = 0.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(56.dp)
                                .background(
                                    if (row.isCurrent) UiViolet else Color.Transparent,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        SongRow(
                            model = SongRowModel(
                                title = row.title,
                                subtitle = row.artist,
                                coverUrl = row.coverUrl,
                                platform = row.platform
                            ),
                            meta = if (row.isCurrent && row.liveDurationMs > 0) {
                                formatDuration((row.livePositionMs / 1000).toInt()) +
                                    " / " +
                                    formatDuration((row.liveDurationMs / 1000).toInt())
                            } else {
                                row.durationText
                            },
                            onClick = { onPlayAt(index) },
                            onLongClick = { sheetFor = index },
                            onOverflow = { sheetFor = index }
                        )
                    }
                }
            }
        }
    }
    sheetFor?.let { idx ->
        val row = rows.getOrNull(idx)
        if (row != null) {
            val faved = row.sourceId.isNotBlank() && row.sourceId in favIds
            SongMenuSheet(
                title = row.title,
                actions = listOf(
                    SongMenuAction("fav", if (faved) "取消收藏" else "收藏"),
                    SongMenuAction("add", "加入歌单"),
                    SongMenuAction("remove", "从队列删除", danger = true)
                ),
                onAction = { id ->
                    val target = row.toRowSong()
                    when (id) {
                        "fav" -> onToggleFav(target)
                        "add" -> onAddToPlaylist(target)
                        "remove" -> onRemove(idx)
                    }
                    sheetFor = null
                },
                onDismiss = { sheetFor = null }
            )
        } else {
            sheetFor = null
        }
    }
}
