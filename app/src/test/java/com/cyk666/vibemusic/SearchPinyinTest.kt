package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPinyinTest {

    @Test
    fun alias_initialAndFullPinyin() {
        assertEquals("周杰伦", resolveSearchAlias("zjl"))
        assertEquals("周杰伦", resolveSearchAlias("zhoujielun"))
        assertEquals("晴天", resolveSearchAlias("qt"))
        assertEquals("晴天", resolveSearchAlias("QINGTIAN"))
        assertEquals("告白气球", resolveSearchAlias("gbqq"))
    }

    @Test
    fun alias_unknownAndBlankNull() {
        assertNull(resolveSearchAlias("晴天"))
        assertNull(resolveSearchAlias("xyz-nomatch"))
        assertNull(resolveSearchAlias("  "))
        assertNull(resolveSearchAlias(""))
    }

    @Test
    fun normalize_trimLowerCollapse() {
        assertEquals("zjl", normalizeSearchInput("  ZJL  "))
        assertEquals("a b", normalizeSearchInput("a   b"))
        assertEquals("晴天", normalizeSearchInput("晴天"))
    }

    @Test
    fun suggestions_pinyinAliasAppended() {
        val out = buildSuggestions(emptyList(), SEARCH_HOTWORDS, emptyList(), "zjl")
        assertTrue(out.contains(Suggestion("周杰伦", SuggestSource.HOTWORD)))
    }

    @Test
    fun suggestions_aliasDedupedAgainstSubstring() {
        // "qt" is not a substring of any hotword, so the alias row is the only one.
        val out = buildSuggestions(emptyList(), SEARCH_HOTWORDS, emptyList(), "qt")
        assertEquals(listOf(Suggestion("晴天", SuggestSource.HOTWORD)), out)
    }

    @Test
    fun platforms_coverBackendSources() {
        assertEquals(
            listOf("netease", "qq", "migu", "kugou", "bilibili"),
            SEARCH_PLATFORMS.map { it.first }
        )
    }
}
