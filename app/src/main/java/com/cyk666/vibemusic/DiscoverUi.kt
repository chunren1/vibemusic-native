package com.cyk666.vibemusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
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
    SectionHeader(
        title = title,
        actionLabel = actionLabel,
        actionBusy = actionBusy,
        onAction = onAction
    )
}

@Composable
fun BannerSkeleton() {
    ShimmerBox(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(selectBannerAspect(null)),
        shape = RoundedCornerShape(12.dp)
    )
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
                .aspectRatio(selectBannerAspect(null))
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
                        .then(
                            if (selected) {
                                Modifier
                                    .width(16.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            } else {
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                            }
                        )
                        .background(
                            if (selected) UiViolet
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
    FormDialog(
        title = "导入歌单",
        confirmText = "导入",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongCard(
    song: Song,
    onPlay: () -> Unit,
    onOverflow: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .combinedClickable(onClick = onPlay, onLongClick = onOverflow)
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
    onPullRefresh: () -> Unit,
    favIds: Set<String> = emptySet(),
    downloadingKeys: Set<String> = emptySet(),
    downloadedKeys: Set<String> = emptySet(),
    onToggleFav: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onDownload: (Song) -> Unit = {},
    onGoSearch: () -> Unit = {}
) {
    var cardSheetFor by remember { mutableStateOf<Song?>(null) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onPullRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item(key = "discover-title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "首页",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onGoSearch, modifier = Modifier.size(48.dp)) {
                        AppIcon(AppIconKind.SEARCH, UiMuted)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            when {
                bannersLoading && banners.isEmpty() -> item(key = "banner-loading") {
                    BannerSkeleton()
                    Spacer(Modifier.height(24.dp))
                }
                banners.isNotEmpty() -> item(key = "banner") {
                    BannerCarousel(banners = banners, onTap = onBannerTap)
                    Spacer(Modifier.height(24.dp))
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
                DiscoverSectionHeader(
                    title = "每日推荐",
                    actionLabel = "换一批",
                    actionBusy = dailyLoading && dailySongs.isNotEmpty(),
                    onAction = onRefreshDaily
                )
                if (dailyReason.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = dailyReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
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
                            SongCard(
                                song = song,
                                onPlay = { onPlayDaily(index) },
                                onOverflow = { cardSheetFor = song }
                            )
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
                Spacer(Modifier.height(24.dp))
                DiscoverSectionHeader(title = "猜你喜欢")
                Spacer(Modifier.height(8.dp))
            }
            when {
                guessLoading && guessSongs.isEmpty() -> item(key = "guess-loading") {
                    SearchSkeleton()
                }
                guessSongs.isNotEmpty() -> item(key = "guess-list") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            guessSongs,
                            key = { _, s -> "guess-" + s.sourceId + s.platform }
                        ) { index, song ->
                            SongCard(
                                song = song,
                                onPlay = { onPlayGuess(index) },
                                onOverflow = { cardSheetFor = song }
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
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
                DiscoverSectionHeader(title = "推荐歌单")
                Spacer(Modifier.height(8.dp))
            }
            when {
                hotLoading && hotPlaylists.isEmpty() -> item(key = "hot-loading") {
                    SearchSkeleton()
                }
                hotPlaylists.isNotEmpty() -> item(key = "hot-list") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            hotPlaylists,
                            key = { pl -> "hot-" + pl.id + pl.name }
                        ) { pl ->
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clickable { onPlaylistTap(pl) }
                            ) {
                                AsyncImage(
                                    model = pl.picUrl.ifBlank { null },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(140.dp)
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
    cardSheetFor?.let { target ->
        val dlKey = offlineBaseName(target)
        val faved = target.sourceId.isNotBlank() && target.sourceId in favIds
        SongMenuSheet(
            title = target.name.ifBlank { "(untitled)" },
            actions = listOf(
                SongMenuAction("fav", if (faved) "取消收藏" else "收藏"),
                SongMenuAction(
                    "download",
                    when {
                        dlKey in downloadingKeys -> "下载中…"
                        dlKey in downloadedKeys -> "已下载"
                        else -> "下载"
                    },
                    enabled = dlKey !in downloadingKeys
                ),
                SongMenuAction("add", "加入歌单")
            ),
            onAction = { id ->
                when (id) {
                    "fav" -> onToggleFav(target)
                    "download" -> onDownload(target)
                    "add" -> onAddToPlaylist(target)
                }
                cardSheetFor = null
            },
            onDismiss = { cardSheetFor = null }
        )
    }
}
