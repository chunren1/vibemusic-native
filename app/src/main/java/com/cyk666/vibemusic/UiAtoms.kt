package com.cyk666.vibemusic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

// ---- 0. Diagonal shimmer sweep (Track B3: replaces alpha-pulse placeholders).
//
// Pure offset math lives in shimmerTranslateX (VisualFx.kt, unit-tested); this
// composable measures its own width and sweeps a highlight band diagonally
// (linearGradient, GPU-cheap brush, no layout animation).

/**
 * Shimmer placeholder box: muted base with a diagonal highlight band sweeping
 * left→right on a 1400ms infinite loop. Same layout footprint as the old
 * pulse boxes — drop-in replacement.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    base: Color = UiMuted.copy(alpha = 0.35f),
    highlight: Color = UiInk.copy(alpha = 0.35f)
) {
    BoxWithConstraints(modifier = modifier) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        val sweep = rememberInfiniteTransition(label = "shimmerSweep")
        val progress by sweep.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing)
            ),
            label = "shimmerProgress"
        )
        val x = shimmerTranslateX(progress, wPx)
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(x - wPx / 4f, 0f),
                    end = Offset(x + wPx / 4f, wPx / 2f)
                ),
                shape = shape
            )
        )
    }
}

// UI overhaul atoms (P0+P1 batch): Canvas-drawn Material-style icons for the
// general set + official material-icons-extended vectors for the four play
// modes (BOM-managed, no version pin). AppIconKind.MODE_* entries are kept
// (PlayMode.playModeIconKind + PlayerScreen call sites unchanged) but render
// via modeMaterialIcon() instead of Canvas paths.
// Obsidian Bloom palette duplicated per-file (MainActivity vals are private).

val UiInk = Color(0xFFEDEDF2)
val UiMuted = Color(0xFF9CA3AF)
val UiViolet = Color(0xFF8B5CF6)
val UiCyan = Color(0xFF06B6D4)
val UiChampagne = Color(0xFFF5E6C8)
val UiSurface = Color(0xFF14141C)
val UiFavRed = Color(0xFFEF4444)
val UiGold = Color(0xFFE8B84B)
val UiPink = Color(0xFFF472B6)

// ---- 1. Canvas Material-style icons (24dp viewport, filled/outlined pair) ----

enum class AppIconKind {
    SEARCH, EXPLORE, PLAY_CIRCLE, PERSON,
    PLAY, PAUSE, PREV, NEXT,
    CLOSE, MORE, HEART, DOWNLOAD, ADD, TIMER, QUEUE,
    CHECK, CHEVRON_RIGHT, CHEVRON_LEFT,    HISTORY, TRENDING, MUSIC_NOTE, MESSAGE, SHARE,
    INFO, SETTINGS, MODE_SEQUENTIAL, MODE_LOOP, MODE_SINGLE, MODE_SHUFFLE
}

/**
 * 播放模式官方图标映射 (androidx.compose.material:material-icons-extended,
 * 版本由 compose-bom 托管，libs.versions.toml 中无版本号):
 * - 顺序 SEQUENTIAL (MODE_SEQUENTIAL) → Icons.AutoMirrored.Filled.PlaylistPlay
 * - 列表循环 LIST_LOOP (MODE_LOOP) → Icons.Filled.Repeat
 * - 单曲循环 SINGLE_LOOP (MODE_SINGLE) → Icons.Filled.RepeatOne
 * - 随机 SHUFFLE (MODE_SHUFFLE) → Icons.Filled.Shuffle
 *
 * SEQUENTIAL 备选说明: 官方图标库没有"顺序播放"字形；PlaylistPlay
 * (播放列表 + 三角) 语义最接近"按列表顺序播放"，且与"下一首"无视觉
 * 冲突，故不用 ArrowForward (单箭头易与 NEXT 切歌混淆)。
 * 手绘 Canvas 路径已删除 (drawKind 不再含 MODE_* 分支)，终结糊字 debate。
 */
