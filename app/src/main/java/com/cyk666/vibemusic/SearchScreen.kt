package com.cyk666.vibemusic

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.activity.SystemBarStyle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cyk666.vibemusic.MediaCache.toCachedMediaItem
import com.cyk666.vibemusic.OfflineStore.toLocalMediaItem
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun SearchSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(5) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(vertical = 4.dp)
            )
        }
    }
}

private val SEARCH_SORT_OPTIONS: List<Pair<SearchSort, String>> = listOf(
    SearchSort.RELEVANCE to "相关度",
    SearchSort.DURATION_ASC to "时长↑",
    SearchSort.DURATION_DESC to "时长↓",
    SearchSort.ARTIST_NAME to "歌手名"
)

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    loading: Boolean,
    searched: Boolean,
    results: List<Song>,
    total: Int,
    searchError: String? = null,
    onRetrySearch: () -> Unit = {},
    onPlayAt: (List<Song>, Int) -> Unit,
    // Search-result tap handler: single-enqueue after current index.
    // Null = legacy whole-list replace via onPlayAt.
    onPlaySingle: ((Song) -> Unit)? = null,
    artistFilter: String? = null,
    onArtistFilterChange: (String?) -> Unit = {},
    sort: SearchSort = SearchSort.RELEVANCE,
    onSortChange: (SearchSort) -> Unit = {},
    history: List<String> = emptyList(),
    onHistorySelect: (String) -> Unit = {},
    onHistoryDelete: (String) -> Unit = {},
    guessSongs: List<Song> = emptyList(),
    guessLoading: Boolean = false,
    guessError: String? = null,
    onRetryGuess: () -> Unit = {},
    onPlayGuessAt: (List<Song>, Int) -> Unit = { _, _ -> },
    onAddToPlaylist: (Song) -> Unit = {},
    onDownload: (Song) -> Unit = {},
    downloadingKeys: Set<String> = emptySet(),
    downloadedKeys: Set<String> = emptySet(),
    suggestions: List<Suggestion> = emptyList(),
    onSuggestionSelect: (Suggestion) -> Unit = {},
    favIds: Set<String> = emptySet(),
    onToggleFav: (Song) -> Unit = {}
) {
    val artists = remember(results) { distinctArtists(results) }
    val visible = remember(results, artistFilter, sort) {
        filterAndSortSongs(results, artistFilter, sort)
    }
    var songMenuFor by remember { mutableStateOf<Song?>(null) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("搜歌名、歌手") },
            leadingIcon = { AppIcon(AppIconKind.SEARCH, GrayMuted) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(AppIconKind.CLOSE, GrayMuted, size = 20.dp)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth()
        )
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                suggestions.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionSelect(s) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            kind = when (s.source) {
                                SuggestSource.HISTORY -> AppIconKind.HISTORY
                                SuggestSource.HOTWORD -> AppIconKind.TRENDING
                                SuggestSource.LIVE -> AppIconKind.MUSIC_NOTE
                            },
                            tint = GrayMuted,
                            size = 20.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = s.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = s.source.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayMuted
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (loading) {
            SearchSkeleton()
        } else if (query.isBlank()) {
            if (history.isNotEmpty()) {
                Text(
                    text = "搜索历史",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(history, key = { "h-$it" }) { h ->
                        InputChip(
                            selected = false,
                            onClick = { onHistorySelect(h) },
                            label = { Text(h) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            trailingIcon = {
                                // Documented nested-clickable fix: the trailing
                                // dismiss owns a bounded 48dp clickable that
                                // consumes the tap, so the chip onClick never
                                // fires on ×. M3 trailingIcon slot keeps a
                                // single ripple per target.
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable { onHistoryDelete(h) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIcon(AppIconKind.CLOSE, GrayMuted, size = 18.dp)
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = "热门搜索",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SEARCH_HOTWORDS, key = { "w-$it" }) { w ->
                    FilterChip(
                        selected = false,
                        onClick = { onHistorySelect(w) },
                        label = { Text(w) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "猜你喜欢",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            when {
                guessLoading && guessSongs.isEmpty() -> SearchSkeleton()
                guessSongs.isNotEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        guessSongs.forEachIndexed { index, song ->
                            SongRow(
                                model = buildSongRowModel(song),
                                meta = formatDuration(song.durationSec),
                                onClick = { onPlayGuessAt(guessSongs, index) },
                                onOverflow = { songMenuFor = song }
                            )
                        }
                    }
                }
                guessError != null -> DiscoverRetryRow(
                    message = guessError,
                    onRetry = onRetryGuess
                )
                else -> Text(
                    text = "暂无推荐，下拉刷新试试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (searched || searchError != null) {
            // Failed refresh with stale rows: error line above the results
            // instead of swapping to a blank retry page.
            if (searchError != null && results.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = searchError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = onRetrySearch) { Text("重试") }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (artists.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "artist-all") {
                        FilterChip(
                            selected = artistFilter == null,
                            onClick = { onArtistFilterChange(null) },
                            label = { Text("全部") }
                        )
                    }
                    items(artists, key = { "artist-$it" }) { a ->
                        FilterChip(
                            selected = artistFilter == a,
                            onClick = {
                                onArtistFilterChange(if (artistFilter == a) null else a)
                            },
                            label = { Text(a) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    SEARCH_SORT_OPTIONS,
                    key = { (s, _) -> "sort-${s.name}" }
                ) { (s, label) ->
                    FilterChip(
                        selected = sort == s,
                        onClick = { onSortChange(s) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "共 $total 首",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            if (visible.isEmpty()) {
                // Retry row only when nothing stale is on screen; stale rows
                // keep their filter-empty message under the error line above.
                val retryError = if (results.isEmpty()) searchError else null
                when (selectListState(loading = false, error = retryError, isEmpty = true)) {
                    ListState.ERROR -> DiscoverRetryRow(
                        message = searchError.orEmpty(),
                        onRetry = onRetrySearch
                    )
                    else -> if (results.isEmpty()) {
                        EmptyStateLine(text = "没有搜到，换个关键词试试")
                    } else {
                        EmptyStateLine(
                            text = "该歌手无结果",
                            actionLabel = "清除筛选",
                            onAction = { onArtistFilterChange(null) }
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(visible, key = { _, s -> s.sourceId + s.platform }) { index, song ->
                        SongRow(
                            model = buildSongRowModel(song),
                            meta = formatDuration(song.durationSec),
                            onClick = {
                                val single = onPlaySingle
                                if (single != null) single(song) else onPlayAt(visible, index)
                            },
                            onOverflow = { songMenuFor = song }
                        )
                    }
                }
            }
        }
    }
    songMenuFor?.let { target ->
        val dlKey = offlineBaseName(target)
        val downloading = dlKey in downloadingKeys
        val downloaded = dlKey in downloadedKeys
        val faved = target.sourceId.isNotBlank() && target.sourceId in favIds
        SongMenuSheet(
            title = target.name.ifBlank { "(untitled)" },
            actions = listOf(
                SongMenuAction("fav", if (faved) "取消收藏" else "收藏"),
                SongMenuAction(
                    "download",
                    when {
                        downloading -> "下载中…"
                        downloaded -> "已下载"
                        else -> "下载"
                    },
                    enabled = !downloading
                ),
                SongMenuAction("add", "加入歌单")
            ),
            onAction = { id ->
                when (id) {
                    "fav" -> onToggleFav(target)
                    "download" -> onDownload(target)
                    "add" -> onAddToPlaylist(target)
                }
                songMenuFor = null
            },
            onDismiss = { songMenuFor = null }
        )
    }
}
