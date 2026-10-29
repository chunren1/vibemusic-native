package com.cyk666.vibemusic

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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

/**
 * UI-5：可用高度低于此值（横屏、折叠屏半开、分屏）时，播放页封面分支改为可滚动布局。
 * 预算：非加权子项合计约 440dp + 上下 12dp padding + 状态栏 ≈ 470dp，取 480dp 为界；
 * 竖屏手机（≥600dp）与横屏平板都不会误触发。
 */
private const val PLAYER_COMPACT_HEIGHT_DP = 480

/**
 * UI-5：紧凑布局下封面的固定边长（正方形）。横屏时不能再用 weight 吃剩余空间 ——
 * verticalScroll 会给子项无限高约束，加权子项会被算成 0 高（封面直接消失）。
 */
private val PLAYER_COMPACT_COVER_DP = 180.dp

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
private fun LyricOffsetRow(
    offsetMs: Long,
    onStep: (Int) -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = { onStep(-1) },
            enabled = offsetMs > -LYRIC_OFFSET_MAX_MS,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("−0.1秒")
        }
        Text(
            text = "歌词校准 " + formatLyricOffset(offsetMs),
            style = MaterialTheme.typography.bodySmall,
            color = GrayMuted,
            maxLines = 1
        )
        TextButton(
            onClick = { onStep(1) },
            enabled = offsetMs < LYRIC_OFFSET_MAX_MS,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("+0.1秒")
        }
        if (offsetMs != 0L) {
            TextButton(
                onClick = onReset,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("复位")
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
    lyricOffsetMs: Long = 0L,
    onLyricOffsetChange: (Long) -> Unit = {},
    onSwitchLyricSource: () -> Unit = {},
    lyricSwitching: Boolean = false,
    onRetryLyric: () -> Unit = {},
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
    // UI-3：过滤只在歌词变化时做一次。原实现每次重组都新建 List + 全表 filterNot，
    // 而 500ms 的位置 tick 会稳定触发重组 —— 几百行歌词每 tick 白白分配/扫描一遍。
    val lines = remember(lyricState) {
        (lyricState as? LyricUiState.Ok)?.lines.orEmpty()
            .filterNot { isBlankLyricLine(it.text) || isMusicSymbolLine(it.text) }
    }
    // Manual per-song karaoke offset: the effective clock drives the active
    // line, the sweep, and the cover preview — one shift, everywhere.
    val offsetMs = clampLyricOffset(lyricOffsetMs)
    val effPositionMs = applyLyricOffset(positionMs, offsetMs)
    // UI-3：positionMs 是普通参数（每个 tick 都是新值），derivedStateOf 的计算式
    // 只有读到 State 才会被失效重算，所以先把它装进 State。这样包在
    // derivedStateOf 里的读取只有「effPositionState.value + lines」；计算结果
    // （当前行下标）与上次相同时不会向下游通知，因此歌词行的"当前行高亮"只在
    // 跨行那一刻重组，而不是每个 tick 都重组一遍。
    val effPositionState = rememberUpdatedState(effPositionMs)
    val currentLine by remember(lines) {
        derivedStateOf {
            lines.indexOfLast { it.timeSec * 1000 <= effPositionState.value }
        }
    }
    val lyricsListState = rememberLazyListState()
    // UI-4：不再拿 song?.sourceId 当 key —— 自动切歌（一首播完进下一首）不该把
    // 正在看歌词的用户弹回封面。至于"重新进入播放页回封面"，由组合生命周期保证：
    // MainActivity 的 Crossfade(targetState = screen) 在离开 Screen.Player 后会
    // 销毁本组合，remember 自然重置成 COVER，无需挂在歌曲变化上。
    var view by remember { mutableStateOf(PlayerView.COVER) }
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

    // UI-3：自动跟随必须让位于用户手势。
    // ① isScrollInProgress：手指还按着或惯性未停时不抢；
    // ② lastManualScrollAt：用户拖动过列表后 LYRIC_MANUAL_SCROLL_HOLD_MS 内不自动跟随
    //    （想往上翻副歌，不能每换一句就被硬拉回当前行）。
    // 时间基准用 elapsedRealtime：墙钟会被用户改系统时间影响（与 CODE-7 同一笔债务）。
    val lastManualScrollAt = remember { mutableLongStateOf(0L) }
    LaunchedEffect(lyricsListState) {
        // interactionSource 只在"用户拖动"时发 DragInteraction.Start；
        // 程序化的 scrollToItem 不会产生它，所以不会自己把自己锁住 5 秒。
        lyricsListState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                lastManualScrollAt.longValue = SystemClock.elapsedRealtime()
            }
        }
    }
    LaunchedEffect(view, lines) {
        if (view != PlayerView.LYRICS) return@LaunchedEffect
        // snapshotFlow 自带去重：读的是 derivedStateOf，值不变就不发事件，
        // 因此这里只在"当前行变了"时执行，不会跟着 500ms tick 反复滚。
        var firstEmission = true
        snapshotFlow { currentLine }.collect { line ->
            // 刚进入歌词页 / 刚换完歌词的第一次必须立刻定位，否则"防抢手势"
            // 会退化成"永远不跟随"。
            val entering = firstEmission
            firstEmission = false
            if (line < 0) return@collect
            if (lyricsListState.isScrollInProgress) return@collect
            if (!entering && SystemClock.elapsedRealtime() - lastManualScrollAt.longValue <
                LYRIC_MANUAL_SCROLL_HOLD_MS
            ) {
                return@collect
            }
            try {
                lyricsListState.scrollToItem(line)
            } catch (_: Exception) {
                SilentFail.note("PlayerScreen.PlayerScreen:autoScroll")
            }
        }
    }

    fun fireGesture(g: PlayerGesture) {
        if (g == PlayerGesture.NONE) return
        // CODE-7：手势去抖的两端都是内存值，用 elapsedRealtime（改系统时间不影响）。
        val now = android.os.SystemClock.elapsedRealtime()
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
                    CoverImage(
                        coverUrl = song.coverUrl,
                        size = 48.dp,
                        cornerDp = 8.dp,
                        iconSize = 20.dp
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
                        if (isBilibiliSong(song)) {
                            Spacer(Modifier.height(2.dp))
                            BiliBadge()
                        }
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
                            text = LyricEmptyReason.LOAD_FAILED.copy,
                            actionLabel = "重试",
                            onAction = onRetryLyric,
                            secondaryActionLabel = "返回封面",
                            onSecondaryAction = { view = PlayerView.COVER }
                        )
                        is LyricUiState.Ok -> if (lines.isEmpty()) {
                            val reason = describeLyricEmpty(lyricState, song, 0)
                            EmptyStateLine(
                                text = reason?.copy
                                    ?: LyricEmptyReason.NO_LYRIC.copy,
                                actionLabel = if (lyricSwitching) "换源中…" else "换源试试",
                                onAction = onSwitchLyricSource,
                                actionEnabled = !lyricSwitching,
                                secondaryActionLabel = "返回封面",
                                onSecondaryAction = { view = PlayerView.COVER }
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                LyricOffsetRow(
                                    offsetMs = offsetMs,
                                    onStep = { onLyricOffsetChange(stepLyricOffset(offsetMs, it)) },
                                    onReset = { onLyricOffsetChange(0L) }
                                )
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
                                            else -> effPositionMs.coerceAtLeast(lineStartMs) + 4_000L
                                        }
                                        KaraokeLine(
                                            line = line,
                                            positionMs = if (active) {
                                                applyLyricOffset(lyricNowMs, offsetMs)
                                            } else {
                                                effPositionMs
                                            },
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
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FavHeart(faved = isFav, onClick = onToggleFav, enabled = true)
                    IconButton(
                        onClick = onAddCurrentToPlaylist,
                        enabled = true,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(AppIconKind.ADD, InkOnDark)
                    }
                    IconButton(
                        onClick = onDownloadCurrent,
                        enabled = !downloadingCurrent,
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
                    enabled = true,
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
                        enabled = true,
                        modifier = Modifier.size(48.dp).semantics {
                            contentDescription = "播放模式：" +
                                playMode.label + "，点击切换"
                        }
                    ) {
                        AppIcon(playModeIconKind(playMode), InkOnDark)
                    }
                    IconButton(
                        onClick = onPrev,
                        enabled = true,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(AppIconKind.PREV, InkOnDark)
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(NeonViolet)
                            .clickable(onClick = onPlayPause),
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
                        enabled = true,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(AppIconKind.NEXT, InkOnDark)
                    }
                    IconButton(
                        onClick = onOpenQueue,
                        enabled = true,
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
            // UI-5：横屏（可用高度 < PLAYER_COMPACT_HEIGHT_DP）时，非加权子项合计约
            // 440dp 会把进度条和播放键挤出屏幕。竖屏保持原布局（封面用 weight 吃掉
            // 剩余空间，视觉零变化）；横屏改为整列可滚动 + 封面固定高度。
            // 二者必须互斥：verticalScroll 给子项的是无限高约束，加权子项会被算成 0 高
            // （封面直接消失），所以横屏这一支不保留任何 weight。
            val compactHeight =
                LocalConfiguration.current.screenHeightDp < PLAYER_COMPACT_HEIGHT_DP
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .then(
                        if (compactHeight) Modifier.verticalScroll(rememberScrollState())
                        else Modifier
                    ),
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
                        // 竖屏：weight 吃剩余高度（原行为）；横屏：固定正方形，因为滚动
                        // 容器里没有"剩余高度"可分配（见上方 compactHeight 注释）。
                        // 用 size 而不是 height：VinylCover 是"按宽度取边长"的
                        // （fillMaxWidth(fraction) + aspectRatio(1f)），只限高会让
                        // 0.62×宽度 超过可用高度，aspectRatio 退化成非正方形。
                        .then(
                            if (compactHeight) Modifier.size(PLAYER_COMPACT_COVER_DP)
                            else Modifier.weight(1f).fillMaxWidth()
                        ),
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
                            // 紧凑布局下 Box 已是定宽正方形，fraction=1 才能铺满且不畸变。
                            fraction = if (compactHeight) 1f else 0.62f
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
                Text(
                    text = song?.artist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GrayMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (song != null && isBilibiliSong(song)) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        BiliBadge()
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
                        text = lyricPreviewLine(lines, effPositionMs) ?: "暂无歌词",
                        style = MaterialTheme.typography.bodySmall,
                        color = Champagne.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
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
