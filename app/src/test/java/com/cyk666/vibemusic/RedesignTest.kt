package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RedesignTest {

    @Test
    fun `登录态_三连显示数量`() {
        val triple = buildMineTriple(
            favCount = 7,
            historyCount = 12,
            offlineCount = 5,
            loggedIn = true
        )
        assertEquals(3, triple.size)
        assertEquals("喜欢", triple[0].title)
        assertEquals("7", triple[0].count)
        assertEquals("最近", triple[1].title)
        assertEquals("12", triple[1].count)
        assertEquals("本地", triple[2].title)
        assertEquals("5", triple[2].count)
    }

    @Test
    fun `访客态_账号格显示占位_本地保留数量`() {
        val triple = buildMineTriple(
            favCount = 0,
            historyCount = 0,
            offlineCount = 5,
            loggedIn = false
        )
        assertEquals(3, triple.size)
        assertEquals("–", triple[0].count)
        assertEquals("–", triple[1].count)
        assertEquals("5", triple[2].count)
    }

    @Test
    fun `三连id_稳定可分发`() {
        val triple = buildMineTriple(1, 2, 3, loggedIn = true)
        assertEquals(
            listOf("favorites", "history", "offline"),
            triple.map { it.id }
        )
    }

    private fun line(sec: Double, text: String) = LyricLine(
        timeSec = sec,
        text = text,
        words = null
    )

    @Test
    fun `歌词预览_返回当前行并剥离时间标签`() {
        val lines = listOf(
            line(0.0, "[00:00.00]前奏"),
            line(10.0, "[00:10.00]第一句"),
            line(20.0, "[00:20.00]第二句")
        )
        assertEquals("第一句", lyricPreviewLine(lines, 15_000L))
    }

    @Test
    fun `歌词预览_进度在首行之前返回空`() {
        val lines = listOf(line(10.0, "第一句"))
        assertNull(lyricPreviewLine(lines, 5_000L))
    }

    @Test
    fun `歌词预览_空列表返回空`() {
        assertNull(lyricPreviewLine(emptyList(), 5_000L))
    }

    @Test
    fun `歌词预览_空白行返回空`() {
        val lines = listOf(line(0.0, "   "))
        assertNull(lyricPreviewLine(lines, 5_000L))
    }
}
