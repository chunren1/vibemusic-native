package com.cyk666.vibemusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun DiscoverRetryRow(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "加载失败：$message",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onRetry) {
            Text("重试")
        }
    }
}

@Composable
fun DiscoverSectionHeader(
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
        Text(text = title, style = MaterialTheme.typography.titleMedium)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerCarousel(
    banners: List<DiscoverBanner>,
    onTap: (DiscoverBanner) -> Unit
) {
    if (banners.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { banners.size })
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp))
        ) { page ->
            val b = banners[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onTap(b) }
            ) {
                AsyncImage(
                    model = b.coverUrl.ifBlank { null },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = b.name.ifBlank { "(untitled)" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (b.desc.isNotBlank()) {
                        Text(
                            text = b.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFDDDDDD),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            banners.indices.forEach { i ->
                val selected = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
fun RecommendImportDialog(
    playlist: RecommendPlaylist,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入歌单") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = playlist.picUrl.ifBlank { null },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name.ifBlank { "(untitled)" },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "网易云 · ID ${playlist.id.ifBlank { "?" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (playlist.copywriter.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = playlist.copywriter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    banners: List<DiscoverBanner>,
    bannersLoading: Boolean,
    bannersError: String?,
    onRetryBanners: () -> Unit,
    onBannerTap: (DiscoverBanner) -> Unit,
    dailySongs: List<Song>,
    dailyReason: String,
    dailyLoading: Boolean,
    dailyError: String?,
    onRetryDaily: () -> Unit,
    onRefreshDaily: () -> Unit,
    onPlayDaily: (Int) -> Unit,
    guessSongs: List<Song>,
    guessLoading: Boolean,
    guessError: String?,
    onRetryGuess: () -> Unit,
    onPlayGuess: (Int) -> Unit,
    hotPlaylists: List<RecommendPlaylist>,
    hotLoading: Boolean,
    hotError: String?,
    onRetryHot: () -> Unit,
    onPlaylistTap: (RecommendPlaylist) -> Unit,
    refreshing: Boolean,
    onPullRefresh: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onPullRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item(key = "discover-title") {
                Text(text = "发现", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
            }
            when {
                bannersLoading && banners.isEmpty() -> item(key = "banner-loading") {
                    SearchSkeleton()
                }
                banners.isNotEmpty() -> item(key = "banner") {
                    BannerCarousel(banners = banners, onTap = onBannerTap)
                    Spacer(Modifier.height(4.dp))
                }
                bannersError != null -> item(key = "banner-error") {
                    DiscoverRetryRow(message = bannersError, onRetry = onRetryBanners)
                }
                else -> item(key = "banner-empty") {
                    Text(
                        text = "暂无推荐",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "daily-head") {
                Spacer(Modifier.height(12.dp))
                DiscoverSectionHeader(
                    title = "每日推荐",
                    actionLabel = "换一批",
                    actionBusy = dailyLoading && dailySongs.isNotEmpty(),
                    onAction = onRefreshDaily
                )
                if (dailyReason.isNotBlank()) {
                    Text(
                        text = dailyReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            when {
                dailyLoading && dailySongs.isEmpty() -> item(key = "daily-loading") {
                    SearchSkeleton()
                }
                dailySongs.isNotEmpty() -> item(key = "daily-list") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            dailySongs,
                            key = { _, s -> "daily-" + s.sourceId + s.platform }
                        ) { index, song ->
                            Column(
                                modifier = Modifier
                                    .width(120.dp)
                                    .clickable { onPlayDaily(index) }
                            ) {
                                AsyncImage(
                                    model = song.coverUrl.ifBlank { null },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = song.name.ifBlank { "(untitled)" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                dailyError != null -> item(key = "daily-error") {
                    DiscoverRetryRow(message = dailyError, onRetry = onRetryDaily)
                }
                else -> item(key = "daily-empty") {
                    Text(
                        text = "暂无推荐，点击换一批试试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "guess-head") {
                Spacer(Modifier.height(12.dp))
                DiscoverSectionHeader(title = "猜你喜欢")
                Spacer(Modifier.height(4.dp))
            }
            when {
                guessLoading && guessSongs.isEmpty() -> item(key = "guess-loading") {
                    SearchSkeleton()
                }
                guessSongs.isNotEmpty() -> {
                    itemsIndexed(
                        guessSongs,
                        key = { _, s -> "guess-" + s.sourceId + s.platform }
                    ) { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayGuess(index) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.coverUrl.ifBlank { null },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.name.ifBlank { "(untitled)" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = formatDuration(song.durationSec),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                guessError != null -> item(key = "guess-error") {
                    DiscoverRetryRow(message = guessError, onRetry = onRetryGuess)
                }
                else -> item(key = "guess-empty") {
                    Text(
                        text = "暂无推荐，下拉刷新试试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "hot-head") {
                Spacer(Modifier.height(12.dp))
                DiscoverSectionHeader(title = "热门歌单")
                Spacer(Modifier.height(4.dp))
            }
            when {
                hotLoading && hotPlaylists.isEmpty() -> item(key = "hot-loading") {
                    SearchSkeleton()
                }
                hotPlaylists.isNotEmpty() -> {
                    items(
                        hotPlaylists.chunked(2),
                        key = { row -> "hot-row-" + row.firstOrNull()?.id + row.firstOrNull()?.name }
                    ) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { pl ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onPlaylistTap(pl) }
                                ) {
                                    AsyncImage(
                                        model = pl.picUrl.ifBlank { null },
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = pl.name.ifBlank { "(untitled)" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (pl.copywriter.isNotBlank()) pl.copywriter
                                        else "播放 " + formatPlayCount(pl.playCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                hotError != null -> item(key = "hot-error") {
                    DiscoverRetryRow(message = hotError, onRetry = onRetryHot)
                }
                else -> item(key = "hot-empty") {
                    Text(
                        text = "暂无歌单，下拉刷新试试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
