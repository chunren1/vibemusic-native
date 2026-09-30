package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Lyrics batch: offset math, fallback reason, alternate-source pick (all pure). */
@RunWith(RobolectricTestRunner::class)
class LyricBatchTest {

    private fun song(platform: String, sourceId: String = "s1") = Song(
        sourceId = sourceId,
        name = "予以",
        artist = "队长",
        album = "",
        coverUrl = "",
        durationSec = 200,
        platform = platform
    )

    // ---- clampLyricOffset / stepLyricOffset ----

    @Test
    fun offset_clampsToRange() {
        assertEquals(2_000L, clampLyricOffset(9_999L))
        assertEquals(-2_000L, clampLyricOffset(-9_999L))
        assertEquals(300L, clampLyricOffset(300L))
        assertEquals(0L, clampLyricOffset(0L))
    }

    @Test
    fun offset_stepsByTenthAndClamps() {
        assertEquals(100L, stepLyricOffset(0L, 1))
        assertEquals(-100L, stepLyricOffset(0L, -1))
        assertEquals(500L, stepLyricOffset(300L, 2))
        assertEquals(2_000L, stepLyricOffset(1_950L, 5))
        assertEquals(-2_000L, stepLyricOffset(-1_950L, -5))
    }

    @Test
    fun offset_rangeCoversHalfSecond() {
        assertTrue(LYRIC_OFFSET_MAX_MS >= 500L)
        assertEquals(100L, LYRIC_OFFSET_STEP_MS)
    }

    // ---- applyLyricOffset ----

    @Test
    fun offset_shiftsClockBothWays() {
        assertEquals(10_300L, applyLyricOffset(10_000L, 300L))
        assertEquals(9_700L, applyLyricOffset(10_000L, -300L))
        assertEquals(10_000L, applyLyricOffset(10_000L, 0L))
    }

    @Test
    fun offset_neverNegative() {
        assertEquals(0L, applyLyricOffset(100L, -500L))
        assertEquals(0L, applyLyricOffset(0L, -2_000L))
    }

    @Test
    fun offset_outOfRangeInputClamped() {
        assertEquals(12_000L, applyLyricOffset(10_000L, 99_000L))
        assertEquals(8_000L, applyLyricOffset(10_000L, -99_000L))
    }

    // ---- formatLyricOffset ----

    @Test
    fun offset_formatsChineseLabel() {
        assertEquals("±0秒", formatLyricOffset(0L))
        assertEquals("+0.3秒", formatLyricOffset(300L))
        assertEquals("−0.2秒", formatLyricOffset(-200L))
        assertEquals("+2.0秒", formatLyricOffset(9_999L))
    }

    // ---- describeLyricEmpty ----

    @Test
    fun empty_loadingOrNullIsNotEmpty() {
        assertNull(describeLyricEmpty(null, song("netease"), 0))
        assertNull(describeLyricEmpty(LyricUiState.Loading, song("netease"), 0))
    }

    @Test
    fun empty_failedExplainsNetwork() {
        val r = describeLyricEmpty(LyricUiState.Failed, song("netease"), 0)
        assertEquals(LyricEmptyReason.LOAD_FAILED, r)
        assertTrue(r!!.copy.contains("网络"))
    }

    @Test
    fun empty_biliNamesMissingSource() {
        val r = describeLyricEmpty(LyricUiState.Ok(emptyList()), song("bilibili"), 0)
        assertEquals(LyricEmptyReason.NO_BILI_SOURCE, r)
        assertTrue(r!!.copy.contains("B站"))
    }

    @Test
    fun empty_otherPlatformGeneric() {
        val r = describeLyricEmpty(LyricUiState.Ok(emptyList()), song("netease"), 0)
        assertEquals(LyricEmptyReason.NO_LYRIC, r)
    }

    @Test
    fun empty_symbolOnlyPayloadIsNoLyric() {
        // Backend emits "♪" placeholders; the UI filter drops them — honest
        // reason is NO_LYRIC, and null song still resolves (never crashes).
        val lines = listOf(LyricLine(1.0, "♪"))
        val r = describeLyricEmpty(LyricUiState.Ok(lines), null, 0)
        assertEquals(LyricEmptyReason.NO_LYRIC, r)
    }

    @Test
    fun empty_visibleLinesMeansNoReason() {
        val lines = listOf(LyricLine(1.0, "爱意随风起"))
        assertNull(describeLyricEmpty(LyricUiState.Ok(lines), song("netease"), 1))
    }

    // ---- pickAlternateSource ----

    @Test
    fun alternate_prefersDifferentPlatform() {
        val cur = song("netease", "n1")
        val cands = listOf(
            song("netease", "n2"),
            song("qq", "q1"),
            song("kugou", "k1")
        )
        assertEquals("q1", pickAlternateSource(cands, cur)!!.sourceId)
    }

    @Test
    fun alternate_samePlatformFallbackAndSelfSkip() {
        val cur = song("qq", "q1")
        val cands = listOf(song("qq", "q1"), song("qq", "q2"))
        assertEquals("q2", pickAlternateSource(cands, cur)!!.sourceId)
    }

    @Test
    fun alternate_noneWhenOnlySelfOrBlank() {
        val cur = song("netease", "n1")
        assertNull(pickAlternateSource(emptyList(), cur))
        assertNull(pickAlternateSource(listOf(song("netease", "n1")), cur))
        assertNull(pickAlternateSource(listOf(song("qq", "")), cur))
    }
}
