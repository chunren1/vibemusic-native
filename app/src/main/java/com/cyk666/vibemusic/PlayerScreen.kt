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
                            fraction = 0.62f
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
