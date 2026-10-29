package com.cyk666.vibemusic

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * C5 第 1 片：`playMode` 状态持有者。重点守两条不变量 ——
 * ① 推进顺序与 controller 同步的先后不能颠倒；
 * ② controller 写失败时模式必须保持不变（否则 UI 与播放器不一致）。
 */
@RunWith(RobolectricTestRunner::class)
class PlayModeStateTest {

    @Test
    fun defaultIsSequential() {
        assertEquals(PlayMode.SEQUENTIAL, PlayModeState().mode)
    }

    @Test
    fun cycleWalksTheFullLoop() {
        val state = PlayModeState()
        val seen = mutableListOf<PlayMode>()
        repeat(4) { seen += state.cycle() }
        assertEquals(
            listOf(PlayMode.LIST_LOOP, PlayMode.SINGLE_LOOP, PlayMode.SHUFFLE, PlayMode.SEQUENTIAL),
            seen
        )
        assertEquals(PlayMode.SEQUENTIAL, state.mode)
    }

    @Test
    fun cycleAppliesTheNewModeToController() {
        val state = PlayModeState(PlayMode.SEQUENTIAL)
        val applied = mutableListOf<RepeatShuffle>()
        state.cycle { applied += it }
        assertEquals(1, applied.size)
        assertEquals(Player.REPEAT_MODE_ALL, applied[0].repeatMode)
        assertFalse(applied[0].shuffleOn)
        assertEquals(PlayMode.LIST_LOOP, state.mode)
    }

    @Test
    fun cycleToShuffleTurnsShuffleOn() {
        val state = PlayModeState(PlayMode.SINGLE_LOOP)
        var applied: RepeatShuffle? = null
        state.cycle { applied = it }
        assertEquals(Player.REPEAT_MODE_ALL, applied?.repeatMode)
        assertTrue(applied?.shuffleOn == true)
        assertEquals(PlayMode.SHUFFLE, state.mode)
    }

    @Test
    fun controllerFailureLeavesModeUntouched() {
        val state = PlayModeState(PlayMode.LIST_LOOP)
        try {
            state.cycle { throw IllegalStateException("controller gone") }
            fail("apply 的异常应向上抛给调用方提示")
        } catch (e: IllegalStateException) {
            assertEquals("controller gone", e.message)
        }
        // 关键不变量：controller 没同步成功，模式就不能前进。
        assertEquals(PlayMode.LIST_LOOP, state.mode)
    }

    @Test
    fun cycleWithoutControllerStillAdvances() {
        val state = PlayModeState()
        assertEquals(PlayMode.LIST_LOOP, state.cycle())
        assertEquals(PlayMode.LIST_LOOP, state.mode)
    }

    @Test
    fun restoreMapsRepeatAndShuffleTruthTable() {
        val state = PlayModeState()
        state.restore(RepeatShuffle(Player.REPEAT_MODE_OFF, false))
        assertEquals(PlayMode.SEQUENTIAL, state.mode)
        state.restore(RepeatShuffle(Player.REPEAT_MODE_ALL, false))
        assertEquals(PlayMode.LIST_LOOP, state.mode)
        state.restore(RepeatShuffle(Player.REPEAT_MODE_ONE, false))
        assertEquals(PlayMode.SINGLE_LOOP, state.mode)
        state.restore(RepeatShuffle(Player.REPEAT_MODE_ALL, true))
        assertEquals(PlayMode.SHUFFLE, state.mode)
    }

    @Test
    fun restoreIgnoresShuffleWhenRepeatIsOff() {
        // controller 上报「不循环 + 随机」时以 repeat 为准，回落到顺序（与 playModeFrom 一致）。
        val state = PlayModeState(PlayMode.SHUFFLE)
        state.restore(RepeatShuffle(Player.REPEAT_MODE_OFF, true))
        assertEquals(PlayMode.SEQUENTIAL, state.mode)
    }

    @Test
    fun repeatsAllCoversListLoopAndShuffleOnly() {
        assertFalse(PlayModeState(PlayMode.SEQUENTIAL).repeatsAll())
        assertTrue(PlayModeState(PlayMode.LIST_LOOP).repeatsAll())
        assertFalse(PlayModeState(PlayMode.SINGLE_LOOP).repeatsAll())
        assertTrue(PlayModeState(PlayMode.SHUFFLE).repeatsAll())
    }
}
