package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PositionTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    @Test
    fun restore_midTrackRestores() {
        assertTrue(shouldRestorePosition(60_000L, 231_000L))
    }

    @Test
    fun restore_introBoundary() {
        assertFalse(shouldRestorePosition(5_000L, 231_000L))
        assertTrue(shouldRestorePosition(5_001L, 231_000L))
        assertFalse(shouldRestorePosition(0L, 231_000L))
    }

    @Test
    fun restore_outroBoundary() {
        // duration 231s: restore only while saved < 221_000
        assertFalse(shouldRestorePosition(221_000L, 231_000L))
        assertFalse(shouldRestorePosition(225_000L, 231_000L))
        assertFalse(shouldRestorePosition(230_999L, 231_000L))
        assertTrue(shouldRestorePosition(220_999L, 231_000L))
    }

    @Test
    fun restore_unknownDurationNeverRestores() {
        assertFalse(shouldRestorePosition(60_000L, 0L))
        assertFalse(shouldRestorePosition(60_000L, -1L))
    }

    @Test
    fun restore_shortTrackNeverRestores() {
        assertFalse(shouldRestorePosition(6_000L, 12_000L))
        assertFalse(shouldRestorePosition(11_000L, 12_000L))
    }

    @Test
    fun restore_constantsMatchSpec() {
        assertEquals(10_000L, POSITION_SAVE_INTERVAL_MS)
        assertEquals(5_000L, POSITION_RESTORE_MIN_MS)
        assertEquals(10_000L, POSITION_RESTORE_END_MARGIN_MS)
    }

    @Test
    fun throttle_gate() {
        assertTrue(shouldSavePositionTick(10_000L, 0L))
        assertTrue(shouldSavePositionTick(25_000L, 10_000L))
        assertFalse(shouldSavePositionTick(9_999L, 0L))
        assertFalse(shouldSavePositionTick(15_000L, 10_000L))
        assertFalse(shouldSavePositionTick(5_000L, 5_000L))
    }

    @Test
    fun positions_jsonRoundTrip() {
        val rendered = renderPositions(mapOf("netease:1" to 60_000L, "qq:9" to 0L))
        val parsed = parsePositions(rendered)
        assertEquals(60_000L, parsed["netease:1"])
        assertEquals(0L, parsed["qq:9"])
    }

    @Test
    fun positions_blankAndCorruptAreEmpty() {
        assertTrue(parsePositions("").isEmpty())
        assertTrue(parsePositions("not-json{{{").isEmpty())
    }

    @Test
    fun store_saveLoadRoundTrip(): Unit = runBlocking {
        val ctx = context()
        QueueStore.savePosition(ctx, "netease:pos-probe", 61_000L)
        assertEquals(61_000L, QueueStore.loadPosition(ctx, "netease:pos-probe"))
        // Overwrite wins.
        QueueStore.savePosition(ctx, "netease:pos-probe", 62_000L)
        assertEquals(62_000L, QueueStore.loadPosition(ctx, "netease:pos-probe"))
        // Unknown key is zero; blank key is zero and never stored.
        assertEquals(0L, QueueStore.loadPosition(ctx, "netease:missing"))
        assertEquals(0L, QueueStore.loadPosition(ctx, ""))
        QueueStore.savePosition(ctx, "", 999L)
        assertEquals(0L, QueueStore.loadPosition(ctx, ""))
    }

    @Test
    fun materialize_knownDurationDefersToWindow() {
        assertEquals(60_000L, resolveMaterializePosition(60_000L, 231_000L))
        assertEquals(0L, resolveMaterializePosition(2_000L, 231_000L))
        assertEquals(0L, resolveMaterializePosition(225_000L, 231_000L))
    }

    @Test
    fun materialize_unknownDurationRestoresPastIntro() {
        // Duration unknown pre-prepare (TIME_UNSET + no metadata): restore when
        // past the 5s intro and let the player clamp at the real duration.
        assertEquals(60_000L, resolveMaterializePosition(60_000L, 0L))
        assertEquals(60_000L, resolveMaterializePosition(60_000L, -1L))
        assertEquals(0L, resolveMaterializePosition(5_000L, 0L))
        assertEquals(0L, resolveMaterializePosition(0L, 0L))
    }

    @Test
    fun store_positionSaveTimestampProof(): Unit = runBlocking {
        val ctx = context()
        QueueStore.savePosition(ctx, "netease:ts-probe", 61_000L)
        assertTrue(QueueStore.loadLastPositionSaveTs(ctx) > 0L)
    }
}
