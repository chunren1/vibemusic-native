package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VisualFxTest {

    // ---- supportsRenderEffectBlur (version gate) ----

    @Test
    fun blurGate_api31AndAboveAllowed() {
        assertTrue(supportsRenderEffectBlur(31))
        assertTrue(supportsRenderEffectBlur(33))
        assertTrue(supportsRenderEffectBlur(35))
    }

    @Test
    fun blurGate_belowApi31FallsBack() {
        assertFalse(supportsRenderEffectBlur(26))
        assertFalse(supportsRenderEffectBlur(29))
        assertFalse(supportsRenderEffectBlur(30))
    }

    // ---- mapFftToBars ----

    @Test
    fun fft_emptyYieldsSilence() {
        assertEquals(List(SPECTRUM_BAR_COUNT) { 0f }, mapFftToBars(ByteArray(0)))
        assertEquals(List(24) { 0f }, mapFftToBars(ByteArray(2), 24))
    }

    @Test
    fun fft_silenceBytesYieldZeroBars() {
        val bars = mapFftToBars(ByteArray(64))
        assertEquals(SPECTRUM_BAR_COUNT, bars.size)
        assertTrue(bars.all { it == 0f })
    }

    @Test
    fun fft_fullScaleClampsToOne() {
        // Max byte magnitude must never exceed 1 (clamp, no NaN/Inf).
        val fft = ByteArray(64) { 127 }
        val bars = mapFftToBars(fft)
        assertEquals(SPECTRUM_BAR_COUNT, bars.size)
        assertTrue(bars.all { it in 0f..1f })
        assertTrue(bars.any { it == 1f })
    }

    @Test
    fun fft_singleToneLightsSomeBars() {
        // One hot bin: nearby bars pick it up, DC-skip keeps mapping stable.
        val fft = ByteArray(64)
        fft[2] = 64
        fft[3] = 64
        val bars = mapFftToBars(fft)
        assertTrue(bars.any { it > 0f })
        assertTrue(bars.all { it in 0f..1f })
    }

    @Test
    fun fft_barCountHonored() {
        assertEquals(24, mapFftToBars(ByteArray(64), 24).size)
        assertEquals(32, mapFftToBars(ByteArray(64), 32).size)
        assertTrue(mapFftToBars(ByteArray(64), 0).isEmpty())
        assertTrue(mapFftToBars(ByteArray(64), -3).isEmpty())
    }

    @Test
    fun fft_defaultBarCountInWindow() {
        assertTrue(SPECTRUM_BAR_COUNT in 24..32)
    }

    // ---- shimmerTranslateX ----

    @Test
    fun shimmer_sweepsLeftToRight() {
        val w = 400f
        assertEquals(-w, shimmerTranslateX(0f, w), 0.001f)
        assertEquals(w, shimmerTranslateX(1f, w), 0.001f)
        assertEquals(0f, shimmerTranslateX(0.5f, w), 0.001f)
    }

    @Test
    fun shimmer_monotonicAndClamped() {
        val w = 300f
        var prev = shimmerTranslateX(0f, w)
        for (p in listOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f)) {
            val cur = shimmerTranslateX(p, w)
            assertTrue(cur >= prev)
            prev = cur
        }
        // Out-of-range progress clamps instead of overshooting.
        assertEquals(-w, shimmerTranslateX(-2f, w), 0.001f)
        assertEquals(w, shimmerTranslateX(5f, w), 0.001f)
    }

    @Test
    fun shimmer_zeroWidthStaysPut() {
        assertEquals(0f, shimmerTranslateX(0.5f, 0f), 0.001f)
        assertTrue(abs(shimmerTranslateX(0.7f, -50f)) < 0.001f)
    }

    // ---- vinylAngleDeg (20s/rev rotation math) ----

    @Test
    fun vinyl_zeroAtStartHalfAtHalfPeriod() {
        assertEquals(0f, vinylAngleDeg(0), 0.001f)
        assertEquals(180f, vinylAngleDeg(VINYL_ROTATION_MS / 2), 0.5f)
    }

    @Test
    fun vinyl_wrapsAtFullPeriod() {
        assertEquals(0f, vinylAngleDeg(VINYL_ROTATION_MS), 0.001f)
        assertEquals(
            vinylAngleDeg(1_000L),
            vinylAngleDeg(VINYL_ROTATION_MS + 1_000L),
            0.001f
        )
    }

    @Test
    fun vinyl_negativeElapsedWrapsPositive() {
        assertTrue(vinylAngleDeg(-1_000L) > 270f)
        assertTrue(vinylAngleDeg(-1_000L) < 360f)
    }

    @Test
    fun vinyl_nonPositivePeriodNeverNaN() {
        assertEquals(0f, vinylAngleDeg(5_000L, 0L), 0.001f)
        assertEquals(0f, vinylAngleDeg(5_000L, -100L), 0.001f)
    }

    @Test
    fun vinyl_defaultPeriodIs20s() {
        assertEquals(20_000L, VINYL_ROTATION_MS)
        assertEquals(90f, vinylAngleDeg(5_000L), 0.5f)
    }

    // ---- vinylShouldSpin (play-state gating predicate) ----

    @Test
    fun vinylSpin_runsOnlyWhilePlayingWithTrack() {
        assertTrue(vinylShouldSpin(isPlaying = true, hasTrack = true))
    }

    @Test
    fun vinylSpin_pausedOrTracklessFreezes() {
        assertFalse(vinylShouldSpin(isPlaying = false, hasTrack = true))
        assertFalse(vinylShouldSpin(isPlaying = true, hasTrack = false))
        assertFalse(vinylShouldSpin(isPlaying = false, hasTrack = false))
    }

    // ---- vinylSpinAngle (pause-freeze / resume-continue math) ----

    @Test
    fun vinylSpinAngle_advancesWithClock() {
        assertEquals(30f, vinylSpinAngle(0f, 30f, 0f), 0.001f)
        assertEquals(100f, vinylSpinAngle(90f, 30f, 20f), 0.001f)
    }

    @Test
    fun vinylSpinAngle_wrapsPositive() {
        assertEquals(10f, vinylSpinAngle(350f, 20f, 0f), 0.001f)
        assertEquals(0f, vinylSpinAngle(0f, 360f, 0f), 0.001f)
    }

    @Test
    fun vinylSpinAngle_neverNegative() {
        val a = vinylSpinAngle(10f, 5f, 350f)
        assertTrue(a >= 0f && a < 360f)
        assertEquals(25f, a, 0.001f)
    }

    @Test
    fun vinylSpinAngle_resumeFromFrozenBaseIsContinuous() {
        // Pause froze the cover at 90 (base); clock kept ticking and now
        // reads 200. Re-anchor must render exactly the base — no jump.
        val frozenBase = 90f
        val spinNow = 200f
        assertEquals(frozenBase, vinylSpinAngle(frozenBase, spinNow, spinNow), 0.001f)
        // …and 10 clock-degrees later the cover advanced exactly 10.
        assertEquals(100f, vinylSpinAngle(frozenBase, spinNow + 10f, spinNow), 0.001f)
    }

    // ---- spectrumShouldSample (Top-9 lifecycle gating predicate) ----

    @Test
    fun spectrum_runsOnlyWhilePlayingForegroundWithSession() {
        assertTrue(spectrumShouldSample(isPlaying = true, isForeground = true, audioSessionId = 1))
        assertTrue(spectrumShouldSample(isPlaying = true, isForeground = true, audioSessionId = 42))
    }

    @Test
    fun spectrum_pausedBackgroundOrBadSessionStops() {
        assertFalse(spectrumShouldSample(isPlaying = false, isForeground = true, audioSessionId = 1))
        assertFalse(spectrumShouldSample(isPlaying = true, isForeground = false, audioSessionId = 1))
        assertFalse(spectrumShouldSample(isPlaying = false, isForeground = false, audioSessionId = 1))
        assertFalse(spectrumShouldSample(isPlaying = true, isForeground = true, audioSessionId = 0))
        assertFalse(spectrumShouldSample(isPlaying = true, isForeground = true, audioSessionId = -3))
        assertFalse(spectrumShouldSample(isPlaying = true, isForeground = false, audioSessionId = 0))
    }
}
