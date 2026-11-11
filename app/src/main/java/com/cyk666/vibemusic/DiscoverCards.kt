package com.cyk666.vibemusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// Discover 页的自包含卡片（2026-11-07 从 DiscoverUi.kt 整块搬出，纯搬运零行为变更）：
// HomeSongCard 同时被「每日推荐」「新歌速递」两行复用，EncounterRow /
// TreasureCell 只属于发现页；拆出来是为了控制 DiscoverUi.kt 的体量
// （DiscoverUi.kt 是硬上限 400 纯 LOC 的守门文件，不是设计原则）。
// internal 而非 private：同模块其它文件（DiscoverUi.kt）要调用。

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeSongCard(
    song: Song,
    backCoverUrl: String,
    enLabel: String,
    enColor: Color,
    onPlay: () -> Unit,
    onOverflow: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(248.dp)
            .background(UiSurface, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onPlay, onLongClick = onOverflow)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(72.dp)) {
            AsyncImage(
                model = coverModelOrDefault(backCoverUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.cover_default),
                fallback = painterResource(R.drawable.cover_default),
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(10.dp))
                    .background(UiMuted.copy(alpha = 0.25f))
            )
            AsyncImage(
                model = coverModelOrDefault(song.coverUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.cover_default),
                fallback = painterResource(R.drawable.cover_default),
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(10.dp))
                    .background(UiMuted.copy(alpha = 0.25f))
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name.ifBlank { "(untitled)" },
                style = MaterialTheme.typography.bodyLarge,
                color = UiInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist.ifBlank { "未知歌手" },
                style = MaterialTheme.typography.bodySmall,
                color = UiMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = enLabel,
                style = MaterialTheme.typography.bodySmall,
                color = enColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(UiAccent)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(kind = AppIconKind.PLAY, tint = Color.White, size = 20.dp)
        }
    }
}

@Composable
internal fun EncounterRow(
    song: Song,
    faved: Boolean,
    onPlay: () -> Unit,
    onToggleFav: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = coverModelOrDefault(song.coverUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.cover_default),
            fallback = painterResource(R.drawable.cover_default),
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(UiMuted.copy(alpha = 0.25f))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name.ifBlank { "(untitled)" },
                style = MaterialTheme.typography.bodyLarge,
                color = UiInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist.ifBlank { "未知歌手" },
                style = MaterialTheme.typography.bodySmall,
                color = UiMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        FavHeart(faved = faved, onClick = onToggleFav)
    }
}

@Composable
internal fun TreasureCell(
    playlist: RecommendPlaylist,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onTap)
    ) {
        AsyncImage(
            model = coverModelOrDefault(playlist.picUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.cover_default),
            fallback = painterResource(R.drawable.cover_default),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(UiMuted.copy(alpha = 0.25f))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = playlist.name.ifBlank { "(untitled)" },
            style = MaterialTheme.typography.bodyMedium,
            color = UiInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (playlist.copywriter.isNotBlank()) playlist.copywriter
            else "播放 " + formatPlayCount(playlist.playCount),
            style = MaterialTheme.typography.bodySmall,
            color = UiMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
