package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricCreditTest {

    @Test
    fun creditPrefixes_match() {
        assertTrue(isCreditLine("作词：林夕"))
        assertTrue(isCreditLine("作曲: 周杰伦"))
        assertTrue(isCreditLine("编曲：洪敬尧"))
        assertTrue(isCreditLine("制作人：张亚东"))
        assertTrue(isCreditLine("出品：某某公司"))
        assertTrue(isCreditLine("OP：片头曲"))
        assertTrue(isCreditLine("ED: ending theme"))
        assertTrue(isCreditLine("sp 主题曲"))
        assertTrue(isCreditLine("主题曲：something"))
        assertTrue(isCreditLine("片头：xxx"))
        assertTrue(isCreditLine("片尾 xxx"))
        assertTrue(isCreditLine("  作词：林夕"))
        assertTrue(isCreditLine("编曲"))
    }

    @Test
    fun realLyrics_doNotMatch() {
        assertFalse(isCreditLine("编曲的故事还在继续"))
        assertFalse(isCreditLine("作词的人走了"))
        assertFalse(isCreditLine("open mic tonight"))
        assertFalse(isCreditLine("special day with you"))
        assertFalse(isCreditLine("editorial dreams"))
        assertFalse(isCreditLine("爱你一万年"))
        assertFalse(isCreditLine(""))
        assertFalse(isCreditLine("   "))
    }

    @Test
    fun stripNoise_unescapesAndTrims() {
        assertEquals("a&b", stripLyricNoise("a&amp;b"))
        assertEquals("a<b", stripLyricNoise("a&lt;b"))
        assertEquals("a>b", stripLyricNoise("a&gt;b"))
        assertEquals("a\"b", stripLyricNoise("a&quot;b"))
        assertEquals("a'b", stripLyricNoise("a&#39;b"))
        assertEquals("hello", stripLyricNoise("  hello\r\n"))
        assertEquals("[00:01.00]hi", stripLyricNoise("[00:01.00]hi"))
    }
}
