package com.cyk666.vibemusic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test
    fun `封面门_非空URL走Coil`() {
        assertTrue(hasCoverUrl("https://cdn/x.jpg"))
    }

    @Test
    fun `封面门_空URL走默认音符占位`() {
        assertFalse(hasCoverUrl(""))
        assertFalse(hasCoverUrl("   "))
    }

    @Test
    fun `B站来源_大小写与首尾空格均识别`() {
        assertTrue(isBilibiliPlatform("bilibili"))
        assertTrue(isBilibiliPlatform("BiliBili"))
        assertTrue(isBilibiliPlatform("  bilibili  "))
    }

    @Test
    fun `B站来源_其它平台与空值不识别`() {
        assertFalse(isBilibiliPlatform("netease"))
        assertFalse(isBilibiliPlatform("qq"))
        assertFalse(isBilibiliPlatform(""))
        assertFalse(isBilibiliPlatform("   "))
        assertFalse(isBilibiliPlatform("bilibili1"))
    }

    @Test
    fun `B站歌曲_按platform字段判定`() {
        val bili = song().copy(platform = "bilibili")
        assertTrue(isBilibiliSong(bili))
        assertFalse(isBilibiliSong(song()))
    }

    @Test
    fun `行模型_携带platform供徽标使用`() {
        assertEquals("bilibili", buildSongRowModel(song().copy(platform = "bilibili")).platform)
        assertEquals("netease", buildSongRowModel(song()).platform)
        assertEquals("", SongRowModel("t", "s", "c").platform)
    }

    @Test
    fun `播放模式图标_顺序映射PlaylistPlay`() {
        assertSame(Icons.AutoMirrored.Filled.PlaylistPlay, modeMaterialIcon(AppIconKind.MODE_SEQUENTIAL))
    }

    @Test
    fun `播放模式图标_列表循环映射Repeat`() {
        assertSame(Icons.Filled.Repeat, modeMaterialIcon(AppIconKind.MODE_LOOP))
    }

    @Test
    fun `播放模式图标_单曲循环映射RepeatOne`() {
        assertSame(Icons.Filled.RepeatOne, modeMaterialIcon(AppIconKind.MODE_SINGLE))
    }

    @Test
    fun `播放模式图标_随机映射Shuffle`() {
        assertSame(Icons.Filled.Shuffle, modeMaterialIcon(AppIconKind.MODE_SHUFFLE))
    }

    @Test
    fun `播放模式图标_四种互不相同`() {
        val vectors = listOf(
            AppIconKind.MODE_SEQUENTIAL,
            AppIconKind.MODE_LOOP,
            AppIconKind.MODE_SINGLE,
            AppIconKind.MODE_SHUFFLE
        ).map { modeMaterialIcon(it) }
        assertEquals(4, vectors.toSet().size)
    }

    @Test
    fun `播放模式判定_仅四种模式返回真`() {
        assertTrue(isPlayModeKind(AppIconKind.MODE_SEQUENTIAL))
        assertTrue(isPlayModeKind(AppIconKind.MODE_LOOP))
        assertTrue(isPlayModeKind(AppIconKind.MODE_SINGLE))
        assertTrue(isPlayModeKind(AppIconKind.MODE_SHUFFLE))
        assertFalse(isPlayModeKind(AppIconKind.PLAY))
        assertFalse(isPlayModeKind(AppIconKind.QUEUE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `播放模式图标_非模式抛异常`() {
        modeMaterialIcon(AppIconKind.PLAY)
    }
}
