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
}
