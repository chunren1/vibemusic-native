package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 主题枚举的解析与稳定性。
 *
 * <p>持久化存的是 name，解析必须对未知值兜底——旧版本写进去的键名、
 * 或以后删掉某个主题，都不能让应用起不来。</p>
 */
class AppThemeTest {

    @Test
    fun parse_knownNames() {
        assertEquals(AppTheme.OBSIDIAN, AppTheme.parse("OBSIDIAN"))
        assertEquals(AppTheme.OLED, AppTheme.parse("OLED"))
        assertEquals(AppTheme.WARM_GRAY, AppTheme.parse("WARM_GRAY"))
    }

    @Test
    fun parse_unknownFallsBackToObsidian() {
        assertEquals(AppTheme.OBSIDIAN, AppTheme.parse(null))
        assertEquals(AppTheme.OBSIDIAN, AppTheme.parse(""))
        assertEquals(AppTheme.OBSIDIAN, AppTheme.parse("SOME_REMOVED_THEME"))
    }

    @Test
    fun themes_areDistinct() {
        assertEquals(3, AppTheme.entries.size)
        assertEquals(3, AppTheme.entries.map { it.label }.toSet().size)
        assertNotEquals(AppTheme.OBSIDIAN.bg, AppTheme.OLED.bg)
        assertNotEquals(AppTheme.OBSIDIAN.bg, AppTheme.WARM_GRAY.bg)
    }
}
