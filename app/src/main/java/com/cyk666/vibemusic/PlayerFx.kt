package com.cyk666.vibemusic

import android.media.audiofx.Visualizer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Track B2: spectrum visualizer (zero new deps — framework Visualizer+Canvas).
// GPU-friendly: solid-color rounded bars, no layout animation. The Visualizer
// attaches to the ExoPlayer audio session (needs only MODIFY_AUDIO_SETTINGS,
// NO RECORD_AUDIO) and is released on dispose/pause/background(STOP) to
// avoid battery drain. Init failure → settled flat strip, never crash.

private val PfxViolet = Color(0xFF8B5CF6)
private val PfxCyan = Color(0xFF06B6D4)

/**
 * Neon spectrum bars driven by the player audio session.
 *
 * @param audioSessionId ExoPlayer audio session (> 0). 0/negative → renders
 * nothing (hidden, no Visualizer created).
 * @param isPlaying false → Visualizer released (battery), flat bars.
 */
@Composable
fun SpectrumVisualizer(
    audioSessionId: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    if (audioSessionId <= 0) return
    var bars by remember(audioSessionId) {
        mutableStateOf(List(SPECTRUM_BAR_COUNT) { 0f })
    }
    // Construction failure settles here: flat bars, no retry loop, no
    // loading-looking animation — the Canvas below draws an empty strip.
    var vizUnavailable by remember(audioSessionId) { mutableStateOf(false) }
    // Foreground gate: the composition survives backgrounding, so isPlaying
    // alone would keep the 30fps FFT loop + state writes running off-screen.
    // ON_STOP the effect key below flips → job cancelled + Visualizer
    // released; ON_START it recreates. Same predicate as the unit-tested
    // spectrumShouldSample (VisualFx.kt).
    val lifecycleOwner = LocalLifecycleOwner.current
    var isForeground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isForeground = event.targetState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val scope = rememberCoroutineScope()
    DisposableEffect(audioSessionId, isPlaying, isForeground) {
        if (!spectrumShouldSample(isPlaying, isForeground, audioSessionId)) {
            bars = List(SPECTRUM_BAR_COUNT) { 0f }
            return@DisposableEffect onDispose {}
        }
        val viz: Visualizer? = try {
            Visualizer(audioSessionId)
        } catch (_: Exception) {
            null
        }
        if (viz == null) {
            vizUnavailable = true
            bars = List(SPECTRUM_BAR_COUNT) { 0f }
            return@DisposableEffect onDispose {}
        }
        vizUnavailable = false
        var job: Job? = null
        var attached = false
        try {
            val range = Visualizer.getCaptureSizeRange()
            viz.captureSize = range[1].coerceIn(range[0], range[1])
            viz.enabled = true
            attached = true
            val buf = ByteArray(viz.captureSize)
            job = scope.launch {
                while (true) {
                    try {
                        viz.getFft(buf)
                        bars = mapFftToBars(buf)
                    } catch (_: Exception) {
                    }
                    delay(SPECTRUM_SAMPLE_INTERVAL_MS)
                }
            }
        } catch (_: Exception) {
            job?.cancel()
            if (attached) {
                try {
                    viz.enabled = false
                } catch (_: Exception) {
                }
            }
            try {
                viz.release()
            } catch (_: Exception) {
            }
            return@DisposableEffect onDispose {}
        }
        onDispose {
            job?.cancel()
            try {
                viz.enabled = false
            } catch (_: Exception) {
            }
            try {
                viz.release()
            } catch (_: Exception) {
            }
        }
    }
    Canvas(modifier = modifier) {
        // Settled failure state: Visualizer unavailable → empty strip, never
        // an animated/loading look. (bars are already flat zeros; early-out.)
        if (vizUnavailable) return@Canvas
        val n = bars.size
        if (n == 0) return@Canvas
        val gapPx = drawDp(3f)
        val bw = (size.width - gapPx * (n - 1)) / n
        if (bw <= 0f) return@Canvas
        bars.forEachIndexed { i, level ->
            val h = (size.height * level.coerceIn(0f, 1f))
                .coerceAtLeast(if (level > 0f) drawDp(2f) else 0f)
            if (h <= 0f) return@forEachIndexed
            drawRoundRect(
                color = lerp(PfxViolet, PfxCyan, i.toFloat() / (n - 1).coerceAtLeast(1)),
                topLeft = Offset(i * (bw + gapPx), size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f)
            )
        }
    }
}

