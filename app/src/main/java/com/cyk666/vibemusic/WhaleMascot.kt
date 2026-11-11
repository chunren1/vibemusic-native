package com.cyk666.vibemusic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 「大肥鱼」吉祥物（2026-11-11，多色矢量，100 网格）。
 *
 * 设计口径：只借鉴「蓝色大肥鱼」的通用萌系特征（蓝白配色、圆润身体、
 * 大眼睛、腮红、喷水），纯手工路径绘制——不复制社区任何同人画作
 * （鲸鱼娘是画师二创、无官方授权，直接搬素材有版权风险）。
 *
 * 使用位置：助手页空态大图。tab/头像仍用单色 [AppIconKind.WHALE] 剪影——
 * 小尺寸放不下多色细节，混用会糊。
 */
private val WhaleBlueTop = Color(0xFF60A5FA)
private val WhaleBlueBottom = Color(0xFF3B82F6)
private val WhaleInk = Color(0xFF0F172A)
private val WhaleBlush = Color(0x73FB7185)
private val WhaleSpout = Color(0xE693C5FD)

@Composable
fun WhaleMascot(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 100f
        drawBody(s)
        drawBelly(s)
        drawEyes(s)
        drawBlush(s)
        drawSpout(s)
    }
}

private fun DrawScope.drawBody(s: Float) {
    val body = Path().apply {
        moveTo(14f * s, 60f * s)
        cubicTo(14f * s, 34f * s, 30f * s, 22f * s, 50f * s, 22f * s)
        cubicTo(68f * s, 22f * s, 80f * s, 34f * s, 84f * s, 50f * s)
        lineTo(96f * s, 36f * s)
        lineTo(92f * s, 60f * s)
        lineTo(99f * s, 78f * s)
        lineTo(82f * s, 72f * s)
        cubicTo(77f * s, 88f * s, 62f * s, 96f * s, 46f * s, 96f * s)
        cubicTo(24f * s, 96f * s, 14f * s, 86f * s, 14f * s, 60f * s)
        close()
    }
    drawPath(
        path = body,
        brush = Brush.linearGradient(
            listOf(WhaleBlueTop, WhaleBlueBottom),
            start = Offset(20f * s, 24f * s),
            end = Offset(80f * s, 92f * s)
        )
    )
}

private fun DrawScope.drawBelly(s: Float) {
    val belly = Path().apply {
        moveTo(18f * s, 66f * s)
        cubicTo(20f * s, 92f * s, 40f * s, 96f * s, 52f * s, 94f * s)
        cubicTo(68f * s, 91f * s, 80f * s, 80f * s, 84f * s, 70f * s)
        lineTo(80f * s, 88f * s)
        lineTo(62f * s, 99f * s)
        lineTo(44f * s, 99f * s)
        lineTo(26f * s, 96f * s)
        lineTo(18f * s, 80f * s)
        close()
    }
    drawPath(belly, Color.White)
}

private fun DrawScope.drawEyes(s: Float) {
    for ((cx, cy) in listOf(33f to 46f, 60f to 45f)) {
        drawCircle(Color.White, radius = 10f * s, center = Offset(cx * s, cy * s))
        drawCircle(WhaleInk, radius = 6f * s, center = Offset(cx * s, cy * s))
        drawCircle(Color.White, radius = 2f * s, center = Offset((cx - 2f) * s, (cy - 4f) * s))
    }
}

private fun DrawScope.drawBlush(s: Float) {
    for ((cx, cy) in listOf(22f to 62f, 72f to 58f)) {
        drawOval(
            color = WhaleBlush,
            topLeft = Offset((cx - 7f) * s, (cy - 4f) * s),
            size = androidx.compose.ui.geometry.Size(14f * s, 8f * s)
        )
    }
}

private fun DrawScope.drawSpout(s: Float) {
    for ((cx, cy, r) in listOf(Triple(44f, 10f, 3f), Triple(52f, 6f, 4f), Triple(60f, 11f, 2.5f))) {
        drawCircle(WhaleSpout, radius = r * s, center = Offset(cx * s, cy * s))
    }
}
