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
}
