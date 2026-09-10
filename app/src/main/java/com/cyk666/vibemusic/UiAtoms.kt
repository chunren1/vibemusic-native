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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

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

// UI overhaul atoms (P0+P1 batch): zero new dependencies — Canvas-drawn
// Material-style icons (material-icons-core is runtime-only via material3,
// NOT on the compile classpath, so Icons.* is unimportable without adding a
// dep — forbidden), shared row atoms, bottom-sheet menus, dialog atoms.
// Obsidian Bloom palette duplicated per-file (MainActivity vals are private).

val UiInk = Color(0xFFEDEDF2)
val UiMuted = Color(0xFF9CA3AF)
val UiViolet = Color(0xFF8B5CF6)
val UiCyan = Color(0xFF06B6D4)
val UiChampagne = Color(0xFFF5E6C8)
val UiSurface = Color(0xFF14141C)
val UiFavRed = Color(0xFFEF4444)

// ---- 1. Canvas Material-style icons (24dp viewport, filled/outlined pair) ----

enum class AppIconKind {
    SEARCH, EXPLORE, PLAY_CIRCLE, PERSON,
    PLAY, PAUSE, PREV, NEXT,
    CLOSE, MORE, HEART, DOWNLOAD, ADD, TIMER, QUEUE,
    CHECK, CHEVRON_RIGHT, CHEVRON_LEFT,
    HISTORY, TRENDING, MUSIC_NOTE,
    INFO, MODE_SEQUENTIAL, MODE_LOOP, MODE_SINGLE, MODE_SHUFFLE
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
        AppIconKind.INFO -> {
            drawCircle(color = c, radius = 9f * s, center = pt(12f, 12f), style = Stroke(w))
            drawCircle(color = c, radius = 1.5f * s, center = pt(12f, 8f), style = Fill)
            line(pt(12f, 11f), pt(12f, 16.5f), w, c)
        }
        AppIconKind.MODE_SEQUENTIAL -> {
            line(pt(4f, 12f), pt(19f, 12f), w, c)
            line(pt(15f, 8.5f), pt(19f, 12f), w, c)
            line(pt(15f, 15.5f), pt(19f, 12f), w, c)
        }
        AppIconKind.MODE_LOOP -> {
            line(pt(7f, 8f), pt(17f, 8f), w, c)
            line(pt(14f, 5.5f), pt(17f, 8f), w, c)
            line(pt(14f, 10.5f), pt(17f, 8f), w, c)
            line(pt(17f, 8f), pt(17f, 16f), w, c)
            line(pt(17f, 16f), pt(7f, 16f), w, c)
            line(pt(10f, 13.5f), pt(7f, 16f), w, c)
            line(pt(10f, 18.5f), pt(7f, 16f), w, c)
            line(pt(7f, 16f), pt(7f, 8f), w, c)
        }
        AppIconKind.MODE_SINGLE -> {
            line(pt(7f, 8f), pt(17f, 8f), w, c)
            line(pt(14f, 5.5f), pt(17f, 8f), w, c)
            line(pt(14f, 10.5f), pt(17f, 8f), w, c)
            line(pt(17f, 8f), pt(17f, 16f), w, c)
            line(pt(17f, 16f), pt(7f, 16f), w, c)
            line(pt(10f, 13.5f), pt(7f, 16f), w, c)
            line(pt(10f, 18.5f), pt(7f, 16f), w, c)
            line(pt(7f, 16f), pt(7f, 8f), w, c)
            line(pt(10.8f, 11.5f), pt(12f, 10.5f), w, c)
            line(pt(12f, 10.5f), pt(12f, 14.5f), w, c)
        }
        AppIconKind.MODE_SHUFFLE -> {
            line(pt(4f, 7f), pt(20f, 17f), w, c)
            line(pt(16.8f, 17f), pt(20f, 17f), w, c)
            line(pt(18.6f, 13.8f), pt(20f, 17f), w, c)
            line(pt(4f, 17f), pt(20f, 7f), w, c)
            line(pt(16.8f, 7f), pt(20f, 7f), w, c)
            line(pt(18.6f, 10.2f), pt(20f, 7f), w, c)
        }
    }
}

/**
 * Material-style icon (filled/outlined pair) drawn on Canvas — zero deps.
 * 24dp viewport convention; selected tab icons use filled + neon tint.
 */
@Composable
fun AppIcon(
    kind: AppIconKind,
    tint: Color = UiMuted,
    filled: Boolean = true,
    size: Dp = 24.dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val s = size.toPx() / 24f
        drawKind(kind, tint, filled, s)
    }
}

// ---- 2. Song row model (pure) ----

/** Display model for one song row: title fallback + subtitle in one place. */
data class SongRowModel(
    val title: String,
    val subtitle: String,
    val coverUrl: String
)

/**
 * Pure builder: blank name -> "(untitled)" (matches existing tests/parsers);
 * subtitle defaults to artist, overridable (date/artist lines for EntryRow).
 */
fun buildSongRowModel(song: Song, subtitleOverride: String? = null): SongRowModel =
    SongRowModel(
        title = song.name.ifBlank { "(untitled)" },
        subtitle = subtitleOverride ?: song.artist,
        coverUrl = song.coverUrl
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
        if (coverUrl.isNotBlank() || coverSize > 0.dp) {
            AsyncImage(
                model = coverUrl.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(coverSize).clip(RoundedCornerShape(12.dp))
            )
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