fun modeMaterialIcon(kind: AppIconKind): ImageVector = when (kind) {
    AppIconKind.MODE_SEQUENTIAL -> Icons.AutoMirrored.Filled.PlaylistPlay
    AppIconKind.MODE_LOOP -> Icons.Filled.Repeat
    AppIconKind.MODE_SINGLE -> Icons.Filled.RepeatOne
    AppIconKind.MODE_SHUFFLE -> Icons.Filled.Shuffle
    else -> throw IllegalArgumentException("非播放模式图标: $kind")
}

/** Pure: true when [kind] is one of the four official-vector play modes. */
fun isPlayModeKind(kind: AppIconKind): Boolean = when (kind) {
    AppIconKind.MODE_SEQUENTIAL, AppIconKind.MODE_LOOP,
    AppIconKind.MODE_SINGLE, AppIconKind.MODE_SHUFFLE -> true
    else -> false
}

private fun DrawScope.line(a: Offset, b: Offset, w: Float, c: Color) =
    drawLine(c, a, b, w, StrokeCap.Round)

private fun heartPath(s: Float): Path = Path().apply {
    moveTo(12f * s, 20.5f * s)
    cubicTo(5f * s, 15f * s, 2.5f * s, 11.5f * s, 2.5f * s, 8.5f * s)
    cubicTo(2.5f * s, 6f * s, 4.5f * s, 4f * s, 7f * s, 4f * s)
    cubicTo(9f * s, 4f * s, 11f * s, 5.5f * s, 12f * s, 7f * s)
    cubicTo(13f * s, 5.5f * s, 15f * s, 4f * s, 17f * s, 4f * s)
    cubicTo(19.5f * s, 4f * s, 21.5f * s, 6f * s, 21.5f * s, 8.5f * s)
    cubicTo(21.5f * s, 11.5f * s, 19f * s, 15f * s, 12f * s, 20.5f * s)
    close()
}

