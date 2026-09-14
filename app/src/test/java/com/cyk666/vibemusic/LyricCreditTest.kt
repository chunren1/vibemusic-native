package com.cyk666.vibemusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

    private fun sampleLyricJson(): String {
        val words = JSONArray()
            .put(JSONObject().put("startMs", 30000).put("endMs", 30500).put("text", "hel"))
            .put(JSONObject().put("startMs", 30500).put("endMs", 31000).put("text", "lo"))
        val data = JSONArray()
            .put(JSONObject().put("time", 12.5).put("text", "爱你一万年"))
            .put(JSONObject().put("time", "20.0").put("text", "作词：林夕"))
            .put(JSONObject().put("time", 30).put("text", "hello").put("words", words))
        return JSONObject().put("code", 200).put("data", data).toString()
    }

    @Test
    fun parseLyricBody_filtersCreditsAndKeepsWordTiming() {
        val lines = VibeApi.parseLyricBody(sampleLyricJson())
        assertEquals(2, lines.size)
        assertEquals(12.5, lines[0].timeSec, 0.0)
        assertEquals("爱你一万年", lines[0].text)
        assertEquals(null, lines[0].words)
        assertEquals(30.0, lines[1].timeSec, 0.0)
        assertEquals(2, lines[1].words!!.size)
        assertEquals("hel", lines[1].words!![0].text)
    }

    @Test
    fun parseLyricBody_offMainReturnsIdenticalData(): Unit = runBlocking {
        val json = sampleLyricJson()
        val caller = Thread.currentThread()
        val direct = VibeApi.parseLyricBody(json)
        val offMain = withContext(Dispatchers.Default) {
            assertTrue(Thread.currentThread() != caller)
            VibeApi.parseLyricBody(json)
        }
        assertEquals(direct, offMain)
    }
}
