package com.cyk666.vibemusic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

// Phase 10 beauty overhaul: pure logic + shared atoms. Zero new dependencies
// (Compose Material3 / Coil / foundation only). Colors duplicated per-file on
// purpose: the Obsidian palette vals in MainActivity.kt are file-private.

private val P10Bg = Color(0xFF0A0A0F)
private val P10Surface = Color(0xFF14141C)
private val P10Violet = Color(0xFF8B5CF6)
private val P10Muted = Color(0xFF9CA3AF)
private val P10Ink = Color(0xFFEDEDF2)

/** Minimum touch target height for every tappable added/changed in Phase 10. */
const val MIN_TOUCH_DP = 44

fun Modifier.minTouch(): Modifier = this.heightIn(min = MIN_TOUCH_DP.dp)

// ---- 1. Cover <-> lyrics toggle (pure) ----

enum class PlayerView { COVER, LYRICS }

fun togglePlayerView(current: PlayerView): PlayerView =
    if (current == PlayerView.COVER) PlayerView.LYRICS else PlayerView.COVER

// ---- 2. Player gestures (pure) ----
//
// ZONE CHOICE (documented per spec): swipe-down-close and horizontal
// track-swipes are handled ONLY in the top cover zone (the cover-art Box).
// The lyrics list keeps its own vertical scroll untouched; small vertical
// drags (< threshold) are ignored so lyric scrolling never misfires into a
// close/next/prev. Lyrics view therefore has no drag gestures — it offers a
// header back button + tap-to-toggle instead.

/** Horizontal/vertical swipe threshold (dp) before a drag becomes a gesture. */
const val PLAYER_SWIPE_THRESHOLD_DP = 80f

/** Debounce window against double-fire on next/prev/close. */
const val GESTURE_DEBOUNCE_MS = 300L

enum class PlayerGesture { CLOSE, NEXT, PREV, NONE }

/**
 * Pure gesture resolver (dp units; caller converts px via LocalDensity).
 * - fromCoverZone + dominant downward drag past threshold -> CLOSE
 * - dominant leftward drag past threshold -> NEXT
 * - dominant rightward drag past threshold -> PREV
 * - anything smaller / ambiguous / swipe-down outside the cover zone -> NONE
 */
fun resolvePlayerGesture(
    dxDp: Float,
    dyDp: Float,
    fromCoverZone: Boolean,
    thresholdDp: Float = PLAYER_SWIPE_THRESHOLD_DP
): PlayerGesture {
    val ax = kotlin.math.abs(dxDp)
    val ay = kotlin.math.abs(dyDp)
    if (fromCoverZone && dyDp >= thresholdDp && ay > ax) return PlayerGesture.CLOSE
    if (dxDp <= -thresholdDp && ax > ay) return PlayerGesture.NEXT
    if (dxDp >= thresholdDp && ax > ay) return PlayerGesture.PREV
    return PlayerGesture.NONE
}

/** Pure debounce: first fire (lastMs < 0) always passes; refire needs the window. */
fun shouldFireGesture(nowMs: Long, lastMs: Long, windowMs: Long = GESTURE_DEBOUNCE_MS): Boolean {
    if (lastMs < 0L) return true
    return nowMs - lastMs >= windowMs
}

// ---- 3. List tri-state selector (pure) ----

enum class ListState { LOADING, ERROR, EMPTY, CONTENT }

/** Pure: loading wins, then error (only when nothing to show), then empty. */
fun selectListState(loading: Boolean, error: String?, isEmpty: Boolean): ListState = when {
    loading -> ListState.LOADING
    error != null && isEmpty -> ListState.ERROR
    isEmpty -> ListState.EMPTY
    else -> ListState.CONTENT
}

// ---- 4. Settings rows model (pure) ----
//
// NOTE (音质 row omitted on purpose): backend StreamController
// GET /api/songs/stream accepts only sourceId/name/artist/platform — there is
// NO quality/bitrate param server-side (verified read-only 2026-09-10), so a
// 标准/高 switch would be a fake control writing a query param the server
// ignores. Revisit only if the backend gains a real quality contract.

const val SETTINGS_GITHUB_URL = "https://github.com/chunren1/vibemusic-native"