private fun DrawScope.drawKind(kind: AppIconKind, c: Color, filled: Boolean, s: Float) {
    val w = 2f * s
    fun pt(x: Float, y: Float) = Offset(x * s, y * s)
    when (kind) {
        AppIconKind.SEARCH -> {
            drawCircle(color = c, radius = 6.5f * s, center = pt(11f, 11f), style = if (filled) Fill else Stroke(w))
            line(pt(15.8f, 15.8f), pt(21f, 21f), w * 1.4f, c)
        }
        AppIconKind.EXPLORE -> {
            drawCircle(color = c, radius = 9f * s, center = pt(12f, 12f), style = Stroke(w))
            val needle = Path().apply {
                moveTo(15.5f * s, 8.5f * s)
                lineTo(10.8f * s, 10.8f * s)
                lineTo(8.5f * s, 15.5f * s)
                lineTo(13.2f * s, 13.2f * s)
                close()
            }
            drawPath(path = needle, color = c, style = if (filled) Fill else Stroke(w))
        }
        AppIconKind.PLAY_CIRCLE -> {
            if (filled) drawCircle(color = c, radius = 9.5f * s, center = pt(12f, 12f), style = Fill)
            else drawCircle(color = c, radius = 9f * s, center = pt(12f, 12f), style = Stroke(w))
            val tri = Path().apply {
                moveTo(10f * s, 8f * s); lineTo(10f * s, 16f * s); lineTo(16f * s, 12f * s); close()
            }
            drawPath(path = tri, color = if (filled) Color(0xFF0A0A0F) else c, style = Fill)
        }
        AppIconKind.PERSON -> {
            drawCircle(color = c, radius = 3.6f * s, center = pt(12f, 7.8f), style = if (filled) Fill else Stroke(w))
            val shoulders = Path().apply {
                moveTo(5.5f * s, 20f * s)
                quadraticTo(5.5f * s, 14.5f * s, 12f * s, 14.5f * s)
                quadraticTo(18.5f * s, 14.5f * s, 18.5f * s, 20f * s)
                if (filled) close()
            }
            drawPath(path = shoulders, color = c, style = if (filled) Fill else Stroke(w))
        }
        AppIconKind.PLAY -> {
            val tri = Path().apply {
                moveTo(8f * s, 5f * s); lineTo(8f * s, 19f * s); lineTo(18.5f * s, 12f * s); close()
            }
            drawPath(path = tri, color = c, style = Fill)
        }
        AppIconKind.PAUSE -> {
            drawRect(c, pt(6.5f, 5f), androidx.compose.ui.geometry.Size(4.2f * s, 14f * s))
            drawRect(c, pt(13.3f, 5f), androidx.compose.ui.geometry.Size(4.2f * s, 14f * s))
        }
        AppIconKind.PREV -> {
            drawRect(c, pt(5f, 6f), androidx.compose.ui.geometry.Size(2.6f * s, 12f * s))
            val tri = Path().apply {
                moveTo(18.5f * s, 6f * s); lineTo(18.5f * s, 18f * s); lineTo(9f * s, 12f * s); close()
            }
            drawPath(path = tri, color = c, style = Fill)
        }
        AppIconKind.NEXT -> {
            drawRect(c, pt(16.4f, 6f), androidx.compose.ui.geometry.Size(2.6f * s, 12f * s))
            val tri = Path().apply {
                moveTo(5.5f * s, 6f * s); lineTo(5.5f * s, 18f * s); lineTo(15f * s, 12f * s); close()
            }
            drawPath(path = tri, color = c, style = Fill)
        }
        AppIconKind.CLOSE -> {
            line(pt(6f, 6f), pt(18f, 18f), w, c)
            line(pt(18f, 6f), pt(6f, 18f), w, c)
        }
        AppIconKind.MORE -> {
            drawCircle(color = c, radius = 1.9f * s, center = pt(12f, 5f), style = Fill)
            drawCircle(color = c, radius = 1.9f * s, center = pt(12f, 12f), style = Fill)
            drawCircle(color = c, radius = 1.9f * s, center = pt(12f, 19f), style = Fill)
        }
        AppIconKind.HEART -> drawPath(
            path = heartPath(s),
            color = c,
            style = if (filled) Fill else Stroke(w)
        )
        AppIconKind.DOWNLOAD -> {
            line(pt(12f, 3.5f), pt(12f, 15f), w, c)
            line(pt(7.5f, 10.5f), pt(12f, 15.5f), w, c)
            line(pt(16.5f, 10.5f), pt(12f, 15.5f), w, c)
            line(pt(5f, 19.5f), pt(19f, 19.5f), w, c)
        }
        AppIconKind.ADD -> {
            line(pt(12f, 5f), pt(12f, 19f), w, c)
            line(pt(5f, 12f), pt(19f, 12f), w, c)
        }
        AppIconKind.TIMER -> {
            drawCircle(color = c, radius = 7f * s, center = pt(12f, 13.5f), style = Stroke(w))
            line(pt(12f, 13.5f), pt(12f, 9.8f), w, c)
            line(pt(12f, 13.5f), pt(14.8f, 14.8f), w, c)
            line(pt(10f, 2.5f), pt(14f, 2.5f), w * 1.2f, c)
            line(pt(12f, 2.5f), pt(12f, 5.5f), w, c)
        }
        AppIconKind.QUEUE -> {
            line(pt(9f, 6.5f), pt(20f, 6.5f), w, c)
            line(pt(9f, 12f), pt(20f, 12f), w, c)
            line(pt(9f, 17.5f), pt(20f, 17.5f), w, c)
            drawCircle(color = c, radius = 1.5f * s, center = pt(5f, 6.5f), style = Fill)
            drawCircle(color = c, radius = 1.5f * s, center = pt(5f, 12f), style = Fill)
            drawCircle(color = c, radius = 1.5f * s, center = pt(5f, 17.5f), style = Fill)
        }
        AppIconKind.CHECK -> {
            line(pt(5f, 13f), pt(10f, 18f), w * 1.2f, c)
            line(pt(10f, 18f), pt(19f, 7f), w * 1.2f, c)
        }
        AppIconKind.CHEVRON_RIGHT -> {
            line(pt(9.5f, 5.5f), pt(15.5f, 12f), w, c)
            line(pt(15.5f, 12f), pt(9.5f, 18.5f), w, c)
        }
        AppIconKind.CHEVRON_LEFT -> {
            line(pt(14.5f, 5.5f), pt(8.5f, 12f), w, c)
            line(pt(8.5f, 12f), pt(14.5f, 18.5f), w, c)
        }
        AppIconKind.HISTORY -> {
            drawCircle(color = c, radius = 8.5f * s, center = pt(12f, 12.5f), style = Stroke(w))
            line(pt(12f, 12.5f), pt(12f, 8f), w, c)
            line(pt(12f, 12.5f), pt(15f, 13.8f), w, c)
            line(pt(9f, 2.5f), pt(9f, 5f), w, c)
            line(pt(12f, 2f), pt(12f, 4f), w, c)
            line(pt(15f, 2.5f), pt(15f, 5f), w, c)
        }
        AppIconKind.TRENDING -> {
            line(pt(3f, 17f), pt(9f, 11f), w, c)
            line(pt(9f, 11f), pt(13f, 15f), w, c)
            line(pt(13f, 15f), pt(21f, 7f), w, c)
            line(pt(15.5f, 7f), pt(21f, 7f), w, c)
            line(pt(21f, 7f), pt(21f, 12.5f), w, c)
        }
        AppIconKind.MUSIC_NOTE -> {
            drawCircle(color = c, radius = 2.4f * s, center = pt(6.8f, 18.5f), style = Fill)
            drawCircle(color = c, radius = 2.4f * s, center = pt(17.2f, 16.5f), style = Fill)
            line(pt(9f, 18.5f), pt(9f, 6f), w, c)
            line(pt(19.4f, 16.5f), pt(19.4f, 4f), w, c)
            line(pt(9f, 6f), pt(19.4f, 4f), w, c)
        }
        AppIconKind.MESSAGE -> {
            drawRoundRect(
                color = c,
                topLeft = pt(3.5f, 5f),
                size = Size(17f * s, 10.5f * s),
                cornerRadius = CornerRadius(2.5f * s, 2.5f * s),
                style = if (filled) Fill else Stroke(w)
            )
            line(pt(8f, 15.5f), pt(10.5f, 19.5f), w, c)
            line(pt(10.5f, 19.5f), pt(13.5f, 15.5f), w, c)
        }
        AppIconKind.SHARE -> {
            line(pt(12f, 16f), pt(12f, 4f), w, c)
            line(pt(8f, 8f), pt(12f, 3.5f), w, c)
            line(pt(16f, 8f), pt(12f, 3.5f), w, c)
            line(pt(5f, 13f), pt(5f, 20f), w, c)
            line(pt(5f, 20f), pt(19f, 20f), w, c)
            line(pt(19f, 20f), pt(19f, 13f), w, c)
        }
        AppIconKind.INFO -> {
            drawCircle(color = c, radius = 9f * s, center = pt(12f, 12f), style = Stroke(w))
            drawCircle(color = c, radius = 1.5f * s, center = pt(12f, 8f), style = Fill)
            line(pt(12f, 11f), pt(12f, 16.5f), w, c)
        }
        AppIconKind.SETTINGS -> {
            // Gear: ring + 8 spokes + hub (no trig — axis-aligned + diagonals).
            drawCircle(color = c, radius = 5.5f * s, center = pt(12f, 12f), style = Stroke(w))
            line(pt(2.7f, 12f), pt(5f, 12f), w, c)
            line(pt(19f, 12f), pt(21.3f, 12f), w, c)
            line(pt(12f, 2.7f), pt(12f, 5f), w, c)
            line(pt(12f, 19f), pt(12f, 21.3f), w, c)
            line(pt(5.4f, 5.4f), pt(7f, 7f), w, c)
            line(pt(17f, 7f), pt(18.6f, 5.4f), w, c)
            line(pt(5.4f, 18.6f), pt(7f, 17f), w, c)
            line(pt(17f, 17f), pt(18.6f, 18.6f), w, c)
            drawCircle(color = c, radius = 1.6f * s, center = pt(12f, 12f), style = Fill)
        }
        // MODE_* 无 Canvas 分支：四种播放模式走官方 material-icons-extended
        // 向量 (AppIcon 直接渲染 modeMaterialIcon)，永不到达 drawKind。
        else -> Unit
    }
}

