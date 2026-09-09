package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun formatDuration_zero() {
        assertEquals("0:00", formatDuration(0))
    }

    @Test
    fun formatDuration_minutePlusSecond() {
        assertEquals("1:01", formatDuration(61))
    }

    @Test
    fun formatDuration_typicalSong() {
        assertEquals("3:51", formatDuration(231))
    }

    @Test
    fun formatDuration_exactHour() {
        assertEquals("60:00", formatDuration(3600))
    }

    @Test
    fun formatDuration_negativeClampsToZero() {
        assertEquals("0:00", formatDuration(-5))
    }
}
