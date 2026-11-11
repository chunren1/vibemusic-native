package com.cyk666.vibemusic

// AppIcon 的矢量绘制内部实现（从 UiAtoms 拆出；round6 T5）。
// drawKind 由同包 AppIcon 调用，故为 internal。

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.IconButton
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector

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

internal fun DrawScope.drawKind(kind: AppIconKind, c: Color, filled: Boolean, s: Float) {
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
