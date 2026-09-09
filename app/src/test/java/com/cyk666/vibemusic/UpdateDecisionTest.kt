package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateDecisionTest {

    // ---- compareAndDecide decision table ----

    @Test
    fun decide_newerTag_updateAvailable() {
        assertEquals(
            UpdateDecision.UPDATE_AVAILABLE,
            compareAndDecide("1.0.18-ai", "v1.0.19-ai", fetchOk = true)
        )
    }

    @Test
    fun decide_equalVersions_upToDate() {
        assertEquals(
            UpdateDecision.UP_TO_DATE,
            compareAndDecide("1.0.18-ai", "v1.0.18-ai", fetchOk = true)
        )
    }

    @Test
    fun decide_suffixOnlyDiff_upToDate() {
        assertEquals(
            UpdateDecision.UP_TO_DATE,
            compareAndDecide("1.0.18", "1.0.18-ai", fetchOk = true)
        )
        assertEquals(
            UpdateDecision.UP_TO_DATE,
            compareAndDecide("1.0.18-ai", "1.0.18", fetchOk = true)
        )
    }

    @Test
    fun decide_olderTag_upToDate() {
        assertEquals(
            UpdateDecision.UP_TO_DATE,
            compareAndDecide("1.0.19-ai", "v1.0.18-ai", fetchOk = true)
        )
    }

    @Test
    fun decide_malformedCurrent_checkFailed() {
        assertEquals(
            UpdateDecision.CHECK_FAILED,
            compareAndDecide("", "v1.0.19-ai", fetchOk = true)
        )
        assertEquals(
            UpdateDecision.CHECK_FAILED,
            compareAndDecide("not-a-version", "v1.0.19-ai", fetchOk = true)
        )
    }

    @Test
    fun decide_malformedTag_checkFailed() {
        assertEquals(
            UpdateDecision.CHECK_FAILED,
            compareAndDecide("1.0.18-ai", "", fetchOk = true)
        )
        assertEquals(
            UpdateDecision.CHECK_FAILED,
            compareAndDecide("1.0.18-ai", null, fetchOk = true)
        )
        assertEquals(
            UpdateDecision.CHECK_FAILED,
            compareAndDecide("1.0.18-ai", "latest", fetchOk = true)
        )
    }

    @Test
    fun decide_fetchFailed_checkFailed() {
        assertEquals(
            UpdateDecision.CHECK_FAILED,
            compareAndDecide("1.0.18-ai", "v1.0.19-ai", fetchOk = false)
        )
        assertEquals(
            UpdateDecision.CHECK_FAILED,
            compareAndDecide("1.0.18-ai", null, fetchOk = false)
        )
    }

    // ---- shouldRunUpdateCheck force-bypass throttle ----

    @Test
    fun gate_manualForce_bypassesStaleTimestamp() {
        val now = 1_750_000_000_000L
        val recent = now - 3_600_000L
        assertTrue(shouldRunUpdateCheck(now, recent, force = true))
    }

    @Test
    fun gate_auto_respectsStaleTimestamp() {
        val now = 1_750_000_000_000L
        val recent = now - 3_600_000L
        assertFalse(shouldRunUpdateCheck(now, recent, force = false))
        assertFalse(shouldRunUpdateCheck(now, recent))
    }

    @Test
    fun gate_auto_allowsAfterInterval() {
        val now = 1_750_000_000_000L
        assertTrue(shouldRunUpdateCheck(now, now - UPDATE_CHECK_INTERVAL_MS, force = false))
        assertTrue(shouldRunUpdateCheck(now, 0L, force = false))
    }

    // ---- formatVersionLabel ----

    @Test
    fun versionLabel_format() {
        assertEquals("版本 1.0.18-ai", formatVersionLabel("1.0.18-ai"))
    }

    @Test
    fun versionLabel_blankFallsBack() {
        assertEquals("版本 未知", formatVersionLabel(""))
    }
}
