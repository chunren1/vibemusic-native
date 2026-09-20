package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 进程被杀后从通知栏/媒体按钮恢复播放（Media3 官方 [androidx.media3.session.MediaSession.Callback.onPlaybackResumption]）
 * 的纯决策：恢复起始下标与起始位置的钳制规则。
 *
 * 这几条钉死"按钮点了没反应"修复的边界：空快照不可恢复（返回 -1 让 Media3 走空操作），
 * 越界/负数一律钳进合法范围，避免把非法 seek 位置交给播放器。
 */
class PlaybackResumptionTest {

    private fun song(id: String) = Song(id, "n$id", "a", "al", "", 180, "netease")

    @Test
    fun emptyQueue_notResumable() {
        assertEquals(-1, resumptionStartIndex(emptyList(), 3))
    }

    @Test
    fun validIndex_passthrough() {
        val songs = listOf(song("1"), song("2"), song("3"))
        assertEquals(1, resumptionStartIndex(songs, 1))
    }

    @Test
    fun negativeIndex_clampsToFirst() {
        assertEquals(0, resumptionStartIndex(listOf(song("1")), -5))
    }

    @Test
    fun indexBeyondEnd_clampsToLast() {
        val songs = listOf(song("1"), song("2"))
        assertEquals(1, resumptionStartIndex(songs, 99))
    }

    @Test
    fun negativePosition_startsAtZero() {
        assertEquals(0L, resumptionStartPosition(-1_200L))
    }

    @Test
    fun positivePosition_passthrough() {
        assertEquals(42_000L, resumptionStartPosition(42_000L))
    }
}
