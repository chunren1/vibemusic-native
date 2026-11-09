package com.cyk666.vibemusic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 气泡圆角：用户右切角、助手左切角，暗示说话方向。 */
private val BubbleUser = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
private val BubbleAi = RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp)

/** 气泡里最多铺几首；多出的收成一行「还有 N 首」，不把对话拉成歌单页。 */
internal const val MAX_SONG_ROWS = 5

/**
 * 助手头像：品牌渐变圆 + 音符。
 *
 * 大厂聊天助手的共同点是「谁在说话一眼可见」——纯气泡贴左会让人分不清
 * 是用户在复述还是 AI 在回答。头像固定在气泡外侧，用户消息则不加头像
 * （右对齐本身就是身份信号，再加头像会显得两边对称、没有主次）。
 */
@Composable
internal fun AssistantAvatar(size: Dp = 30.dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(UiViolet, UiCyan))),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(
            AppIconKind.MUSIC_NOTE, Color.White,
            size = size * 0.52f, contentDescription = null
        )
    }
}

/** 一条消息：用户右对齐渐变气泡，助手头像 + 淡气泡 + 可选歌曲卡片。 */
@Composable
internal fun AssistantMessageRow(
    msg: ChatMessage,
    maxBubble: Dp,
    onPlaySongs: (List<Song>, Int) -> Unit
) {
    if (msg.role == ChatRole.USER) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = maxBubble).clip(BubbleUser)
                    .background(Brush.linearGradient(listOf(UiViolet, UiViolet.copy(alpha = 0.78f))))
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            )
        }
        return
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        AssistantAvatar()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                msg.text,
                style = MaterialTheme.typography.bodyMedium,
                // 失败消息只降饱和、不刷红：整页暗色，红字像报警而不是「没连上」
                color = if (msg.failed) UiMuted else UiInk,
                lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = maxBubble).clip(BubbleAi)
                    .background(UiSurface.copy(alpha = 0.72f))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            )
            if (msg.songs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AssistantSongCard(msg.songs, maxBubble, onPlaySongs)
            }
        }
    }
}

/**
 * 歌曲结果卡片：独立容器 + 头部「全部播放」+ 紧凑单曲行。
 *
 * 不复用 [SongRow] 是因为它按「歌单条目」设计（56dp 封面 + 溢出菜单），
 * 塞进对话气泡后每行都像一屏，一屏只能放三首；这里 42dp 封面 + 30dp
 * 播放按钮，一屏能顺畅扫完五首。
 */
@Composable
internal fun AssistantSongCard(
    songs: List<Song>,
    maxBubble: Dp,
    onPlaySongs: (List<Song>, Int) -> Unit
) {
    val shown = songs.take(MAX_SONG_ROWS)
    Column(
        Modifier.widthIn(max = maxBubble).clip(RoundedCornerShape(16.dp))
            .background(UiSurface)
            .border(1.dp, UiInk.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "为你找到 ${songs.size} 首",
                style = MaterialTheme.typography.labelMedium,
                color = UiMuted, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                "全部播放",
                style = MaterialTheme.typography.labelMedium,
                color = UiCyan, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { onPlaySongs(songs, 0) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
        shown.forEachIndexed { index, song ->
            AssistantSongTile(song) { onPlaySongs(songs, index) }
        }
        if (songs.size > shown.size) {
            Text(
                "还有 ${songs.size - shown.size} 首",
                style = MaterialTheme.typography.labelSmall, color = UiMuted,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 10.dp)
            )
        }
    }
}

@Composable
private fun AssistantSongTile(song: Song, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(song.coverUrl, size = 42.dp, cornerDp = 9.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.name.ifBlank { "(未命名)" },
                style = MaterialTheme.typography.bodyMedium,
                color = UiInk, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist.ifBlank { "未知歌手" },
                style = MaterialTheme.typography.labelSmall,
                color = UiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(UiViolet.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(AppIconKind.PLAY, UiViolet, size = 15.dp, contentDescription = null)
        }
    }
}

/** 思考态：头像 + 「正在想」+ 三颗呼吸点（保持原有节奏，换成与消息行一致的排版）。 */
@Composable
internal fun AssistantThinkingRow(maxBubble: Dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        AssistantAvatar()
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.widthIn(max = maxBubble).clip(BubbleAi)
                .background(UiSurface.copy(alpha = 0.72f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("正在想", style = MaterialTheme.typography.bodyMedium, color = UiMuted)
            Spacer(Modifier.width(7.dp))
            val phase by rememberInfiniteTransition(label = "thinking").animateFloat(
                0f, 3f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 1050, easing = LinearEasing)),
                label = "thinkingDots"
            )
            val active = phase.toInt() % 3
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier.size(5.dp).clip(CircleShape)
                            .background(if (i == active) UiCyan else UiMuted.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}