/**
 * Material-style icon: official material-icons-extended vector for the four
 * play modes, Canvas-drawn glyph for everything else. 24dp viewport
 * convention; selected tab icons use filled + neon tint. Mode buttons keep
 * their 48dp IconButton target + text label at the call site (PlayerScreen).
 */
/**
 * R4-A2 无障碍：Canvas 自绘图标没有可读文本，IconButton 里只能靠这里给语义标签。
 * 纯函数便于单测钉死；纯装饰用途（列表行内点缀等）调用处传 null 关闭播报。
 */
fun defaultAppIconLabel(kind: AppIconKind): String? = when (kind) {
    AppIconKind.PLAY, AppIconKind.PLAY_CIRCLE -> "播放"
    AppIconKind.PAUSE -> "暂停"
    AppIconKind.PREV -> "上一首"
    AppIconKind.NEXT -> "下一首"
    AppIconKind.CLOSE -> "关闭"
    AppIconKind.MORE -> "更多"
    AppIconKind.HEART -> "收藏"
    AppIconKind.DOWNLOAD -> "下载"
    AppIconKind.ADD -> "添加"
    AppIconKind.TIMER -> "定时"
    AppIconKind.QUEUE -> "播放队列"
    AppIconKind.SEARCH -> "搜索"
    AppIconKind.SHARE -> "分享"
    AppIconKind.INFO -> "提示"
    AppIconKind.SETTINGS -> "设置"
    AppIconKind.PERSON -> "我的"
    AppIconKind.EXPLORE -> "发现"
    AppIconKind.HISTORY -> "历史"
    AppIconKind.TRENDING -> "热门"
    AppIconKind.MODE_SEQUENTIAL, AppIconKind.MODE_LOOP,
    AppIconKind.MODE_SINGLE, AppIconKind.MODE_SHUFFLE -> "播放模式"
    // 纯装饰/导航指示类：不产生冗余播报
    AppIconKind.CHECK, AppIconKind.CHEVRON_RIGHT, AppIconKind.CHEVRON_LEFT,
    AppIconKind.MUSIC_NOTE, AppIconKind.MESSAGE -> null
}

