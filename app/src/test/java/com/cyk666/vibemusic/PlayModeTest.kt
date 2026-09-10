package com.cyk666.vibemusic

import android.content.Context
import androidx.media3.common.Player
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayModeTest {

    @Test
    fun truthTable_sequentialIsRepeatOffShuffleOff() {
        val rs = PlayMode.SEQUENTIAL.toRepeatShuffle()
        assertEquals(Player.REPEAT_MODE_OFF, rs.repeatMode)
        assertFalse(rs.shuffleOn)
    }

    @Test
    fun truthTable_listLoopIsRepeatAllShuffleOff() {
        val rs = PlayMode.LIST_LOOP.toRepeatShuffle()
        assertEquals(Player.REPEAT_MODE_ALL, rs.repeatMode)
        assertFalse(rs.shuffleOn)
    }

    @Test
    fun truthTable_singleLoopIsRepeatOne() {
        val rs = PlayMode.SINGLE_LOOP.toRepeatShuffle()
        assertEquals(Player.REPEAT_MODE_ONE, rs.repeatMode)
    }

    @Test
    fun truthTable_shuffleIsRepeatAllShuffleOn() {
        val rs = PlayMode.SHUFFLE.toRepeatShuffle()
        assertEquals(Player.REPEAT_MODE_ALL, rs.repeatMode)
        assertTrue(rs.shuffleOn)
    }

    @Test
    fun cycleOrder_sequentialToListToSingleToShuffleAndBack() {
        assertEquals(PlayMode.LIST_LOOP, PlayMode.SEQUENTIAL.next())
        assertEquals(PlayMode.SINGLE_LOOP, PlayMode.LIST_LOOP.next())
        assertEquals(PlayMode.SHUFFLE, PlayMode.SINGLE_LOOP.next())
        assertEquals(PlayMode.SEQUENTIAL, PlayMode.SHUFFLE.next())
    }

    @Test
    fun playModeFrom_recoversEachMode() {
        assertEquals(
            PlayMode.SEQUENTIAL,
            playModeFrom(Player.REPEAT_MODE_OFF, false)
        )
        assertEquals(
            PlayMode.LIST_LOOP,
            playModeFrom(Player.REPEAT_MODE_ALL, false)
        )
        assertEquals(
            PlayMode.SINGLE_LOOP,
            playModeFrom(Player.REPEAT_MODE_ONE, false)
        )
        assertEquals(
            PlayMode.SINGLE_LOOP,
            playModeFrom(Player.REPEAT_MODE_ONE, true)
        )
        assertEquals(
            PlayMode.SHUFFLE,
            playModeFrom(Player.REPEAT_MODE_ALL, true)
        )
    }

    @Test
    fun roundTrip_modeSurvivesMappingBothWays() {
        for (m in PlayMode.entries) {
            val rs = m.toRepeatShuffle()
            assertEquals(m, playModeFrom(rs.repeatMode, rs.shuffleOn))
        }
    }

    @Test
    fun labels_areDistinctNonBlank() {
        val labels = PlayMode.entries.map { it.label }
        assertEquals(4, labels.toSet().size)
        assertTrue(labels.all { it.isNotBlank() })
    }

    @Test
    fun iconKinds_areDistinctPerMode() {
        val kinds = PlayMode.entries.map { playModeIconKind(it) }
        assertEquals(4, kinds.toSet().size)
    }

    @Test
    fun announcements_embedModeLabel() {
        for (m in PlayMode.entries) {
            val text = playModeAnnouncement(m)
            assertTrue(text.contains(m.label))
            assertTrue(text.isNotBlank())
        }
        assertEquals("已切换到：随机播放", playModeAnnouncement(PlayMode.SHUFFLE))
    }

    @Test
    fun metaLine_emptyWhenNothingToShow() {
        assertEquals(null, buildPlayerMetaLine(false, false, "睡眠定时：关闭"))
    }

    @Test
    fun metaLine_cachedOnly() {
        assertEquals("已缓存", buildPlayerMetaLine(true, false, "睡眠定时：关闭"))
    }

    @Test
    fun metaLine_sleepOnlyWhenActive() {
        val sleep = "睡眠定时：25分钟 (剩 12:00)"
        assertEquals(sleep, buildPlayerMetaLine(false, true, sleep))
        assertEquals("已缓存 · $sleep", buildPlayerMetaLine(true, true, sleep))
    }

    private fun context(): Context = RuntimeEnvironment.getApplication()

    @Test
    fun persistence_roundTripPreservesRepeatAndShuffle(): Unit = runBlocking {
        QueueStore.savePlayMode(context(), Player.REPEAT_MODE_ALL, true)
        val loaded = QueueStore.loadPlayMode(context())
        assertEquals(Player.REPEAT_MODE_ALL, loaded.repeatMode)
        assertTrue(loaded.shuffleOn)
        assertEquals(PlayMode.SHUFFLE, playModeFrom(loaded.repeatMode, loaded.shuffleOn))
    }

    @Test
    fun persistence_singleLoopRoundTrip(): Unit = runBlocking {
        QueueStore.savePlayMode(context(), Player.REPEAT_MODE_ONE, false)
        val loaded = QueueStore.loadPlayMode(context())
        assertEquals(PlayMode.SINGLE_LOOP, playModeFrom(loaded.repeatMode, loaded.shuffleOn))
    }
}
