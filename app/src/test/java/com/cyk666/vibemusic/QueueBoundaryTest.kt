package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Next/Prev-at-boundary truth table for [nextBoundaryAction] /
 * [prevBoundaryAction]: mid-queue advances, queue end/start wraps to
 * first/last track in 顺序/列表循环/随机, stays put in 单曲循环.
 */
class QueueBoundaryTest {

    @Test
    fun next_advancesMidQueue_allModes() {
        for (m in PlayMode.entries) {
            assertEquals(BoundaryAction.ADVANCE, nextBoundaryAction(m, true))
        }
    }

    @Test
    fun next_wrapsToFirstAtEnd_unlessSingleLoop() {
        assertEquals(
            BoundaryAction.WRAP_TO_FIRST,
            nextBoundaryAction(PlayMode.SEQUENTIAL, false)
        )
        assertEquals(
            BoundaryAction.WRAP_TO_FIRST,
            nextBoundaryAction(PlayMode.LIST_LOOP, false)
        )
        assertEquals(
            BoundaryAction.WRAP_TO_FIRST,
            nextBoundaryAction(PlayMode.SHUFFLE, false)
        )
    }

    @Test
    fun next_staysAtEnd_singleLoop() {
        assertEquals(BoundaryAction.STAY, nextBoundaryAction(PlayMode.SINGLE_LOOP, false))
    }

    @Test
    fun prev_advancesMidQueue_allModes() {
        for (m in PlayMode.entries) {
            assertEquals(BoundaryAction.ADVANCE, prevBoundaryAction(m, true))
        }
    }

    @Test
    fun prev_wrapsToLastAtStart_unlessSingleLoop() {
        assertEquals(
            BoundaryAction.WRAP_TO_LAST,
            prevBoundaryAction(PlayMode.SEQUENTIAL, false)
        )
        assertEquals(
            BoundaryAction.WRAP_TO_LAST,
            prevBoundaryAction(PlayMode.LIST_LOOP, false)
        )
        assertEquals(
            BoundaryAction.WRAP_TO_LAST,
            prevBoundaryAction(PlayMode.SHUFFLE, false)
        )
    }

    @Test
    fun prev_staysAtStart_singleLoop() {
        assertEquals(BoundaryAction.STAY, prevBoundaryAction(PlayMode.SINGLE_LOOP, false))
    }

    /**
     * 2026-11-03 播放链路审计 P1：单曲循环下 `seekToNextMediaItem()` 会原地重开本曲
     * （PB-6），所以手动切歌必须按下标算 —— 否则「下一首」等于失效。
     */
    @Test
    fun manualSkip_singleLoopStepsByIndex() {
        // 三首队列，中间位置
        assertEquals(2, manualSkipTarget(PlayMode.SINGLE_LOOP, 3, 1, true))
        assertEquals(0, manualSkipTarget(PlayMode.SINGLE_LOOP, 3, 1, false))
        // 边界环绕
        assertEquals(0, manualSkipTarget(PlayMode.SINGLE_LOOP, 3, 2, true))
        assertEquals(2, manualSkipTarget(PlayMode.SINGLE_LOOP, 3, 0, false))
        // 单首队列：仍重开本曲（单曲循环语义），返回 0 而不是"不处理"
        assertEquals(0, manualSkipTarget(PlayMode.SINGLE_LOOP, 1, 0, true))
    }

    /** 其它模式必须返回 -1（交给 seekToNext/Previous）：洗牌序只有播放器知道。 */
    @Test
    fun manualSkip_otherModesDeferToPlayerOrder() {
        for (m in listOf(PlayMode.SEQUENTIAL, PlayMode.LIST_LOOP, PlayMode.SHUFFLE)) {
            assertEquals(-1, manualSkipTarget(m, 3, 1, true))
            assertEquals(-1, manualSkipTarget(m, 3, 1, false))
        }
        // 空队列也不能返回 0（会让 seekTo(0) 落到不存在的项）
        assertEquals(-1, manualSkipTarget(PlayMode.SINGLE_LOOP, 0, 0, true))
    }
}