@Composable
fun AppIcon(
    kind: AppIconKind,
    tint: Color = UiMuted,
    filled: Boolean = true,
    size: Dp = 24.dp,
    contentDescription: String? = defaultAppIconLabel(kind)
) {
    if (isPlayModeKind(kind)) {
        Icon(
            imageVector = modeMaterialIcon(kind),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size)
        )
        return
    }
    val a11y = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier = Modifier.size(size).then(a11y)) {
        val s = size.toPx() / 24f
        drawKind(kind, tint, filled, s)
    }
}

// ---- 2b. CoverImage: single cover render rule (no empty holes) ----

/**
 * Pure: absolute image URL for a backend path. Blank / literal "null" (the
 * backend's String.valueOf(null) legacy) → "". Relative paths (e.g. the
 * image-proxy cover for B站/酷狗 sources) get the API base prepended;
 * absolute http(s) URLs pass through unchanged (idempotent).
 */
fun absImgUrl(path: String): String {
    val t = path.trim()
    if (t.isBlank() || t == "null") return ""
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    return VibeApi.BASE_URL.trimEnd('/') + (if (t.startsWith("/")) t else "/$t")
}

/**
 * Pure: Coil model for a cover URL — null when nothing loadable. Every cover
 * render point (home gate, CoverImage, player backdrop/vinyl, artworkUri)
 * funnels through this so relative proxy paths and "null" strings never
 * reach Coil raw (2026-09-17 cover incident).
 */