private fun DrawScope.drawDp(dp: Float): Float = dp * density

/**
 * Static blurred cover backdrop. Owns NOTHING animatable: it composes once
 * per song and never recomposes with the rotation, so the RenderEffect blur
 * is rendered once and cached on the GPU layer. Small decoded bitmap
 * (PLAYER_BACKDROP_REQ_PX) — indistinguishable under blur + scrim.
 */
@Composable
fun BoxScope.PlayerBackdrop(coverUrl: String?) {
    val ctx = LocalContext.current
    val request = remember(coverUrl, ctx) {
        ImageRequest.Builder(ctx)
            .data(coverUrl?.ifBlank { null })
            .size(PLAYER_BACKDROP_REQ_PX)
            .crossfade(true)
            .build()
    }
    val backdropModifier = if (supportsRenderEffectBlur()) {
        Modifier.matchParentSize()
            .graphicsLayer {
                renderEffect = android.graphics.RenderEffect.createBlurEffect(
                    PLAYER_BACKDROP_BLUR_PX,
                    PLAYER_BACKDROP_BLUR_PX,
                    android.graphics.Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            }
            .alpha(0.35f)
    } else {
        Modifier.matchParentSize().alpha(0.25f)
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = backdropModifier
    )
}

/**
 * Rotating vinyl cover. The infinite clock is created UNCONDITIONALLY so a
 * play-state flap or a Crossfade branch switch can never dispose it mid-spin
 * (the 1.0.23 regression: the clock lived inside `if (isPlaying)` and its
 * SideEffect mirror starved, freezing the cover). Only the ANGLE is gated:
 * the live clock value is read during composition solely while
 * [vinylShouldSpin] holds, so the parent never recomposes per frame and a
 * paused cover costs no recomposition. Pause folds the current visual angle
 * into [baseAngle] (freeze, no snap); resume re-anchors the clock to that
 * base (continue, no jump); a new [spinKey] resets everything per song.
 */
@Composable
fun VinylCover(
    coverUrl: String?,
    isPlaying: Boolean,
    spinKey: Any?,
    scale: Float = 1f,
    cornerDp: Dp = 160.dp,
    modifier: Modifier = Modifier
) {
    val running = vinylShouldSpin(isPlaying, spinKey != null)
    val spinTransition = rememberInfiniteTransition(label = "vinylSpin")
    val spinState = spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(VINYL_ROTATION_MS.toInt(), easing = LinearEasing)
        ),
        label = "vinylAngle"
    )
    var baseAngle by remember(spinKey) { mutableFloatStateOf(0f) }
    var anchorSpin by remember(spinKey) { mutableFloatStateOf(0f) }
    var wasRunning by remember(spinKey) { mutableStateOf(false) }
    val angle: Float = if (running) {
        val spin by spinState
        if (!wasRunning) {
            anchorSpin = spin
        }
        SideEffect { wasRunning = true }
        vinylSpinAngle(baseAngle, spin, anchorSpin)
    } else {
        SideEffect {
            if (wasRunning) {
                baseAngle = vinylSpinAngle(baseAngle, spinState.value, anchorSpin)
                wasRunning = false
            }
        }
        baseAngle
    }
    val ctx = LocalContext.current
    val request = remember(coverUrl, ctx) {
        ImageRequest.Builder(ctx)
            .data(coverUrl?.ifBlank { null })
            .size(VINYL_COVER_REQ_PX)
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth(0.72f)
            .aspectRatio(1f)
            .graphicsLayer {
                rotationZ = angle
                scaleX = scale
                scaleY = scale
            }
            .shadow(16.dp, RoundedCornerShape(cornerDp))
            .clip(RoundedCornerShape(cornerDp))
    )
}
