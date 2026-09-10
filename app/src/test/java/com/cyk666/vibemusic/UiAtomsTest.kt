package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiAtomsTest {

    private fun song(name: String = "n", artist: String = "a", cover: String = "c") = Song(
        sourceId = "s",
        name = name,
        artist = artist,
        album = "",
        coverUrl = cover,
        durationSec = 200,
        platform = "netease"
    )

    @Test
    fun rowModel_mapsTitleArtistCover() {
        val m = buildSongRowModel(song("予以", "队长", "http://x/y.jpg"))
        assertEquals("予以", m.title)
        assertEquals("队长", m.subtitle)
        assertEquals("http://x/y.jpg", m.coverUrl)
    }

    @Test
    fun rowModel_blankNameFallsBackToUntitled() {
        assertEquals("(untitled)", buildSongRowModel(song(name = "")).title)
    }

    @Test
    fun rowModel_subtitleOverrideWins() {
        val m = buildSongRowModel(song(artist = "a"), subtitleOverride = "2026-01-01 · a")
        assertEquals("2026-01-01 · a", m.subtitle)
    }

    @Test
    fun sleepOptions_offFirstThenPresets() {
        val opts = sleepPresetOptions()
        assertEquals(0, opts.first())
        assertTrue(opts.containsAll(listOf(15, 30, 60)))
    }

    @Test
    fun sleepLabel_offAndMinutes() {
        assertEquals("关闭定时", sleepOptionLabel(0))
        assertEquals("15分钟", sleepOptionLabel(15))
        assertEquals("60分钟", sleepOptionLabel(60))
    }

    @Test
    fun sleepChoice_validCustomWinsOverRadio() {
        assertEquals(45, resolveSleepChoice(30, "45"))
    }

    @Test
    fun sleepChoice_blankCustomKeepsRadio() {
        assertEquals(30, resolveSleepChoice(30, ""))
        assertEquals(0, resolveSleepChoice(0, "   "))
    }

    @Test
    fun sleepChoice_invalidCustomFallsBackToRadio() {
        assertEquals(30, resolveSleepChoice(30, "abc"))
        assertEquals(15, resolveSleepChoice(15, "4"))
    }

    @Test
    fun sleepChoice_nothingSelectedIsNull() {
        assertNull(resolveSleepChoice(null, ""))
        assertNull(resolveSleepChoice(null, "   "))
    }

    @Test
    fun confirmStyle_dangerVsPlain() {
        assertEquals(ConfirmStyle.DANGER, selectConfirmStyle(true))
        assertEquals(ConfirmStyle.PLAIN, selectConfirmStyle(false))
    }

    @Test
    fun topToast_dismissConstAndVisibility() {
        assertEquals(1500L, TOP_TOAST_DISMISS_MS)
        assertTrue(isTopToastVisible("已切换到：随机播放"))
        assertFalse(isTopToastVisible(null))
        assertFalse(isTopToastVisible(""))
        assertFalse(isTopToastVisible("   "))
    }
}
