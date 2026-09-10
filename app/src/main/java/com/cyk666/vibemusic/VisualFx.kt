package com.cyk666.vibemusic

import kotlin.math.sqrt

// Track B pure visual helpers (zero new dependencies — framework APIs only).
// Composables live in PlayerFx.kt / UiAtoms.kt; everything testable lives here
// with no Android dependency so plain JUnit covers it.

/** Spectrum bars rendered per frame (24–32 window per spec). */
const val SPECTRUM_BAR_COUNT = 28

/** Visualizer sampling cadence: ~30fps ticker (33ms loop in PlayerFx). */
const val SPECTRUM_SAMPLE_INTERVAL_MS = 33L

/** Blur radius (px) for the Player RenderEffect backdrop on API 31+. */
const val PLAYER_BACKDROP_BLUR_PX = 28f

/** Tab-switch crossfade duration (ms). */
const val TAB_CROSSFADE_MS = 150

/** Player cover↔lyrics view crossfade duration (ms). */
const val PLAYER_VIEW_CROSSFADE_MS = 200

/**
 * Pure version gate for the RenderEffect blur backdrop: API 31+ only.
 * Callers pass [Build.VERSION.SDK_INT] implicitly; tests pass explicit ints.
 * API 26–30 keeps the existing alpha+scrim path — never crash.
 */
fun supportsRenderEffectBlur(sdkInt: Int = android.os.Build.VERSION.SDK_INT): Boolean =
    sdkInt >= android.os.Build.VERSION_CODES.S

/**
 * Pure: map a Visualizer FFT byte array to [barCount] 0..1 bar heights.
 *
 * getFft layout is (real, imag) byte pairs per bin; bin 0 is DC (skipped).
 * Magnitude per bar bin is normalized by 128 and gamma-lifted (sqrt) so quiet
 * passages stay visible. Defensive: empty/short input → all zeros, never
 * throws; outputs always clamped to 0..1; non-positive [barCount] → empty.
 */
fun mapFftToBars(fft: ByteArray, barCount: Int = SPECTRUM_BAR_COUNT): List<Float> {
    if (barCount <= 0) return emptyList()
    if (fft.size < 4) return List(barCount) { 0f }
    val bins = fft.size / 2
    val usable = (bins - 1).coerceAtLeast(1)
    return List(barCount) { i ->
        val bin = 1 + (i * usable / barCount).coerceIn(0, usable - 1)
        val re = fft[2 * bin].toInt()
        val im = fft[2 * bin + 1].toInt()
        val mag = sqrt((re * re + im * im).toDouble()) / 128.0
        sqrt(mag.coerceIn(0.0, 1.0)).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * Pure: diagonal shimmer sweep offset for [progress] 0..1 across a box of
 * [widthPx]. Band travels left→right: -width at 0, +width at 1 (monotonic).
 * Callers feed it into a linearGradient start/end Offset.
 */
fun shimmerTranslateX(progress: Float, widthPx: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val w = widthPx.coerceAtLeast(0f)
    return -w + p * 2f * w
}
