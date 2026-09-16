package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricSymbolFilterTest {

    @Test
    fun `空白行_纯空格与空串判定为空白`() {
        assertTrue(isBlankLyricLine(""))
        assertTrue(isBlankLyricLine("   "))
        assertTrue(isBlankLyricLine("[00:10.00]   "))
        assertFalse(isBlankLyricLine("爱你一万年"))
        assertFalse(isBlankLyricLine("♪"))
    }

    @Test
    fun `纯音符行_单个与多个音符均命中`() {
        assertTrue(isMusicSymbolLine("♪"))
        assertTrue(isMusicSymbolLine("♪♪"))
        assertTrue(isMusicSymbolLine("♫ ♩ ♬"))
        assertTrue(isMusicSymbolLine("🎵🎶"))
        assertTrue(isMusicSymbolLine("  ♪  "))
        assertTrue(isMusicSymbolLine("[00:10.00]♪"))
    }

    @Test
    fun `纯音符行_音符加装饰符号仍命中`() {
        assertTrue(isMusicSymbolLine("♪～♪"))
        assertTrue(isMusicSymbolLine("♪..."))
        assertTrue(isMusicSymbolLine("♫-♫"))
    }

    @Test
    fun `真歌词_含任一字母数字即保留`() {
        assertFalse(isMusicSymbolLine("爱你一万年"))
        assertFalse(isMusicSymbolLine("啦啦啦"))
        assertFalse(isMusicSymbolLine("爱你♪"))
        assertFalse(isMusicSymbolLine("♪爱你"))
        assertFalse(isMusicSymbolLine("hello♪"))
        assertFalse(isMusicSymbolLine("oh~"))
        assertFalse(isMusicSymbolLine("123"))
    }

    @Test
    fun `纯音符行_空白行不算音符行`() {
        assertFalse(isMusicSymbolLine(""))
        assertFalse(isMusicSymbolLine("   "))
    }

    @Test
    fun `歌词预览_跳过音符行回退到上一句真歌词`() {
        val lines = listOf(
            LyricLine(timeSec = 0.0, text = "第一句"),
            LyricLine(timeSec = 10.0, text = "♪"),
            LyricLine(timeSec = 20.0, text = "♪♪")
        )
        assertEquals("第一句", lyricPreviewLine(lines, 25_000L))
    }

    @Test
    fun `歌词预览_全是音符行返回空`() {
        val lines = listOf(LyricLine(timeSec = 0.0, text = "♪"))
        assertNull(lyricPreviewLine(lines, 5_000L))
    }
}
