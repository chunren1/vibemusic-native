package com.cyk666.vibemusic

import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Track B2: spectrum visualizer (zero new deps — framework Visualizer+Canvas).
// GPU-friendly: solid-color rounded bars, no layout animation. The Visualizer
// attaches to the ExoPlayer audio session (needs only MODIFY_AUDIO_SETTINGS,
// NO RECORD_AUDIO) and is released on dispose/pause to avoid battery drain.
// Init failure → hide bars silently, never crash.

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
    val scope = rememberCoroutineScope()
    DisposableEffect(audioSessionId, isPlaying) {
        if (!isPlaying) {
            bars = List(SPECTRUM_BAR_COUNT) { 0f }
            return@DisposableEffect onDispose {}
        }
        val viz: Visualizer? = try {
            Visualizer(audioSessionId)
        } catch (_: Exception) {
            null
        }
        if (viz == null) return@DisposableEffect onDispose {}
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
