package com.cyk666.vibemusic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipPolicyTest {

    @Test
    fun skip_zeroFailsWithNext() {
        assertTrue(shouldAutoSkip(0, true))
    }

    @Test
    fun skip_twoFailsWithNext() {
        assertTrue(shouldAutoSkip(2, true))
    }

    @Test
    fun stop_threeFailsEvenWithNext() {
        assertFalse(shouldAutoSkip(3, true))
    }

    @Test
    fun stop_zeroFailsWithoutNext() {
        assertFalse(shouldAutoSkip(0, false))
    }

    @Test
    fun stop_twoFailsWithoutNext() {
        assertFalse(shouldAutoSkip(2, false))
    }

    @Test
    fun stop_threeFailsWithoutNext() {
        assertFalse(shouldAutoSkip(3, false))
    }
}
