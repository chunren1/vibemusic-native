package com.cyk666.vibemusic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ControllerReconnectTest {

    @Test
    fun missingController_reconnects() {
        assertTrue(shouldReconnectController(controllerNull = true, probeFailed = false))
    }

    @Test
    fun deadBinder_reconnects() {
        assertTrue(shouldReconnectController(controllerNull = false, probeFailed = true))
    }

    @Test
    fun liveController_neverRebinds() {
        assertFalse(shouldReconnectController(controllerNull = false, probeFailed = false))
    }

    @Test
    fun failureMessage_carriesReasonAndBatteryGuidance() {
        val msg = controllerFailureMessage("timeout")
        assertTrue(msg.contains("timeout"))
        assertTrue(msg.contains("电池"))
        assertTrue(msg.contains("MIUI"))
    }
}
