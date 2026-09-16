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

sealed interface Screen {
    data object Discover : Screen
    data object Search : Screen
    data object Player : Screen
    data object Queue : Screen
    data object Login : Screen
    data object Mine : Screen
    data object Offline : Screen
    data object History : Screen
    data object Favorites : Screen
    data object Settings : Screen
    data object Playlists : Screen
    data class PlaylistDetail(val playlist: Playlist) : Screen
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
        // Edge-to-edge with a single Obsidian background behind the status
        // bar: transparent bars + dark icons so no translucent band shows.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val snackbar = remember { SnackbarHostState() }
            // Transient top announcements (mode switch first); errors keep the
            // bottom Snackbar. Generation-guarded so a re-announce restarts
            // the 1.5s timer instead of being cut off by the previous one.
            var topToast by remember { mutableStateOf<String?>(null) }
            var topToastGen by remember { mutableIntStateOf(0) }
            fun announceTop(message: String) {
                topToast = message
                topToastGen += 1
            }
            LaunchedEffect(topToastGen) {
                if (topToastGen == 0) return@LaunchedEffect
                val gen = topToastGen
                delay(TOP_TOAST_DISMISS_MS)
                if (topToastGen == gen) topToast = null
            }
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
            // Long-background reconnect generation: bumped on ON_START when the
            // bound controller is missing or its binder died (service killed by
            // the OS / MIUI battery saver). The controller effect keys on it
            // and rebuilds; timeline recovery stays on ensureTimeline from the
            // QueueStore snapshot — never on session callbacks (see
            // PlaybackService NOTEs: materializers reverted twice).
            var controllerGen by remember { mutableIntStateOf(0) }
            var screen by remember { mutableStateOf<Screen>(Screen.Discover) }
            // Phase 10: where the user came from before entering Player, so
            // swipe-down-close returns to the previous tab (default Search).
            var playerOrigin by remember { mutableStateOf<Screen>(Screen.Search) }
            // Phase 10: session-only collapse of the mini-player bar (X button).
            // UI state only — queue/playback untouched; reset on next playAt.
            var miniDismissed by remember { mutableStateOf(false) }
            // Blank on launch: no auto-search (see shouldAutoSearchOnLaunch);
            // the empty state shows history chips + hotwords + 猜你喜欢.
            var query by remember { mutableStateOf("") }
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
            // 联想 overlay visibility flag: SELECT dismisses it at once,
            // only an explicit search-button press re-shows it; keystrokes
            // (debounce auto-search) never touch it (see
            // reduceSuggestOverlayVisible + SuggestOverlayEvent).
            var suggestVisible by remember { mutableStateOf(true) }
            var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }
            var searchSort by remember { mutableStateOf(SearchSort.RELEVANCE) }
            var artistFilter by remember { mutableStateOf<String?>(null) }
            var searchGuessSongs by remember { mutableStateOf(listOf<Song>()) }
            var searchGuessLoading by remember { mutableStateOf(false) }
            var searchGuessError by remember { mutableStateOf<String?>(null) }
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
            // One-shot: Player next/prev transport seeks resolve their target
            // index async, so the saved-position restore runs on the next
            // MEDIA_ITEM_TRANSITION instead of guessing the index up front.
            var pendingRestoreAfterSeek by remember { mutableStateOf(false) }

            // ---- auth state ----
            var currentUser by remember { mutableStateOf<LoggedInUser?>(null) }
            var authChecked by remember { mutableStateOf(false) }
            var loginBusy by remember { mutableStateOf(false) }
            var loginInitialRegister by remember { mutableStateOf(false) }
            // Settings 登录诊断 row: code + timestamps from "playback".
            var authDiagCode by remember { mutableStateOf("") }
            var authDiagTimeMs by remember { mutableLongStateOf(0L) }
            var positionSaveTimeMs by remember { mutableLongStateOf(0L) }
            LaunchedEffect(screen) {
                if (screen is Screen.Settings) {
                    try {
                        val (code, ts) = QueueStore.loadAuthDiag(context)
                        authDiagCode = code
                        authDiagTimeMs = ts
                        positionSaveTimeMs = QueueStore.loadLastPositionSaveTs(context)
                    } catch (_: Exception) {
                    }
                }
            }

            // ---- Phase 8: favorites + history ----
            var favIds by remember { mutableStateOf(setOf<String>()) }
            var favBusy by remember { mutableStateOf(setOf<String>()) }
            var favSongs by remember { mutableStateOf(listOf<Song>()) }
            var favLoading by remember { mutableStateOf(false) }
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
            var playlistSongsError by remember { mutableStateOf<String?>(null) }
            // Same Job-cancel + generation guards as runSearch: fast playlist
            // switching must not land stale songs into the new selection.
            var songsJob by remember { mutableStateOf<Job?>(null) }
            var songsGen by remember { mutableIntStateOf(0) }
            // Playback generation: bumped on every playAt / queueSeekTo /
            // queueRemoveAt so a stale async seek (slow IO landing after a
            // rapid double-tap) can never hit the new song. Same idiom as
            // songsGen + isStalePlaylistSongs above (see isStalePlayGen).
            var playGen by remember { mutableIntStateOf(0) }

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
            // Track A SWR: per-section last-loaded timestamps (memory only).
            // Discover sections 30-min TTL, Mine playlists 5-min TTL.
            var bannersLastLoaded by remember { mutableLongStateOf(0L) }
            var dailyLastLoaded by remember { mutableLongStateOf(0L) }
            var guessLastLoaded by remember { mutableLongStateOf(0L) }
            var hotLastLoaded by remember { mutableLongStateOf(0L) }
            var playlistsLastLoaded by remember { mutableLongStateOf(0L) }

            // ---- self-update state (dialog only when a newer release is found) ----
            var updateRelease by remember { mutableStateOf<GithubRelease?>(null) }
            var updateBusy by remember { mutableStateOf(false) }
            var manualChecking by remember { mutableStateOf(false) }
            var updateChannel by remember { mutableStateOf(UpdateChannel.STABLE) }
            fun setUpdateChannel(channel: UpdateChannel) {
                updateChannel = channel
                scope.launch {
                    try {
                        QueueStore.saveUpdateChannel(context, channel)
                    } catch (_: Exception) {
                    }
                }
            }
            LaunchedEffect("update-channel") {
                updateChannel = try {
                    QueueStore.loadUpdateChannel(context)
                } catch (_: Exception) {
                    UpdateChannel.STABLE
                }
            }
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
            // Absolute expiry backing the countdown: persisted, so process
            // death restores the true remaining time instead of a full window.
            var sleepDeadlineMs by remember { mutableLongStateOf(0L) }
            var showSleepDialog by remember { mutableStateOf(false) }
            fun setSleep(min: Int) {
                val m = min.coerceAtLeast(0)
                val deadline = computeSleepDeadlineMs(System.currentTimeMillis(), m)
                sleepMinutes = m
                sleepDeadlineMs = deadline
                scope.launch {
                    try {
                        QueueStore.saveSleepMinutes(context, m)
                    } catch (_: Exception) {
                    }
                    try {
                        QueueStore.saveSleepDeadline(context, deadline)
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
            LaunchedEffect(sleepDeadlineMs) {
                val left = sleepRemainingSec(sleepDeadlineMs, System.currentTimeMillis())
                if (sleepDeadlineMs <= 0L || left <= 0L) {
                    sleepLeftSec = 0L
                    return@LaunchedEffect
                }
                sleepLeftSec = left
                while (sleepLeftSec > 0) {
                    delay(1000)
                    sleepLeftSec--
                }
                try {
                    controller?.pause()
                } catch (_: Exception) {
                }
                // Clearing via setSleep writes zeros to both persisted keys,
                // so a relaunch never resurrects the timer.
                setSleep(0)
                scope.launch { snackbar.showSnackbar("已按定时暂停") }
            }

            fun showError(reason: String) {
                scope.launch { snackbar.showSnackbar(reason) }
            }

            fun recordAuthDiag(code: String) {
                scope.launch {
                    try {
                        QueueStore.saveAuthDiag(context, code)
                    } catch (_: Exception) {
                    }
                }
            }

            fun runSearch(keyword: String) {
                val kw = keyword.trim()
                if (kw.isEmpty()) {
                    showError("请输入搜索关键词")
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
                        // A failed attempt still counts as searched: the result
                        // branch owns the error UI, otherwise a first-search
                        // failure renders a blank screen with no retry.
                        searched = true
                        showError("搜索失败: ${friendlyNetworkMessage(e)}")
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

            fun loadSearchGuess() {
                if (searchGuessLoading) return
                searchGuessLoading = true
                searchGuessError = null
                scope.launch {
                    try {
                        searchGuessSongs = VibeApi.randomSongs(6)
                        searchGuessError = null
                    } catch (e: Exception) {
                        searchGuessError = friendlyNetworkMessage(e)
                    } finally {
                        searchGuessLoading = false
                    }
                }
            }

            fun restorePosition(target: Song, gen: Int = playGen) {
                if (target.sourceId.isBlank()) return
                scope.launch(Dispatchers.IO) {
                    val saved = try {
                        QueueStore.loadPosition(context, playKey(target))
                    } catch (_: Exception) {
                        0L
                    }
                    if (saved <= 0L) return@launch
                    if (isStalePlayGen(gen, playGen)) return@launch
                    withContext(Dispatchers.Main) {
                        if (isStalePlayGen(gen, playGen)) return@withContext
                        val cc = controller ?: return@withContext
                        if (!isRestoreTargetCurrent(queue, currentIndex, target)) return@withContext
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
                    showError("播放器连接中，请稍候")
                    return
                }
                if (list.isEmpty()) {
                    showError("列表是空的，先搜一首吧")
                    return
                }
                val safeIndex = index.coerceIn(list.indices)
                val target = list[safeIndex]
                val online = isNetworkAvailable(context)
                playGen += 1
                val gen = playGen
                // Review item 5: gate + timeline items precomputed off-main
                // from one availability snapshot (no new network hops — pure
                // local disk/cache pass); transport applies back on main.
                scope.launch {
                    val avail = withContext(Dispatchers.IO) {
                        try {
                            buildOfflineAvailability(context, list)
                        } catch (_: Exception) {
                            OfflineAvailability()
                        }
                    }
                    if (isStalePlayGen(gen, playGen)) return@launch
                    if (!avail.isGatePlayable(target, online)) {
                        showError("无网络且未缓存")
                        return@launch
                    }
                    val items = withContext(Dispatchers.IO) {
                        list.map { it.toPlayMediaItem(context, avail) }
                    }
                    if (isStalePlayGen(gen, playGen)) return@launch
                    try {
                        queue = list
                        timelineSongs = list
                        currentIndex = safeIndex
                        playerOrigin = if (screen is Screen.Player) playerOrigin else screen
                        miniDismissed = false
                        c.setMediaItems(
                            items,
                            currentIndex,
                            0L
                        )
                        c.prepare()
                        c.play()
                        screen = Screen.Player
                        cacheTick += 1
                        restorePosition(target, gen)
                        val snapshot = queue
                        val snapshotIndex = currentIndex
                        scope.launch {
                            try {
                                QueueStore.saveQueue(context, snapshot, snapshotIndex)
                            } catch (_: Exception) {
                            }
                        }
                    } catch (e: Exception) {
                        showError("播放失败: ${e.message ?: e.javaClass.simpleName}")
                    }
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

            // Search-result tap: enqueue ONLY the tapped song right after the
            // current index (existing queue intact) and start it. Same
            // playGen + gate + availability-snapshot idiom as playAt, so
            // stale async landings and restore-position guards keep working.
            fun playSingleFromSearch(song: Song) {
                val c = controller
                if (c == null) {
                    showError("播放器连接中，请稍候")
                    return
                }
                // R4-A1 gate-before-clear: the search UI snapshot is captured
                // at tap time but only cleared on the path that will actually
                // start playback (gate passed + gen fresh). Tapping an
                // unplayable result offline returns below with the whole
                // snapshot intact, so Back lands on a restorable search page.
                // History chips + hotwords come from persisted stores and stay
                // either way.
                val searchSnapshot = SearchViewState(
                    query = query,
                    results = results,
                    total = total,
                    searched = searched,
                    liveQuery = liveQuery,
                    artistFilter = artistFilter,
                    suggestVisible = suggestVisible
                )
                val online = isNetworkAvailable(context)
                playGen += 1
                val gen = playGen
                scope.launch {
                    // Pure placement first: the availability snapshot below
                    // covers the full NEW queue, so every timeline item
                    // resolves from one consistent pass (review item 5).
                    val placed = enqueueSingleAfterCurrent(queue, currentIndex, song)
                    val avail = withContext(Dispatchers.IO) {
                        try {
                            buildOfflineAvailability(context, placed.queue)
                        } catch (_: Exception) {
                            OfflineAvailability()
                        }
                    }
                    val stale = isStalePlayGen(gen, playGen)
                    val playable = avail.isGatePlayable(song, online)
                    // Pure gate-before-clear verdict under the same guards:
                    // only a fresh + playable tap may clear the snapshot.
                    val nextSearch = searchStateAfterPlayTap(
                        searchSnapshot,
                        gatePlayable = playable,
                        isStale = stale
                    )
                    if (stale) return@launch
                    if (!playable) {
                        showError("无网络且未缓存")
                        return@launch
                    }
                    val items = withContext(Dispatchers.IO) {
                        placed.queue.map { it.toPlayMediaItem(context, avail) }
                    }
                    if (isStalePlayGen(gen, playGen)) return@launch
                    query = nextSearch.query
                    results = nextSearch.results
                    total = nextSearch.total
                    searched = nextSearch.searched
                    liveQuery = nextSearch.liveQuery
                    artistFilter = nextSearch.artistFilter
                    suggestVisible = nextSearch.suggestVisible
                    debounceJob?.cancel()
                    try {
                        queue = placed.queue
                        timelineSongs = placed.queue
                        currentIndex = placed.index
                        playerOrigin = if (screen is Screen.Player) playerOrigin else screen
                        miniDismissed = false
                        c.setMediaItems(
                            items,
                            currentIndex,
                            0L
                        )
                        c.prepare()
                        c.play()
                        screen = Screen.Player
                        cacheTick += 1
                        restorePosition(song, gen)
                        persistQueue()
                    } catch (e: Exception) {
                        showError("播放失败: ${e.message ?: e.javaClass.simpleName}")
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
            // Suspend: the MediaItem list is precomputed off-main from one
            // availability snapshot (review item 5); controller calls stay on
            // main. Callers must launch (see togglePlayPause/queueSeekTo).
            suspend fun ensureTimeline(
                c: MediaController,
                restoreSaved: Boolean = true,
                gen: Int = playGen
            ): Boolean {
                val count = try {
                    c.mediaItemCount
                } catch (_: Exception) {
                    0
                }
                if (!needsMaterialize(count, queue.size)) return false
                if (queue.isEmpty()) return false
                return try {
                    val idx = currentIndex.coerceIn(queue.indices)
                    val snapshot = queue
                    val items = withContext(Dispatchers.IO) {
                        val avail = try {
                            buildOfflineAvailability(context, snapshot)
                        } catch (_: Exception) {
                            OfflineAvailability()
                        }
                        snapshot.map { it.toPlayMediaItem(context, avail) }
                    }
                    c.setMediaItems(items, idx, 0L)
                    c.prepare()
                    if (restoreSaved) queue.getOrNull(idx)?.let { restorePosition(it, gen) }
                    true
                } catch (_: Exception) {
                    false
                }
            }

            fun togglePlayPause() {
                val c = controller
                if (c == null) {
                    showError("播放器连接中，请稍候")
                    return
                }
                scope.launch {
                    try {
                        ensureTimeline(c)
                        if (c.playbackState == Player.STATE_IDLE) c.prepare()
                        if (c.isPlaying) c.pause() else c.play()
                    } catch (e: Exception) {
                        showError("播放失败: ${e.message ?: e.javaClass.simpleName}")
                    }
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
                redownloadedKeys = redownloadedKeys + key
                scope.launch(Dispatchers.IO) {
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
                    showError("播放器连接中，请稍候")
                    return
                }
                playGen += 1
                val gen = playGen
                scope.launch {
                    try {
                        ensureTimeline(c, restoreSaved = false, gen = gen)
                        if (c.mediaItemCount == 0) return@launch
                        val safeIndex = index.coerceIn(0, c.mediaItemCount - 1)
                        val target = queue.getOrNull(safeIndex)
                        if (!skipPlayableCheck && target != null) {
                            // Single-song gate off-main (review item 5).
                            val online = isNetworkAvailable(context)
                            val playable = withContext(Dispatchers.IO) {
                                try {
                                    buildOfflineAvailability(context, listOf(target))
                                        .isGatePlayable(target, online)
                                } catch (_: Exception) {
                                    online
                                }
                            }
                            if (isStalePlayGen(gen, playGen)) return@launch
                            if (!playable) {
                                showError("无网络且未缓存")
                                return@launch
                            }
                        }
                        if (isStalePlayGen(gen, playGen)) return@launch
                        c.seekTo(safeIndex, 0L)
                        if (c.playbackState == Player.STATE_IDLE) c.prepare()
                        c.play()
                        // Restore the REQUESTED song: currentMediaItemIndex is still
                        // the old window here (seekTo is async), so resolving the
                        // target from it restores the wrong song's position.
                        currentIndex = safeIndex
                        selectSeekRestoreTarget(queue, safeIndex)?.let { restorePosition(it, gen) }
                    } catch (e: Exception) {
                        showError("切歌失败: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
            }

            fun queueRemoveAt(index: Int) {
                val c = controller
                if (c == null) {
                    showError("播放器连接中，请稍候")
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
                // Invalidate any in-flight playAt / restorePosition seek: the
                // timeline it would land on no longer exists.
                playGen += 1
                scope.launch {
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
            }

            fun queueClearKeepCurrent() {
                val c = controller
                if (c == null) {
                    showError("播放器连接中，请稍候")
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
                scope.launch {
                    try {
                        ensureTimeline(c, restoreSaved = false)
                        val count = try {
                            c.mediaItemCount
                        } catch (_: Exception) {
                            0
                        }
                        if (count <= 1) {
                            showError("至少保留一首")
                            return@launch
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
            }

            fun clearMediaCache() {
                // Pause first so no new spans are written mid-eviction; the
                // pipeline itself stays intact (MediaCache.clear never releases
                // the live instance), so no rebuild or auto-resume is needed.
                try {
                    controller?.pause()
                } catch (_: Exception) {
                }
                scope.launch(Dispatchers.IO) {
                    val ok = try {
                        MediaCache.clear(context)
                    } catch (_: Exception) {
                        false
                    }
                    cacheTick += 1
                    withContext(Dispatchers.Main) {
                        if (ok) showError("已清理缓存")
                        else showError("清理失败，请重试")
                    }
                }
            }

            fun downloadSong(song: Song, silent: Boolean) {
                val key = offlineBaseName(song)
                if (key in downloadingIds) return
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

            fun loadPlaylists(background: Boolean = false) {
                if (playlistsLoading) return
                if (currentUser == null) return
                if (!background) playlistsLoading = true
                scope.launch {
                    try {
                        playlists = VibeApi.myPlaylists()
                        playlistsLastLoaded = System.currentTimeMillis()
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
                        // Background refresh failures stay silent when stale
                        // content keeps showing; foreground loads surface them
                        // with a diagnosable message (no silent failure).
                        if (!background) {
                            showError(diagnosableError("歌单加载失败", e))
                        }
                    } finally {
                        if (!background) playlistsLoading = false
                    }
                }
            }

            fun loadSongs(pl: Playlist) {
                selectedPlaylist = pl
                playlistSongs = emptyList()
                songsLoading = true
                playlistSongsError = null
                songsJob?.cancel()
                songsGen += 1
                val gen = songsGen
                val plId = pl.id
                songsJob = scope.launch {
                    try {
                        val songs = VibeApi.playlistSongs(pl.id)
                        if (isStalePlaylistSongs(gen, songsGen, selectedPlaylist?.id, plId)) return@launch
                        playlistSongs = songs
                        playlistSongsError = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: AuthException) {
                        if (isStalePlaylistSongs(gen, songsGen, selectedPlaylist?.id, plId)) return@launch
                        showError(e.message ?: "密码错/登录过期，请重登")
                        scope.launch {
                            try {
                                AuthStore.clear(context)
                            } catch (_: Exception) {
                            }
                        }
                        currentUser = null
                    } catch (e: Exception) {
                        if (isStalePlaylistSongs(gen, songsGen, selectedPlaylist?.id, plId)) return@launch
                        playlistSongsError = diagnosableError("歌曲加载失败", e)
                        showError(diagnosableError("歌曲加载失败", e))
                    } finally {
                        if (!isStalePlaylistSongs(gen, songsGen, selectedPlaylist?.id, plId)) songsLoading = false
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

            fun loadDiscoverBanners(
                markLoading: Boolean = true,
                background: Boolean = false,
                onDone: () -> Unit = {}
            ) {
                if (markLoading) discoverBannersLoading = true
                if (!background) discoverBannersError = null
                scope.launch {
                    try {
                        discoverBanners = VibeApi.discoverBanners()
                        discoverBannersError = null
                        bannersLastLoaded = System.currentTimeMillis()
                    } catch (e: Exception) {
                        if (!background) {
                            discoverBannersError = friendlyNetworkMessage(e)
                            noteDiscoverInitialFailure(e)
                        }
                    } finally {
                        discoverBannersLoading = false
                        onDone()
                    }
                }
            }

            fun loadDaily(
                refresh: Boolean = false,
                markLoading: Boolean = true,
                background: Boolean = false,
                onDone: () -> Unit = {}
            ) {
                if (markLoading) dailyLoading = true
                if (!background) dailyError = null
                scope.launch {
                    try {
                        val r = VibeApi.personalized(refresh)
                        dailySongs = r.songs
                        dailyReason = r.reason
                        dailyError = null
                        dailyLastLoaded = System.currentTimeMillis()
                    } catch (e: Exception) {
                        if (!background) {
                            dailyError = friendlyNetworkMessage(e)
                            noteDiscoverInitialFailure(e)
                        }
                    } finally {
                        dailyLoading = false
                        onDone()
                    }
                }
            }

            fun loadGuess(
                markLoading: Boolean = true,
                background: Boolean = false,
                onDone: () -> Unit = {}
            ) {
                if (markLoading) guessLoading = true
                if (!background) guessError = null
                scope.launch {
                    try {
                        guessSongs = VibeApi.randomSongs(8)
                        guessError = null
                        guessLastLoaded = System.currentTimeMillis()
                    } catch (e: Exception) {
                        if (!background) {
                            guessError = friendlyNetworkMessage(e)
                            noteDiscoverInitialFailure(e)
                        }
                    } finally {
                        guessLoading = false
                        onDone()
                    }
                }
            }

            fun loadHot(
                markLoading: Boolean = true,
                background: Boolean = false,
                onDone: () -> Unit = {}
            ) {
                if (markLoading) hotLoading = true
                if (!background) hotError = null
                scope.launch {
                    try {
                        hotPlaylists = VibeApi.recommendPlaylists()
                        hotError = null
                        hotLastLoaded = System.currentTimeMillis()
                    } catch (e: Exception) {
                        if (!background) {
                            hotError = friendlyNetworkMessage(e)
                            noteDiscoverInitialFailure(e)
                        }
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

            fun handleAuthLost(msg: String?, clearTokens: Boolean = true) {
                showError(msg ?: "密码错/登录过期，请重登")
                if (!clearTokens) return
                scope.launch {
                    try {
                        AuthStore.clear(context)
                    } catch (_: Exception) {
                    }
                    try {
                        QueueStore.saveAuthDiag(context, "ME_401_INVALID")
                    } catch (_: Exception) {
                    }
                }
                currentUser = null
                playlists = emptyList()
                favIds = emptySet()
                favSongs = emptyList()
                historyItems = emptyList()
                lastReportedKey = null
            }

            fun loadFavIds() {
                if (currentUser == null) return
                scope.launch {
                    try {
                        favIds = VibeApi.favIds()
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
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
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
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
                        favSongs = if (faved) {
                            if (favSongs.any { it.sourceId == song.sourceId }) favSongs
                            else favSongs + song
                        } else {
                            favSongs.filterNot { it.sourceId == song.sourceId }
                        }
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError(diagnosableError("收藏失败", e))
                    } finally {
                        favBusy = favBusy - song.sourceId
                    }
                }
            }

            fun loadFavorites() {
                if (currentUser == null) return
                if (favLoading) return
                favLoading = true
                scope.launch {
                    try {
                        favSongs = VibeApi.favList(200)
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError(diagnosableError("收藏加载失败", e))
                    } finally {
                        favLoading = false
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
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError(diagnosableError("新建歌单失败", e))
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
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
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
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
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
                            playlistSongsError = null
                            if (screen is Screen.PlaylistDetail) screen = Screen.Mine
                        }
                        showError("已删除「${pl.name}」")
                        loadPlaylists()
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
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
                            playlistSongsError = null
                            if (screen is Screen.PlaylistDetail) screen = Screen.Mine
                        }
                        showError("已删除 $n 个歌单")
                        loadPlaylists()
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
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
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
                        loadPlaylists()
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
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
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
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
                    } catch (e: AuthException) {
                        handleAuthLost(e.message)
                    } catch (e: Exception) {
                        showError("导入失败: ${friendlyNetworkMessage(e)}")
                    }
                }
            }

            fun sharePlaylistText(text: String) {
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享歌单"))
                } catch (e: Exception) {
                    showError("分享失败: ${friendlyNetworkMessage(e)}")
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
                    if (!isSameSignature(context, apk)) {
                        showError(UPDATE_SIGNATURE_MISMATCH_MESSAGE)
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
                        if (!isDownloadComplete(apk, -1L)) {
                            try {
                                partFileFor(apk).delete()
                            } catch (_: Exception) {
                            }
                            val fallback = fetchFallbackApkUrl(otherUpdateSourceUrl(rel.apkUrl))
                            downloadApk(
                                rel.apkUrl,
                                apk,
                                fallbackApkUrl = fallback,
                                expectedBytes = -1L
                            )
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
                            fetchLatestForChannel(updateChannel)
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
                        val decision = compareAndDecide(current, rel.tag, fetchOk = true)
                        when (decision) {
                            UpdateDecision.UPDATE_AVAILABLE -> updateRelease = rel
                            else -> manualUpdateCheckMessage(decision)?.let { showError(it) }
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
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
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
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
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
                    showError("请输入用户名和密码")
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
                        playlistSongsError = null
                        screen = Screen.Mine
                        loadPlaylists()
                        loadFavIds()
                        loadHistory()
                    } catch (e: AuthException) {
                        showError(e.message ?: "密码错/登录过期，请重登")
                    } catch (e: Exception) {
                        showError("登录失败: ${friendlyNetworkMessage(e)}")
                    } finally {
                        loginBusy = false
                    }
                }
            }

            fun doRegister(username: String, password: String, nickname: String) {
                if (loginBusy) return
                if (username.isBlank() || password.isBlank()) {
                    showError("请输入用户名和密码")
                    return
                }
                if (password.length < 8) {
                    showError("注册失败: 密码至少8位")
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
                        playlistSongsError = null
                        screen = Screen.Mine
                        showError("注册成功，已自动登录")
                        loadPlaylists()
                        loadFavIds()
                        loadHistory()
                    } catch (e: AuthException) {
                        showError(e.message ?: "密码错/登录过期，请重登")
                    } catch (e: Exception) {
                        showError("注册失败: ${friendlyNetworkMessage(e)}")
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
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
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
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
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
                    } catch (e: NetworkAuthException) {
                        withContext(Dispatchers.Main) {
                            handleAuthLost(e.message, clearTokens = false)
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
                    } catch (e: NetworkAuthException) {
                        handleAuthLost(e.message, clearTokens = false)
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
                        showError("退出失败: ${e.message ?: e.javaClass.simpleName}")
                        return@launch
                    }
                    recordAuthDiag("USER_LOGOUT")
                    currentUser = null
                    playlists = emptyList()
                    selectedPlaylist = null
                    playlistSongs = emptyList()
                    playlistSongsError = null
                    favIds = emptySet()
                    favBusy = emptySet()
                    favSongs = emptyList()
                    historyItems = emptyList()
                    lastReportedKey = null
                    loginInitialRegister = false
                    screen = Screen.Login
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

            DisposableEffect(context, controllerGen) {
                val builtGen = controllerGen
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
                        if (pendingRestoreAfterSeek) {
                            pendingRestoreAfterSeek = false
                            queue.getOrNull(currentIndex)?.let { restorePosition(it) }
                        }
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
                                    val hasNext = try {
                                        c?.hasNextMediaItem() == true
                                    } catch (_: Exception) {
                                        false
                                    }
                                    showError(
                                        skipErrorToast(
                                            null,
                                            error.errorCodeName,
                                            inferSkipOutcome(PlaybackService.lastSkipOutcome, hasNext)
                                        )
                                    )
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
                                // Review item 5: intact-file probe off-main.
                                scope.launch {
                                    val fileValid = withContext(Dispatchers.IO) {
                                        try {
                                            OfflineStore.isAudioFileIntact(context, song)
                                        } catch (_: Exception) {
                                            false
                                        }
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
                            }
                            LocalErrorAction.ERROR_MESSAGE -> {
                                showError("本地文件已损坏，联网后重新下载")
                            }
                            LocalErrorAction.SKIP -> {
                                // Single owner: PlaybackService already jumps to
                                // the next offline-playable item (or stops) on
                                // IO — the Activity only reports, never seeks
                                // or stops here, or the queue burns twice.
                                // Target recomputed off-main from one snapshot
                                // purely to pick the toast (zero main-thread I/O).
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
                                    scope.launch {
                                        val target = withContext(Dispatchers.IO) {
                                            try {
                                                val avail = buildOfflineAvailability(context, q)
                                                selectNextOfflineIndex(
                                                    q,
                                                    from,
                                                    isPlayable = { idx ->
                                                        q.getOrNull(idx)?.let {
                                                            avail.isOfflinePlayable(it)
                                                        } ?: false
                                                    },
                                                    repeatAll = repeatAll
                                                )
                                            } catch (_: Exception) {
                                                -1
                                            }
                                        }
                                        if (target < 0) {
                                            showError("离线可播已播完")
                                        }
                                    }
                                    return
                                }
                                val title = try {
                                    c?.currentMediaItem?.mediaMetadata?.title
                                } catch (_: Exception) {
                                    null
                                }
                                val hasNext = try {
                                    c?.hasNextMediaItem() == true
                                } catch (_: Exception) {
                                    false
                                }
                                val reported = PlaybackService.lastSkipOutcome
                                val errorName = error.errorCodeName
                                val titleText = title?.toString()
                                val probeSong = queue.getOrNull(currentIndex)
                                scope.launch {
                                    // Offline + partial cache: CacheDataSource served cached bytes then
                                    // hit a hole the unreachable upstream couldn't fill (probe off-main).
                                    val partialOffline = withContext(Dispatchers.IO) {
                                        try {
                                            !online &&
                                                probeSong != null &&
                                                MediaCache.cachedBytes(
                                                    context,
                                                    probeSong.streamUrl()
                                                ) > 0
                                        } catch (_: Exception) {
                                            false
                                        }
                                    }
                                    if (partialOffline) {
                                        showError("该歌曲未缓存完整，请联网后完整播一次")
                                    } else {
                                        // Toast follows the service's actual outcome (same-track
                                        // retry may still succeed; queue-end/melt-down stops):
                                        // fall back to hasNext only when the service never
                                        // recorded one for this error.
                                        showError(
                                            skipErrorToast(
                                                titleText,
                                                errorName,
                                                inferSkipOutcome(reported, hasNext)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                future.addListener(
                    {
                        try {
                            val c = future.get()
                            if (builtGen != controllerGen) {
                                try {
                                    c.release()
                                } catch (_: Exception) {
                                }
                                return@addListener
                            }
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
                            if (builtGen != controllerGen) return@addListener
                            showError(
                                controllerFailureMessage(
                                    e.message ?: e.javaClass.simpleName
                                )
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

            // Foreground reconnect: after 30+ min background the bound
            // controller may point at a dead session (binder died with the
            // service process) while non-null, so every button silently
            // no-ops. Probe cheaply on ON_START; on failure release + bump
            // the generation so the effect above rebinds to the live
            // session. Audible recovery after rebind is ensureTimeline's job
            // (QueueStore snapshot), called by every transport entry point.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        val c = controller
                        val probeFailed = if (c == null) false
                        else try {
                            c.playbackState
                            c.mediaItemCount
                            false
                        } catch (_: Exception) {
                            true
                        }
                        if (shouldReconnectController(c == null, probeFailed)) {
                            try {
                                c?.release()
                            } catch (_: Exception) {
                            }
                            controller = null
                            controllerGen += 1
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }

            LaunchedEffect(Unit) {
                VibeApi.init(context)
                try {
                    val now = System.currentTimeMillis()
                    val deadline = try {
                        QueueStore.loadSleepDeadline(context)
                    } catch (_: Exception) {
                        0L
                    }
                    val legacy = try {
                        QueueStore.loadSleepMinutes(context)
                    } catch (_: Exception) {
                        0
                    }
                    val restored = resolveSleepRestore(deadline, legacy, now)
                    if (restored.deadlineMs <= 0L) {
                        sleepMinutes = 0
                        sleepDeadlineMs = 0L
                        sleepLeftSec = 0L
                    } else if (deadline > now) {
                        sleepMinutes = restored.minutes
                        sleepDeadlineMs = restored.deadlineMs
                        sleepLeftSec = restored.leftSec
                    } else {
                        // Legacy minutes with no live deadline: restart one
                        // fresh full-length window and persist deadline form.
                        setSleep(restored.minutes)
                        sleepLeftSec = restored.leftSec
                    }
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
                if (shouldAutoSearchOnLaunch(query)) runSearch(query)
                loadSearchGuess()
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
                        val channel = try {
                            QueueStore.loadUpdateChannel(context)
                        } catch (_: Exception) {
                            UpdateChannel.STABLE
                        }
                        updateChannel = channel
                        val rel = try {
                            fetchLatestForChannel(channel)
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
                        StartRoute.SEARCH_GUEST -> screen = Screen.Discover
                        StartRoute.RESTORE -> Unit
                    }
                    if (snap.token.isBlank()) {
                        recordAuthDiag("NO_TOKEN")
                    } else {
                        var restored: LoggedInUser? = null
                        var settledError: Exception? = null
                        var invalidCredential = false
                        var retried = false
                        while (true) {
                            try {
                                restored = VibeApi.me()
                                settledError = null
                                break
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: AuthException) {
                                invalidCredential = true
                                settledError = e
                                break
                            } catch (e: Exception) {
                                settledError = e
                                val networkFail = e is NetworkAuthException ||
                                    mapAuthFailureToCode(e) == "ME_NETWORK"
                                if (!retried && networkFail) {
                                    retried = true
                                    try {
                                        delay(3000)
                                    } catch (ce: CancellationException) {
                                        throw ce
                                    }
                                    continue
                                }
                                break
                            }
                        }
                        when {
                            invalidCredential -> {
                                try {
                                    AuthStore.clear(context)
                                } catch (_: Exception) {
                                }
                                currentUser = null
                                showError(
                                    settledError?.message ?: "密码错/登录过期，请重登"
                                )
                                recordAuthDiag("ME_401_INVALID")
                            }
                            settledError == null -> {
                                if (restored != null) {
                                    currentUser = restored
                                    loadFavIds()
                                    loadHistory()
                                    recordAuthDiag("OK")
                                } else {
                                    // Guest-null: transient guest response, keep stored
                                    // token; only 401/AuthException owns clearing.
                                    currentUser = null
                                    recordAuthDiag("ME_GUEST")
                                }
                            }
                            else -> {
                                // Network or server issue: keep token, stay guest for now.
                                currentUser = null
                                showError(
                                    "登录态恢复失败: " +
                                        "${settledError.message ?: settledError.javaClass.simpleName}"
                                )
                                recordAuthDiag(mapAuthFailureToCode(settledError))
                            }
                        }
                    }
                    try {
                        QueueStore.saveHasLaunchedBefore(context)
                    } catch (_: Exception) {
                    }
                } catch (e: Exception) {
                    showError("登录态恢复失败: ${e.message ?: e.javaClass.simpleName}")
                    recordAuthDiag("RESTORE_EXCEPTION:" + e.javaClass.simpleName)
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
            // Track A SWR: empty → blocking load (skeleton); fresh → show
            // instantly, no reload; stale → show cached + silent refresh.
            LaunchedEffect(screen, currentUser, authChecked) {
                if ((screen is Screen.Mine || screen is Screen.Playlists) &&
                    currentUser != null && !playlistsLoading) {
                    if (needsBlockingLoad(playlists.isNotEmpty())) {
                        loadPlaylists()
                    } else if (
                        shouldBackgroundRefresh(
                            true,
                            playlistsLastLoaded,
                            hotspotTtlMs(HotspotSection.PLAYLISTS),
                            System.currentTimeMillis()
                        )
                    ) {
                        loadPlaylists(background = true)
                    }
                }
                if (screen is Screen.Mine && currentUser != null && historyItems.isEmpty() && !historyLoading) {
                    loadHistory()
                }
                if (screen is Screen.History && currentUser != null && historyItems.isEmpty() && !historyLoading) {
                    loadHistory()
                }
                if (screen is Screen.Favorites && currentUser != null && favSongs.isEmpty() && !favLoading) {
                    loadFavorites()
                }
            }

            // Discover SWR on tab visit: per-section TTLs (30 min). Fresh cache
            // shows instantly; stale cache shows immediately + refreshes
            // silently (no reload flash); skeleton only when nothing cached.
            // One section helper keeps the four branches identical.
            fun swrSection(
                hasCached: Boolean,
                lastLoaded: Long,
                section: HotspotSection,
                busy: Boolean,
                load: (Boolean, Boolean) -> Unit
            ) {
                if (busy) return
                if (needsBlockingLoad(hasCached)) {
                    load(true, false)
                } else if (
                    shouldBackgroundRefresh(
                        true,
                        lastLoaded,
                        hotspotTtlMs(section),
                        System.currentTimeMillis()
                    )
                ) {
                    load(false, true)
                }
            }
            LaunchedEffect(screen) {
                if (screen is Screen.Discover && !discoverRefreshing) {
                    swrSection(
                        discoverBanners.isNotEmpty(), bannersLastLoaded,
                        HotspotSection.BANNERS,
                        discoverBannersLoading
                    ) { mark, bg -> loadDiscoverBanners(mark, bg) }
                    swrSection(
                        dailySongs.isNotEmpty(), dailyLastLoaded,
                        HotspotSection.DAILY, dailyLoading
                    ) { mark, bg -> loadDaily(markLoading = mark, background = bg) }
                    swrSection(
                        guessSongs.isNotEmpty(), guessLastLoaded,
                        HotspotSection.GUESS, guessLoading
                    ) { mark, bg -> loadGuess(mark, bg) }
                    swrSection(
                        hotPlaylists.isNotEmpty(), hotLastLoaded,
                        HotspotSection.HOT, hotLoading
                    ) { mark, bg -> loadHot(mark, bg) }
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

            fun pollPosition() {
                controller?.let { c ->
                    try {
                        positionMs = c.currentPosition
                        durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                        if (c.isPlaying) persistPosition()
                    } catch (_: Exception) {
                    }
                }
            }

            val positionLifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(screen, controller) {
                if (screen is Screen.Player || screen is Screen.Queue) {
                    // RESUMED-gated: the composition stays alive while the app
                    // is backgrounded, so a bare while(true) would keep polling
                    // the controller off-screen. repeatOnLifecycle stops the
                    // loop on pause and re-runs (refresh once) on return.
                    positionLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        pollPosition()
                        while (true) {
                            delay(500)
                            pollPosition()
                        }
                    }
                }
            }

            val selectedTab: Int = when (screen) {
                is Screen.Discover -> 0
                is Screen.Search -> 1
                is Screen.Player, is Screen.Queue -> 2
                is Screen.Mine, is Screen.Login, is Screen.Offline, is Screen.History,
                is Screen.Favorites, is Screen.Settings, is Screen.Playlists,
                is Screen.PlaylistDetail -> 3
            }

            // HOME cover gate: coverless tracks/playlists never render on
            // HOME rows. Search/playlist pages keep their placeholder path
            // and must NOT use these lists.
            val dailyVisible = remember(dailySongs) { homeVisibleSongs(dailySongs) }
            val guessVisible = remember(guessSongs) { homeVisibleSongs(guessSongs) }
            val hotVisible = remember(hotPlaylists) { homeVisiblePlaylists(hotPlaylists) }

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
                                    onDismiss = { miniDismissed = true },
                                    positionMs = positionMs,
                                    durationMs = durationMs
                                )
                            }
                            NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { screen = Screen.Discover },
                                label = { Text("首页") },
                                icon = {
                                    AppIcon(
                                        kind = AppIconKind.EXPLORE,
                                        tint = if (selectedTab == 0) NeonViolet else GrayMuted,
                                        filled = selectedTab == 0
                                    )
                                }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { screen = Screen.Search },
                                label = { Text("搜索") },
                                icon = {
                                    AppIcon(
                                        kind = AppIconKind.SEARCH,
                                        tint = if (selectedTab == 1) NeonViolet else GrayMuted,
                                        filled = selectedTab == 1
                                    )
                                }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { screen = Screen.Player },
                                label = { Text("播放") },
                                icon = {
                                    AppIcon(
                                        kind = AppIconKind.PLAY_CIRCLE,
                                        tint = if (selectedTab == 2) NeonViolet else GrayMuted,
                                        filled = selectedTab == 2
                                    )
                                }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { screen = Screen.Mine },
                                label = { Text("我的") },
                                icon = {
                                    AppIcon(
                                        kind = AppIconKind.PERSON,
                                        tint = if (selectedTab == 3) NeonViolet else GrayMuted,
                                        filled = selectedTab == 3
                                    )
                                }
                            )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.fillMaxSize(), color = ObsidianBg) {
                        Box(modifier = Modifier.fillMaxSize()) {
                        // Track B4: 150ms crossfade on tab content (GPU alpha only).
                        Crossfade(
                            targetState = screen,
                            animationSpec = tween(TAB_CROSSFADE_MS),
                            label = "tabSwitch"
                        ) { s ->
                            when (s) {
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
                                dailySongs = dailyVisible,
                                dailyReason = dailyReason,
                                dailyLoading = dailyLoading,
                                dailyError = dailyError,
                                onRetryDaily = { loadDaily() },
                                onRefreshDaily = { loadDaily(refresh = true) },
                                onPlayDaily = { idx ->
                                    if (dailyVisible.isNotEmpty()) {
                                        playAt(dailyVisible, idx.coerceIn(dailyVisible.indices))
                                    }
                                },
                                guessSongs = guessVisible,
                                guessLoading = guessLoading,
                                guessError = guessError,
                                onRetryGuess = { loadGuess() },
                                onPlayGuess = { idx ->
                                    if (guessVisible.isNotEmpty()) {
                                        playAt(guessVisible, idx.coerceIn(guessVisible.indices))
                                    }
                                },
                                hotPlaylists = hotVisible,
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
                                onPullRefresh = { loadDiscover(isPullRefresh = true) },
                                favIds = favIds,
                                downloadingKeys = downloadingIds,
                                downloadedKeys = downloadedKeys,
                                onToggleFav = ::toggleFav,
                                onAddToPlaylist = ::openAddSheet,
                                onDownload = { song -> downloadSong(song, false) },
                                onGoSearch = { screen = Screen.Search },
                                onOpenHistory = { screen = Screen.History }
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
                                    suggestVisible = reduceSuggestOverlayVisible(
                                        suggestVisible,
                                        SuggestOverlayEvent.SEARCH_PRESS
                                    )
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
                                onPlaySingle = ::playSingleFromSearch,
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
                                guessSongs = searchGuessSongs,
                                guessLoading = searchGuessLoading,
                                guessError = searchGuessError,
                                onRetryGuess = { loadSearchGuess() },
                                onPlayGuessAt = ::playAt,
                                onHistorySelect = { h ->
                                    debounceJob?.cancel()
                                    suggestVisible = reduceSuggestOverlayVisible(
                                        suggestVisible,
                                        SuggestOverlayEvent.SELECT
                                    )
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
                                suggestions = run {
                                    val built = buildSuggestions(
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
                                    )
                                    if (shouldShowSuggestOverlay(suggestVisible, built.isNotEmpty())) {
                                        built
                                    } else {
                                        emptyList()
                                    }
                                },
                                onSuggestionSelect = { s ->
                                    debounceJob?.cancel()
                                    suggestVisible = reduceSuggestOverlayVisible(
                                        suggestVisible,
                                        SuggestOverlayEvent.SELECT
                                    )
                                    query = s.text
                                    runSearch(s.text)
                                }
                            )

                            is Screen.Player -> PlayerScreen(
                                // Edge-to-edge: consume only the bottom bar
                                // inset here; the top status inset is handled
                                // inside PlayerScreen (statusBarsPadding on the
                                // top bar over a full-bleed Obsidian backdrop).
                                modifier = Modifier.fillMaxSize().padding(
                                    bottom = innerPadding.calculateBottomPadding()
                                ),
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
                                        showError("播放器连接中，请稍候")
                                    } else {
                                        scope.launch {
                                            try {
                                                val materialized = ensureTimeline(c, restoreSaved = false)
                                            if (c.playbackState == Player.STATE_IDLE) c.prepare()
                                            when (nextBoundaryAction(playMode, c.hasNextMediaItem())) {
                                                BoundaryAction.ADVANCE -> {
                                                    pendingRestoreAfterSeek = true
                                                    c.seekToNextMediaItem()
                                                }
                                                BoundaryAction.WRAP_TO_FIRST -> {
                                                    if (c.mediaItemCount > 0) {
                                                        pendingRestoreAfterSeek = true
                                                        c.seekTo(0, 0L)
                                                    }
                                                }
                                                BoundaryAction.STAY ->
                                                    showError("单曲循环：已是最后一首")
                                                else -> {}
                                            }
                                            if (materialized) c.play()
                                        } catch (e: Exception) {
                                            showError(
                                                "切歌失败: " +
                                                    "${e.message ?: e.javaClass.simpleName}"
                                            )
                                        }
                                        }
                                    }
                                },
                                onPrev = {
                                    val c = controller
                                    if (c == null) {
                                        showError("播放器连接中，请稍候")
                                    } else {
                                        scope.launch {
                                            try {
                                                val materialized = ensureTimeline(c, restoreSaved = false)
                                            if (c.playbackState == Player.STATE_IDLE) c.prepare()
                                            when (prevBoundaryAction(playMode, c.hasPreviousMediaItem())) {
                                                BoundaryAction.ADVANCE -> {
                                                    pendingRestoreAfterSeek = true
                                                    c.seekToPreviousMediaItem()
                                                }
                                                BoundaryAction.WRAP_TO_LAST -> {
                                                    val last = c.mediaItemCount - 1
                                                    if (last >= 0) {
                                                        pendingRestoreAfterSeek = true
                                                        c.seekTo(last, 0L)
                                                    }
                                                }
                                                BoundaryAction.STAY ->
                                                    showError("单曲循环：已是第一首")
                                                else -> {}
                                            }
                                            if (materialized) c.play()
                                        } catch (e: Exception) {
                                            showError(
                                                "切歌失败: " +
                                                    "${e.message ?: e.javaClass.simpleName}"
                                            )
                                        }
                                        }
                                    }
                                },
                                onSeek = { targetMs ->
                                    scope.launch {
                                        try {
                                            val c = controller
                                            if (c == null) {
                                                showError("播放器连接中，请稍候")
                                            } else {
                                                ensureTimeline(c, restoreSaved = false)
                                                c.seekTo(targetMs)
                                            }
                                        } catch (e: Exception) {
                                            showError(
                                                "进度跳转失败: ${e.message ?: e.javaClass.simpleName}"
                                            )
                                        }
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
                                playMode = playMode,
                                onCycleMode = ::cyclePlayMode,
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
                                },
                                sleepActive = sleepMinutes > 0,
                                audioSessionId = PlaybackService.lastAudioSessionId,
                                onOpenSearch = { screen = Screen.Search }
                            )

                            is Screen.Queue -> QueueScreen(
                                modifier = Modifier.padding(innerPadding),
                                rows = buildQueueRowDisplays(
                                    queue,
                                    resolveQueueTimeline(queue, timelineSongs),
                                    currentIndex,
                                    positionMs,
                                    durationMs
                                ),
                                modeLabel = playMode.label,
                                onPlayAt = ::queueSeekTo,
                                onRemove = ::queueRemoveAt,
                                onClear = ::queueClearKeepCurrent,
                                onBack = { screen = Screen.Player },
                                onGoSearch = { screen = Screen.Search },
                                favIds = favIds,
                                onToggleFav = ::toggleFav,
                                onAddToPlaylist = ::openAddSheet
                            )

                            is Screen.Login -> LoginScreen(
                                modifier = Modifier.padding(innerPadding),
                                busy = loginBusy,
                                initialRegister = loginInitialRegister,
                                onLogin = ::doLogin,
                                onRegister = ::doRegister,
                                onBrowseAsGuest = { screen = Screen.Discover },
                                onBack = { screen = Screen.Mine }
                            )

                            is Screen.Mine -> MineScreen(
                                modifier = Modifier.padding(innerPadding),
                                user = currentUser,
                                authChecked = authChecked,
                                playlists = playlists,
                                playlistsLoading = playlistsLoading,
                                onLoginClick = {
                                    loginInitialRegister = false
                                    screen = Screen.Login
                                },
                                onRegisterClick = {
                                    loginInitialRegister = true
                                    screen = Screen.Login
                                },
                                onRetryPlaylists = { loadPlaylists() },
                                onSelectPlaylist = { pl ->
                                    loadSongs(pl)
                                    screen = Screen.PlaylistDetail(pl)
                                },
                                onCreatePlaylist = ::createPlaylistAction,
                                onRenamePlaylist = ::renamePlaylistAction,
                                onUpdateDescription = ::updateDescAction,
                                onDeletePlaylist = ::deletePlaylistAction,
                                onBatchDelete = ::batchDeleteAction,
                                onMovePlaylist = ::movePlaylistAction,
                                onImportPlaylist = ::importAction,
                                offlineCount = offlineCount,
                                onOpenOffline = { screen = Screen.Offline },
                                historyCount = historyItems.size,
                                onOpenHistory = { screen = Screen.History },
                                favCount = favIds.size,
                                onOpenFavorites = {
                                    if (currentUser == null) {
                                        loginInitialRegister = false
                                        screen = Screen.Login
                                    } else {
                                        screen = Screen.Favorites
                                    }
                                },
                                onGoSearch = { screen = Screen.Search },
                                onBrowse = { screen = Screen.Discover },
                                onOpenSettings = { screen = Screen.Settings },
                                onOpenPlaylists = {
                                    screen = Screen.Playlists
                                },
                                onOpenMessages = { screen = Screen.History }
                            )

                            is Screen.Playlists -> PlaylistsScreen(
                                modifier = Modifier.padding(innerPadding),
                                playlists = playlists,
                                playlistsLoading = playlistsLoading,
                                onBack = { screen = Screen.Mine },
                                onSelectPlaylist = { pl ->
                                    loadSongs(pl)
                                    screen = Screen.PlaylistDetail(pl)
                                },
                                onCreatePlaylist = ::createPlaylistAction,
                                onRenamePlaylist = ::renamePlaylistAction,
                                onUpdateDescription = ::updateDescAction,
                                onDeletePlaylist = ::deletePlaylistAction,
                                onBatchDelete = ::batchDeleteAction,
                                onMovePlaylist = ::movePlaylistAction,
                                onImportPlaylist = ::importAction,
                                onRetryPlaylists = { loadPlaylists() },
                                onGoSearch = { screen = Screen.Search }
                            )

                            is Screen.PlaylistDetail -> PlaylistDetailScreen(
                                modifier = Modifier.padding(innerPadding),
                                playlist = s.playlist,
                                songs = playlistSongs,
                                songsLoading = songsLoading,
                                songsError = playlistSongsError,
                                onRetry = { loadSongs(s.playlist) },
                                onBack = { screen = Screen.Playlists },
                                onGoSearch = { screen = Screen.Search },
                                onPlayAll = {
                                    if (playlistSongs.isNotEmpty()) playAt(playlistSongs, 0)
                                },
                                onPlaySong = { idx -> playAt(playlistSongs, idx) },
                                favIds = favIds,
                                downloadingKeys = downloadingIds,
                                downloadedKeys = downloadedKeys,
                                onToggleFav = ::toggleFav,
                                onAddToPlaylist = ::openAddSheet,
                                onDownload = { song -> downloadSong(song, false) },
                                onRemoveSong = ::removeSongAction,
                                onShare = ::sharePlaylistText,
                                onRenamePlaylist = ::renamePlaylistAction,
                                onUpdateDescription = ::updateDescAction,
                                onDeletePlaylist = ::deletePlaylistAction,
                                onImportPlaylist = ::importAction
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
                                onAddToPlaylist = ::openAddSheet,
                                onDeleteOne = { item ->
                                    doRemoveHistory(listOf(item.song.sourceId), clearAll = false)
                                },
                                onClearAll = {
                                    doRemoveHistory(historyItems.map { it.song.sourceId }, clearAll = true)
                                },
                                onRetry = { loadHistory() },
                                onBack = { screen = Screen.Mine }
                            )

                            is Screen.Favorites -> FavoritesScreen(
                                modifier = Modifier.padding(innerPadding),
                                songs = favSongs,
                                loading = favLoading,
                                favIds = favIds,
                                onPlayAt = { idx ->
                                    if (favSongs.isNotEmpty()) playAt(favSongs, idx.coerceIn(favSongs.indices))
                                },
                                onToggleFav = ::toggleFav,
                                onAddToPlaylist = ::openAddSheet,
                                onRetry = { loadFavorites() },
                                onBack = { screen = Screen.Mine }
                            )

                            is Screen.Settings -> SettingsScreen(
                                modifier = Modifier.padding(innerPadding),
                                user = currentUser,
                                cacheSizeLabel = cacheSizeLabel,
                                storageLabel = storageTotalLabel(cacheBytes, downloadBytes),
                                versionLabel = formatVersionLabel(appVersionName),
                                checkingUpdate = manualChecking,
                                onCheckUpdate = ::runManualUpdateCheck,
                                updateChannel = updateChannel,
                                onSelectChannel = ::setUpdateChannel,
                                onClearCache = ::clearMediaCache,
                                onBack = { screen = Screen.Mine },
                                onLoginClick = {
                                    loginInitialRegister = false
                                    screen = Screen.Login
                                },
                                onLogout = ::doLogout,
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
                                authDiagCode = authDiagCode,
                                authDiagTimeMs = authDiagTimeMs,
                                positionSaveTimeMs = positionSaveTimeMs
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
                            TopToast(
                                message = topToast,
                                modifier = Modifier.align(Alignment.TopCenter)
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
 * Reconnect policy (pure): rebind the MediaController when it is missing
 * (never connected / released after process death) or when the ON_START
 * probe threw (binder died with the service process while the reference
 * stayed non-null). A live controller is never rebound — rebinding it
 * would drop the listener and flicker playback state for no gain.
 */
fun shouldReconnectController(controllerNull: Boolean, probeFailed: Boolean): Boolean =
    controllerNull || probeFailed

/**
 * Connection-failure toast copy (pure): keeps the raw reason diagnosable
 * and tells the user the one thing that actually fixes OEM background
 * kills — self-start + ignore-battery-optimization (MIUI path included).
 * The OS kill itself is outside app control (see PlaybackService
 * onTaskRemoved); this message is the documented guidance.
 */
fun controllerFailureMessage(reason: String): String =
    "播放器连接失败: $reason。若后台常被杀，请给 VibeMusic 开自启动并忽略电池优化（MIUI：设置→省电与电池）"

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

/**
 * Pure landing guard for playback transport (same idiom as
 * [isStalePlaylistSongs]): drop an async result that belongs to a
 * superseded playAt / queueSeekTo / queueRemoveAt (generation mismatch),
 * so a rapid double-tap on two songs can't cross-seek.
 */
fun isStalePlayGen(completedGen: Int, latestGen: Int): Boolean =
    completedGen != latestGen

/**
 * Pure: the queued song at [index] must still be [target] before an async
 * restore seek lands — guards the window where a list swap kept the
 * generation but moved the rows.
 */
fun isRestoreTargetCurrent(queue: List<Song>, index: Int, target: Song): Boolean =
    queue.getOrNull(index)?.sourceId == target.sourceId

/**
 * Pure: queue-page display list. playAt assigns timelineSongs alongside
 * queue, so the page follows list swaps; the empty fallback covers the
 * cold-start window before any timeline exists.
 */
fun resolveQueueTimeline(queue: List<Song>, timelineSongs: List<Song>): List<Song> =
    timelineSongs.ifEmpty { queue }

/** Placed single-enqueue result: new queue + index of the inserted song. */
data class EnqueueSingle(val queue: List<Song>, val index: Int)

/**
 * Pure: tap-a-search-result inserts ONLY that song right after the current
 * index (documented choice: it plays next, existing order otherwise
 * untouched). Empty queue → single-item queue at 0; out-of-range index
 * clamps to the tail. Never drops or reorders existing items.
 */
fun enqueueSingleAfterCurrent(
    queue: List<Song>,
    currentIndex: Int,
    song: Song
): EnqueueSingle {
    if (queue.isEmpty()) return EnqueueSingle(listOf(song), 0)
    val at = (currentIndex.coerceIn(queue.indices) + 1).coerceIn(0, queue.size)
    val out = ArrayList<Song>(queue.size + 1)
    out.addAll(queue.subList(0, at))
    out.add(song)
    out.addAll(queue.subList(at, queue.size))
    return EnqueueSingle(out, at)
}

/** Pure: absolute sleep-timer expiry (persisted form); non-positive minutes mean off. */
fun computeSleepDeadlineMs(nowMs: Long, minutes: Int): Long =
    if (minutes <= 0) 0L else nowMs + minutes * 60_000L

/** Pure: whole remaining seconds until [deadlineMs]; expired/missing deadlines read 0. */
fun sleepRemainingSec(deadlineMs: Long, nowMs: Long): Long =
    ((deadlineMs - nowMs) / 1000L).coerceAtLeast(0L)

/** Resolved sleep state at startup: total minutes shown, persisted deadline, live countdown. */
data class SleepRestore(val minutes: Int, val deadlineMs: Long, val leftSec: Long)

/**
 * Pure: resolve startup sleep state from the persisted deadline plus the
 * legacy total-minutes value. A live deadline wins (remaining = deadline -
 * now; expired deadline clears); a legacy minutes value with no deadline
 * (pre-deadline installs) restarts one fresh full-length window once, and
 * the caller overwrites it in deadline form.
 */
fun resolveSleepRestore(deadlineMs: Long, legacyMinutes: Int, nowMs: Long): SleepRestore {
    val legacy = legacyMinutes.coerceAtLeast(0)
    if (deadlineMs > nowMs) {
        val left = sleepRemainingSec(deadlineMs, nowMs)
        val minutes = if (legacy > 0) legacy else ((left + 59L) / 60L).toInt().coerceAtLeast(1)
        return SleepRestore(minutes, deadlineMs, left)
    }
    // A past deadline means the timer already fired: clear even when the
    // legacy minutes value lingers (its zero-write may have lost the race
    // with process death). Only a never-set deadline (0) with legacy
    // minutes migrates to one fresh full-length window.
    if (deadlineMs > 0L) return SleepRestore(0, 0L, 0L)
    if (legacy > 0) {
        return SleepRestore(legacy, computeSleepDeadlineMs(nowMs, legacy), legacy * 60L)
    }
    return SleepRestore(0, 0L, 0L)
}

/**
 * Pure: manual "检查更新" feedback per decision. UPDATE_AVAILABLE returns
 * null (the update dialog is the feedback); the auto path never calls this
 * and stays silent.
 */
fun manualUpdateCheckMessage(decision: UpdateDecision): String? = when (decision) {
    UpdateDecision.UPDATE_AVAILABLE -> null
    UpdateDecision.UP_TO_DATE -> "已是最新版本"
    UpdateDecision.CHECK_FAILED -> "检查更新失败，请稍后重试"
}

/** Signature-mismatch install gate copy: old-key users must reinstall (clears offline content). */
const val UPDATE_SIGNATURE_MISMATCH_MESSAGE = "签名不一致，需卸载重装（会清空离线内容）"

/**
 * Pure: which release-check URL serves the OTHER update source (download
 * fallback for [downloadApk]). Gitee is primary, GitHub the fallback: a
 * primary apk URL on gitee falls back to the GitHub check, anything else
 * falls back to the Gitee check.
 */
fun otherUpdateSourceUrl(primaryApkUrl: String): String =
    if (primaryApkUrl.contains("gitee", ignoreCase = true)) UPDATE_LATEST_URL
    else UPDATE_GITEE_LATEST_URL

private val fallbackCheckHttp = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

/**
 * Best-effort fetch of the other source's release apk URL for the download
 * fallback (null on any failure — the primary URL is still tried alone).
 * Runs on IO; never throws.
 */
suspend fun fetchFallbackApkUrl(checkUrl: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val accept = if (checkUrl.contains("github", ignoreCase = true)) {
                "application/vnd.github+json"
            } else {
                null
            }
            val builder = Request.Builder().url(checkUrl).get()
            if (!accept.isNullOrBlank()) builder.header("Accept", accept)
            fallbackCheckHttp.newCall(builder.build()).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val body = res.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                try {
                    parseLatestRelease(body).apkUrl.ifBlank { null }
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
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

@Composable
fun HeroControls(
    isPlaying: Boolean,
    enabled: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    heroSize: Dp = 64.dp,
    playMode: PlayMode = PlayMode.SEQUENTIAL,
    onCycleMode: () -> Unit = {},
    onOpenQueue: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCycleMode,
            enabled = enabled,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = "播放模式：" + playMode.label + "，点击切换"
            }
        ) {
            AppIcon(playModeIconKind(playMode), if (enabled) InkOnDark else GrayMuted)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onPrev,
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            AppIcon(AppIconKind.PREV, if (enabled) InkOnDark else GrayMuted)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(heroSize)
                .clip(CircleShape)
                .background(if (enabled) NeonViolet else GrayMuted.copy(alpha = 0.4f))
                .clickable(enabled = enabled, onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                kind = if (isPlaying) AppIconKind.PAUSE else AppIconKind.PLAY,
                tint = Color.White,
                size = heroSize * 0.45f
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onNext,
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            AppIcon(AppIconKind.NEXT, if (enabled) InkOnDark else GrayMuted)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onOpenQueue,
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            AppIcon(AppIconKind.QUEUE, if (enabled) InkOnDark else GrayMuted)
        }
    }
}

@Composable
private fun PlayerSeekSection(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableLongStateOf(0L) }
    val shownMs = if (dragging) dragValue else positionMs
    val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
    Column(modifier = Modifier.fillMaxWidth()) {
        // 5dp custom track drawn under a transparent-track Slider:
        // the value-based Slider in material3 1.3.0 has no track/thumb
        // slots (only the experimental SliderState overload does), so
        // the thick violet track is an overlay and the Slider itself
        // supplies the drag handling + champagne thumb.
        val sliderValue = shownMs.coerceIn(0L, durationMs.coerceAtLeast(0L)).toFloat()
            .coerceIn(0f, sliderMax)
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(GrayMuted.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((sliderValue / sliderMax).coerceIn(0f, 1f))
                        .background(NeonViolet)
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = {
                    dragging = true
                    dragValue = it.toLong()
                },
                onValueChangeFinished = {
                    dragging = false
                    onSeek(dragValue.coerceIn(0L, durationMs.coerceAtLeast(0L)))
                },
                valueRange = 0f..sliderMax,
                enabled = enabled && durationMs > 0,
                colors = SliderDefaults.colors(
                    thumbColor = Champagne,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration((shownMs / 1000).toInt()),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = InkOnDark
            )
            Text(
                text = formatDuration((durationMs.coerceAtLeast(0L) / 1000).toInt()),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = GrayMuted
            )
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
    playMode: PlayMode = PlayMode.SEQUENTIAL,
    onCycleMode: () -> Unit = {},
    onDownloadCurrent: () -> Unit = {},
    downloadingCurrent: Boolean = false,
    downloadedCurrent: Boolean = false,
    isFav: Boolean = false,
    onToggleFav: () -> Unit = {},
    sleepActive: Boolean = false,
    audioSessionId: Int = 0,
    onOpenSearch: () -> Unit = {}
) {
    val song = queue.getOrNull(currentIndex)
    val lines = (lyricState as? LyricUiState.Ok)?.lines.orEmpty()
    val currentLine = lines.indexOfLast { it.timeSec * 1000 <= positionMs }
    val lyricsListState = rememberLazyListState()
    var view by remember(song?.sourceId) { mutableStateOf(PlayerView.COVER) }
    var showHints by remember { mutableStateOf(false) }
    var lastGestureMs by remember { mutableStateOf(-1L) }
    val density = LocalDensity.current
    val coverUrl = song?.coverUrl?.ifBlank { null }
    // 100ms lyric ticker for the ACTIVE-line fraction only (gated to
    // lyrics-visible; everything else stays on the 500ms activity ticker).
    // The authoritative 500ms positionMs snaps correct any drift.
    var lyricNowMs by remember(song?.sourceId) { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, song?.sourceId) { lyricNowMs = positionMs }
    val lyricLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(view, isPlaying, song?.sourceId) {
        if (view != PlayerView.LYRICS || !isPlaying) return@LaunchedEffect
        // Same RESUMED gating as the 500ms position ticker: the 100ms loop
        // must not spin while backgrounded. Re-runs (snaps to positionMs)
        // on return; animations untouched.
        lyricLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            lyricNowMs = positionMs
            while (true) {
                delay(LYRIC_FAST_TICK_MS)
                lyricNowMs = advanceLyricTicker(lyricNowMs, LYRIC_FAST_TICK_MS, true)
            }
        }
    }
    // Vinyl rotation state lives inside VinylCover (PlayerFx.kt): the angle
    // is read only there, so the rest of PlayerScreen does not recompose
    // per frame. Scale/corner stay here — they animate on toggle/play only.
    // Track B4: cover scale/corner animate on view toggle + play state
    // (GPU layer props; the toggle itself crossfades below).
    val coverScale by animateFloatAsState(
        targetValue = if (view == PlayerView.COVER && isPlaying) 1f else 0.92f,
        animationSpec = tween(300),
        label = "coverScale"
    )
    val coverCornerDp by animateDpAsState(
        targetValue = 28.dp,
        animationSpec = tween(300),
        label = "coverCorner"
    )

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
        // Static blurred backdrop (composed once per song — never spins).
        PlayerBackdrop(coverUrl = coverUrl)
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
        // Track B4: 200ms crossfade between 封面 and 歌词 (GPU alpha only).
        Crossfade(
            targetState = view,
            animationSpec = tween(PLAYER_VIEW_CROSSFADE_MS),
            label = "playerView"
        ) { v ->
            if (v == PlayerView.LYRICS && song != null) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .pointerInput(song.sourceId) {
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
                                            fromCoverZone = false
                                        )
                                    )
                                }
                            )
                        },
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
                            style = MaterialTheme.typography.titleMedium,
                            color = InkOnDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(AppIconKind.CLOSE, InkOnDark)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
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
                                modifier = Modifier.fillMaxSize(),
                                state = lyricsListState,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(lines, key = { idx, _ -> idx }) { idx, line ->
                                    val active = idx == currentLine
                                    val lineStartMs = (line.timeSec * 1000).toLong()
                                    val nextStartMs = lines.getOrNull(idx + 1)
                                        ?.let { (it.timeSec * 1000).toLong() }
                                    val lineEndMs = when {
                                        nextStartMs != null && nextStartMs > lineStartMs -> nextStartMs
                                        durationMs > 0 -> durationMs
                                        else -> positionMs.coerceAtLeast(lineStartMs) + 4_000L
                                    }
                                    KaraokeLine(
                                        line = line,
                                        positionMs = if (active) lyricNowMs else positionMs,
                                        lineEndMs = lineEndMs,
                                        isActive = active,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { view = PlayerView.COVER }
                                            .padding(vertical = 6.dp, horizontal = 16.dp)
                                    )
                                }
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
                    FavHeart(faved = isFav, onClick = onToggleFav, enabled = song != null)
                    IconButton(
                        onClick = onAddCurrentToPlaylist,
                        enabled = song != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(AppIconKind.ADD, InkOnDark)
                    }
                    IconButton(
                        onClick = onDownloadCurrent,
                        enabled = song != null && !downloadingCurrent,
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (downloadingCurrent) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            AppIcon(
                                AppIconKind.DOWNLOAD,
                                if (downloadedCurrent) NeonCyan else InkOnDark
                            )
                        }
                    }
                    IconButton(
                        onClick = onSleepClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(
                            AppIconKind.TIMER,
                            if (sleepActive) NeonViolet else InkOnDark
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                PlayerSeekSection(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    enabled = song != null,
                    onSeek = onSeek
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCycleMode,
                        enabled = song != null,
                        modifier = Modifier.size(48.dp).semantics {
                            contentDescription = "播放模式：" +
                                playMode.label + "，点击切换"
                        }
                    ) {
                        AppIcon(playModeIconKind(playMode), InkOnDark)
                    }
                    IconButton(
                        onClick = onPrev,
                        enabled = song != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(AppIconKind.PREV, if (song != null) InkOnDark else GrayMuted)
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (song != null) NeonViolet else GrayMuted.copy(alpha = 0.4f))
                            .clickable(enabled = song != null, onClick = onPlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcon(
                            kind = if (isPlaying) AppIconKind.PAUSE else AppIconKind.PLAY,
                            tint = Color.White,
                            size = 56.dp * 0.45f
                        )
                    }
                    IconButton(
                        onClick = onNext,
                        enabled = song != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(AppIconKind.NEXT, if (song != null) InkOnDark else GrayMuted)
                    }
                    IconButton(
                        onClick = onOpenQueue,
                        enabled = song != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(AppIconKind.QUEUE, InkOnDark)
                    }
                }
                buildPlayerMetaLine(
                    isCached = false,
                    sleepActive = sleepActive,
                    sleepLabel = sleepLabel
                )?.let { meta ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = GrayMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            // Same unified top inset as the LYRICS branch above.
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(AppIconKind.CLOSE, InkOnDark)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { view = PlayerView.LYRICS },
                            enabled = song != null,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("词")
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { showHints = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            AppIcon(AppIconKind.INFO, GrayMuted)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
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
                        VinylCover(
                            coverUrl = coverUrl,
                            isPlaying = isPlaying,
                            spinKey = song?.sourceId,
                            scale = coverScale,
                            cornerDp = coverCornerDp,
                            fraction = 0.86f
                        )
                    }
                }
                // Track B2: spectrum below the cover (hidden when no session).
                SpectrumVisualizer(
                    audioSessionId = audioSessionId,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = song?.name ?: "暂无播放",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkOnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = song?.artist ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GrayMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (song != null) {
                        Spacer(Modifier.width(8.dp))
                        VipBadge()
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable(enabled = song != null) { view = PlayerView.LYRICS },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lyricPreviewLine(lines, positionMs) ?: "暂无歌词",
                        style = MaterialTheme.typography.bodySmall,
                        color = Champagne.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FavHeart(faved = isFav, onClick = onToggleFav, enabled = song != null)
                }
                Spacer(Modifier.height(8.dp))
                PlayerSeekSection(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    enabled = song != null,
                    onSeek = onSeek
                )
                Spacer(Modifier.height(16.dp))
                HeroControls(
                    isPlaying = isPlaying,
                    enabled = song != null,
                    onPrev = onPrev,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    heroSize = 64.dp,
                    playMode = playMode,
                    onCycleMode = onCycleMode,
                    onOpenQueue = onOpenQueue
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onAddCurrentToPlaylist,
                        enabled = song != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(AppIconKind.ADD, InkOnDark)
                    }
                    IconButton(
                        onClick = onDownloadCurrent,
                        enabled = song != null && !downloadingCurrent,
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (downloadingCurrent) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            AppIcon(
                                AppIconKind.DOWNLOAD,
                                if (downloadedCurrent) NeonCyan else InkOnDark
                            )
                        }
                    }
                    IconButton(
                        onClick = onSleepClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(
                            AppIconKind.TIMER,
                            if (sleepActive) NeonViolet else InkOnDark
                        )
                    }
                }
                buildPlayerMetaLine(
                    isCached = false,
                    sleepActive = sleepActive,
                    sleepLabel = sleepLabel
                )?.let { meta ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = GrayMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (showHints) {
                    AlertDialog(
                        onDismissRequest = { showHints = false },
                        title = { Text("操作提示") },
                        text = {
                            Text("点封面看歌词\n左右滑动切换歌曲\n封面下滑关闭播放页")
                        },
                        confirmButton = {
                            TextButton(onClick = { showHints = false }) {
                                Text("知道了")
                            }
                        }
                    )
                }
            }
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
                AppIcon(AppIconKind.CHEVRON_LEFT, GrayMuted, size = 20.dp)
                Text("返回")
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
fun CacheManageRow(
    cacheSizeLabel: String,
    showConfirm: Boolean,
    onAskClear: () -> Unit,
    onConfirmClear: () -> Unit,
    onDismissClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
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
        DangerConfirmDialog(
            title = "清理缓存",
            text = "确定清除播放缓存吗？本地下载不受影响； rolling 缓存离线将无法播放，需联网重新缓存。",
            confirmText = "清除",
            onConfirm = onConfirmClear,
            onDismiss = onDismissClear
        )
    }
}

@Composable
fun UpdateChannelRow(
    channel: UpdateChannel,
    onSelect: (UpdateChannel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "更新通道",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = updateChannelSubtitle(channel),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = { onSelect(UpdateChannel.STABLE) },
            enabled = channel != UpdateChannel.STABLE
        ) {
            Text(if (channel == UpdateChannel.STABLE) "✓正式版" else "正式版")
        }
        TextButton(
            onClick = { onSelect(UpdateChannel.BETA) },
            enabled = channel != UpdateChannel.BETA
        ) {
            Text(if (channel == UpdateChannel.BETA) "✓测试版" else "测试版")
        }
    }
}

@Composable
fun VersionRow(
    versionLabel: String,
    checking: Boolean,
    onCheck: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
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
fun ProfileViewDialog(
    user: LoggedInUser,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val displayName = user.nickname.ifBlank { user.username }
    val sub = listOf(user.gender, user.birthday)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenSettings) { Text("去设置改资料") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = GrayMuted,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    user: LoggedInUser?,
    cacheSizeLabel: String,
    storageLabel: String,
    versionLabel: String,
    checkingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    updateChannel: UpdateChannel = UpdateChannel.STABLE,
    onSelectChannel: (UpdateChannel) -> Unit = {},
    onClearCache: () -> Unit,
    onBack: () -> Unit,
    onLoginClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    onChangePassword: (String, String) -> Unit = { _, _ -> },
    onUpdateProfile: (String?, String?, String?) -> Unit = { _, _, _ -> },
    onPickAvatar: () -> Unit = {},
    onPickBg: () -> Unit = {},
    authDiagCode: String = "",
    authDiagTimeMs: Long = 0L,
    positionSaveTimeMs: Long = 0L
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var showChangePwd by remember(user?.username) { mutableStateOf(false) }
    var showProfile by remember(user?.username) { mutableStateOf(false) }
    var showCookiePaste by remember { mutableStateOf(false) }
    var cookieStatus by remember { mutableStateOf<VibeApi.CookieStatus?>(null) }
    var cookieLoading by remember { mutableStateOf(false) }
    var cookieMsg by remember { mutableStateOf("") }
    val cookieScope = rememberCoroutineScope()
    fun loadCookieStatus() {
        if (user == null) {
            cookieStatus = null
            cookieMsg = ""
            cookieLoading = false
            return
        }
        cookieLoading = true
        cookieScope.launch {
            try {
                cookieStatus = VibeApi.cookieStatus()
            } catch (e: AuthException) {
                cookieStatus = null
                cookieMsg = e.message ?: "登录过期，请重登"
            } catch (e: Exception) {
                cookieStatus = null
                cookieMsg = friendlyNetworkMessage(e)
            } finally {
                cookieLoading = false
            }
        }
    }
    LaunchedEffect(user?.userId) { loadCookieStatus() }
    val cookieState = cookieStatus
    val cookieSub = when {
        user == null -> "登录后绑定"
        cookieLoading && cookieState == null -> "加载中…"
        cookieState != null -> cookieRowSubtitle(cookieState.has, cookieState.valid, cookieState.needsRebind)
        cookieMsg.isNotBlank() -> cookieMsg
        else -> "未绑定"
    }
    val rows = remember(cacheSizeLabel, storageLabel, versionLabel, cookieSub) {
        buildSettingsRows(
            cacheLabel = "已用 $cacheSizeLabel · 满150MB自动清理",
            storageLabel = storageLabel,
            versionLabel = versionLabel.ifBlank { formatVersionLabel("") },
            cookieLabel = cookieSub
        )
    }
    val themeRow = rows.first { it.id == "theme" }
    val cookieRow = rows.first { it.id == "cookie" }
    val storageRow = rows.first { it.id == "storage" }
    val aboutRow = rows.first { it.id == "about" }
    val cookieRebind = user != null && cookieState?.needsRebind == true
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                AppIcon(AppIconKind.CHEVRON_LEFT, GrayMuted, size = 20.dp)
                Text("我的")
            }
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }
        if (user != null) {
            SettingsSectionTitle("账号资料")
            MineSectionCard {
                SettingsRowShell(
                    title = "@${user.username}",
                    subtitle = user.nickname.ifBlank { "昵称未设置" },
                    showChevron = false
                )
                MineDivider()
                SettingsRowShell(
                    title = "改资料",
                    subtitle = "昵称/性别/生日",
                    onClick = { showProfile = true }
                )
                MineDivider()
                SettingsRowShell(
                    title = "改密码",
                    subtitle = "至少8位",
                    onClick = { showChangePwd = true }
                )
                MineDivider()
                SettingsRowShell(
                    title = "换头像",
                    subtitle = "最大 2MB",
                    onClick = onPickAvatar
                )
                MineDivider()
                SettingsRowShell(
                    title = "换背景",
                    subtitle = "我的页头图",
                    onClick = onPickBg
                )
            }
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
        } else {
            SettingsSectionTitle("账号")
            Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
                Text("去登录")
            }
        }
        SettingsSectionTitle("音乐服务")
        MineSectionCard {
            SettingsRowShell(
                title = cookieRow.title,
                subtitle = cookieRow.subtitle,
                onClick = {
                    if (user == null) onLoginClick()
                    else showCookiePaste = true
                }
            )
            if (cookieRebind) {
                MineDivider()
                SettingsRowShell(
                    title = "重新绑定",
                    subtitle = "网易 Cookie 已失效，请粘贴新的",
                    onClick = { showCookiePaste = true }
                )
            }
            if (user != null && cookieMsg.isNotBlank()) {
                MineDivider()
                SettingsRowShell(
                    title = "状态",
                    subtitle = cookieMsg,
                    showChevron = false
                )
            }
        }
        if (showCookiePaste) {
            CookiePasteDialog(
                showUnbind = cookieState?.has == true,
                onConfirm = { pasted ->
                    showCookiePaste = false
                    cookieScope.launch {
                        try {
                            VibeApi.saveNeteaseCookie(pasted)
                            cookieStatus = VibeApi.cookieStatus()
                            cookieMsg = "绑定成功"
                        } catch (e: AuthException) {
                            cookieMsg = e.message ?: "登录过期，请重登"
                        } catch (e: Exception) {
                            cookieMsg = "绑定失败: ${friendlyNetworkMessage(e)}"
                        }
                    }
                },
                onDismiss = { showCookiePaste = false },
                onDelete = {
                    showCookiePaste = false
                    cookieScope.launch {
                        try {
                            VibeApi.deleteNeteaseCookie()
                            cookieStatus = VibeApi.cookieStatus()
                            cookieMsg = "已解绑"
                        } catch (e: AuthException) {
                            cookieMsg = e.message ?: "登录过期，请重登"
                        } catch (e: Exception) {
                            cookieMsg = "解绑失败: ${friendlyNetworkMessage(e)}"
                        }
                    }
                }
            )
        }
        SettingsSectionTitle("存储")
        MineSectionCard {
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
            MineDivider()
            SettingsRowShell(title = storageRow.title, subtitle = storageRow.subtitle)
        }
        SettingsSectionTitle("关于与退出")
        MineSectionCard {
            SettingsRowShell(
                title = themeRow.title,
                subtitle = themeRow.subtitle,
                trailing = { Text(text = "✓", color = NeonViolet) }
            )
            MineDivider()
            SettingsRowShell(
                title = "登录诊断",
                subtitle = remember(authDiagCode, authDiagTimeMs, positionSaveTimeMs) {
                    formatAuthDiag(authDiagCode, authDiagTimeMs, positionSaveTimeMs)
                },
                showChevron = false
            )
            MineDivider()
            SettingsRowShell(
                title = aboutRow.title,
                subtitle = "${aboutRow.subtitle}\n$SETTINGS_GITHUB_URL\n开源致谢：感谢每一位贡献者"
            )
        }
        Spacer(Modifier.height(4.dp))
        UpdateChannelRow(
            channel = updateChannel,
            onSelect = onSelectChannel
        )
        VersionRow(
            versionLabel = versionLabel,
            checking = checkingUpdate,
            onCheck = onCheckUpdate
        )
        if (user != null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("退出登录")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MineScreen(
    modifier: Modifier = Modifier,
    user: LoggedInUser?,
    authChecked: Boolean,
    playlists: List<Playlist>,
    playlistsLoading: Boolean,
    onLoginClick: () -> Unit,
    onRetryPlaylists: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String, String) -> Unit = { _, _ -> },
    onRenamePlaylist: (Playlist, String) -> Unit = { _, _ -> },
    onUpdateDescription: (Playlist, String) -> Unit = { _, _ -> },
    onDeletePlaylist: (Playlist) -> Unit = {},
    onBatchDelete: (List<String>) -> Unit = {},
    onMovePlaylist: (Playlist, Int) -> Unit = { _, _ -> },
    onImportPlaylist: (String, String) -> Unit = { _, _ -> },
    offlineCount: Int = 0,
    onOpenOffline: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    historyCount: Int = 0,
    onOpenHistory: () -> Unit = {},
    favCount: Int = 0,
    onOpenFavorites: () -> Unit = {},
    onGoSearch: () -> Unit = {},
    onBrowse: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
    onOpenMessages: () -> Unit = {}
) {
    var showProfileView by remember { mutableStateOf(false) }
    val overviewScroll = rememberScrollState()
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
            .verticalScroll(overviewScroll)
    ) {
        if (!authChecked) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(8.dp))
            Text("正在恢复登录态…", style = MaterialTheme.typography.bodySmall)
            return
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "我的",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = InkOnDark,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onOpenMessages,
                modifier = Modifier.size(48.dp)
            ) {
                AppIcon(AppIconKind.MESSAGE, GrayMuted)
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(48.dp)
            ) {
                AppIcon(AppIconKind.SETTINGS, GrayMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (user == null) {
            MineUserCard(
                displayName = "未登录",
                sub = "登录后看我的歌单；访客可继续搜歌",
                avatarUrl = "",
                bgUrl = "",
                showVip = false,
                onClick = onLoginClick
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
                Text("去登录")
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRegisterClick, modifier = Modifier.fillMaxWidth()) {
                Text("注册新账号")
            }
            TextButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                Text("先逛逛")
            }
            Spacer(Modifier.height(12.dp))
            MineTripleRow(
                items = buildMineTriple(
                    favCount = 0,
                    historyCount = 0,
                    offlineCount = offlineCount,
                    loggedIn = false
                ),
                onCell = { id ->
                    when (id) {
                        "favorites" -> onOpenFavorites()
                        "history" -> onLoginClick()
                        else -> onOpenOffline()
                    }
                }
            )
            return
        }
        val displayName = user.nickname.ifBlank { user.username }
        val profileSub = "@${user.username}" +
            listOf(user.gender, user.birthday)
                .filter { it.isNotBlank() }
                .joinToString(" · ", prefix = " · ")
                .takeIf { user.gender.isNotBlank() || user.birthday.isNotBlank() }
                .orEmpty()
        MineUserCard(
            displayName = displayName,
            sub = profileSub,
            avatarUrl = user.avatar,
            bgUrl = user.bgImage,
            showVip = true,
            onClick = { showProfileView = true }
        )
        Spacer(Modifier.height(12.dp))
        if (showProfileView) {
            ProfileViewDialog(
                user = user,
                onOpenSettings = {
                    showProfileView = false
                    onOpenSettings()
                },
                onDismiss = { showProfileView = false }
            )
        }
        MineTripleRow(
            items = buildMineTriple(
                favCount = favCount,
                historyCount = historyCount,
                offlineCount = offlineCount,
                loggedIn = true
            ),
            onCell = { id ->
                when (id) {
                    "favorites" -> onOpenFavorites()
                    "history" -> onOpenHistory()
                    else -> onOpenOffline()
                }
            }
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "自建歌单 (${playlists.size})",
                style = MaterialTheme.typography.titleSmall,
                color = InkOnDark,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenPlaylists) {
                Text("管理")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (playlistsLoading) {
            SearchSkeleton()
        } else if (playlists.isEmpty()) {
            EmptyStateLine(
                text = "还没有歌单，新建一个开始收藏吧",
                actionLabel = "去搜索",
                onAction = onGoSearch
            )
        } else {
            MineSectionCard {
                playlists.forEachIndexed { index, pl ->
                    if (index > 0) MineDivider()
                    EntryRow(
                        modifier = Modifier.heightIn(min = 56.dp),
                        title = pl.name.ifBlank { "(untitled)" },
                        subtitle = "${pl.songCount} 首",
                        coverUrl = pl.coverUrl,
                        onClick = { onSelectPlaylist(pl) },
                        trailing = { AppIcon(AppIconKind.CHEVRON_RIGHT, GrayMuted) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MineAvatar(avatarUrl: String, displayName: String) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(NeonViolet, NeonCyan)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = avatarInitial(displayName),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(absImgUrl(avatarUrl).ifBlank { null })
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
        )
    }
}

@Composable
private fun MineUserCard(
    displayName: String,
    sub: String,
    avatarUrl: String,
    bgUrl: String,
    showVip: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(ObsidianSurface)
    ) {
        if (shouldShowMineBg(bgUrl)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(absImgUrl(bgUrl))
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MineAvatar(avatarUrl = avatarUrl, displayName = displayName)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = InkOnDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (showVip) {
                        Spacer(Modifier.width(8.dp))
                        VipBadge()
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MineTripleRow(
    items: List<MineTripleItem>,
    onCell: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurface)
            .padding(vertical = 8.dp)
    ) {
        items.forEach { e ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .clickable { onCell(e.id) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = e.count,
                    style = MaterialTheme.typography.titleLarge,
                    color = InkOnDark,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = e.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    var selected by remember(current) { mutableStateOf(current) }
    var custom by remember { mutableStateOf("") }
    val customTrim = custom.trim()
    val customInvalid = customTrim.isNotEmpty() && parseSleepMinutes(customTrim) == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("睡眠定时") },
        text = {
            Column {
                sleepPresetOptions().forEach { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                selected = opt
                                custom = ""
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == opt && customTrim.isEmpty(),
                            onClick = {
                                selected = opt
                                custom = ""
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = sleepOptionLabel(opt),
                            style = MaterialTheme.typography.bodyLarge
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
                if (customInvalid) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "请输入 ${SLEEP_CUSTOM_MIN}~${SLEEP_CUSTOM_MAX} 的整数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (customInvalid) {
                    onInvalid()
                    return@Button
                }
                onConfirm(resolveSleepChoice(selected, customTrim) ?: 0)
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
    FormDialog(
        title = title,
        confirmText = confirmText,
        confirmEnabled = firstOk,
        onConfirm = { onConfirm(first.trim(), second.trim()) },
        onDismiss = onDismiss
    ) {
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
}

@Composable
fun ImportDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var source by remember { mutableStateOf("netease") }
    var id by remember { mutableStateOf("") }
    val parsed = VibeApi.extractPlaylistId(id)
    FormDialog(
        title = "导入外部歌单",
        confirmText = "导入",
        confirmEnabled = parsed.isNotEmpty(),
        onConfirm = { onConfirm(source, parsed) },
        onDismiss = onDismiss
    ) {
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
                betaReleaseNote(release.prerelease)?.let { note ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note,
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
    FormDialog(
        title = "修改密码",
        confirmText = "保存",
        confirmEnabled = ok,
        onConfirm = { onConfirm(oldPwd, newPwd) },
        onDismiss = onDismiss
    ) {
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
    FormDialog(
        title = "改资料",
        confirmText = "保存",
        confirmEnabled = birthdayOk,
        onConfirm = {
            val nn = nickname.trim().takeIf { it != initialNickname }
            val gg = gender.takeIf { it != initialGender && it in PROFILE_GENDERS }
            val bb = birthday.trim().takeIf { it != initialBirthday.trim() }
            onConfirm(nn, gg, bb)
        },
        onDismiss = onDismiss
    ) {
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
}

@Composable
fun CookiePasteDialog(
    showUnbind: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit = {}
) {
    var input by remember { mutableStateOf("") }
    FormDialog(
        title = "绑定网易 Cookie",
        confirmText = "保存",
        confirmEnabled = input.isNotBlank(),
        onConfirm = { onConfirm(input.trim()) },
        onDismiss = onDismiss
    ) {
        Text(
            text = "粘贴你自己的网易 Cookie，不要填密码",
            style = MaterialTheme.typography.bodySmall,
            color = GrayMuted
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Cookie") },
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
        if (showUnbind) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onDelete,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("解绑", color = Color(0xFFEF4444))
            }
        }
    }
}

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    items: List<VibeApi.HistoryItem>,
    loading: Boolean,
    favIds: Set<String> = emptySet(),
    onPlayAt: (Int) -> Unit,
    onToggleFav: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onDeleteOne: (VibeApi.HistoryItem) -> Unit = {},
    onClearAll: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<VibeApi.HistoryItem?>(null) }
    var sheetFor by remember { mutableStateOf<VibeApi.HistoryItem?>(null) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                AppIcon(AppIconKind.CHEVRON_LEFT, GrayMuted, size = 20.dp)
                Text("我的")
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
                SongRow(
                    model = buildSongRowModel(
                        song,
                        subtitleOverride = if (item.playedAt.isNotBlank()) {
                            item.playedAt.replace("T", " ").take(16) + " · " + song.artist
                        } else {
                            "第${index + 1}首 · " + song.artist
                        }
                    ),
                    onClick = { onPlayAt(index) },
                    onOverflow = { sheetFor = item }
                )
            }
        }
    }
    sheetFor?.let { target ->
        val faved = target.song.sourceId.isNotBlank() && target.song.sourceId in favIds
        SongMenuSheet(
            title = target.song.name.ifBlank { "(untitled)" },
            actions = listOf(
                SongMenuAction("fav", if (faved) "取消收藏" else "收藏"),
                SongMenuAction("add", "加入歌单"),
                SongMenuAction("delete", "删除记录", danger = true)
            ),
            onAction = { id ->
                when (id) {
                    "fav" -> onToggleFav(target.song)
                    "add" -> onAddToPlaylist(target.song)
                    "delete" -> confirmDelete = target
                }
                sheetFor = null
            },
            onDismiss = { sheetFor = null }
        )
    }
    if (showClearConfirm) {
        DangerConfirmDialog(
            title = "清空历史",
            text = "确定清空全部 ${items.size} 条播放记录吗？不可恢复。",
            confirmText = "清空",
            onConfirm = {
                showClearConfirm = false
                onClearAll()
            },
            onDismiss = { showClearConfirm = false }
        )
    }
    confirmDelete?.let { target ->
        DangerConfirmDialog(
            title = "删除记录",
            text = "确定删除《${target.song.name.ifBlank { "(untitled)" }}》的播放记录吗？",
            confirmText = "删除",
            onConfirm = {
                confirmDelete = null
                onDeleteOne(target)
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    loading: Boolean,
    favIds: Set<String> = emptySet(),
    onPlayAt: (Int) -> Unit,
    onToggleFav: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit
) {
    var sheetFor by remember { mutableStateOf<Song?>(null) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                AppIcon(AppIconKind.CHEVRON_LEFT, GrayMuted, size = 20.dp)
                Text("我的")
            }
            Text(
                text = "我的收藏",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (songs.isNotEmpty()) {
                Text(
                    text = "${songs.size} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayMuted
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (loading) {
            SearchSkeleton()
            return
        }
        if (songs.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("还没有收藏，去搜一首点❤吧")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry) {
                Text("刷新")
            }
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(songs, key = { idx, s -> s.sourceId + s.platform + idx }) { index, song ->
                SongRow(
                    model = buildSongRowModel(song),
                    meta = if (song.durationSec > 0) {
                        formatDuration(song.durationSec)
                    } else {
                        null
                    },
                    onClick = { onPlayAt(index) },
                    onOverflow = { sheetFor = song }
                )
            }
        }
    }
    sheetFor?.let { target ->
        val faved = target.sourceId.isNotBlank() && target.sourceId in favIds
        SongMenuSheet(
            title = target.name.ifBlank { "(untitled)" },
            actions = listOf(
                SongMenuAction("fav", if (faved) "取消收藏" else "收藏"),
                SongMenuAction("add", "加入歌单")
            ),
            onAction = { id ->
                when (id) {
                    "fav" -> onToggleFav(target)
                    "add" -> onAddToPlaylist(target)
                }
                sheetFor = null
            },
            onDismiss = { sheetFor = null }
        )
    }
}