fun coverModel(coverUrl: String): String? = absImgUrl(coverUrl).ifBlank { null }

/**
 * Pure gate: only positively-loadable cover URLs count as "has cover".
 * Blank / "null" / non-http garbage is rejected; relative proxy paths count
 * (absImgUrl resolves them) — so the home gate never hides a playable cover.
 */
fun hasCoverUrl(coverUrl: String): Boolean = absImgUrl(coverUrl).isNotBlank()

/**
 * Shared cover atom: non-blank URL → Coil cover; blank → the same
 * default-note placeholder EntryRow uses (muted box + MUSIC_NOTE), never
 * an empty hole. All non-HOME cover paths go through here; HOME rows stay
 * behind the cover gate (homeVisibleSongs/homeVisiblePlaylists).
 */
@Composable
fun CoverImage(
    coverUrl: String,
    size: Dp,
    cornerDp: Dp = 12.dp,
    iconSize: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val model = remember(coverUrl) { coverModel(coverUrl) }
    // Load-failure fallback (2026-09-17 cover incident): a URL that passes
    // the gate can still 404 or fail at the CDN — show the same note
    // placeholder instead of a blank hole.
    var failed by remember(coverUrl) { mutableStateOf(false) }
    if (model != null && !failed) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onState = { st ->
                if (st is AsyncImagePainter.State.Error) failed = true
            },
            modifier = modifier.size(size).clip(RoundedCornerShape(cornerDp))
        )
    } else {
        Box(
            modifier = modifier.size(size)
                .clip(RoundedCornerShape(cornerDp))
                .background(UiMuted.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(AppIconKind.MUSIC_NOTE, UiMuted, size = iconSize)
        }
    }
}

/** Display model for one song row: title fallback + subtitle in one place. */
data class SongRowModel(
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val platform: String = ""
)

/**
 * Pure: true when a track comes from Bilibili (platform match is
 * case-insensitive + blank-tolerant; any other/blank platform is false).
 */
fun isBilibiliPlatform(platform: String): Boolean =
    platform.trim().equals("bilibili", ignoreCase = true)

/** Pure: song-level Bilibili check (delegates to [isBilibiliPlatform]). */
fun isBilibiliSong(song: Song): Boolean = isBilibiliPlatform(song.platform)

/**
 * Pure builder: blank name -> "(untitled)" (matches existing tests/parsers);
 * subtitle defaults to artist, overridable (date/artist lines for EntryRow).
 */
fun buildSongRowModel(song: Song, subtitleOverride: String? = null): SongRowModel =
    SongRowModel(
        title = song.name.ifBlank { "(untitled)" },
        subtitle = subtitleOverride ?: song.artist,
        coverUrl = song.coverUrl,
        platform = song.platform
    )

/**
 * Tiny presentational source badge for Bilibili tracks ("B站" pill, cyan on
 * muted surface — dark-theme only, no click behavior). Callers gate it with
 * [isBilibiliPlatform]/[isBilibiliSong]; it never decides visibility itself.
 */
