package com.cyk666.vibemusic

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// 歌单详情页头卡（从 PlaylistDetailUi.kt 搬出：那个文件靠 PL 棘轮卡 469 纯 LOC）。

@Composable
internal fun DetailHeader(
    playlist: Playlist,
    count: Int,
    vipCount: Int,
    onPlayAll: () -> Unit,
    onShare: () -> Unit,
    playEnabled: Boolean,
    onJoinMine: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = coverModelOrDefault(playlist.coverUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.cover_default),
                fallback = painterResource(R.drawable.cover_default),
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name.ifBlank { "(untitled)" },
                    style = MaterialTheme.typography.titleLarge,
                    color = UiInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.padding(top = 4.dp))
                if (playlist.creator.isNotBlank()) {
                    Text(
                        text = playlist.creator,
                        style = MaterialTheme.typography.bodySmall,
                        color = UiMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "共${count.coerceAtLeast(0)}首",
                    style = MaterialTheme.typography.bodySmall,
                    color = UiMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (playlist.description.isNotBlank()) {
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                text = playlist.description,
                style = MaterialTheme.typography.bodySmall,
                color = UiMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.padding(top = 12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onJoinMine != null) {
                OutlinedButton(
                    onClick = onJoinMine,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                ) {
                    Text("加入我的歌单")
                }
            }
            OutlinedButton(
                onClick = onShare,
                enabled = playEnabled,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Text("分享")
            }
        }
        Spacer(Modifier.padding(top = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(UiSurface)
                .clickable(enabled = playEnabled) { onPlayAll() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                kind = AppIconKind.PLAY_CIRCLE,
                tint = if (playEnabled) UiAccent else UiMuted,
                filled = true,
                size = 32.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "播放全部",
                    style = MaterialTheme.typography.titleMedium,
                    color = UiInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildPlayAllLabel(count, vipCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = UiMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.padding(top = 8.dp))
    }
}

@Composable
internal fun DetailHeaderSkeleton() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ShimmerBox(modifier = Modifier.size(96.dp), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp))
            Spacer(Modifier.padding(top = 8.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).heightIn(min = 16.dp))
        }
    }
    Spacer(Modifier.padding(top = 12.dp))
}
