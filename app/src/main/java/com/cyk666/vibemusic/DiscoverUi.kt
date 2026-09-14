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
import androidx.compose.ui.text.font.FontWeight
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

@Composable
private fun HomeTabLabel(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) UiInk else UiMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) UiViolet else Color.Transparent)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeSongCard(
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
                model = backCoverUrl.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(10.dp))
                    .background(UiMuted.copy(alpha = 0.25f))
            )
            AsyncImage(
                model = song.coverUrl.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Crop,
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
                .background(UiViolet)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(kind = AppIconKind.PLAY, tint = Color.White, size = 20.dp)
        }
    }
}

@Composable
private fun EncounterRow(
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
            model = song.coverUrl.ifBlank { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
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
private fun TreasureCell(
    playlist: RecommendPlaylist,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onTap)
    ) {
        AsyncImage(
            model = playlist.picUrl.ifBlank { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
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
    onGoSearch: () -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    var cardSheetFor by remember { mutableStateOf<Song?>(null) }
    var homeTab by remember { mutableStateOf(0) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onPullRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item(key = "home-top") {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Music",
                        style = MaterialTheme.typography.titleLarge,
                        color = UiInk,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onOpenHistory, modifier = Modifier.size(48.dp)) {
                        AppIcon(AppIconKind.HISTORY, UiMuted)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeTabLabel(
                        text = "乐库",
                        selected = homeTab == 0,
                        onClick = { homeTab = 0 }
                    )
                    HomeTabLabel(
                        text = "商城",
                        selected = homeTab == 1,
                        onClick = { homeTab = 1 }
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(UiSurface)
                            .clickable(onClick = onGoSearch)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(AppIconKind.SEARCH, UiMuted, size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "搜索歌曲 / 歌手",
                            style = MaterialTheme.typography.bodySmall,
                            color = UiMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            item(key = "daily-head") {
                HomeSectionHeader(
                    title = "每日推荐",
                    enSubtitle = "DAILY",
                    enColor = UiViolet,
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
                            val back = dailySongs.getOrNull(index + 1)?.coverUrl.orEmpty()
                            HomeSongCard(
                                song = song,
                                backCoverUrl = back,
                                enLabel = "DAILY SONGS",
                                enColor = UiViolet,
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
                HomeSectionHeader(
                    title = "新歌速递",
                    enSubtitle = "NEW",
                    enColor = UiCyan
                )
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
                            val back = guessSongs.getOrNull(index + 1)?.coverUrl.orEmpty()
                            HomeSongCard(
                                song = song,
                                backCoverUrl = back,
                                enLabel = "NEW SONGS",
                                enColor = UiCyan,
                                onPlay = { onPlayGuess(index) },
                                onOverflow = { cardSheetFor = song }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
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
            item(key = "encounter-head") {
                Spacer(Modifier.height(16.dp))
                HomeSectionHeader(
                    title = "偶遇心动单曲",
                    enSubtitle = "ENCOUNTER",
                    enColor = UiPink
                )
                Spacer(Modifier.height(4.dp))
            }
            if (dailySongs.isNotEmpty()) {
                itemsIndexed(
                    dailySongs,
                    key = { _, s -> "encounter-" + s.sourceId + s.platform }
                ) { index, song ->
                    EncounterRow(
                        song = song,
                        faved = song.sourceId.isNotBlank() && song.sourceId in favIds,
                        onPlay = { onPlayDaily(index) },
                        onToggleFav = { onToggleFav(song) }
                    )
                }
            }
            item(key = "hot-head") {
                Spacer(Modifier.height(16.dp))
                HomeSectionHeader(
                    title = "宝藏歌单库",
                    enSubtitle = "PLAYLISTS",
                    enColor = UiGold
                )
                Spacer(Modifier.height(8.dp))
            }
            when {
                hotLoading && hotPlaylists.isEmpty() -> item(key = "hot-loading") {
                    SearchSkeleton()
                }
                hotPlaylists.isNotEmpty() -> {
                    hotPlaylists.chunked(2).forEachIndexed { rowIdx, pair ->
                        item(key = "treasure-row-$rowIdx") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEach { pl ->
                                    TreasureCell(
                                        playlist = pl,
                                        onTap = { onPlaylistTap(pl) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
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
