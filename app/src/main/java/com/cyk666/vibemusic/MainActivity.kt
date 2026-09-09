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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import java.io.File

sealed interface Screen {
    data object Discover : Screen
    data object Search : Screen
    data object Player : Screen
    data object Queue : Screen
    data object Login : Screen
    data object Mine : Screen
    data object Offline : Screen
    data object History : Screen
    data object Settings : Screen
}

private val ObsidianBg = Color(0xFF0A0A0F)
private val ObsidianSurface = Color(0xFF14141C)
private val NeonViolet = Color(0xFF8B5CF6)
private val NeonCyan = Color(0xFF06B6D4)
private val Champagne = Color(0xFFF5E6C8)
private val InkOnDark = Color(0xFFEDEDF2)
private val GrayMuted = Color(0xFF9CA3AF)

private val ObsidianScheme = darkColorScheme(
    background = ObsidianBg,
    surface = ObsidianSurface,
    surfaceVariant = ObsidianSurface,
    primary = NeonViolet,
    secondary = NeonCyan,
    tertiary = Champagne,
    onBackground = InkOnDark,
    onSurface = InkOnDark,
    onSurfaceVariant = GrayMuted,
    onPrimary = InkOnDark
)

sealed interface LyricUiState {
    data object Loading : LyricUiState
    data class Ok(val lines: List<LyricLine>) : LyricUiState
    data object Failed : LyricUiState
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val snackbar = remember { SnackbarHostState() }
            // Media3 posts playback state to a notification; on SDK 33+ that needs
            // a runtime grant, requested once on launch.
            val notifPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            LaunchedEffect("notif-permission") {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } catch (_: Exception) {
                    }
                }
            }
            var controller by remember { mutableStateOf<MediaController?>(null) }
            var screen by remember { mutableStateOf<Screen>(Screen.Search) }
            // Phase 10: where the user came from before entering Player, so
            // swipe-down-close returns to the previous tab (default Search).
            var playerOrigin by remember { mutableStateOf<Screen>(Screen.Search) }
            // Phase 10: session-only collapse of the mini-player bar (X button).
            // UI state only — queue/playback untouched; reset on next playAt.
            var miniDismissed by remember { mutableStateOf(false) }
            var query by remember { mutableStateOf("予以") }
            var results by remember { mutableStateOf<List<Song>>(emptyList()) }
            var total by remember { mutableIntStateOf(0) }
            var loading by remember { mutableStateOf(false) }
            var searched by remember { mutableStateOf(false) }
            // Phase 10: last search failure for the error row (null = no error).
            var searchError by remember { mutableStateOf<String?>(null) }
            var searchJob by remember { mutableStateOf<Job?>(null) }
            var searchGen by remember { mutableIntStateOf(0) }
            // 500ms debounce auto-search job (cancelled + superseded on each keystroke).
            var debounceJob by remember { mutableStateOf<Job?>(null) }
            // Query text that produced `results` (generation-guarded); 联想 live
            // suggestions only use results while the input still equals this.
            var liveQuery by remember { mutableStateOf("") }
            var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }
            var searchSort by remember { mutableStateOf(SearchSort.RELEVANCE) }
            var artistFilter by remember { mutableStateOf<String?>(null) }
            var queue by remember { mutableStateOf<List<Song>>(emptyList()) }
            var currentIndex by remember { mutableIntStateOf(0) }
            // Raw controller timeline (durations unknown); durations resolve from
            // `queue` at row-build time via resolveQueueRowDisplay.
            var timelineSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
            var isPlaying by remember { mutableStateOf(false) }
            var positionMs by remember { mutableLongStateOf(0L) }
            var durationMs by remember { mutableLongStateOf(0L) }

            var playMode by remember { mutableStateOf(PlayMode.SEQUENTIAL) }
            // ---- on-device auto-cache (ExoPlayer CacheDataSource): replay/airplane
            // serves from cache; cacheTick forces badge/size refresh after a wipe.
            var cacheTick by remember { mutableIntStateOf(0) }
            var cachedBadge by remember { mutableStateOf(false) }
            var cacheSizeLabel by remember { mutableStateOf("计算中…") }
            // Phase 10: downloads dir usage for the Settings storage row.
            var downloadsSizeLabel by remember { mutableStateOf("计算中…") }
            var cacheBytes by remember { mutableLongStateOf(0L) }
            var downloadBytes by remember { mutableLongStateOf(0L) }
            // ---- user-driven offline downloads (filesDir/offline, never evicted) ----
            var downloadingIds by remember { mutableStateOf(setOf<String>()) }
            var downloadedTick by remember { mutableIntStateOf(0) }
            var downloadedKeys by remember { mutableStateOf(setOf<String>()) }
            var offlineItems by remember { mutableStateOf(listOf<OfflineMeta>()) }
            var offlineLoading by remember { mutableStateOf(false) }
            var offlineCount by remember { mutableIntStateOf(0) }
            var lastCountedKey by remember { mutableStateOf<String?>(null) }
            // Validate-before-delete strikes: per-song consecutive local-failure
            // count keyed by playKey; a local file is deleted only on strike 2.
            // Cleared when the local file plays fine, or when the file is
            // (re)downloaded or removed. redownloadedKeys bounds poison-delete
            // recovery to one background re-fetch per song (also the in-flight
            // guard); both are session memory and vanish on process death.
            var localFailCounts by remember { mutableStateOf(mapOf<String, Int>()) }
            var redownloadedKeys by remember { mutableStateOf(setOf<String>()) }
            // Throttled position persistence: at most one write per 10s.
            var lastSavedMs by remember { mutableLongStateOf(0L) }

            // ---- auth state ----
            var currentUser by remember { mutableStateOf<LoggedInUser?>(null) }
            var authChecked by remember { mutableStateOf(false) }
            var loginBusy by remember { mutableStateOf(false) }
            var loginInitialRegister by remember { mutableStateOf(false) }

            // ---- Phase 8: favorites + history ----
            var favIds by remember { mutableStateOf(setOf<String>()) }
            var favBusy by remember { mutableStateOf(setOf<String>()) }
            var historyItems by remember { mutableStateOf(listOf<VibeApi.HistoryItem>()) }
            var historyLoading by remember { mutableStateOf(false) }
            var lastReportedKey by remember { mutableStateOf<String?>(null) }
            var uploadTarget by remember { mutableStateOf<String?>(null) }

            // ---- mine state ----
            var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
            var playlistsLoading by remember { mutableStateOf(false) }
            var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
            var playlistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
            var songsLoading by remember { mutableStateOf(false) }

            // ---- discover state (memory only; 10-min TTL via discoverLastLoaded) ----
            var discoverBanners by remember { mutableStateOf(listOf<DiscoverBanner>()) }
            var discoverBannersLoading by remember { mutableStateOf(false) }
            var discoverBannersError by remember { mutableStateOf<String?>(null) }
            var dailySongs by remember { mutableStateOf(listOf<Song>()) }
            var dailyReason by remember { mutableStateOf("") }
            var dailyLoading by remember { mutableStateOf(false) }
            var dailyError by remember { mutableStateOf<String?>(null) }
            var guessSongs by remember { mutableStateOf(listOf<Song>()) }
            var guessLoading by remember { mutableStateOf(false) }
            var guessError by remember { mutableStateOf<String?>(null) }
            var hotPlaylists by remember { mutableStateOf(listOf<RecommendPlaylist>()) }
            var hotLoading by remember { mutableStateOf(false) }
            var hotError by remember { mutableStateOf<String?>(null) }
            var discoverLastLoaded by remember { mutableLongStateOf(0L) }
            var discoverRefreshing by remember { mutableStateOf(false) }
            var discoverInitialErrorShown by remember { mutableStateOf(false) }
            var recommendConfirm by remember { mutableStateOf<RecommendPlaylist?>(null) }

            // ---- self-update state (dialog only when a newer release is found) ----
            var updateRelease by remember { mutableStateOf<GithubRelease?>(null) }
            var updateBusy by remember { mutableStateOf(false) }
            var manualChecking by remember { mutableStateOf(false) }
            var appVersionName by remember { mutableStateOf("") }
            LaunchedEffect("app-version") {
                appVersionName = try {
                    @Suppress("DEPRECATION")
                    context.packageManager
                        .getPackageInfo(context.packageName, 0)
                        .versionName.orEmpty()
                } catch (_: Exception) {
                    ""
                }
            }

            // ---- sleep timer (activity-level: survives song change + screen switch) ----
            var sleepMinutes by remember { mutableIntStateOf(0) }
            var sleepLeftSec by remember { mutableLongStateOf(0L) }
            var showSleepDialog by remember { mutableStateOf(false) }
            fun setSleep(min: Int) {
                sleepMinutes = min.coerceAtLeast(0)
                scope.launch {
                    try {
                        QueueStore.saveSleepMinutes(context, sleepMinutes)
                    } catch (_: Exception) {
                    }
                }
            }
            val sleepLabel = if (sleepMinutes <= 0) "睡眠定时：关闭"
            else "睡眠定时：${sleepMinutes}分钟 (剩 ${formatDuration(sleepLeftSec.toInt())})"

            // ---- lyrics cache (activity-level, memory only, per sourceId) ----
            var lyricStates by remember { mutableStateOf(mapOf<String, LyricUiState>()) }
            val activeSong = queue.getOrNull(currentIndex)
            LaunchedEffect(activeSong?.sourceId) {
                val id = activeSong?.sourceId
                if (id.isNullOrBlank()) return@LaunchedEffect
                if (lyricStates.containsKey(id)) return@LaunchedEffect
                lyricStates = lyricStates + (id to LyricUiState.Loading)
                lyricStates = try {
                    lyricStates + (id to LyricUiState.Ok(VibeApi.lyric(id)))
                } catch (_: Exception) {
                    lyricStates + (id to LyricUiState.Failed)
                }
            }
            LaunchedEffect(sleepMinutes) {
                if (sleepMinutes <= 0) {
                    sleepLeftSec = 0L
                    return@LaunchedEffect
                }
                sleepLeftSec = sleepMinutes * 60L
                while (sleepLeftSec > 0) {
                    delay(1000)
                    sleepLeftSec--
                }
                try {
                    controller?.pause()
                } catch (_: Exception) {
                }
                sleepMinutes = 0
                scope.launch { snackbar.showSnackbar("已按定时暂停") }
            }

            fun showError(reason: String) {
                scope.launch { snackbar.showSnackbar(reason) }
            }

            fun runSearch(keyword: String) {
                val kw = keyword.trim()
                if (kw.isEmpty()) {
                    showError("Search failed: keyword is empty")
                    return
                }
                searchJob?.cancel()
                searchGen += 1
                val gen = searchGen
                loading = true
                searchError = null
                searchJob = scope.launch {
                    try {
                        val r = VibeApi.search(kw)
                        if (isStaleSearchResult(gen, searchGen)) return@launch
                        results = r.list
                        total = r.total
                        searched = true
                        searchError = null
                        liveQuery = kw
                        artistFilter = null
                        try {
                            searchHistory = SearchStore.addHistory(context, kw)
                        } catch (_: Exception) {
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (isStaleSearchResult(gen, searchGen)) return@launch
                        searchError = friendlyNetworkMessage(e)
                        showError("Search failed: ${friendlyNetworkMessage(e)}")
                    } finally {
                        if (!isStaleSearchResult(gen, searchGen)) loading = false
                    }
                }
            }

            // 联想 debounce: auto-search 500ms after the last keystroke, reusing
            // the same Job-cancel + generation-counter guards as manual search.
            // Blank input never fires; the IME Search action fires immediately.
            fun scheduleSearchDebounce(text: String) {
                debounceJob?.cancel()
                if (text.isBlank()) return
                val snapshot = text
                debounceJob = scope.launch {
                    delay(500)
                    runSearch(snapshot)
                    debounceJob = null
                }
            }

            fun restorePosition(target: Song) {
                if (target.sourceId.isBlank()) return
                scope.launch(Dispatchers.IO) {
                    val saved = try {
                        QueueStore.loadPosition(context, playKey(target))
                    } catch (_: Exception) {
                        0L
                    }
                    if (saved <= 0L) return@launch
                    withContext(Dispatchers.Main) {
                        val cc = controller ?: return@withContext
                        val dur = try {
                            cc.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                                ?: (target.durationSec * 1000L)
                        } catch (_: Exception) {
                            target.durationSec * 1000L
                        }
                        val start = resolveMaterializePosition(saved, dur)
                        if (start > 0L) {
                            try {
                                cc.seekTo(start)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }

            fun playAt(list: List<Song>, index: Int) {
                val c = controller
                if (c == null) {
                    showError("Playback failed: player not connected yet")
                    return
                }
                if (list.isEmpty()) {
                    showError("Playback failed: empty result list")
                    return
                }
                val safeIndex = index.coerceIn(list.indices)
                val target = list[safeIndex]
                if (!isLocalOrCachedPlayable(context, target)) {
                    showError("无网络且未缓存")
                    return
                }
                try {
                    queue = list
                    currentIndex = safeIndex
                    playerOrigin = if (screen is Screen.Player) playerOrigin else screen
                    miniDismissed = false
                    c.setMediaItems(
                        list.map { it.toPlayMediaItem(context) },
                        currentIndex,
                        0L
                    )
                    c.prepare()
                    c.play()
                    screen = Screen.Player
                    cacheTick += 1
                    restorePosition(target)
                    val snapshot = queue
                    val snapshotIndex = currentIndex
                    scope.launch {
                        try {
                            QueueStore.saveQueue(context, snapshot, snapshotIndex)
                        } catch (_: Exception) {
                        }
                    }
                } catch (e: Exception) {
                    showError("Playback failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }

            fun persistQueue() {
                val snapshot = queue
                val snapshotIndex = currentIndex
                scope.launch {
                    try {
                        QueueStore.saveQueue(context, snapshot, snapshotIndex)
                    } catch (_: Exception) {
                    }
                }
            }

            fun persistPosition(force: Boolean = false) {                val song = queue.getOrNull(currentIndex) ?: return
                if (song.sourceId.isBlank()) return
                val pos = try {
                    controller?.currentPosition ?: positionMs
                } catch (_: Exception) {
                    positionMs
                }
                if (pos <= 0L) return
                val now = System.currentTimeMillis()
                if (!force && !shouldSavePositionTick(now, lastSavedMs)) return
                lastSavedMs = now
                val key = playKey(song)
                scope.launch {
                    try {
                        QueueStore.savePosition(context, key, pos)
                    } catch (_: Exception) {
                    }
                }
            }

            // Cold-start lazy materialization: the Activity queue (restored from
            // DataStore) is the source of truth; a fresh service player after
            // process death has an empty timeline. Fill it on demand so
            // Play/Next/Prev/seek/queue-tap recover audibly. Returns true when
            // the timeline was (re)built — caller then proceeds with its own
            // play/seek action. Saved position restores async via
            // restorePosition; callers that seek explicitly pass restoreSaved=false
            // to avoid a stale async seek overwriting their target.
            fun ensureTimeline(c: MediaController, restoreSaved: Boolean = true): Boolean {
                val count = try {
                    c.mediaItemCount
                } catch (_: Exception) {
                    0
                }
                if (!needsMaterialize(count, queue.size)) return false
                if (queue.isEmpty()) return false
                return try {
                    val idx = currentIndex.coerceIn(queue.indices)
                    c.setMediaItems(queue.map { it.toPlayMediaItem(context) }, idx, 0L)
                    c.prepare()
                    if (restoreSaved) queue.getOrNull(idx)?.let { restorePosition(it) }
                    true
                } catch (_: Exception) {
                    false
                }
            }

            fun togglePlayPause() {
                val c = controller
                if (c == null) {
                    showError("Playback failed: player not connected yet")
                    return
                }
                try {
                    ensureTimeline(c)
                    if (c.playbackState == Player.STATE_IDLE) c.prepare()
                    if (c.isPlaying) c.pause() else c.play()
                } catch (e: Exception) {
                    showError("Playback failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }

            fun swapToStreamAndPlay(idx: Int, song: Song) {                try {
                    val cc = controller ?: return
                    val streamItem = song.toCachedMediaItem()
                    if (idx in 0 until cc.mediaItemCount) {
                        cc.replaceMediaItem(idx, streamItem)
                    } else {
                        cc.setMediaItem(streamItem)
                    }
                    cc.prepare()
                    cc.play()
                } catch (_: Exception) {
                }
            }

            fun redownloadSong(song: Song) {
                if (song.sourceId.isBlank()) return
                val key = playKey(song)
                if (key in redownloadedKeys) return
                if (!isNetworkAvailable(context)) return
                val already = try {
                    OfflineStore.isDownloaded(context, song)
                } catch (_: Exception) {
                    false
                }
                if (already) return
                redownloadedKeys = redownloadedKeys + key
                scope.launch(Dispatchers.IO) {
                    try {
                        val bytes = VibeApi.fetchStreamBytes(song.streamUrl())
                        val ok = OfflineStore.saveBytes(
                            context,
                            song,
                            bytes,
                            System.currentTimeMillis()
                        )
                        withContext(Dispatchers.Main) {
                            if (ok) {
                                downloadedTick += 1
                                if (key in localFailCounts) {
                                    localFailCounts = localFailCounts - key
                                }
                                showError("已重新下载「${song.name}」")
                            } else {
                                showError("下载失败：保存失败")
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            showError("下载失败：${friendlyNetworkMessage(e)}")
                        }
                    }
                }
            }

            fun cyclePlayMode() {
                val next = playMode.next()
                val c = controller
                try {
                    if (c != null) {
                        val rs = next.toRepeatShuffle()
                        c.repeatMode = rs.repeatMode
                        c.shuffleModeEnabled = rs.shuffleOn
                    }
                    playMode = next
                    scope.launch {
                        try {
                            val rs = next.toRepeatShuffle()
                            QueueStore.savePlayMode(context, rs.repeatMode, rs.shuffleOn)
                        } catch (_: Exception) {
                        }
                    }
                } catch (e: Exception) {
                    showError("切换模式失败: ${e.message ?: e.javaClass.simpleName}")
                }
            }

            fun syncQueueFromController() {
                val c = controller ?: return
                try {
                    if (c.mediaItemCount == 0) {
                        queue = emptyList()
                        timelineSongs = emptyList()
                        currentIndex = 0
                        return
                    }
                    val tl = (0 until c.mediaItemCount).map {
                        songFromMediaItem(c.getMediaItemAt(it))
                    }
                    timelineSongs = tl
                    queue = mergeQueueDurations(queue, tl)
                    currentIndex = c.currentMediaItemIndex.coerceIn(0, c.mediaItemCount - 1)
                } catch (_: Exception) {
                }
            }

            fun queueSeekTo(index: Int, skipPlayableCheck: Boolean = false) {
                val c = controller
                if (c == null) {
                    showError("Playback failed: player not connected yet")
                    return
                }
                try {
                    ensureTimeline(c, restoreSaved = false)
                    if (c.mediaItemCount == 0) return
                    val safeIndex = index.coerceIn(0, c.mediaItemCount - 1)
                    val target = queue.getOrNull(safeIndex)
                    if (!skipPlayableCheck && target != null &&
                        !isLocalOrCachedPlayable(context, target)
                    ) {
                        showError("无网络且未缓存")
                        return
                    }
                    c.seekTo(safeIndex, 0L)
                    if (c.playbackState == Player.STATE_IDLE) c.prepare()
                    c.play()
                    currentIndex = c.currentMediaItemIndex
                    queue.getOrNull(currentIndex)?.let { restorePosition(it) }
                } catch (e: Exception) {
                    showError("切歌失败: ${e.message ?: e.javaClass.simpleName}")
                }
            }

            fun queueRemoveAt(index: Int) {
                val c = controller
                if (c == null) {
                    showError("Playback failed: player not connected yet")
                    return
                }
                val countNow = try {
                    c.mediaItemCount
                } catch (_: Exception) {
                    0
                }
                if (!needsMaterialize(countNow, queue.size) && countNow <= 1) {
                    showError("至少保留一首")
                    return
                }
                try {
                    ensureTimeline(c, restoreSaved = false)
                    val count = c.mediaItemCount
                    val removedIdx = index.coerceIn(0, count - 1)
                    val curBefore = c.currentMediaItemIndex
                    val wasCurrent = removedPlayingItem(removedIdx, curBefore)
                    c.removeMediaItem(removedIdx)
                    syncQueueFromController()
                    if (wasCurrent && c.mediaItemCount > 0) {
                        // Fix: removing the playing item idled silently. Advance to the
                        // item that slid into the slot (the next song) and keep playing.
                        val nextIdx = nextIndexAfterRemove(count, removedIdx, curBefore)
                            .coerceIn(0, c.mediaItemCount - 1)
                        c.seekTo(nextIdx, 0L)
                        if (c.playbackState == Player.STATE_IDLE) c.prepare()
                        c.play()
                        currentIndex = c.currentMediaItemIndex
                    }
                    persistQueue()
                } catch (e: Exception) {
                    showError("删除失败: ${e.message ?: e.javaClass.simpleName}")
                    syncQueueFromController()
                }
            }

            fun queueClearKeepCurrent() {
                val c = controller
                if (c == null) {
                    showError("Playback failed: player not connected yet")
                    return
                }
                val countBefore = try {
                    c.mediaItemCount
                } catch (_: Exception) {
                    0
                }
                if (!needsMaterialize(countBefore, queue.size) && countBefore <= 1) {
                    showError("至少保留一首")
                    return
                }
                try {
                    ensureTimeline(c, restoreSaved = false)
                    val count = try {
                        c.mediaItemCount
                    } catch (_: Exception) {
                        0
                    }
                    if (count <= 1) {
                        showError("至少保留一首")
                        return
                    }
                    val cur = c.currentMediaItemIndex.coerceIn(0, count - 1)
                    if (cur < count - 1) c.removeMediaItems(cur + 1, count)
                    if (cur > 0) c.removeMediaItems(0, cur)
                    syncQueueFromController()
                    persistQueue()
                    showError("已清空，仅保留当前播放")
                } catch (e: Exception) {
                    showError("清空失败: ${e.message ?: e.javaClass.simpleName}")
                    syncQueueFromController()
                }
            }

            fun clearMediaCache() {
                scope.launch(Dispatchers.IO) {
                    try {
                        MediaCache.clear(context)
                    } catch (_: Exception) {
                    }
                    cacheTick += 1
                    withContext(Dispatchers.Main) {
                        showError("已清理缓存")
                    }
                }
            }

            fun downloadSong(song: Song, silent: Boolean) {
                val key = offlineBaseName(song)
                if (key in downloadingIds) return
                val already = try {
                    OfflineStore.isDownloaded(context, song)
                } catch (_: Exception) {
                    false
                }
                if (already) {
                    if (!silent) showError("已下载")
                    return
                }
                if (!silent) downloadingIds = downloadingIds + key
                scope.launch(Dispatchers.IO) {
                    try {
                        if (OfflineStore.isDownloaded(context, song)) {
                            withContext(Dispatchers.Main) {
                                downloadingIds = downloadingIds - key
                                if (!silent) showError("已下载")
                            }
                            return@launch
                        }
                        val bytes = VibeApi.fetchStreamBytes(song.streamUrl())
                        val ok = OfflineStore.saveBytes(
                            context,
                            song,
                            bytes,
                            System.currentTimeMillis()
                        )
                        withContext(Dispatchers.Main) {
                            downloadingIds = downloadingIds - key
                            if (ok) {
                                downloadedTick += 1
                                val healedKey = playKey(song)
                                if (healedKey in localFailCounts) {
                                    localFailCounts = localFailCounts - healedKey
                                }
                                if (healedKey in redownloadedKeys) {
                                    redownloadedKeys = redownloadedKeys - healedKey
                                }
                                showError(
                                    if (silent) "常听歌曲已自动离线「${song.name}」"
                                    else "已下载，可离线播放"
                                )
                            } else if (!silent) {
                                showError("下载失败：保存失败")
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            downloadingIds = downloadingIds - key
                            if (!silent) {
                                showError("下载失败：${friendlyNetworkMessage(e)}")
                            }
                        }
                    }
                }
            }

            fun maybeCountPlay(song: Song?) {
                if (song == null || song.sourceId.isBlank()) return
                val key = playKey(song)
                if (key == lastCountedKey) return
                lastCountedKey = key
                scope.launch(Dispatchers.IO) {
                    val count = try {
                        QueueStore.incrementPlayCount(context, key)
                    } catch (_: Exception) {
                        0
                    }
                    if (!shouldAutoSave(count)) return@launch
                    if (!isNetworkAvailable(context)) return@launch
                    val already = try {
                        OfflineStore.isDownloaded(context, song)
                    } catch (_: Exception) {
                        false
                    }
                    if (already) return@launch
                    try {
                        val bytes = VibeApi.fetchStreamBytes(song.streamUrl())
                        val ok = OfflineStore.saveBytes(
                            context,
                            song,
                            bytes,
                            System.currentTimeMillis()
                        )
                        if (ok) {
                            withContext(Dispatchers.Main) {
                                downloadedTick += 1
                                showError("常听歌曲已自动离线「${song.name}」")
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            fun deleteOffline(meta: OfflineMeta) {
                scope.launch(Dispatchers.IO) {
                    val ok = try {
                        OfflineStore.delete(context, meta)
                    } catch (_: Exception) {
                        false
                    }
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            downloadedTick += 1
                            val goneKey = playKey(meta.platform, meta.sourceId)
                            if (goneKey in localFailCounts) {
                                localFailCounts = localFailCounts - goneKey
                            }
                            if (goneKey in redownloadedKeys) {
                                redownloadedKeys = redownloadedKeys - goneKey
                            }
                            showError("已删除本地《${meta.name}》")
                        } else {
                            showError("删除失败")
                        }
                    }
                }
            }

            fun loadPlaylists() {
                if (playlistsLoading) return
                if (currentUser == null) return
                playlistsLoading = true
                scope.launch {
                    try {
                        playlists = VibeApi.myPlaylists()
                    } catch (e: AuthException) {
                        showError(e.message ?: "密码错/登录过期，请重登")
                        scope.launch {
                            try {
                                AuthStore.clear(context)
                            } catch (_: Exception) {
                            }
                        }
                        currentUser = null
                        playlists = emptyList()
                    } catch (e: Exception) {
                        showError("Playlists failed: ${friendlyNetworkMessage(e)}")
                    } finally {
                        playlistsLoading = false
                    }
                }
            }

            fun loadSongs(pl: Playlist) {
                selectedPlaylist = pl
                playlistSongs = emptyList()
                songsLoading = true
                scope.launch {
                    try {
                        playlistSongs = VibeApi.playlistSongs(pl.id)
                    } catch (e: AuthException) {
                        showError(e.message ?: "密码错/登录过期，请重登")
                        scope.launch {
                            try {
                                AuthStore.clear(context)
                            } catch (_: Exception) {
                            }
                        }
                        currentUser = null
                    } catch (e: Exception) {
                        showError("Playlist songs failed: ${friendlyNetworkMessage(e)}")
                    } finally {
                        songsLoading = false
                    }
                }
            }

            // ---- discover loaders (memory only; one Snackbar max on first load) ----
            fun noteDiscoverInitialFailure(e: Exception) {
                if (!discoverInitialErrorShown) {
                    discoverInitialErrorShown = true
                    showError("发现页加载失败: ${friendlyNetworkMessage(e)}")
                }
            }

            fun loadDiscoverBanners(markLoading: Boolean = true, onDone: () -> Unit = {}) {
                if (markLoading) discoverBannersLoading = true
                discoverBannersError = null
                scope.launch {
                    try {
                        discoverBanners = VibeApi.discoverBanners()
                        discoverBannersError = null
                    } catch (e: Exception) {
                        discoverBannersError = friendlyNetworkMessage(e)
                        noteDiscoverInitialFailure(e)
                    } finally {
                        discoverBannersLoading = false
                        onDone()
                    }
                }
            }

            fun loadDaily(refresh: Boolean = false, markLoading: Boolean = true, onDone: () -> Unit = {}) {
                if (markLoading) dailyLoading = true
                dailyError = null
                scope.launch {
                    try {
                        val r = VibeApi.personalized(refresh)
                        dailySongs = r.songs
                        dailyReason = r.reason
                        dailyError = null
                    } catch (e: Exception) {
                        dailyError = friendlyNetworkMessage(e)
                        noteDiscoverInitialFailure(e)
                    } finally {
                        dailyLoading = false
                        onDone()
                    }
                }
            }

            fun loadGuess(markLoading: Boolean = true, onDone: () -> Unit = {}) {
                if (markLoading) guessLoading = true
                guessError = null
                scope.launch {
                    try {
                        guessSongs = VibeApi.randomSongs(8)
                        guessError = null
                    } catch (e: Exception) {
                        guessError = friendlyNetworkMessage(e)
                        noteDiscoverInitialFailure(e)
                    } finally {
                        guessLoading = false
                        onDone()
                    }
                }
            }

            fun loadHot(markLoading: Boolean = true, onDone: () -> Unit = {}) {
                if (markLoading) hotLoading = true
                hotError = null
                scope.launch {
                    try {
                        hotPlaylists = VibeApi.recommendPlaylists()
                        hotError = null
                    } catch (e: Exception) {
                        hotError = friendlyNetworkMessage(e)
                        noteDiscoverInitialFailure(e)
                    } finally {
                        hotLoading = false
                        onDone()
                    }
                }
            }

            fun loadDiscover(isPullRefresh: Boolean = false) {
                val silent = isPullRefresh
                if (silent) discoverRefreshing = true
                var pending = 4
                fun oneDone() {
                    pending -= 1
                    if (pending > 0) return
                    if (silent) discoverRefreshing = false
                    if (discoverBanners.isNotEmpty() || dailySongs.isNotEmpty() ||
                        guessSongs.isNotEmpty() || hotPlaylists.isNotEmpty()
                    ) {
                        discoverLastLoaded = System.currentTimeMillis()
                    }
                }
                loadDiscoverBanners(markLoading = !silent) { oneDone() }
                loadDaily(markLoading = !silent) { oneDone() }
                loadGuess(markLoading = !silent) { oneDone() }
                loadHot(markLoading = !silent) { oneDone() }
            }

            fun handleAuthLost(msg: String?) {                showError(msg ?: "密码错/登录过期，请重登")
                scope.launch {
                    try {
                        AuthStore.clear(context)
                    } catch (_: Exception) {
                    }
                }
                currentUser = null
                playlists = emptyList()
                favIds = emptySet()
                historyItems = emptyList()
                lastReportedKey = null
            }

            fun loadFavIds() {
                if (currentUser == null) return
                scope.launch {
                    try {
                        favIds = VibeApi.favIds()
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (_: Exception) {
                    }
                }
            }

            fun loadHistory() {
                if (currentUser == null) return
                if (historyLoading) return
                historyLoading = true
                scope.launch {
                    try {
                        historyItems = VibeApi.history(20)
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("历史加载失败: ${friendlyNetworkMessage(e)}")
                    } finally {
                        historyLoading = false
                    }
                }
            }

            fun toggleFav(song: Song) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                if (song.sourceId.isBlank()) return
                if (song.sourceId in favBusy) return
                favBusy = favBusy + song.sourceId
                scope.launch {
                    try {
                        val faved = VibeApi.toggleFavorite(song, VibeApi.newRequestId())
                        favIds = if (faved) favIds + song.sourceId else favIds - song.sourceId
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("收藏失败: ${friendlyNetworkMessage(e)}")
                    } finally {
                        favBusy = favBusy - song.sourceId
                    }
                }
            }

            fun maybeReportPlay(song: Song?) {
                if (song == null || song.sourceId.isBlank()) return
                if (currentUser == null) return
                val key = playKey(song)
                if (!VibeApi.shouldReportPlay(true, key, lastReportedKey)) return
                lastReportedKey = key
                scope.launch(Dispatchers.IO) {
                    try {
                        VibeApi.reportPlay(song)
                    } catch (_: Exception) {
                    }
                    try {
                        withContext(Dispatchers.Main) {
                            if (currentUser != null) loadHistory()
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            var pendingAddSong by remember { mutableStateOf<Song?>(null) }
            var addBusy by remember { mutableStateOf(false) }

            fun openAddSheet(song: Song) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                if (playlists.isEmpty() && !playlistsLoading) loadPlaylists()
                pendingAddSong = song
            }

            fun createPlaylistAction(name: String, description: String) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                scope.launch {
                    try {
                        val r = VibeApi.createPlaylist(name, description)
                        showError(if (r.duplicate) "歌单已存在，已打开现有歌单" else "新建歌单成功")
                        loadPlaylists()
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("新建歌单失败: ${friendlyNetworkMessage(e)}")
                    }
                }
            }

            fun renamePlaylistAction(pl: Playlist, name: String) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                scope.launch {
                    try {
                        VibeApi.updatePlaylist(pl.id, name = name)
                        showError("重命名成功")
                        loadPlaylists()
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("重命名失败: ${friendlyNetworkMessage(e)}")
                    }
                }
            }

            fun updateDescAction(pl: Playlist, description: String) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                scope.launch {
                    try {
                        VibeApi.updatePlaylist(pl.id, description = description)
                        showError("简介更新成功")
                        loadPlaylists()
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("更新简介失败: ${friendlyNetworkMessage(e)}")
                    }
                }
            }

            fun deletePlaylistAction(pl: Playlist) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                scope.launch {
                    try {
                        VibeApi.deletePlaylist(pl.id)
                        if (selectedPlaylist?.id == pl.id) {
                            selectedPlaylist = null
                            playlistSongs = emptyList()
                        }
                        showError("已删除「${pl.name}」")
                        loadPlaylists()
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("删除失败: ${friendlyNetworkMessage(e)}")
                    }
                }
            }

            fun batchDeleteAction(ids: List<String>) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                if (ids.isEmpty()) return
                scope.launch {
                    try {
                        val n = VibeApi.deletePlaylists(ids)
                        if (selectedPlaylist?.id in ids) {
                            selectedPlaylist = null
                            playlistSongs = emptyList()
                        }
                        showError("已删除 $n 个歌单")
                        loadPlaylists()
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("批量删除失败: ${friendlyNetworkMessage(e)}")
                    }
                }
            }

            fun movePlaylistAction(pl: Playlist, dir: Int) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                val idx = playlists.indexOfFirst { it.id == pl.id }
                val swap = idx + dir
                if (idx < 0 || swap !in playlists.indices) return
                val swapped = playlists.toMutableList().apply {
                    val tmp = this[idx]
                    this[idx] = this[swap]
                    this[swap] = tmp
                }
                playlists = swapped
                scope.launch {
                    try {
                        VibeApi.reorderPlaylists(swapped.map { it.id })
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                        loadPlaylists()
                    } catch (e: Exception) {
                        showError("移动失败: ${friendlyNetworkMessage(e)}")
                        loadPlaylists()
                    }
                }
            }

            fun removeSongAction(song: Song) {
                val pl = selectedPlaylist
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                if (pl == null) return
                scope.launch {
                    try {
                        VibeApi.removeSongFromPlaylist(pl.id, song.sourceId)
                        showError("已从歌单删除《${song.name}》")
                        loadSongs(pl)
                        loadPlaylists()
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("删除歌曲失败: ${friendlyNetworkMessage(e)}")
                    }
                }
            }

            fun importAction(source: String, id: String) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                scope.launch {
                    try {
                        val r = VibeApi.importPlaylist(source, id)
                        showError("成功导入${r.imported}/${r.total}首「${r.name}」")
                        loadPlaylists()
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("导入失败: ${friendlyNetworkMessage(e)}")
                    }
                }
            }

            fun tryInstallUpdate(apk: File) {
                try {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        showError("请允许安装未知应用后，再点立即更新重试")
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        } catch (e: Exception) {
                            showError("无法打开安装权限设置: ${friendlyNetworkMessage(e)}")
                        }
                        return
                    }
                    val uri = FileProvider.getUriForFile(
                        context,
                        context.packageName + ".fileprovider",
                        apk
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    updateRelease = null
                } catch (e: Exception) {
                    showError("安装失败: ${friendlyNetworkMessage(e)}")
                }
            }

            fun startUpdateDownload(rel: GithubRelease) {
                if (updateBusy) return
                updateBusy = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                        val safeTag = rel.tag.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        val apk = File(dir, "vibemusic-$safeTag.apk")
                        if (!apk.exists() || apk.length() <= 0L) {
                            downloadApk(rel.apkUrl, apk)
                        }
                        withContext(Dispatchers.Main) {
                            updateBusy = false
                            tryInstallUpdate(apk)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            updateBusy = false
                            showError("更新失败: ${friendlyNetworkMessage(e)}")
                        }
                    }
                }
            }

            fun runManualUpdateCheck() {
                if (manualChecking) return
                manualChecking = true
                scope.launch {
                    try {
                        val now = System.currentTimeMillis()
                        try {
                            QueueStore.saveLastUpdateCheck(context, now)
                        } catch (_: Exception) {
                        }
                        val rel = try {
                            fetchLatestRelease()
                        } catch (e: Exception) {
                            showError(friendlyNetworkMessage(e))
                            return@launch
                        }
                        val current = appVersionName.ifBlank {
                            try {
                                @Suppress("DEPRECATION")
                                context.packageManager
                                    .getPackageInfo(context.packageName, 0)
                                    .versionName.orEmpty()
                            } catch (_: Exception) {
                                ""
                            }
                        }
                        when (compareAndDecide(current, rel.tag, fetchOk = true)) {
                            UpdateDecision.UPDATE_AVAILABLE -> updateRelease = rel
                            UpdateDecision.UP_TO_DATE -> showError("已是最新版本")
                            UpdateDecision.CHECK_FAILED -> Unit
                        }
                    } finally {
                        manualChecking = false
                    }
                }
            }

            fun addSongAction(pl: Playlist, song: Song) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                if (addBusy) return
                addBusy = true
                scope.launch {
                    try {
                        val added = VibeApi.addSongToPlaylist(pl.id, song)
                        showError(
                            if (added) "已加入「${pl.name}」"
                            else "《${song.name}》已在「${pl.name}」中"
                        )
                        pendingAddSong = null
                        loadPlaylists()
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("加入歌单失败: ${friendlyNetworkMessage(e)}")
                    } finally {
                        addBusy = false
                    }
                }
            }

            fun createAndAddAction(name: String, song: Song) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                if (addBusy) return
                addBusy = true
                scope.launch {
                    try {
                        val r = VibeApi.createPlaylist(name, "")
                        val added = VibeApi.addSongToPlaylist(r.id, song)
                        showError(
                            if (added) "已新建「${r.name}」并加入"
                            else "《${song.name}》已在「${r.name}」中"
                        )
                        pendingAddSong = null
                        loadPlaylists()
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("新建并加入失败: ${friendlyNetworkMessage(e)}")
                    } finally {
                        addBusy = false
                    }
                }
            }

            fun doLogin(username: String, password: String) {
                if (loginBusy) return
                if (username.isBlank() || password.isBlank()) {
                    showError("Login failed: username and password required")
                    return
                }
                loginBusy = true
                scope.launch {
                    try {
                        val r = VibeApi.login(username.trim(), password)
                        AuthStore.save(context, r.token, r.user.username, r.user.nickname, r.refreshToken)
                        currentUser = r.user
                        selectedPlaylist = null
                        playlistSongs = emptyList()
                        screen = Screen.Mine
                        loadPlaylists()
                        loadFavIds()
                        loadHistory()
                    } catch (e: AuthException) {
                        showError(e.message ?: "密码错/登录过期，请重登")
                    } catch (e: Exception) {
                        showError("Login failed: ${friendlyNetworkMessage(e)}")
                    } finally {
                        loginBusy = false
                    }
                }
            }

            fun doRegister(username: String, password: String, nickname: String) {
                if (loginBusy) return
                if (username.isBlank() || password.isBlank()) {
                    showError("Register failed: username and password required")
                    return
                }
                if (password.length < 8) {
                    showError("Register failed: 密码至少8位")
                    return
                }
                loginBusy = true
                scope.launch {
                    try {
                        val r = VibeApi.register(
                            username.trim(),
                            password,
                            nickname.trim().ifBlank { null }
                        )
                        AuthStore.save(context, r.token, r.user.username, r.user.nickname, r.refreshToken)
                        currentUser = r.user
                        selectedPlaylist = null
                        playlistSongs = emptyList()
                        screen = Screen.Mine
                        showError("注册成功，已自动登录")
                        loadPlaylists()
                        loadFavIds()
                        loadHistory()
                    } catch (e: AuthException) {
                        showError(e.message ?: "密码错/登录过期，请重登")
                    } catch (e: Exception) {
                        showError("Register failed: ${friendlyNetworkMessage(e)}")
                    } finally {
                        loginBusy = false
                    }
                }
            }

            fun doChangePassword(oldPassword: String, newPassword: String) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                if (newPassword.length < 8) {
                    showError("新密码至少8位")
                    return
                }
                scope.launch {
                    try {
                        VibeApi.changePassword(oldPassword, newPassword)
                        showError("密码修改成功")
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("改密失败: ${friendlyNetworkMessage(e)}")
                    }
                }
            }

            fun doUpdateProfile(nickname: String?, gender: String?, birthday: String?) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                scope.launch {
                    try {
                        currentUser = VibeApi.updateProfile(nickname, gender, birthday)
                        try {
                            val snap = AuthStore.load(context)
                            AuthStore.save(
                                context,
                                snap.token,
                                snap.username,
                                currentUser?.nickname.orEmpty(),
                                snap.refreshToken
                            )
                        } catch (_: Exception) {
                        }
                        showError("资料已更新")
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("改资料失败: ${friendlyNetworkMessage(e)}")
                    }
                }
            }

            fun doUploadImage(bytes: ByteArray, filename: String, mime: String, bg: Boolean) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                scope.launch(Dispatchers.IO) {
                    try {
                        val res = if (bg) {
                            VibeApi.uploadBgImage(bytes, filename, mime)
                        } else {
                            VibeApi.uploadAvatar(bytes, filename, mime)
                        }
                        val refreshed = try {
                            VibeApi.me()
                        } catch (_: Exception) {
                            res.user
                        }
                        withContext(Dispatchers.Main) {
                            if (refreshed != null) currentUser = refreshed
                            showError(if (bg) "背景已更新" else "头像已更新")
                        }
                    } catch (e: AuthException) {
                        withContext(Dispatchers.Main) {
                            handleAuthLost(e.message)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            showError("上传失败: ${friendlyNetworkMessage(e)}")
                        }
                    }
                }
            }

            fun doRemoveHistory(sourceIds: List<String>, clearAll: Boolean) {
                if (currentUser == null) {
                    screen = Screen.Login
                    return
                }
                if (sourceIds.isEmpty()) return
                scope.launch {
                    try {
                        VibeApi.removeHistory(sourceIds)
                        historyItems = historyItems.filterNot { it.song.sourceId in sourceIds.toSet() }
                        showError(if (clearAll) "已清空播放历史" else "已删除")
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("删除历史失败: ${friendlyNetworkMessage(e)}")
                    }
                }
            }

            fun doLogout() {
                scope.launch {
                    try {
                        VibeApi.logoutBestEffort()
                    } catch (_: Exception) {
                    }
                    try {
                        AuthStore.clear(context)
                    } catch (e: Exception) {
                        showError("Logout failed: ${e.message ?: e.javaClass.simpleName}")
                        return@launch
                    }
                    currentUser = null
                    playlists = emptyList()
                    selectedPlaylist = null
                    playlistSongs = emptyList()
                    favIds = emptySet()
                    favBusy = emptySet()
                    historyItems = emptyList()
                    lastReportedKey = null
                    screen = Screen.Mine
                }
            }

            val photoPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                val target = uploadTarget
                uploadTarget = null
                if (uri == null || target == null) return@rememberLauncherForActivityResult
                scope.launch(Dispatchers.IO) {
                    try {
                        val cr = context.contentResolver
                        val mime = try {
                            cr.getType(uri)
                        } catch (_: Exception) {
                            null
                        }.orEmpty().ifBlank { "image/jpeg" }
                        val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw RuntimeException("无法读取图片")
                        if (bytes.size > 2 * 1024 * 1024) {
                            throw RuntimeException("图片不能超过 2MB")
                        }
                        val ext = when {
                            mime.contains("png") -> "png"
                            mime.contains("webp") -> "webp"
                            mime.contains("gif") -> "gif"
                            else -> "jpg"
                        }
                        withContext(Dispatchers.Main) {
                            doUploadImage(bytes, "upload.$ext", mime, bg = target == "bg")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            showError("读取图片失败: ${friendlyNetworkMessage(e)}")
                        }
                    }
                }
            }

            DisposableEffect(context) {
                val sessionToken = SessionToken(
                    context,
                    ComponentName(context, PlaybackService::class.java)
                )
                val future = MediaController.Builder(context, sessionToken).buildAsync()
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        if (playing) {
                            try {
                                if (controller?.currentMediaItem?.mediaId.orEmpty()
                                        .startsWith("local:")
                                ) {
                                    val s = try {
                                        controller?.currentMediaItem?.let {
                                            songFromMediaItem(it)
                                        }
                                    } catch (_: Exception) {
                                        null
                                    }
                                    if (s != null && s.sourceId.isNotBlank()) {
                                        val k = playKey(s)
                                        if (k in localFailCounts) {
                                            localFailCounts = localFailCounts - k
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        } else {
                            persistPosition(force = true)
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val c = controller ?: return
                        if (queue.isEmpty() && c.mediaItemCount > 0) {
                            val rebuilt =
                                (0 until c.mediaItemCount).map { songFromMediaItem(c.getMediaItemAt(it)) }
                            timelineSongs = rebuilt
                            if (rebuilt.isNotEmpty()) {
                                queue = rebuilt
                                currentIndex = c.currentMediaItemIndex.coerceIn(rebuilt.indices)
                            } else {
                                currentIndex = c.currentMediaItemIndex
                            }
                        } else {
                            currentIndex = c.currentMediaItemIndex
                        }
                        positionMs = c.currentPosition
                        durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                        if (queue.isNotEmpty()) {
                            val snapshot = queue
                            val snapshotIndex = currentIndex
                            scope.launch {
                                try {
                                    QueueStore.saveQueue(context, snapshot, snapshotIndex)
                                } catch (_: Exception) {
                                }
                            }
                        }
                        if (c.playbackState == Player.STATE_READY || c.isPlaying) {
                            try {
                                val started = queue.getOrNull(currentIndex)
                                    ?: songFromMediaItem(c.currentMediaItem!!)
                                maybeCountPlay(started)
                                maybeReportPlay(started)
                            } catch (_: Exception) {
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state != Player.STATE_READY) return
                        val c = controller ?: return
                        try {
                            val started = queue.getOrNull(c.currentMediaItemIndex)
                                ?: c.currentMediaItem?.let { songFromMediaItem(it) }
                            maybeCountPlay(started)
                            maybeReportPlay(started)
                        } catch (_: Exception) {
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val c = controller
                        val mediaId = try {
                            c?.currentMediaItem?.mediaId.orEmpty()
                        } catch (_: Exception) {
                            ""
                        }
                        val online = isNetworkAvailable(context)
                        when (localErrorDecision(mediaId, online)) {
                            LocalErrorAction.DELETE_AND_STREAM -> {
                                val song = try {
                                    c?.currentMediaItem?.let { songFromMediaItem(it) }
                                } catch (_: Exception) {
                                    null
                                }
                                if (song == null || song.sourceId.isBlank()) {
                                    showError("播不了，已跳过 (${error.errorCodeName})")
                                    return
                                }
                                val key = playKey(song)
                                val fails = (localFailCounts[key] ?: 0) + 1
                                if (fails > LOCAL_HEAL_QUIET_AFTER) {
                                    showError("本地文件已损坏，联网后重新下载")
                                    return
                                }
                                val idx = try {
                                    c?.currentMediaItemIndex ?: 0
                                } catch (_: Exception) {
                                    0
                                }
                                val fileValid = try {
                                    OfflineStore.isAudioFileIntact(context, song)
                                } catch (_: Exception) {
                                    false
                                }
                                when (healAction(fileValid, fails, online)) {
                                    HealAction.KEEP_STREAM_ONCE -> {
                                        localFailCounts = localFailCounts + (key to fails)
                                        swapToStreamAndPlay(idx, song)
                                        showError(
                                            if (fileValid) "本次在线播放"
                                            else "本地播放异常，已转在线；下次还坏再删"
                                        )
                                    }
                                    HealAction.DELETE_AND_STREAM -> {
                                        localFailCounts = localFailCounts + (key to fails)
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                OfflineStore.delete(
                                                    context,
                                                    OfflineMeta.of(song, 0L)
                                                )
                                            } catch (_: Exception) {
                                            }
                                            withContext(Dispatchers.Main) {
                                                downloadedTick += 1
                                                swapToStreamAndPlay(idx, song)
                                                redownloadSong(song)
                                            }
                                        }
                                        showError("本地文件已损坏已删除，转在线播放")
                                    }
                                    HealAction.ERROR_MESSAGE -> {
                                        showError("本地文件已损坏，联网后重新下载")
                                    }
                                }
                            }
                            LocalErrorAction.ERROR_MESSAGE -> {
                                showError("本地文件已损坏，联网后重新下载")
                            }
                            LocalErrorAction.SKIP -> {
                                // Offline: never die on the first hole — advance only to
                                // the next offline-playable queue item (valid download
                                // or cached bytes, helpers already in use elsewhere),
                                // wrapping once per repeat-all at most; else stop.
                                // Online falls through to the existing path untouched.
                                if (c != null && !isNetworkAvailable(context)) {
                                    val q = queue
                                    val from = try {
                                        c.currentMediaItemIndex
                                    } catch (_: Exception) {
                                        currentIndex
                                    }
                                    val repeatAll = try {
                                        c.repeatMode == Player.REPEAT_MODE_ALL
                                    } catch (_: Exception) {
                                        playMode == PlayMode.LIST_LOOP ||
                                            playMode == PlayMode.SHUFFLE
                                    }
                                    val target = selectNextOfflineIndex(
                                        q,
                                        from,
                                        isPlayable = { idx ->
                                            val s = q.getOrNull(idx)
                                            if (s == null || s.sourceId.isBlank()) false
                                            else try {
                                                OfflineStore.isAudioFileIntact(context, s) ||
                                                    MediaCache.cachedBytes(
                                                        context,
                                                        s.streamUrl()
                                                    ) > 0
                                            } catch (_: Exception) {
                                                false
                                            }
                                        },
                                        repeatAll = repeatAll
                                    )
                                    if (target >= 0) {
                                        queueSeekTo(target, skipPlayableCheck = true)
                                    } else {
                                        try {
                                            c.stop()
                                        } catch (_: Exception) {
                                        }
                                        showError("离线可播已播完")
                                    }
                                    return
                                }
                                val title = try {
                                    c?.currentMediaItem?.mediaMetadata?.title
                                } catch (_: Exception) {
                                    null
                                }
                                // Offline + partial cache: CacheDataSource served cached bytes then
                                // hit a hole the unreachable upstream couldn't fill.
                                val partialOffline = try {
                                    val song = queue.getOrNull(currentIndex)
                                    !isNetworkAvailable(context) &&
                                        song != null &&
                                        MediaCache.cachedBytes(context, song.streamUrl()) > 0
                                } catch (_: Exception) {
                                    false
                                }
                                if (partialOffline) {
                                    showError("该歌曲未缓存完整，请联网后完整播一次")
                                } else {
                                    showError(
                                        "《${title ?: "unknown"}》播不了，已跳过 " +
                                            "(${error.errorCodeName})"
                                    )
                                }
                            }
                        }
                    }
                }
                future.addListener(
                    {
                        try {
                            val c = future.get()
                            c.addListener(listener)
                            isPlaying = c.isPlaying
                            if (queue.isEmpty() && c.mediaItemCount > 0) {
                                val rebuilt =
                                    (0 until c.mediaItemCount).map { songFromMediaItem(c.getMediaItemAt(it)) }
                                timelineSongs = rebuilt
                                if (rebuilt.isNotEmpty()) {
                                    queue = rebuilt
                                    currentIndex = c.currentMediaItemIndex.coerceIn(rebuilt.indices)
                                } else {
                                    currentIndex = c.currentMediaItemIndex
                                }
                            } else {
                                currentIndex = c.currentMediaItemIndex
                            }
                            controller = c
                            scope.launch {
                                try {
                                    val rs = QueueStore.loadPlayMode(context)
                                    try {
                                        c.repeatMode = rs.repeatMode
                                        c.shuffleModeEnabled = rs.shuffleOn
                                    } catch (_: Exception) {
                                    }
                                    playMode = playModeFrom(rs.repeatMode, rs.shuffleOn)
                                } catch (_: Exception) {
                                }
                            }
                        } catch (e: Exception) {
                            showError(
                                "Player connect failed: ${e.message ?: e.javaClass.simpleName}"
                            )
                        }
                    },
                    MoreExecutors.directExecutor()
                )
                onDispose {
                    try {
                        persistPosition(force = true)
                    } catch (_: Exception) {
                    }
                    if (future.isDone) {
                        try {
                            future.get().let {
                                it.removeListener(listener)
                                it.release()
                            }
                        } catch (_: Exception) {
                        }
                    } else {
                        future.cancel(true)
                    }
                }
            }

            LaunchedEffect(Unit) {
                VibeApi.init(context)
                try {
                    sleepMinutes = QueueStore.loadSleepMinutes(context)
                } catch (_: Exception) {
                }
                try {
                    searchSort = SearchStore.loadSort(context)
                } catch (_: Exception) {
                }
                try {
                    searchHistory = SearchStore.loadHistory(context)
                } catch (_: Exception) {
                }
                runSearch(query)
                // Self-update: once per cold start + 24h throttle; silent unless newer.
                scope.launch {
                    try {
                        val now = System.currentTimeMillis()
                        val last = try {
                            QueueStore.loadLastUpdateCheck(context)
                        } catch (_: Exception) {
                            0L
                        }
                        if (!shouldRunUpdateCheck(now, last, force = false)) return@launch
                        try {
                            QueueStore.saveLastUpdateCheck(context, now)
                        } catch (_: Exception) {
                        }
                        val rel = try {
                            fetchLatestRelease()
                        } catch (_: Exception) {
                            return@launch
                        }
                        val current = try {
                            @Suppress("DEPRECATION")
                            context.packageManager
                                .getPackageInfo(context.packageName, 0)
                                .versionName.orEmpty()
                        } catch (_: Exception) {
                            ""
                        }
                        when (compareAndDecide(current, rel.tag, fetchOk = true)) {
                            UpdateDecision.UPDATE_AVAILABLE -> updateRelease = rel
                            else -> Unit
                        }
                    } catch (_: Exception) {
                    }
                }
                // Restore last queue (no autoplay: user taps to play).
                try {
                    val (saved, savedIndex) = QueueStore.loadQueue(context)
                    if (queue.isEmpty() && saved.isNotEmpty()) {
                        val c = controller
                        if (c == null || c.mediaItemCount == 0) {
                            queue = saved
                            currentIndex = savedIndex.coerceIn(saved.indices)
                        }
                    }
                } catch (_: Exception) {
                }
                // Token restore + validate via /api/auth/me.
                // First-run gate: blank token + never launched -> Login screen
                // (guest stays an explicit 先逛逛 choice, not the default).
                try {
                    val snap = AuthStore.load(context)
                    val launchedBefore = try {
                        QueueStore.loadHasLaunchedBefore(context)
                    } catch (_: Exception) {
                        false
                    }
                    when (decideStartRoute(snap.token.isBlank(), launchedBefore)) {
                        StartRoute.LOGIN -> screen = Screen.Login
                        StartRoute.SEARCH_GUEST -> screen = Screen.Search
                        StartRoute.RESTORE -> Unit
                    }
                    if (snap.token.isNotBlank()) {
                        try {
                            val restored = VibeApi.me()
                            if (restored != null) {
                                currentUser = restored
                                loadFavIds()
                                loadHistory()
                            } else {
                                // Guest-null: transient guest response, keep stored
                                // token; only 401/AuthException owns clearing.
                                currentUser = null
                            }
                        } catch (e: AuthException) {
                            try {
                                AuthStore.clear(context)
                            } catch (_: Exception) {
                            }
                            currentUser = null
                            showError(e.message ?: "密码错/登录过期，请重登")
                        } catch (e: Exception) {
                            // Network or server issue: keep token, stay guest for now.
                            currentUser = null
                            showError("Auth restore failed: ${e.message ?: e.javaClass.simpleName}")
                        }
                    }
                    try {
                        QueueStore.saveHasLaunchedBefore(context)
                    } catch (_: Exception) {
                    }
                } catch (e: Exception) {
                    showError("Auth restore failed: ${e.message ?: e.javaClass.simpleName}")
                } finally {
                    authChecked = true
                }
            }

            // Startup download audit (legacy poison from the pre-1.0.9 era still
            // sits on devices): once on launch, IO dispatcher, silent unless
            // findings. Never touches the rolling cache.
            LaunchedEffect("startup-download-audit") {
                val removed = withContext(Dispatchers.IO) {
                    try {
                        OfflineStore.auditInvalid(context)
                    } catch (_: Exception) {
                        0
                    }
                }
                if (removed > 0) {
                    downloadedTick += 1
                    showError("清理${removed}个损坏的下载")
                }
            }

            // Auto-load playlists when entering Mine while logged in.
            LaunchedEffect(screen, currentUser, authChecked) {
                if (screen is Screen.Mine && currentUser != null && playlists.isEmpty() && !playlistsLoading) {
                    loadPlaylists()
                }
                if (screen is Screen.Mine && currentUser != null && historyItems.isEmpty() && !historyLoading) {
                    loadHistory()
                }
                if (screen is Screen.History && currentUser != null && historyItems.isEmpty() && !historyLoading) {
                    loadHistory()
                }
            }

            // Discover auto-load on first visit; revisit reloads only when stale (>10min).
            LaunchedEffect(screen) {
                if (screen is Screen.Discover &&
                    isDiscoverStale(discoverLastLoaded, System.currentTimeMillis()) &&
                    !discoverBannersLoading && !dailyLoading && !guessLoading && !hotLoading &&
                    !discoverRefreshing
                ) {
                    loadDiscover()
                }
            }

            // 已缓存 badge + 缓存大小：song change / wipe 后在 IO 线程重算。
            LaunchedEffect(activeSong?.sourceId, cacheTick) {
                val url = activeSong?.streamUrl().orEmpty()
                cachedBadge = withContext(Dispatchers.IO) {
                    try {
                        MediaCache.isCached(context, url)
                    } catch (_: Exception) {
                        false
                    }
                }
            }

            LaunchedEffect(screen, cacheTick) {
                if (screen is Screen.Mine || screen is Screen.Settings) {
                    val bytes = withContext(Dispatchers.IO) {
                        try {
                            MediaCache.sizeBytes(context)
                        } catch (_: Exception) {
                            0L
                        }
                    }
                    cacheBytes = bytes
                    cacheSizeLabel = formatStorageMb(bytes)
                    val dl = withContext(Dispatchers.IO) {
                        try {
                            dirAudioBytes(OfflineStore.dir(context))
                        } catch (_: Exception) {
                            0L
                        }
                    }
                    downloadBytes = dl
                    downloadsSizeLabel = formatStorageMb(dl)
                }
            }

            LaunchedEffect(results, playlistSongs, downloadedTick) {
                downloadedKeys = withContext(Dispatchers.IO) {
                    try {
                        (results + playlistSongs)
                            .filter { OfflineStore.isDownloaded(context, it) }
                            .map { offlineBaseName(it) }
                            .toSet()
                    } catch (_: Exception) {
                        emptySet()
                    }
                }
            }

            LaunchedEffect(screen, downloadedTick) {
                if (screen is Screen.Mine || screen is Screen.Offline) {
                    offlineLoading = true
                    val items = withContext(Dispatchers.IO) {
                        try {
                            OfflineStore.listDownloads(context)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                    offlineItems = items
                    offlineCount = items.size
                    offlineLoading = false
                }
            }

            LaunchedEffect(screen, controller) {
                if (screen is Screen.Player || screen is Screen.Queue) {
                    while (true) {
                        controller?.let { c ->
                            try {
                                positionMs = c.currentPosition
                                durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                                if (c.isPlaying) persistPosition()
                            } catch (_: Exception) {
                            }
                        }
                        delay(500)
                    }
                }
            }

            val selectedTab: Int = when (screen) {
                is Screen.Discover -> 0
                is Screen.Search -> 1
                is Screen.Player, is Screen.Queue -> 2
                is Screen.Mine, is Screen.Login, is Screen.Offline, is Screen.History,
                is Screen.Settings -> 3
            }

            MaterialTheme(colorScheme = ObsidianScheme) {
                Scaffold(
                    containerColor = ObsidianBg,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        Column {
                            if (queue.isNotEmpty() && screen !is Screen.Player && !miniDismissed) {
                                MiniPlayerBar(
                                    song = queue.getOrNull(currentIndex),
                                    isPlaying = isPlaying,
                                    onTap = {
                                        playerOrigin =
                                            if (screen is Screen.Player) playerOrigin else screen
                                        screen = Screen.Player
                                    },
                                    onPlayPause = { togglePlayPause() },
                                    onDismiss = { miniDismissed = true }
                                )
                            }
                            NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { screen = Screen.Discover },
                                label = { Text("发现") },
                                icon = { Text("✨") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { screen = Screen.Search },
                                label = { Text("搜索") },
                                icon = { Text("🔍") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { screen = Screen.Player },
                                label = { Text("播放") },
                                icon = { Text("▶") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { screen = Screen.Mine },
                                label = { Text("我的") },
                                icon = { Text("👤") }
                            )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.fillMaxSize(), color = ObsidianBg) {
                        when (screen) {
                            is Screen.Discover -> DiscoverScreen(
                                modifier = Modifier.padding(innerPadding),
                                banners = discoverBanners,
                                bannersLoading = discoverBannersLoading,
                                bannersError = discoverBannersError,
                                onRetryBanners = { loadDiscoverBanners() },
                                onBannerTap = { b ->
                                    val name = b.name.trim()
                                    if (name.isNotEmpty()) {
                                        debounceJob?.cancel()
                                        query = name
                                        runSearch(name)
                                        screen = Screen.Search
                                    }
                                },
                                dailySongs = dailySongs,
                                dailyReason = dailyReason,
                                dailyLoading = dailyLoading,
                                dailyError = dailyError,
                                onRetryDaily = { loadDaily() },
                                onRefreshDaily = { loadDaily(refresh = true) },
                                onPlayDaily = { idx ->
                                    if (dailySongs.isNotEmpty()) {
                                        playAt(dailySongs, idx.coerceIn(dailySongs.indices))
                                    }
                                },
                                guessSongs = guessSongs,
                                guessLoading = guessLoading,
                                guessError = guessError,
                                onRetryGuess = { loadGuess() },
                                onPlayGuess = { idx ->
                                    if (guessSongs.isNotEmpty()) {
                                        playAt(guessSongs, idx.coerceIn(guessSongs.indices))
                                    }
                                },
                                hotPlaylists = hotPlaylists,
                                hotLoading = hotLoading,
                                hotError = hotError,
                                onRetryHot = { loadHot() },
                                onPlaylistTap = { pl ->
                                    if (currentUser == null) {
                                        screen = Screen.Login
                                    } else {
                                        recommendConfirm = pl
                                    }
                                },
                                refreshing = discoverRefreshing,
                                onPullRefresh = { loadDiscover(isPullRefresh = true) }
                            )

                            is Screen.Search -> SearchScreen(
                                modifier = Modifier.padding(innerPadding),
                                query = query,
                                onQueryChange = {
                                    query = it
                                    scheduleSearchDebounce(it)
                                },
                                onSearch = {
                                    debounceJob?.cancel()
                                    runSearch(query)
                                },
                                loading = loading,
                                searched = searched,
                                results = results,
                                total = total,
                                searchError = searchError,
                                onRetrySearch = {
                                    debounceJob?.cancel()
                                    if (query.trim().isNotEmpty()) runSearch(query)
                                },
                                onPlayAt = ::playAt,
                                artistFilter = artistFilter,
                                onArtistFilterChange = { artistFilter = it },
                                sort = searchSort,
                                onSortChange = { s ->
                                    searchSort = s
                                    scope.launch {
                                        try {
                                            SearchStore.saveSort(context, s)
                                        } catch (_: Exception) {
                                        }
                                    }
                                },
                                history = searchHistory,
                                onHistorySelect = { h ->
                                    debounceJob?.cancel()
                                    query = h
                                    runSearch(h)
                                },
                                onHistoryDelete = { h ->
                                    scope.launch {
                                        try {
                                            searchHistory = SearchStore.removeHistory(context, h)
                                        } catch (_: Exception) {
                                        }
                                    }
                                },
                                onAddToPlaylist = ::openAddSheet,
                                onDownload = { song -> downloadSong(song, false) },
                                downloadingKeys = downloadingIds,
                                downloadedKeys = downloadedKeys,
                                favIds = favIds,
                                onToggleFav = ::toggleFav,
                                suggestions = buildSuggestions(
                                    searchHistory,
                                    SEARCH_HOTWORDS,
                                    if (query.trim().isNotBlank() &&
                                        query.trim() == liveQuery
                                    ) {
                                        results
                                    } else {
                                        emptyList()
                                    },
                                    query
                                ),
                                onSuggestionSelect = { s ->
                                    debounceJob?.cancel()
                                    query = s.text
                                    runSearch(s.text)
                                }
                            )

                            is Screen.Player -> PlayerScreen(
                                modifier = Modifier.padding(innerPadding),
                                queue = queue,
                                currentIndex = currentIndex,
                                isPlaying = isPlaying,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                lyricState = lyricStates[activeSong?.sourceId],
                                sleepLabel = sleepLabel,
                                onSleepClick = { showSleepDialog = true },
                                onPlayPause = {
                                    togglePlayPause()
                                },
                                onNext = {
                                    val c = controller
                                    if (c == null) {
                                        showError("Playback failed: player not connected yet")
                                    } else {
                                        try {
                                            val materialized = ensureTimeline(c, restoreSaved = false)
                                            if (c.playbackState == Player.STATE_IDLE) c.prepare()
                                            if (c.hasNextMediaItem()) c.seekToNextMediaItem()
                                            else c.seekTo(0L)
                                            if (materialized) c.play()
                                        } catch (e: Exception) {
                                            showError(
                                                "Next failed: " +
                                                    "${e.message ?: e.javaClass.simpleName}"
                                            )
                                        }
                                    }
                                },
                                onPrev = {
                                    val c = controller
                                    if (c == null) {
                                        showError("Playback failed: player not connected yet")
                                    } else {
                                        try {
                                            val materialized = ensureTimeline(c, restoreSaved = false)
                                            if (c.playbackState == Player.STATE_IDLE) c.prepare()
                                            if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem()
                                            else c.seekTo(0L)
                                            if (materialized) c.play()
                                        } catch (e: Exception) {
                                            showError(
                                                "Previous failed: " +
                                                    "${e.message ?: e.javaClass.simpleName}"
                                            )
                                        }
                                    }
                                },
                                onSeek = { targetMs ->
                                    try {
                                        val c = controller
                                        if (c == null) {
                                            showError("Seek failed: player not connected yet")
                                        } else {
                                            ensureTimeline(c, restoreSaved = false)
                                            c.seekTo(targetMs)
                                        }
                                    } catch (e: Exception) {
                                        showError(
                                            "Seek failed: ${e.message ?: e.javaClass.simpleName}"
                                        )
                                    }
                                },
                                onClose = {
                                    screen =
                                        if (playerOrigin is Screen.Player) Screen.Search
                                        else playerOrigin
                                },
                                onAddCurrentToPlaylist = {
                                    queue.getOrNull(currentIndex)?.let(::openAddSheet)
                                },
                                onOpenQueue = { screen = Screen.Queue },
                                modeLabel = playMode.label,
                                onCycleMode = ::cyclePlayMode,
                                isCached = cachedBadge,
                                onDownloadCurrent = {
                                    queue.getOrNull(currentIndex)?.let { downloadSong(it, false) }
                                },
                                downloadingCurrent = queue.getOrNull(currentIndex)?.let {
                                    offlineBaseName(it) in downloadingIds
                                } ?: false,
                                downloadedCurrent = queue.getOrNull(currentIndex)?.let {
                                    offlineBaseName(it) in downloadedKeys
                                } ?: false,
                                isFav = queue.getOrNull(currentIndex)?.let {
                                    it.sourceId.isNotBlank() && it.sourceId in favIds
                                } ?: false,
                                onToggleFav = {
                                    queue.getOrNull(currentIndex)?.let(::toggleFav)
                                }
                            )

                            is Screen.Queue -> QueueScreen(
                                modifier = Modifier.padding(innerPadding),
                                rows = buildQueueRowDisplays(
                                    queue,
                                    timelineSongs.ifEmpty { queue },
                                    currentIndex,
                                    positionMs,
                                    durationMs
                                ),
                                modeLabel = playMode.label,
                                onPlayAt = ::queueSeekTo,
                                onRemove = ::queueRemoveAt,
                                onClear = ::queueClearKeepCurrent,
                                onBack = { screen = Screen.Player },
                                onGoSearch = { screen = Screen.Search }
                            )

                            is Screen.Login -> LoginScreen(
                                modifier = Modifier.padding(innerPadding),
                                busy = loginBusy,
                                initialRegister = loginInitialRegister,
                                onLogin = ::doLogin,
                                onRegister = ::doRegister,
                                onBrowseAsGuest = { screen = Screen.Search },
                                onBack = { screen = Screen.Mine }
                            )

                            is Screen.Mine -> MineScreen(
                                modifier = Modifier.padding(innerPadding),
                                user = currentUser,
                                authChecked = authChecked,
                                playlists = playlists,
                                playlistsLoading = playlistsLoading,
                                selectedPlaylist = selectedPlaylist,
                                songs = playlistSongs,
                                songsLoading = songsLoading,
                                onLoginClick = {
                                    loginInitialRegister = false
                                    screen = Screen.Login
                                },
                                onRegisterClick = {
                                    loginInitialRegister = true
                                    screen = Screen.Login
                                },
                                onLogout = ::doLogout,
                                onRetryPlaylists = { loadPlaylists() },
                                onSelectPlaylist = ::loadSongs,
                                onBackToPlaylists = {
                                    selectedPlaylist = null
                                    playlistSongs = emptyList()
                                },
                                onPlaySong = { idx -> playAt(playlistSongs, idx) },
                                onCreatePlaylist = ::createPlaylistAction,
                                onRenamePlaylist = ::renamePlaylistAction,
                                onUpdateDescription = ::updateDescAction,
                                onDeletePlaylist = ::deletePlaylistAction,
                                onBatchDelete = ::batchDeleteAction,
                                onMovePlaylist = ::movePlaylistAction,
                                onImportPlaylist = ::importAction,
                                onRemoveSong = ::removeSongAction,
                                onAddSongToPlaylist = ::openAddSheet,
                                cacheSizeLabel = cacheSizeLabel,
                                onClearCache = ::clearMediaCache,
                                versionLabel = formatVersionLabel(appVersionName),
                                checkingUpdate = manualChecking,
                                onCheckUpdate = ::runManualUpdateCheck,
                                offlineCount = offlineCount,
                                onOpenOffline = { screen = Screen.Offline },
                                favIds = favIds,
                                onToggleFav = ::toggleFav,
                                historyCount = historyItems.size,
                                onOpenHistory = { screen = Screen.History },
                                onChangePassword = ::doChangePassword,
                                onUpdateProfile = ::doUpdateProfile,
                                onPickAvatar = {
                                    uploadTarget = "avatar"
                                    try {
                                        photoPicker.launch("image/*")
                                    } catch (e: Exception) {
                                        uploadTarget = null
                                        showError("打开相册失败: ${e.message ?: e.javaClass.simpleName}")
                                    }
                                },
                                onPickBg = {
                                    uploadTarget = "bg"
                                    try {
                                        photoPicker.launch("image/*")
                                    } catch (e: Exception) {
                                        uploadTarget = null
                                        showError("打开相册失败: ${e.message ?: e.javaClass.simpleName}")
                                    }
                                },
                                onGoSearch = { screen = Screen.Search },
                                onOpenSettings = { screen = Screen.Settings }
                            )

                            is Screen.History -> HistoryScreen(
                                modifier = Modifier.padding(innerPadding),
                                items = historyItems,
                                loading = historyLoading,
                                favIds = favIds,
                                onPlayAt = { idx ->
                                    val songs = historyItems.map { it.song }
                                    if (songs.isNotEmpty()) playAt(songs, idx.coerceIn(songs.indices))
                                },
                                onToggleFav = ::toggleFav,
                                onDeleteOne = { item ->
                                    doRemoveHistory(listOf(item.song.sourceId), clearAll = false)
                                },
                                onClearAll = {
                                    doRemoveHistory(historyItems.map { it.song.sourceId }, clearAll = true)
                                },
                                onRetry = { loadHistory() },
                                onBack = { screen = Screen.Mine }
                            )

                            is Screen.Settings -> SettingsScreen(
                                modifier = Modifier.padding(innerPadding),
                                cacheSizeLabel = cacheSizeLabel,
                                storageLabel = storageTotalLabel(cacheBytes, downloadBytes),
                                versionLabel = formatVersionLabel(appVersionName),
                                checkingUpdate = manualChecking,
                                onCheckUpdate = ::runManualUpdateCheck,
                                onClearCache = ::clearMediaCache,
                                onBack = { screen = Screen.Mine }
                            )

                            is Screen.Offline -> OfflineScreen(
                                modifier = Modifier.padding(innerPadding),
                                items = offlineItems,
                                loading = offlineLoading,
                                onPlayAt = { idx ->
                                    val songs = offlineItems.map { it.toSong() }
                                    if (songs.isNotEmpty()) playAt(songs, idx.coerceIn(songs.indices))
                                },
                                onDelete = ::deleteOffline,
                                onBack = { screen = Screen.Mine },
                                onGoSearch = { screen = Screen.Search }
                            )
                        }
                    }
                    if (showSleepDialog) {
                        SleepTimerDialog(
                            current = sleepMinutes,
                            onConfirm = { min ->
                                showSleepDialog = false
                                setSleep(min)
                            },
                            onInvalid = { showError("请输入 5~180 分钟") },
                            onDismiss = { showSleepDialog = false }
                        )
                    }
                    pendingAddSong?.let { target ->
                        AddToPlaylistSheet(
                            song = target,
                            playlists = playlists,
                            busy = addBusy,
                            onPick = { pl -> addSongAction(pl, target) },
                            onCreateAndAdd = { name -> createAndAddAction(name, target) },
                            onDismiss = { if (!addBusy) pendingAddSong = null }
                        )
                    }
                    recommendConfirm?.let { pl ->
                        RecommendImportDialog(
                            playlist = pl,
                            onConfirm = {
                                recommendConfirm = null
                                importAction("netease", pl.id)
                                screen = Screen.Mine
                            },
                            onDismiss = { recommendConfirm = null }
                        )
                    }
                    updateRelease?.let { rel ->
                        UpdateDialog(
                            release = rel,
                            busy = updateBusy,
                            onUpdate = { startUpdateDownload(rel) },
                            onLater = { if (!updateBusy) updateRelease = null }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cold-start transport guard (pure): after process death the Activity restores
 * the queue LIST from DataStore but the fresh service player has an EMPTY
 * timeline (mediaItemCount == 0). Play/Next/Prev then operate on silence.
 * Materialize the player timeline from the Activity queue on demand.
 */
fun needsMaterialize(mediaItemCount: Int, queueSize: Int): Boolean =
    mediaItemCount == 0 && queueSize > 0

/**
 * Start position for (re)materialization (pure): with a known duration defer
 * to the standard mid-track restore window; duration unknown pre-prepare
 * (controller TIME_UNSET + no metadata) → restore when past the 5s intro and
 * let the player clamp naturally at the real duration.
 */
fun resolveMaterializePosition(savedMs: Long, durationMs: Long): Long {
    if (durationMs > 0L) return if (shouldRestorePosition(savedMs, durationMs)) savedMs else 0L
    return if (savedMs > POSITION_RESTORE_MIN_MS) savedMs else 0L
}

fun songFromMediaItem(mi: MediaItem): Song {
    val md = mi.mediaMetadata
    val raw = mi.mediaId.orEmpty()
    val extrasPlat = md.extras?.getString("platform")
    var sid = raw
    var plat: String? = extrasPlat
    if (raw.startsWith("local:")) {
        val rest = raw.removePrefix("local:")
        val idx = rest.indexOf(':')
        if (idx >= 0) {
            plat = rest.substring(0, idx).ifBlank { extrasPlat }
            sid = rest.substring(idx + 1)
        } else {
            sid = rest
        }
    }
    return Song(
        sourceId = sid,
        name = (md.title?.toString().orEmpty()),
        artist = (md.artist?.toString().orEmpty()),
        album = (md.albumTitle?.toString().orEmpty()),
        coverUrl = md.artworkUri?.toString().orEmpty(),
        durationSec = 0,
        platform = plat.ifNullOrBlankDefault()
    )
}

fun Song.toPlayMediaItem(context: android.content.Context): MediaItem {
    return try {
        val file = OfflineStore.audioFile(context, this)
        val source = selectPlaySource(
            OfflineStore.isDownloaded(context, this),
            file.exists(),
            try {
                file.length()
            } catch (_: Exception) {
                0L
            }
        )
        if (source == PlaySource.LOCAL) toLocalMediaItem(context)
        else toCachedMediaItem()
    } catch (_: Exception) {
        toCachedMediaItem()
    }
}

fun isLocalOrCachedPlayable(context: android.content.Context, song: Song): Boolean {
    try {
        if (OfflineStore.isDownloaded(context, song)) return true
    } catch (_: Exception) {
    }
    val cached = try {
        MediaCache.cachedBytes(context, song.streamUrl())
    } catch (_: Exception) {
        0L
    }
    return offlinePlayDecision(isNetworkAvailable(context), cached) !=
        OfflineDecision.BLOCK_WITH_MESSAGE
}

private fun String?.ifNullOrBlankDefault(default: String = "netease"): String =
    if (this.isNullOrBlank()) default else this

const val SLEEP_CUSTOM_MIN = 5
const val SLEEP_CUSTOM_MAX = 180
val SLEEP_PRESETS = listOf(15, 30, 60)

fun parseSleepMinutes(input: String): Int? {
    val v = input.trim().toIntOrNull() ?: return null
    return if (v in SLEEP_CUSTOM_MIN..SLEEP_CUSTOM_MAX) v else null
}

@Composable
fun SearchSkeleton() {
    val transition = rememberInfiniteTransition(label = "searchSkeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonPulse"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(vertical = 4.dp)
                    .alpha(pulse)
                    .background(
                        GrayMuted.copy(alpha = 0.35f),
                        RoundedCornerShape(8.dp)
                    )
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
    artistFilter: String? = null,
    onArtistFilterChange: (String?) -> Unit = {},
    sort: SearchSort = SearchSort.RELEVANCE,
    onSortChange: (SearchSort) -> Unit = {},
    history: List<String> = emptyList(),
    onHistorySelect: (String) -> Unit = {},
    onHistoryDelete: (String) -> Unit = {},
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
    var songMenuFor by remember { mutableStateOf<String?>(null) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search songs") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSearch) {
                Text("Go")
            }
        }
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
                        Text(
                            text = when (s.source) {
                                SuggestSource.HISTORY -> "🕘"
                                SuggestSource.HOTWORD -> "🔥"
                                SuggestSource.LIVE -> "🎵"
                            }
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
                        FilterChip(
                            selected = false,
                            onClick = { onHistorySelect(h) },
                            label = { Text(h) },
                            trailingIcon = {
                                Text(
                                    text = "×",
                                    modifier = Modifier.clickable { onHistoryDelete(h) }
                                )
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
        } else if (searched) {
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
                text = "Results: $total",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            if (visible.isEmpty()) {
                when (selectListState(loading = false, error = searchError, isEmpty = true)) {
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayAt(visible, index) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.coverUrl.ifBlank { null },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp)
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = formatDuration(song.durationSec),
                                style = MaterialTheme.typography.bodySmall
                            )
                            FavHeart(
                                faved = song.sourceId.isNotBlank() && song.sourceId in favIds,
                                onClick = { onToggleFav(song) }
                            )
                            Box {
                                IconButton(
                                    onClick = { songMenuFor = song.platform + ":" + song.sourceId }
                                ) {
                                    Text("⋯")
                                }
                                DropdownMenu(
                                    expanded = songMenuFor == song.platform + ":" + song.sourceId,
                                    onDismissRequest = { songMenuFor = null }
                                ) {
                                    val dlKey = offlineBaseName(song)
                                    val downloading = dlKey in downloadingKeys
                                    val downloaded = dlKey in downloadedKeys
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when {
                                                    downloading -> "下载中…"
                                                    downloaded -> "已下载"
                                                    else -> "下载"
                                                }
                                            )
                                        },
                                        enabled = !downloading,
                                        onClick = {
                                            songMenuFor = null
                                            onDownload(song)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("加入歌单") },
                                        onClick = {
                                            songMenuFor = null
                                            onAddToPlaylist(song)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    queue: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    lyricState: LyricUiState?,
    sleepLabel: String,
    onSleepClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onAddCurrentToPlaylist: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    modeLabel: String = "",
    onCycleMode: () -> Unit = {},
    isCached: Boolean = false,
    onDownloadCurrent: () -> Unit = {},
    downloadingCurrent: Boolean = false,
    downloadedCurrent: Boolean = false,
    isFav: Boolean = false,
    onToggleFav: () -> Unit = {}
) {
    val song = queue.getOrNull(currentIndex)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableLongStateOf(0L) }
    val shownMs = if (dragging) dragValue else positionMs
    val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
    val lines = (lyricState as? LyricUiState.Ok)?.lines.orEmpty()
    val currentLine = lines.indexOfLast { it.timeSec * 1000 <= positionMs }
    val lyricsListState = rememberLazyListState()
    val coverScroll = rememberScrollState()
    var view by remember(song?.sourceId) { mutableStateOf(PlayerView.COVER) }
    var lastGestureMs by remember { mutableStateOf(-1L) }
    val density = LocalDensity.current
    val coverUrl = song?.coverUrl?.ifBlank { null }

    LaunchedEffect(currentLine, lines, view) {
        if (view == PlayerView.LYRICS && currentLine >= 0) {
            try {
                lyricsListState.scrollToItem(currentLine)
            } catch (_: Exception) {
            }
        }
    }

    fun fireGesture(g: PlayerGesture) {
        if (g == PlayerGesture.NONE) return
        val now = System.currentTimeMillis()
        if (!shouldFireGesture(now, lastGestureMs)) return
        lastGestureMs = now
        when (g) {
            PlayerGesture.CLOSE -> onClose()
            PlayerGesture.NEXT -> onNext()
            PlayerGesture.PREV -> onPrev()
            PlayerGesture.NONE -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize().background(ObsidianBg)) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().alpha(0.25f)
        )
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(
                        ObsidianBg.copy(alpha = 0.55f),
                        ObsidianBg.copy(alpha = 0.88f),
                        ObsidianBg
                    )
                )
            )
        )
        if (view == PlayerView.LYRICS && song != null) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { view = PlayerView.COVER },
                        modifier = Modifier.heightIn(min = MIN_TOUCH_DP.dp)
                    ) {
                        Text("‹ 封面")
                    }
                    Text(
                        text = song.name.ifBlank { "(untitled)" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(MIN_TOUCH_DP.dp)
                    ) {
                        Text("✕")
                    }
                }
                Spacer(Modifier.height(4.dp))
                when (lyricState) {
                    null, LyricUiState.Loading -> SearchSkeleton()
                    LyricUiState.Failed -> EmptyStateLine(
                        text = "歌词加载失败",
                        actionLabel = "返回封面",
                        onAction = { view = PlayerView.COVER }
                    )
                    is LyricUiState.Ok -> if (lines.isEmpty()) {
                        EmptyStateLine(
                            text = "暂无歌词",
                            actionLabel = "返回封面",
                            onAction = { view = PlayerView.COVER }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            state = lyricsListState,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(lines, key = { idx, _ -> idx }) { idx, line ->
                                val active = idx == currentLine
                                Text(
                                    text = line.text.ifBlank { " " },
                                    style = if (active) MaterialTheme.typography.titleMedium
                                    else MaterialTheme.typography.bodyMedium,
                                    color = if (active) Champagne else GrayMuted,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = MIN_TOUCH_DP.dp)
                                        .clickable { view = PlayerView.COVER }
                                        .padding(vertical = 6.dp, horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPrev,
                        modifier = Modifier.heightIn(min = MIN_TOUCH_DP.dp)
                    ) {
                        Text("⏮")
                    }
                    Button(
                        onClick = onPlayPause,
                        modifier = Modifier.heightIn(min = MIN_TOUCH_DP.dp)
                    ) {
                        Text(if (isPlaying) "⏸" else "▶")
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier.heightIn(min = MIN_TOUCH_DP.dp)
                    ) {
                        Text("⏭")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(coverScroll).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(MIN_TOUCH_DP.dp)
                    ) {
                        Text("✕")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .pointerInput(song?.sourceId) {
                            var tx = 0f
                            var ty = 0f
                            detectDragGestures(
                                onDragStart = { tx = 0f; ty = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    tx += amount.x
                                    ty += amount.y
                                },
                                onDragEnd = {
                                    val dxDp = with(density) { tx.toDp().value }
                                    val dyDp = with(density) { ty.toDp().value }
                                    fireGesture(
                                        resolvePlayerGesture(
                                            dxDp,
                                            dyDp,
                                            fromCoverZone = true
                                        )
                                    )
                                }
                            )
                        }
                        .clickable { view = togglePlayerView(view) }
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(280.dp)
                            .shadow(16.dp, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = song?.name ?: "(nothing playing)",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song?.artist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = shownMs.coerceIn(0L, durationMs.coerceAtLeast(0L)).toFloat()
                        .coerceIn(0f, sliderMax),
                    onValueChange = {
                        dragging = true
                        dragValue = it.toLong()
                    },
                    onValueChangeFinished = {
                        dragging = false
                        onSeek(dragValue.coerceIn(0L, durationMs.coerceAtLeast(0L)))
                    },
                    valueRange = 0f..sliderMax,
                    enabled = song != null && durationMs > 0,
                    colors = SliderDefaults.colors(
                        activeTrackColor = NeonViolet,
                        inactiveTrackColor = GrayMuted,
                        thumbColor = Champagne
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration((shownMs / 1000).toInt()),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatDuration((durationMs.coerceAtLeast(0L) / 1000).toInt()),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPrev,
                        enabled = song != null,
                        modifier = Modifier.heightIn(min = MIN_TOUCH_DP.dp)
                    ) {
                        Text("⏮ Prev")
                    }
                    Button(
                        onClick = onPlayPause,
                        enabled = song != null,
                        modifier = Modifier.heightIn(min = MIN_TOUCH_DP.dp)
                    ) {
                        Text(if (isPlaying) "⏸ Pause" else "▶ Play")
                    }
                    Button(
                        onClick = onNext,
                        enabled = song != null,
                        modifier = Modifier.heightIn(min = MIN_TOUCH_DP.dp)
                    ) {
                        Text("Next ⏭")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onOpenQueue, enabled = song != null) {
                        Text("队列")
                    }
                    OutlinedButton(onClick = onCycleMode, enabled = song != null) {
                        Text("模式：$modeLabel")
                    }
                    if (isCached && song != null) {
                        Text(
                            text = "已缓存",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonCyan,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onAddCurrentToPlaylist,
                    enabled = song != null
                ) {
                    Text("＋ 加入歌单")
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FavHeart(faved = isFav, onClick = onToggleFav, enabled = song != null)
                    OutlinedButton(
                        onClick = onDownloadCurrent,
                        enabled = song != null && !downloadingCurrent
                    ) {
                        if (downloadingCurrent) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("下载中…")
                        } else {
                            Text(if (downloadedCurrent) "已下载" else "下载")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onSleepClick) {
                    Text(sleepLabel)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "点封面看歌词 · 左右滑切歌 · 封面下滑关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayMuted
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    busy: Boolean,
    initialRegister: Boolean = false,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit = { _, _, _ -> },
    onBrowseAsGuest: () -> Unit = {},
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var registerMode by remember(initialRegister) { mutableStateOf(initialRegister) }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) {
                Text("‹ 返回")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(text = if (registerMode) "注册" else "登录", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !registerMode,
                onClick = { registerMode = false },
                label = { Text("登录") }
            )
            FilterChip(
                selected = registerMode,
                onClick = { registerMode = true },
                label = { Text("注册") }
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(if (registerMode) "密码（至少8位）" else "密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (registerMode) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("昵称（可选，默认同用户名）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (registerMode) onRegister(username, password, nickname)
                else onLogin(username, password)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(if (registerMode) "注册并登录" else "登录")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "访客可继续搜歌听歌；只存 token，不存密码",
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = onBrowseAsGuest) {
            Text("先逛逛")
        }
    }
}

@Composable
fun SettingsEntryRow(onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_DP.dp)
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "设置", style = MaterialTheme.typography.titleMedium)
        Text(text = "›", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun CacheManageRow(
    cacheSizeLabel: String,
    showConfirm: Boolean,
    onAskClear: () -> Unit,
    onConfirmClear: () -> Unit,
    onDismissClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "播放缓存 (rolling)",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "已用 $cacheSizeLabel · 播放缓存 rolling，满150MB自动清理",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onAskClear) {
            Text("清理缓存")
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = onDismissClear,
            title = { Text("清理缓存") },
            text = { Text("确定清除播放缓存吗？本地下载不受影响； rolling 缓存离线将无法播放，需联网重新缓存。") },
            confirmButton = {
                TextButton(onClick = onConfirmClear) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = onDismissClear) { Text("取消") }
            }
        )
    }
}

@Composable
fun VersionRow(
    versionLabel: String,
    checking: Boolean,
    onCheck: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = versionLabel.ifBlank { formatVersionLabel("") },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onCheck, enabled = !checking) {
            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(6.dp))
            }
            Text("检查更新")
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    cacheSizeLabel: String,
    storageLabel: String,
    versionLabel: String,
    checkingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    val rows = remember(cacheSizeLabel, storageLabel, versionLabel) {
        buildSettingsRows(
            cacheLabel = "已用 $cacheSizeLabel · 满150MB自动清理",
            storageLabel = storageLabel,
            versionLabel = versionLabel.ifBlank { formatVersionLabel("") }
        )
    }
    val themeRow = rows.first { it.id == "theme" }
    val storageRow = rows.first { it.id == "storage" }
    val aboutRow = rows.first { it.id == "about" }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MIN_TOUCH_DP.dp)) {
                Text("‹ 我的")
            }
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(4.dp))
        SettingsRowShell(
            title = themeRow.title,
            subtitle = themeRow.subtitle,
            trailing = { Text(text = "✓", color = NeonViolet) }
        )
        CacheManageRow(
            cacheSizeLabel = cacheSizeLabel,
            showConfirm = showClearConfirm,
            onAskClear = { showClearConfirm = true },
            onConfirmClear = {
                showClearConfirm = false
                onClearCache()
            },
            onDismissClear = { showClearConfirm = false }
        )
        SettingsRowShell(title = storageRow.title, subtitle = storageRow.subtitle)
        SettingsRowShell(
            title = aboutRow.title,
            subtitle = "${aboutRow.subtitle}\n$SETTINGS_GITHUB_URL\n开源致谢：感谢每一位贡献者"
        )
        Spacer(Modifier.height(4.dp))
        VersionRow(
            versionLabel = versionLabel,
            checking = checkingUpdate,
            onCheck = onCheckUpdate
        )
    }
}

@Composable
fun MineScreen(
    modifier: Modifier = Modifier,
    user: LoggedInUser?,
    authChecked: Boolean,
    playlists: List<Playlist>,
    playlistsLoading: Boolean,
    selectedPlaylist: Playlist?,
    songs: List<Song>,
    songsLoading: Boolean,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit,
    onRetryPlaylists: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onBackToPlaylists: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onCreatePlaylist: (String, String) -> Unit = { _, _ -> },
    onRenamePlaylist: (Playlist, String) -> Unit = { _, _ -> },
    onUpdateDescription: (Playlist, String) -> Unit = { _, _ -> },
    onDeletePlaylist: (Playlist) -> Unit = {},
    onBatchDelete: (List<String>) -> Unit = {},
    onMovePlaylist: (Playlist, Int) -> Unit = { _, _ -> },
    onImportPlaylist: (String, String) -> Unit = { _, _ -> },
    onRemoveSong: (Song) -> Unit = {},
    onAddSongToPlaylist: (Song) -> Unit = {},
    cacheSizeLabel: String = "",
    onClearCache: () -> Unit = {},
    versionLabel: String = "",
    checkingUpdate: Boolean = false,
    onCheckUpdate: () -> Unit = {},
    offlineCount: Int = 0,
    onOpenOffline: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    favIds: Set<String> = emptySet(),
    onToggleFav: (Song) -> Unit = {},
    historyCount: Int = 0,
    onOpenHistory: () -> Unit = {},
    onChangePassword: (String, String) -> Unit = { _, _ -> },
    onUpdateProfile: (String?, String?, String?) -> Unit = { _, _, _ -> },
    onPickAvatar: () -> Unit = {},
    onPickBg: () -> Unit = {},
    onGoSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    var selecting by remember { mutableStateOf(false) }
    var checkedIds by remember { mutableStateOf(setOf<String>()) }
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var descTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var showBatchConfirm by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Song?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (!authChecked) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(8.dp))
            Text("正在恢复登录态…", style = MaterialTheme.typography.bodySmall)
            return
        }
        if (user == null) {
            Text(
                text = "未登录",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "登录后看我的歌单；访客可继续搜歌",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
                Text("去登录")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRegisterClick, modifier = Modifier.fillMaxWidth()) {
                Text("注册新账号")
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenOffline)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "本地下载",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$offlineCount 首",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = "›", style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.height(4.dp))
            CacheManageRow(
                cacheSizeLabel = cacheSizeLabel,
                showConfirm = showClearConfirm,
                onAskClear = { showClearConfirm = true },
                onConfirmClear = {
                    showClearConfirm = false
                    onClearCache()
                },
                onDismissClear = { showClearConfirm = false }
            )
            Spacer(Modifier.height(4.dp))
            VersionRow(
                versionLabel = versionLabel,
                checking = checkingUpdate,
                onCheck = onCheckUpdate
            )
            Spacer(Modifier.height(4.dp))
            SettingsEntryRow(onOpen = onOpenSettings)
            return
        }
        // Logged in header
        var showChangePwd by remember { mutableStateOf(false) }
        var showProfile by remember { mutableStateOf(false) }
        val displayName = user.nickname.ifBlank { user.username }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianSurface)
        ) {
            if (shouldShowMineBg(user.bgImage)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(absImgUrl(user.bgImage))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x99000000),
                                    Color(0x66000000),
                                    Color(0xCC000000)
                                )
                            )
                        )
                )
            }
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(NeonViolet),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = avatarInitial(displayName),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(absImgUrl(user.avatar).ifBlank { null })
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "@${user.username}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = listOf(user.gender, user.birthday).filter { it.isNotBlank() }.joinToString(" · ")
                        if (sub.isNotBlank()) {
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onLogout) {
                        Text("退出登录")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "acc-profile") {
                        OutlinedButton(onClick = { showProfile = true }) { Text("改资料") }
                    }
                    item(key = "acc-pwd") {
                        OutlinedButton(onClick = { showChangePwd = true }) { Text("改密") }
                    }
                    item(key = "acc-avatar") {
                        OutlinedButton(onClick = onPickAvatar) { Text("换头像") }
                    }
                    item(key = "acc-bg") {
                        OutlinedButton(onClick = onPickBg) { Text("换背景") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (showChangePwd) {
            ChangePasswordDialog(
                onConfirm = { old, new ->
                    showChangePwd = false
                    onChangePassword(old, new)
                },
                onDismiss = { showChangePwd = false }
            )
        }
        if (showProfile) {
            ProfileDialog(
                initialNickname = user.nickname,
                initialGender = user.gender,
                initialBirthday = user.birthday,
                onConfirm = { nickname, gender, birthday ->
                    showProfile = false
                    onUpdateProfile(nickname, gender, birthday)
                },
                onDismiss = { showProfile = false }
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenHistory)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "最近播放",
                style = MaterialTheme.typography.titleMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$historyCount 首",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "›", style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenOffline)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "本地下载",
                style = MaterialTheme.typography.titleMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$offlineCount 首",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "›", style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(4.dp))
        CacheManageRow(
            cacheSizeLabel = cacheSizeLabel,
            showConfirm = showClearConfirm,
            onAskClear = { showClearConfirm = true },
            onConfirmClear = {
                showClearConfirm = false
                onClearCache()
            },
            onDismissClear = { showClearConfirm = false }
        )
        Spacer(Modifier.height(4.dp))
        VersionRow(
            versionLabel = versionLabel,
            checking = checkingUpdate,
            onCheck = onCheckUpdate
        )
        Spacer(Modifier.height(4.dp))
        SettingsEntryRow(onOpen = onOpenSettings)
        Spacer(Modifier.height(12.dp))
        if (selectedPlaylist == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "我的歌单", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            selecting = !selecting
                            if (!selecting) checkedIds = emptySet()
                        }
                    ) {
                        Text(if (selecting) "取消多选" else "多选")
                    }
                    TextButton(onClick = onRetryPlaylists, enabled = !playlistsLoading) {
                        Text("刷新")
                    }
                }
            }
            if (selecting) {
                val allChecked = playlists.isNotEmpty() && checkedIds.size == playlists.size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        checkedIds =
                            if (allChecked) emptySet()
                            else playlists.map { it.id }.toSet()
                    }) {
                        Text(if (allChecked) "全不选" else "全选")
                    }
                    Text(
                        text = "已选 ${checkedIds.size} 项",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { showBatchConfirm = true },
                        enabled = checkedIds.isNotEmpty()
                    ) {
                        Text("删除所选")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) {
                    Text("新建歌单")
                }
                OutlinedButton(onClick = { showImport = true }, modifier = Modifier.weight(1f)) {
                    Text("导入外部歌单")
                }
            }
            Spacer(Modifier.height(4.dp))
            if (showCreate) {
                PlaylistTextDialog(
                    title = "新建歌单",
                    label = "歌单名",
                    secondLabel = "简介（可选）",
                    confirmText = "创建",
                    onConfirm = { name, desc ->
                        showCreate = false
                        onCreatePlaylist(name, desc)
                    },
                    onDismiss = { showCreate = false }
                )
            }
            if (showImport) {
                ImportDialog(
                    onConfirm = { source, id ->
                        showImport = false
                        onImportPlaylist(source, id)
                    },
                    onDismiss = { showImport = false }
                )
            }
            renameTarget?.let { target ->
                PlaylistTextDialog(
                    title = "重命名",
                    initial = target.name,
                    label = "新歌单名",
                    confirmText = "保存",
                    onConfirm = { name, _ ->
                        renameTarget = null
                        onRenamePlaylist(target, name)
                    },
                    onDismiss = { renameTarget = null }
                )
            }
            descTarget?.let { target ->
                PlaylistTextDialog(
                    title = "改简介",
                    label = "简介",
                    confirmText = "保存",
                    onConfirm = { desc, _ ->
                        descTarget = null
                        onUpdateDescription(target, desc)
                    },
                    onDismiss = { descTarget = null }
                )
            }
            deleteTarget?.let { target ->
                AlertDialog(
                    onDismissRequest = { deleteTarget = null },
                    title = { Text("删除歌单") },
                    text = { Text("确定删除「${target.name}」吗？组内歌曲一并移除，不可恢复。") },
                    confirmButton = {
                        TextButton(onClick = {
                            deleteTarget = null
                            if (selecting) {
                                checkedIds = checkedIds - target.id
                            }
                            onDeletePlaylist(target)
                        }) { Text("删除") }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteTarget = null }) { Text("取消") }
                    }
                )
            }
            if (showBatchConfirm) {
                AlertDialog(
                    onDismissRequest = { showBatchConfirm = false },
                    title = { Text("批量删除") },
                    text = { Text("确定删除选中的 ${checkedIds.size} 个歌单吗？不可恢复。") },
                    confirmButton = {
                        TextButton(onClick = {
                            showBatchConfirm = false
                            selecting = false
                            val ids = checkedIds.toList()
                            checkedIds = emptySet()
                            onBatchDelete(ids)
                        }) { Text("删除所选") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBatchConfirm = false }) { Text("取消") }
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            if (playlistsLoading) {
                SearchSkeleton()
            } else if (playlists.isEmpty()) {
                EmptyStateLine(
                    text = "还没有歌单，新建一个开始收藏吧",
                    actionLabel = "去搜索",
                    onAction = onGoSearch
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(playlists, key = { it.id.ifBlank { it.name } }) { pl ->
                        val checked = pl.id in checkedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selecting) {
                                        checkedIds =
                                            if (checked) checkedIds - pl.id
                                            else checkedIds + pl.id
                                    } else {
                                        onSelectPlaylist(pl)
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selecting) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        checkedIds =
                                            if (checked) checkedIds - pl.id
                                            else checkedIds + pl.id
                                    }
                                )
                            }
                            AsyncImage(
                                model = pl.coverUrl.ifBlank { null },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.name.ifBlank { "(untitled)" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${pl.songCount} 首",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (selecting) {
                                Text("›", style = MaterialTheme.typography.titleLarge)
                            } else {
                                Box {
                                    IconButton(onClick = { menuFor = pl.id }) {
                                        Text("⋯")
                                    }
                                    DropdownMenu(
                                        expanded = menuFor == pl.id,
                                        onDismissRequest = { menuFor = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("重命名") },
                                            onClick = {
                                                menuFor = null
                                                renameTarget = pl
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("改简介") },
                                            onClick = {
                                                menuFor = null
                                                descTarget = pl
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("上移") },
                                            onClick = {
                                                menuFor = null
                                                onMovePlaylist(pl, -1)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("下移") },
                                            onClick = {
                                                menuFor = null
                                                onMovePlaylist(pl, 1)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("删除") },
                                            onClick = {
                                                menuFor = null
                                                deleteTarget = pl
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBackToPlaylists) {
                    Text("‹ 歌单")
                }
                Text(
                    text = selectedPlaylist.name.ifBlank { "(untitled)" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            if (songsLoading) {
                SearchSkeleton()
            } else if (songs.isEmpty()) {
                EmptyStateLine(
                    text = "歌单是空的，去搜索页把喜欢的歌加进来吧",
                    actionLabel = "去搜索",
                    onAction = onGoSearch
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(songs, key = { idx, s -> s.sourceId + s.platform + idx }) { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlaySong(index) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.coverUrl.ifBlank { null },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp)
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (song.durationSec > 0) {
                                Text(
                                    text = formatDuration(song.durationSec),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            FavHeart(
                                faved = song.sourceId.isNotBlank() && song.sourceId in favIds,
                                onClick = { onToggleFav(song) }
                            )
                            TextButton(onClick = { confirmRemove = song }) {
                                Text("移除")
                            }
                        }
                    }
                }
            }
            confirmRemove?.let { target ->
                AlertDialog(
                    onDismissRequest = { confirmRemove = null },
                    title = { Text("从歌单删除") },
                    text = { Text("确定把《${target.name}》从「${selectedPlaylist.name}」移除吗？") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmRemove = null
                            onRemoveSong(target)
                        }) { Text("删除") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmRemove = null }) { Text("取消") }
                    }
                )
            }
        }
    }
}

@Composable
fun SleepTimerDialog(
    current: Int,
    onConfirm: (Int) -> Unit,
    onInvalid: () -> Unit,
    onDismiss: () -> Unit
) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("睡眠定时") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SLEEP_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = current == preset,
                            onClick = { onConfirm(preset) },
                            label = { Text("${preset}分钟") }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("自定义分钟 (${SLEEP_CUSTOM_MIN}~${SLEEP_CUSTOM_MAX})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { onConfirm(0) }) {
                    Text("关闭定时")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (custom.isBlank()) {
                    onDismiss()
                    return@TextButton
                }
                val parsed = parseSleepMinutes(custom)
                if (parsed == null) onInvalid() else onConfirm(parsed)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun PlaylistTextDialog(
    title: String,
    label: String,
    initial: String = "",
    secondLabel: String? = null,
    confirmText: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var first by remember(initial) { mutableStateOf(initial) }
    var second by remember { mutableStateOf("") }
    val firstOk = first.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (secondLabel != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = second,
                        onValueChange = { second = it },
                        label = { Text(secondLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(first.trim(), second.trim()) },
                enabled = firstOk
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun ImportDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var source by remember { mutableStateOf("netease") }
    var id by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入外部歌单") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = source == "netease",
                        onClick = { source = "netease" },
                        label = { Text("网易云") }
                    )
                    FilterChip(
                        selected = source == "qq",
                        onClick = { source = "qq" },
                        label = { Text("QQ音乐") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it.trim() },
                    label = { Text("歌单链接或ID") },
                    placeholder = { Text("粘贴歌单链接，或填数字ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val parsed = VibeApi.extractPlaylistId(id)
            TextButton(
                onClick = { onConfirm(source, parsed) },
                enabled = parsed.isNotEmpty()
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun UpdateDialog(
    release: GithubRelease,
    busy: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onLater() },
        title = { Text("发现新版本 ${release.tag}") },
        text = {
            Column {
                if (release.body.isNotBlank()) {
                    Text(
                        text = release.body,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    )
                } else {
                    Text(
                        text = "新版本可用，立即更新体验改进。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "正在下载更新包…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate, enabled = !busy) { Text("立即更新") }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !busy) { Text("稍后") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(    song: Song,
    playlists: List<Playlist>,
    busy: Boolean,
    onPick: (Playlist) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showInlineCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "加入：《${song.name.ifBlank { "(untitled)" }}》",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            if (playlists.isEmpty()) {
                Text(
                    text = "暂无歌单，先新建一个吧",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                    items(playlists, key = { it.id.ifBlank { it.name } }) { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) { onPick(pl) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = pl.coverUrl.ifBlank { null },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.name.ifBlank { "(untitled)" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${pl.songCount} 首",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            if (showInlineCreate) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新歌单名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onCreateAndAdd(newName.trim()) },
                        enabled = !busy && newName.trim().isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("创建并加入") }
                    OutlinedButton(
                        onClick = { showInlineCreate = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("取消") }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showInlineCreate = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("＋ 新建歌单") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun FavHeart(faved: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Text(
            text = if (faved) "❤" else "♡",
            color = if (faved) Color(0xFFEF4444) else GrayMuted
        )
    }
}

fun absImgUrl(path: String): String {
    val t = path.trim()
    if (t.isBlank()) return ""
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    return VibeApi.BASE_URL.trimEnd('/') + (if (t.startsWith("/")) t else "/$t")
}

fun avatarInitial(name: String): String {
    for (ch in name.trim()) {
        if (ch.isLetterOrDigit()) return ch.uppercaseChar().toString()
    }
    return "♪"
}

fun shouldShowMineBg(bgImage: String): Boolean = bgImage.isNotBlank()

@Composable
fun ChangePasswordDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val ok = oldPwd.isNotBlank() && newPwd.length >= 8 && newPwd == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPwd,
                    onValueChange = { oldPwd = it },
                    label = { Text("旧密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPwd,
                    onValueChange = { newPwd = it },
                    label = { Text("新密码（至少8位）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("确认新密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (confirm.isNotEmpty() && newPwd != confirm) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "两次输入不一致",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF4444)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(oldPwd, newPwd) }, enabled = ok) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private val PROFILE_GENDERS = listOf("男", "女", "保密")
private val BIRTHDAY_RE = Regex("^\\d{4}-\\d{2}-\\d{2}\$")

@Composable
fun ProfileDialog(
    initialNickname: String,
    initialGender: String,
    initialBirthday: String,
    onConfirm: (String?, String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var nickname by remember(initialNickname) { mutableStateOf(initialNickname) }
    var gender by remember(initialGender) { mutableStateOf(initialGender) }
    var birthday by remember(initialBirthday) { mutableStateOf(initialBirthday) }
    val birthdayOk = birthday.isBlank() || BIRTHDAY_RE.matches(birthday.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("改资料") },
        text = {
            Column {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PROFILE_GENDERS.forEach { g ->
                        FilterChip(
                            selected = gender == g,
                            onClick = { gender = if (gender == g) "" else g },
                            label = { Text(g) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = birthday,
                    onValueChange = { birthday = it.trim() },
                    label = { Text("生日（YYYY-MM-DD，可空）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!birthdayOk) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "生日格式应为 YYYY-MM-DD",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF4444)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val nn = nickname.trim().takeIf { it != initialNickname }
                    val gg = gender.takeIf { it != initialGender && it in PROFILE_GENDERS }
                    val bb = birthday.trim().takeIf { it != initialBirthday.trim() }
                    onConfirm(nn, gg, bb)
                },
                enabled = birthdayOk
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    items: List<VibeApi.HistoryItem>,
    loading: Boolean,
    favIds: Set<String> = emptySet(),
    onPlayAt: (Int) -> Unit,
    onToggleFav: (Song) -> Unit = {},
    onDeleteOne: (VibeApi.HistoryItem) -> Unit = {},
    onClearAll: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<VibeApi.HistoryItem?>(null) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹ 我的")
            }
            Text(
                text = "最近播放",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (items.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) {
                    Text("清空")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (loading) {
            SearchSkeleton()
            return
        }
        if (items.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("还没有播放记录，去搜一首播吧")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry) {
                Text("刷新")
            }
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(items, key = { idx, h -> h.song.sourceId + h.song.platform + idx }) { index, item ->
                val song = item.song
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayAt(index) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = song.coverUrl.ifBlank { null },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.name.ifBlank { "(untitled)" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (item.playedAt.isNotBlank()) {
                                item.playedAt.replace("T", " ").take(16) + " · " + song.artist
                            } else {
                                "第${index + 1}首 · " + song.artist
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FavHeart(
                        faved = song.sourceId.isNotBlank() && song.sourceId in favIds,
                        onClick = { onToggleFav(song) }
                    )
                    TextButton(onClick = { confirmDelete = item }) {
                        Text("删除")
                    }
                }
            }
        }
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空历史") },
            text = { Text("确定清空全部 ${items.size} 条播放记录吗？不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearAll()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }
    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除记录") },
            text = { Text("确定删除《${target.song.name.ifBlank { "(untitled)" }}》的播放记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    onDeleteOne(target)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            }
        )
    }
}
