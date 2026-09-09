package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestTest {

    private fun song(name: String) =
        Song("id-$name", name, "artist", "", "", 100, "netease")

    @Test
    fun blankInputEmpty() {
        assertTrue(buildSuggestions(listOf("周杰伦"), SEARCH_HOTWORDS, listOf(song("周杰伦")), "  ").isEmpty())
    }

    @Test
    fun historyMatchesFirst() {
        val out = buildSuggestions(
            listOf("林俊杰现场", "周杰伦"),
            SEARCH_HOTWORDS,
            emptyList(),
            "杰"
        )
        assertEquals(
            listOf(
                Suggestion("林俊杰现场", SuggestSource.HISTORY),
                Suggestion("周杰伦", SuggestSource.HISTORY),
                Suggestion("林俊杰", SuggestSource.HOTWORD)
            ),
            out
        )
    }

    @Test
    fun hotwordsSecondAfterHistory() {
        val out = buildSuggestions(
            listOf("不再犹豫"),
            listOf("周杰伦", "林俊杰"),
            emptyList(),
            "杰"
        )
        assertEquals(
            listOf(
                Suggestion("周杰伦", SuggestSource.HOTWORD),
                Suggestion("林俊杰", SuggestSource.HOTWORD)
            ),
            out
        )
    }

    @Test
    fun historyBeatsHotwordDedup() {
        val out = buildSuggestions(
            listOf("周杰伦"),
            listOf("周杰伦", "周杰伦演唱会"),
            emptyList(),
            "周杰"
        )
        assertEquals(
            listOf(
                Suggestion("周杰伦", SuggestSource.HISTORY),
                Suggestion("周杰伦演唱会", SuggestSource.HOTWORD)
            ),
            out
        )
    }

    @Test
    fun liveTopFiveCap() {
        val live = (1..7).map { song("予以 $it") }
        val out = buildSuggestions(emptyList(), emptyList(), live, "予以")
        assertEquals(5, out.size)
        assertTrue(out.all { it.source == SuggestSource.LIVE })
        assertEquals("予以 1", out.first().text)
        assertEquals("予以 5", out.last().text)
    }

    @Test
    fun liveSkipsBlankAndDupes() {
        val live = listOf(song(""), song("予以"), song("予以"))
        val out = buildSuggestions(listOf("予以珍藏版"), emptyList(), live, "予以")
        assertEquals(
            listOf(
                Suggestion("予以珍藏版", SuggestSource.HISTORY),
                Suggestion("予以", SuggestSource.LIVE)
            ),
            out
        )
    }

    @Test
    fun fullOrderHistoryHotwordLive() {
        val out = buildSuggestions(
            listOf("予以"),
            listOf("予以翻唱"),
            listOf(song("予以完整版")),
            "予以"
        )
        assertEquals(
            listOf(
                Suggestion("予以", SuggestSource.HISTORY),
                Suggestion("予以翻唱", SuggestSource.HOTWORD),
                Suggestion("予以完整版", SuggestSource.LIVE)
            ),
            out
        )
    }

    @Test
    fun totalCapRespected() {
        val history = (1..10).map { "测试历史 $it" }
        val out = buildSuggestions(history, SEARCH_HOTWORDS, (1..7).map { song("测试 $it") }, "测试")
        assertEquals(SUGGEST_TOTAL_MAX, out.size)
        assertTrue(out.all { it.source == SuggestSource.HISTORY })
    }

    @Test
    fun nonMatchingInputEmpty() {
        // The Activity gates live results via the generation counter (passes
        // emptyList when the input no longer equals the producing query), so
        // a non-matching input only consults history + hotwords here.
        assertTrue(
            buildSuggestions(
                listOf("周杰伦"),
                SEARCH_HOTWORDS,
                emptyList(),
                "xyz-nomatch"
            ).isEmpty()
        )
    }
}
