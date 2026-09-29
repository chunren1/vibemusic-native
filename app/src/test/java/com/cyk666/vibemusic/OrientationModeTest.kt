package com.cyk666.vibemusic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OrientationModeTest {

    @Test
    fun parse_followSystemCaseInsensitive() {
        assertEquals(OrientationMode.FOLLOW_SYSTEM, parseOrientationMode("FOLLOW_SYSTEM"))
        assertEquals(OrientationMode.FOLLOW_SYSTEM, parseOrientationMode("  follow_system "))
    }

    @Test
    fun parse_blankOrUnknownDefaultsLockPortrait() {
        assertEquals(OrientationMode.LOCK_PORTRAIT, parseOrientationMode(null))
        assertEquals(OrientationMode.LOCK_PORTRAIT, parseOrientationMode(""))
        assertEquals(OrientationMode.LOCK_PORTRAIT, parseOrientationMode("LANDSCAPE"))
        assertEquals(OrientationMode.LOCK_PORTRAIT, parseOrientationMode("LOCK_PORTRAIT"))
    }

    @Test
    fun subtitle_lockPortraitMentionsNoStateLoss() {
        val s = orientationModeSubtitle(OrientationMode.LOCK_PORTRAIT)
        assertEquals(true, s.contains("锁定竖屏"))
    }

    @Test
    fun subtitle_followSystemWarnsRecreation() {
        val s = orientationModeSubtitle(OrientationMode.FOLLOW_SYSTEM)
        assertEquals(true, s.contains("跟随系统"))
    }

    @Test
    fun store_roundTrip() {
        val ctx = RuntimeEnvironment.getApplication()
        runBlocking {
            QueueStore.saveOrientationMode(ctx, OrientationMode.FOLLOW_SYSTEM)
            assertEquals(OrientationMode.FOLLOW_SYSTEM, QueueStore.loadOrientationMode(ctx))
            QueueStore.saveOrientationMode(ctx, OrientationMode.LOCK_PORTRAIT)
            assertEquals(OrientationMode.LOCK_PORTRAIT, QueueStore.loadOrientationMode(ctx))
        }
    }
}