@Composable
fun BiliBadge(modifier: Modifier = Modifier) {
    Text(
        text = "B站",
        style = MaterialTheme.typography.labelSmall,
        color = UiCyan,
        maxLines = 1,
        modifier = modifier
            .background(UiMuted.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** One Mine content entry: stable id + Chinese title + count subtitle. */
data class MineEntry(
    val id: String,
    val title: String,
    val subtitle: String
)

/**
 * Pure builder for the Mine page's exactly-4 content entries
 * (我的收藏 / 我的歌单 / 最近播放 / 本地下載). Guests see a login
 * prompt subtitle on account-backed entries; 本地下载 is local so it
 * always shows its count.
 */
fun buildMineEntries(
    favCount: Int,
    playlistCount: Int,
    historyCount: Int,
    offlineCount: Int,
    loggedIn: Boolean
): List<MineEntry> = listOf(
    MineEntry(
        id = "favorites",
        title = "我的收藏",
        subtitle = if (loggedIn) "$favCount 首" else "登录后查看"
    ),
    MineEntry(
        id = "playlists",
        title = "我的歌单",
        subtitle = if (loggedIn) "$playlistCount 个" else "登录后查看"
    ),
    MineEntry(
        id = "history",
        title = "最近播放",
        subtitle = if (loggedIn) "$historyCount 首" else "登录后查看"
    ),
    MineEntry(
        id = "offline",
        title = "本地下载",
        subtitle = "$offlineCount 首"
    )
)

// ---- 3. EntryRow / SongRow atoms ----

/**
 * Shared entry row: optional cover + title/subtitle + trailing slot.
 * Covers history/offline/mine entry rows (P0-5) and underlies SongRow.
 */
@Composable
fun EntryRow(
    title: String,
    subtitle: String,
    coverUrl: String = "",
    coverSize: Dp = 56.dp,
    meta: String? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    badge: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    @OptIn(ExperimentalFoundationApi::class)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let {
                if (onLongClick != null) {
                    it.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    it.clickable(onClick = onClick)
                }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coverSize > 0.dp) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverModel(coverUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(coverSize).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(coverSize)
                        .clip(RoundedCornerShape(12.dp))
                        .background(UiMuted.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(AppIconKind.MUSIC_NOTE, UiMuted, size = 24.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = UiInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = UiMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badge != null) {
                Row(modifier = Modifier.padding(top = 2.dp)) {
                    badge()
                }
            }
        }
        if (meta != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = UiMuted,
                maxLines = 1
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

/**
 * P0-1 SongRow atom: 56dp 12dp-radius Coil cover, two-line title/artist
 * (ink/muted), duration meta, ONE trailing overflow slot (fixes 360dp
 * overcrowd). Extra actions live in the overflow sheet, never beside it.
 */
@Composable
fun SongRow(
    model: SongRowModel,
    meta: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onOverflow: () -> Unit
) {
    EntryRow(
        title = model.title,
        subtitle = model.subtitle,
        coverUrl = model.coverUrl,
        coverSize = 56.dp,
        meta = meta,
        onClick = onClick,
        onLongClick = onLongClick,
        badge = if (isBilibiliPlatform(model.platform)) {
            { BiliBadge() }
        } else {
            null
        },
        trailing = {
            IconButton(
                onClick = onOverflow,
                modifier = Modifier.size(48.dp)
            ) {
                AppIcon(AppIconKind.MORE, UiMuted)
            }
        }
    )
}

// ---- 4. Song overflow bottom sheet (reuses AddToPlaylistSheet pattern) ----

/** One sheet row: label + optional danger styling/disabled state. */
data class SongMenuAction(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val danger: Boolean = false
)

/**
 * Bottom sheet for song/playlist overflow menus (P1): ModalBottomSheet with
 * skipPartiallyExpanded, song title header, 48dp action rows. Call sites pass
 * the same callbacks the old DropdownMenus used — no menu rebuilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuSheet(
    title: String,
    actions: List<SongMenuAction>,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = UiInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.padding(top = 8.dp))
            actions.forEach { a ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = a.enabled) { onAction(a.id) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = a.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (a.danger) MaterialTheme.colorScheme.error else UiInk,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

// ---- 5. Mine section card ----

/** Groups a Mine section into an Obsidian card; dividers go between rows. */
@Composable
fun MineSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = UiSurface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            content()
        }
    }
}

/** Divider between card rows. */
@Composable
fun MineDivider() {
    HorizontalDivider(
        color = UiMuted.copy(alpha = 0.25f),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

// ---- 6. Dialog atoms ----

enum class ConfirmStyle { DANGER, PLAIN }

/** Pure selector: danger confirms get a filled error button, forms get text. */
fun selectConfirmStyle(danger: Boolean): ConfirmStyle =
    if (danger) ConfirmStyle.DANGER else ConfirmStyle.PLAIN

/**
 * Confirm-danger atom: filled error confirm button (vs FormDialog's text
 * buttons). For irreversible deletes/clears.
 */
@Composable
fun DangerConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * Form atom: title + custom content slot + text confirm/cancel. For
 * create/rename/import/edit dialogs with input validation.
 */
@Composable
fun FormDialog(
    title: String,
    confirmText: String,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { content() } },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ---- 7. Sleep options (pure) ----

/** Sleep radio options: 0 = off, then the shared minute presets. */
fun sleepPresetOptions(): List<Int> = listOf(0) + SLEEP_PRESETS

/** Pure label: 0 -> 关闭定时, else "N分钟". */
fun sleepOptionLabel(min: Int): String = if (min <= 0) "关闭定时" else "${min}分钟"

/**
 * Pure resolver for the radio+custom sleep dialog: a valid custom field wins,
 * else the selected radio, else null (dismiss without change).
 * parseSleepMinutes bounds custom to 5..180.
 */
fun resolveSleepChoice(selected: Int?, custom: String): Int? {
    val trimmed = custom.trim()
    if (trimmed.isNotEmpty()) {
        val parsed = parseSleepMinutes(trimmed)
        if (parsed != null) return parsed
        return selected
    }
    return selected
}

// ---- 8. FavHeart: Material heart + red token + ripple (IconButton default) ----

@Composable
fun FavHeart(faved: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        AppIcon(
            kind = AppIconKind.HEART,
            tint = if (faved) UiFavRed else UiMuted,
            filled = faved,
            size = 24.dp
        )
    }
}

// ---- 9. Section header (Discover rhythm: titleSmall) ----

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    actionBusy: Boolean = false,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = UiInk)
        if (actionLabel != null && onAction != null) {
            if (actionBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

// ---- 10. Top toast (transient announcements; errors keep the bottom Snackbar) ----

/** Auto-dismiss delay for the top toast (mode switch first). */
const val TOP_TOAST_DISMISS_MS = 1500L

/** Pure: the top toast shows only for non-blank messages. */
fun isTopToastVisible(message: String?): Boolean = !message.isNullOrBlank()

/**
 * Transient announcement pill pinned below the status bar (top-center
 * overlay; caller aligns it inside a full-size Box). Null/blank renders
 * nothing. Dismissal timing is owned by the caller (TOP_TOAST_DISMISS_MS).
 */
@Composable
fun TopToast(message: String?, modifier: Modifier = Modifier) {
    if (!isTopToastVisible(message)) return
    Box(
        modifier = modifier.fillMaxWidth().statusBarsPadding()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = message.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = UiInk,
            modifier = Modifier
                .background(UiSurface.copy(alpha = 0.94f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

// ---- 11. Reference-redesign atoms (HOME/MINE/PLAYER presentational only) ----

/** One cell of the MINE 喜欢/最近/本地 triple: stable id + label + count copy. */
data class MineTripleItem(
    val id: String,
    val title: String,
    val count: String
)

/**
 * Pure builder for the MINE triple row. Guests see "–" on account-backed
 * cells (tap routes to login via the existing onOpenFavorites/history path);
 * 本地下载 is on-device so it always shows its count.
 */
fun buildMineTriple(
    favCount: Int,
    historyCount: Int,
    offlineCount: Int,
    loggedIn: Boolean
): List<MineTripleItem> = listOf(
    MineTripleItem(
        id = "favorites",
        title = "喜欢",
        count = if (loggedIn) "$favCount" else "–"
    ),
    MineTripleItem(
        id = "history",
        title = "最近",
        count = if (loggedIn) "$historyCount" else "–"
    ),
    MineTripleItem(
        id = "offline",
        title = "本地",
        count = "$offlineCount"
    )
)

/**
 * HOME section header: Chinese title + colored EN subtitle on one line,
 * optional trailing action (换一批 etc.). Same titleSmall/ink rhythm as
 * the existing SectionHeader.
 */
@Composable
fun HomeSectionHeader(
    title: String,
    enSubtitle: String,
    enColor: Color,
    actionLabel: String? = null,
    actionBusy: Boolean = false,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = UiInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = enSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = enColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (actionLabel != null && onAction != null) {
            if (actionBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