data class SettingsRow(val id: String, val title: String, val subtitle: String)

fun buildSettingsRows(
    themeName: String = "Obsidian Bloom",
    cacheLabel: String,
    storageLabel: String,
    versionLabel: String,
    cookieLabel: String? = null
): List<SettingsRow> {
    val rows = ArrayList<SettingsRow>(5)
    rows.add(SettingsRow("theme", "主题", "$themeName · 当前主题"))
    if (cookieLabel != null) rows.add(SettingsRow("cookie", "网易 Cookie", cookieLabel))
    rows.add(SettingsRow("cache", "播放缓存", cacheLabel))
    rows.add(SettingsRow("storage", "存储用量", storageLabel))
    rows.add(SettingsRow("about", "关于", versionLabel))
    return rows
}

/** Pure: cookie row subtitle — bound / expired (re-bind) / not set. */
fun cookieRowSubtitle(has: Boolean, valid: Boolean, needsRebind: Boolean = false): String = when {
    needsRebind || (has && !valid) -> "已过期，请重新绑定"
    has && valid -> "已绑定"
    else -> "未绑定"
}

/** Pure: bytes -> "12.3 MB" label (0/negative -> "0.0 MB"). */
fun formatStorageMb(bytes: Long): String {
    val mb = (bytes.coerceAtLeast(0L)) / 1024.0 / 1024.0
    return "%.1f MB".format(mb)
}

/** Pure: "缓存 X · 下载 Y · 共 Z" storage summary line. */
fun storageTotalLabel(cacheBytes: Long, downloadBytes: Long): String {
    val c = cacheBytes.coerceAtLeast(0L)
    val d = downloadBytes.coerceAtLeast(0L)
    return "缓存 ${formatStorageMb(c)} · 下载 ${formatStorageMb(d)} · 共 ${formatStorageMb(c + d)}"
}

/** Sums *.mp3 bytes under [dir] (offline downloads); no Context, unit-testable. */
fun dirAudioBytes(dir: File): Long {
    return try {
        if (!dir.isDirectory) return 0L
        dir.listFiles { f -> f.isFile && f.name.endsWith(".mp3") }
            .orEmpty()
            .sumOf {
                try {
                    it.length().coerceAtLeast(0L)
                } catch (_: Exception) {
                    0L
                }
            }
    } catch (_: Exception) {
        0L
    }
}

// ---- Shared atoms ----

/** One short empty-state line + optional action button (no emoji soup). */
@Composable
fun EmptyStateLine(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = P10Muted
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.padding(top = 4.dp))
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = MIN_TOUCH_DP.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * Global mini-player: floating rounded bar pinned ABOVE the bottom tab bar
 * (it lives in the Scaffold bottomBar slot, so it can never overlap the
 * tabs). Shown when the queue is non-empty and the current screen is not
 * Player. Tap body -> Player screen. X collapses the bar for this session
 * (UI state only — the queue/playback is untouched); it reappears on the
 * next playAt. 48dp touch targets, LinearProgressIndicator hairline.
 */
@Composable
fun MiniPlayerBar(
    song: Song?,
    isPlaying: Boolean,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    onDismiss: () -> Unit,
    positionMs: Long = 0L,
    durationMs: Long = 0L
) {
    if (song == null) return
    val progress = if (durationMs > 0) {
        (positionMs.coerceAtLeast(0L).toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(P10Surface)
            .clickable(onClick = onTap)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = P10Violet,
                    trackColor = P10Muted.copy(alpha = 0.3f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.coverUrl.ifBlank { null },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.name.ifBlank { "(untitled)" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = P10Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = P10Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                    AppIcon(
                        kind = if (isPlaying) AppIconKind.PAUSE else AppIconKind.PLAY,
                        tint = P10Violet
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    AppIcon(AppIconKind.CLOSE, P10Muted)
                }
            }
        }
    }
}

/** Settings row shell: full-width, >= 56dp touch height, chevron, ellipsis. */
@Composable
fun SettingsRowShell(
    title: String,
    subtitle: String,
    showChevron: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .padding(vertical = 8.dp)
    Row(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = P10Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = P10Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (showChevron && onClick != null) {
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                AppIcon(AppIconKind.CHEVRON_RIGHT, P10Muted)
            }
        }
    }
}
