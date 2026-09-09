package com.cyk666.vibemusic

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeautyPhase10Test {

    @Test
    fun touchFloor_is44dp() {
        assertEquals(44, MIN_TOUCH_DP)
    }

    @Test
    fun gestureConstants_matchSpec() {
        assertEquals(80f, PLAYER_SWIPE_THRESHOLD_DP)
        assertEquals(300L, GESTURE_DEBOUNCE_MS)
    }

    @Test
    fun toggle_coverGoesLyrics() {
        assertEquals(PlayerView.LYRICS, togglePlayerView(PlayerView.COVER))
    }

    @Test
    fun toggle_lyricsGoesCover() {
        assertEquals(PlayerView.COVER, togglePlayerView(PlayerView.LYRICS))
    }

    @Test
    fun toggle_doubleToggleIsIdentity() {
        assertEquals(PlayerView.COVER, togglePlayerView(togglePlayerView(PlayerView.COVER)))
    }

    @Test
    fun gesture_leftSwipeIsNext() {
        assertEquals(PlayerGesture.NEXT, resolvePlayerGesture(-100f, 5f, fromCoverZone = true))
    }

    @Test
    fun gesture_rightSwipeIsPrev() {
        assertEquals(PlayerGesture.PREV, resolvePlayerGesture(100f, -5f, fromCoverZone = true))
    }

    @Test
    fun gesture_swipeDownFromCoverCloses() {
        assertEquals(PlayerGesture.CLOSE, resolvePlayerGesture(10f, 120f, fromCoverZone = true))
    }

    @Test
    fun gesture_swipeDownOutsideCoverZoneIgnored() {
        assertEquals(PlayerGesture.NONE, resolvePlayerGesture(10f, 120f, fromCoverZone = false))
    }

    @Test
    fun gesture_smallDragsIgnored() {
        assertEquals(PlayerGesture.NONE, resolvePlayerGesture(30f, 20f, fromCoverZone = true))
        assertEquals(PlayerGesture.NONE, resolvePlayerGesture(-30f, 20f, fromCoverZone = true))
        assertEquals(PlayerGesture.NONE, resolvePlayerGesture(0f, 0f, fromCoverZone = true))
    }

    @Test
    fun gesture_upwardSwipeIgnored() {
        assertEquals(PlayerGesture.NONE, resolvePlayerGesture(0f, -100f, fromCoverZone = true))
    }

    @Test
    fun gesture_diagonalTakesDominantAxis() {
        assertEquals(PlayerGesture.PREV, resolvePlayerGesture(120f, 100f, fromCoverZone = true))
        assertEquals(PlayerGesture.NEXT, resolvePlayerGesture(-120f, 100f, fromCoverZone = true))
    }

    @Test
    fun gesture_exactDiagonalIsAmbiguous() {
        assertEquals(PlayerGesture.NONE, resolvePlayerGesture(100f, 100f, fromCoverZone = true))
    }

    @Test
    fun gesture_upwardDiagonalOutsideCoverIgnored() {
        assertEquals(PlayerGesture.NONE, resolvePlayerGesture(-100f, -120f, fromCoverZone = false))
        assertEquals(PlayerGesture.NONE, resolvePlayerGesture(-100f, -120f, fromCoverZone = true))
    }

    @Test
    fun gesture_exactThresholdFires() {
        assertEquals(PlayerGesture.PREV, resolvePlayerGesture(80f, 0f, fromCoverZone = true))
        assertEquals(PlayerGesture.NEXT, resolvePlayerGesture(-80f, 0f, fromCoverZone = true))
        assertEquals(PlayerGesture.CLOSE, resolvePlayerGesture(0f, 80f, fromCoverZone = true))
    }

    @Test
    fun gesture_justUnderThresholdIgnored() {
        assertEquals(PlayerGesture.NONE, resolvePlayerGesture(79.9f, 0f, fromCoverZone = true))
        assertEquals(PlayerGesture.NONE, resolvePlayerGesture(0f, 79.9f, fromCoverZone = true))
    }

    @Test
    fun gesture_customThresholdRespected() {
        assertEquals(
            PlayerGesture.NONE,
            resolvePlayerGesture(100f, 0f, fromCoverZone = true, thresholdDp = 120f)
        )
        assertEquals(
            PlayerGesture.PREV,
            resolvePlayerGesture(100f, 0f, fromCoverZone = true, thresholdDp = 60f)
        )
    }

    @Test
    fun debounce_firstFireAlwaysPasses() {
        assertTrue(shouldFireGesture(1000L, -1L))
    }

    @Test
    fun debounce_refireInsideWindowBlocked() {
        assertFalse(shouldFireGesture(1299L, 1000L))
        assertFalse(shouldFireGesture(1000L, 1000L))
    }

    @Test
    fun debounce_refireAtWindowEdgePasses() {
        assertTrue(shouldFireGesture(1300L, 1000L))
        assertTrue(shouldFireGesture(5000L, 1000L))
    }

    @Test
    fun listState_loadingWinsOverEverything() {
        assertEquals(ListState.LOADING, selectListState(true, "boom", true))
        assertEquals(ListState.LOADING, selectListState(true, null, false))
    }

    @Test
    fun listState_errorOnlyWhenNothingToShow() {
        assertEquals(ListState.ERROR, selectListState(false, "boom", true))
        assertEquals(ListState.CONTENT, selectListState(false, "boom", false))
    }

    @Test
    fun listState_emptyAndContent() {
        assertEquals(ListState.EMPTY, selectListState(false, null, true))
        assertEquals(ListState.CONTENT, selectListState(false, null, false))
    }

    @Test
    fun settings_hasFourRowsAndNoQualityRow() {
        val rows = buildSettingsRows(
            cacheLabel = "c",
            storageLabel = "s",
            versionLabel = "v1"
        )
        assertEquals(listOf("theme", "cache", "storage", "about"), rows.map { it.id })
        assertTrue(rows.none { it.id.contains("quality") || it.title.contains("音质") })
    }

    @Test
    fun settings_themeRowShowsCurrentTheme() {
        val rows = buildSettingsRows(
            cacheLabel = "c",
            storageLabel = "s",
            versionLabel = "v1"
        )
        val theme = rows.first { it.id == "theme" }
        assertTrue(theme.subtitle.contains("Obsidian Bloom"))
        assertTrue(theme.subtitle.contains("当前主题"))
    }

    @Test
    fun settings_aboutRowCarriesVersion() {
        val rows = buildSettingsRows(
            cacheLabel = "c",
            storageLabel = "s",
            versionLabel = "VibeMusic 1.0.19-ai"
        )
        assertEquals("VibeMusic 1.0.19-ai", rows.first { it.id == "about" }.subtitle)
    }

    @Test
    fun storage_zeroAndNegativeClamp() {
        assertEquals("0.0 MB", formatStorageMb(0L))
        assertEquals("0.0 MB", formatStorageMb(-99L))
    }

    @Test
    fun storage_formatsMegabytes() {
        assertEquals("1.5 MB", formatStorageMb(1_572_864L))
        assertEquals("150.0 MB", formatStorageMb(150L * 1024L * 1024L))
    }

    @Test
    fun storage_totalLabelSumsBothSides() {
        assertEquals(
            "缓存 0.0 MB · 下载 0.0 MB · 共 0.0 MB",
            storageTotalLabel(0L, 0L)
        )
        val label = storageTotalLabel(1_048_576L, 2_097_152L)
        assertTrue(label.contains("缓存 1.0 MB"))
        assertTrue(label.contains("下载 2.0 MB"))
        assertTrue(label.contains("共 3.0 MB"))
    }

    @Test
    fun dirAudioBytes_sumsOnlyMp3() {
        val dir = Files.createTempDirectory("p10dl").toFile()
        try {
            File(dir, "a.mp3").writeBytes(ByteArray(10))
            File(dir, "b.mp3").writeBytes(ByteArray(25))
            File(dir, "note.txt").writeBytes(ByteArray(1000))
            File(dir, "song.meta").writeBytes(ByteArray(50))
            assertEquals(35L, dirAudioBytes(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun dirAudioBytes_missingDirIsZero() {
        assertEquals(0L, dirAudioBytes(File("/nonexistent-p10-dir-xyz")))
    }
}
