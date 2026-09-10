package com.cyk666.vibemusic

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KaraokeTest {

    // ---- parseEnhancedLrcLine ----

    @Test
    fun parse_plainLineYieldsEmpty() {
        assertTrue(parseEnhancedLrcLine("爱意随风起").isEmpty())
        assertTrue(parseEnhancedLrcLine("").isEmpty())
        assertTrue(parseEnhancedLrcLine("[mm:ss] not a tag").isEmpty())
    }

    @Test
    fun parse_basicInlineTags() {
        val words = parseEnhancedLrcLine("[00:10.00]<00:10.00>爱<00:10.50>意<00:11.00>随风")
        assertEquals(3, words.size)
        assertEquals(WordTimed(10_000L, 10_500L, "爱"), words[0])
        assertEquals(WordTimed(10_500L, 11_000L, "意"), words[1])
        assertEquals(WordTimed(11_000L, WORD_END_UNKNOWN, "随风"), words[2])
    }

    @Test
    fun parse_headTextBeforeFirstTag() {
        val words = parseEnhancedLrcLine("[00:05.00]前奏<00:06.00>开唱")
        assertEquals(2, words.size)
        assertEquals(WordTimed(5_000L, 6_000L, "前奏"), words[0])
        assertEquals(WordTimed(6_000L, WORD_END_UNKNOWN, "开唱"), words[1])
    }

    @Test
    fun parse_malformedTagsKeptAsLiteral() {
        // Bad timestamps are literal text, never dropped; no valid tag → empty.
        assertTrue(parseEnhancedLrcLine("[ab:cd]hello").isEmpty())
        assertTrue(parseEnhancedLrcLine("<99:99:99>oops").isEmpty())
        // Malformed tag amid valid ones stays inside the word text.
        val words = parseEnhancedLrcLine("[00:01.00]<00:01.00>a<bad>b<00:02.00>c")
        assertEquals(2, words.size)
        assertEquals("a<bad>b", words[0].text)
        assertEquals("c", words[1].text)
    }

    @Test
    fun parse_blankSegmentsSkipped() {
        val words = parseEnhancedLrcLine("[00:01.00]<00:01.00><00:02.00>hi")
        assertEquals(1, words.size)
        assertEquals(WordTimed(2_000L, WORD_END_UNKNOWN, "hi"), words[0])
    }

    @Test
    fun parse_centisAndMillisFrac() {
        assertEquals(1_500L, lrcTimestampToMs("00", "01", "50"))
        assertEquals(1_250L, lrcTimestampToMs("00", "01", "250"))
        assertEquals(61_000L, lrcTimestampToMs("01", "01", null))
        assertEquals(-1L, lrcTimestampToMs("00", "61", "00"))
        assertEquals(-1L, lrcTimestampToMs("xx", "01", "00"))
    }

    // ---- stripInlineTags ----

    @Test
    fun strip_removesValidTagsKeepsMalformed() {
        assertEquals(
            "爱意随风",
            stripInlineTags("[00:10.00]<00:10.00>爱<00:10.50>意随风")
        )
        assertEquals("[oops]hello", stripInlineTags("[oops]hello"))
    }

    // ---- parseWordsJson ----

    @Test
    fun wordsJson_absentOrEmptyIsNull() {
        assertNull(parseWordsJson(null))
        assertNull(parseWordsJson(JSONArray("[]")))
    }

    @Test
    fun wordsJson_parsesAndSorts() {
        val arr = JSONArray(
            """[
              {"startMs": 2000, "endMs": 2500, "text": "b"},
              {"start": "1000", "end": "2000", "word": "a"},
              {"startMs": 3000, "text": "c"},
              {"startMs": "bad", "text": "x"},
              {"startMs": 4000, "text": "  "}
            ]"""
        )
        val words = parseWordsJson(arr)!!
        assertEquals(3, words.size)
        assertEquals(WordTimed(1_000L, 2_000L, "a"), words[0])
        assertEquals(WordTimed(2_000L, 2_500L, "b"), words[1])
        assertEquals(WordTimed(3_000L, WORD_END_UNKNOWN, "c"), words[2])
    }

    // ---- wordAtTime (boundary-inclusive starts) ----

    @Test
    fun wordAtTime_boundaries() {
        val words = listOf(
            WordTimed(0L, 500L, "a"),
            WordTimed(500L, 1_000L, "b"),
            WordTimed(1_000L, WORD_END_UNKNOWN, "c")
        )
        assertEquals(-1, wordAtTime(emptyList(), 100L))
        assertEquals(-1, wordAtTime(words, -1L))
        assertEquals(0, wordAtTime(words, 0L))
        assertEquals(0, wordAtTime(words, 499L))
        assertEquals(1, wordAtTime(words, 500L))
        assertEquals(2, wordAtTime(words, 1_000L))
        assertEquals(2, wordAtTime(words, 99_000L))
    }

    // ---- proportionalSplit (Mode B approximation) ----

    @Test
    fun split_emptyOrNoDurationIsEmpty() {
        assertTrue(proportionalSplit("", 0L, 4_000L).isEmpty())
        assertTrue(proportionalSplit("你好", 4_000L, 4_000L).isEmpty())
        assertTrue(proportionalSplit("你好", 5_000L, 4_000L).isEmpty())
    }

    @Test
    fun split_cjkEvenSlices() {
        val words = proportionalSplit("爱意随风", 10_000L, 14_000L)
        assertEquals(4, words.size)
        assertEquals(WordTimed(10_000L, 11_000L, "爱"), words[0])
        assertEquals(WordTimed(13_000L, 14_000L, "风"), words[3])
        // Full coverage, no gaps/overlaps.
        assertEquals(10_000L, words.first().startMs)
        assertEquals(14_000L, words.last().endMs)
        words.zipWithNext { a, b -> assertEquals(a.endMs, b.startMs) }
    }

    @Test
    fun split_mixedCjkAsciiAndEmoji() {
        val words = proportionalSplit("hi你好🎵", 0L, 5_000L)
        // h, i, 你, 好, 🎵 (surrogate pair kept whole) = 5 slices.
        assertEquals(5, words.size)
        assertEquals("🎵", words.last().text)
        assertEquals(5_000L, words.last().endMs)
        assertEquals(0, wordAtTime(words, 0L))
        assertEquals(4, wordAtTime(words, 4_999L))
    }

    @Test
    fun split_singleCharCoversWholeLine() {
        val words = proportionalSplit("啊", 2_000L, 6_000L)
        assertEquals(listOf(WordTimed(2_000L, 6_000L, "啊")), words)
    }

    // ---- smooth active-line fraction (Issue 3) ----

    @Test
    fun fraction_midLineAndClamps() {
        assertEquals(0.5f, karaokeLineFraction(15_000L, 10_000L, 20_000L), 1e-6f)
        assertEquals(0f, karaokeLineFraction(10_000L, 10_000L, 20_000L), 1e-6f)
        assertEquals(1f, karaokeLineFraction(20_000L, 10_000L, 20_000L), 1e-6f)
        assertEquals(0f, karaokeLineFraction(5_000L, 10_000L, 20_000L), 1e-6f)
        assertEquals(1f, karaokeLineFraction(25_000L, 10_000L, 20_000L), 1e-6f)
    }

    @Test
    fun fraction_degenerateWindowSnapsBySide() {
        assertEquals(1f, karaokeLineFraction(10_000L, 10_000L, 10_000L), 1e-6f)
        assertEquals(0f, karaokeLineFraction(9_000L, 10_000L, 10_000L), 1e-6f)
        assertEquals(0f, karaokeLineFraction(5_000L, 10_000L, 5_000L), 1e-6f)
    }

    @Test
    fun ticker_advancesOnlyWhilePlaying() {
        assertEquals(1_100L, advanceLyricTicker(1_000L, 100L, true))
        assertEquals(1_000L, advanceLyricTicker(1_000L, 100L, false))
        assertEquals(1_000L, advanceLyricTicker(1_000L, 0L, true))
        assertEquals(1_000L, advanceLyricTicker(1_000L, -50L, true))
        assertEquals(0L, advanceLyricTicker(-5L, 0L, false))
    }

    @Test
    fun smoothPosition_invertsFraction() {
        assertEquals(15_000L, smoothLyricPosition(10_000L, 20_000L, 0.5f))
        assertEquals(10_000L, smoothLyricPosition(10_000L, 20_000L, 0f))
        assertEquals(20_000L, smoothLyricPosition(10_000L, 20_000L, 1f))
        assertEquals(10_000L, smoothLyricPosition(10_000L, 20_000L, -2f))
        assertEquals(20_000L, smoothLyricPosition(10_000L, 20_000L, 9f))
    }

    @Test
    fun karaokeTimingConsts() {
        assertEquals(120, KARAOKE_SMOOTH_MS)
        assertEquals(100L, LYRIC_FAST_TICK_MS)
    }
}
