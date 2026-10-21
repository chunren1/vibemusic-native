package com.cyk666.vibemusic

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SilentFail is pure in-memory bookkeeping, so these cases run as plain JUnit
 * (no Robolectric): they pin the two properties the crash-report summary
 * relies on — counts are exact, and the ordering ring stays bounded.
 */
class SilentFailTest {

    @Before
    fun setUp() = SilentFail.resetForTest()

    @After
    fun tearDown() = SilentFail.resetForTest()

    @Test
    fun emptySummaryIsBlank() {
        assertEquals("", SilentFail.summary())
        assertEquals(0L, SilentFail.lastTsMs)
    }

    @Test
    fun noteCountsTotalAndDistinct() {
        SilentFail.note("A.one:1")
        SilentFail.note("A.one:1")
        SilentFail.note("B.two:2")

        val s = SilentFail.summary()
        assertTrue("total=3", s.contains("total=3"))
        assertTrue("distinct=2", s.contains("distinct=2"))
        assertTrue("counting order", s.contains("A.one:1 x2"))
        assertTrue("single occurrence", s.contains("B.two:2 x1"))
        assertTrue(SilentFail.lastTsMs > 0L)
    }

    @Test
    fun blankCodeIsIgnored() {
        SilentFail.note("")
        SilentFail.note("   ")
        assertEquals("", SilentFail.summary())
    }

    @Test
    fun ringKeepsOnlyTheMostRecentCodes() {
        repeat(SilentFail.RING_MAX + 8) { i -> SilentFail.note("c$i") }

        val s = SilentFail.summary()
        val recent = s.substringAfter("recent=")
        val codes = recent.split(">")

        assertEquals(SilentFail.RING_MAX, codes.size)
        assertEquals("c${SilentFail.RING_MAX + 7}", codes.last())
        assertFalse("oldest entries must be evicted", recent.contains("c0>"))
    }

    @Test
    fun distinctCodesAreCappedButExistingOnesKeepCounting() {
        repeat(SilentFail.DISTINCT_MAX) { i -> SilentFail.note("d$i") }
        // A brand-new code past the cap is dropped, not counted...
        SilentFail.note("overflow")
        // ...while a known code still increments normally.
        SilentFail.note("d0")

        val s = SilentFail.summary()
        assertTrue(s.contains("distinct=${SilentFail.DISTINCT_MAX}"))
        assertTrue(s.contains("total=${SilentFail.DISTINCT_MAX + 1}"))
        assertFalse(s.contains("overflow"))
        assertTrue(s.contains("d0 x2"))
    }

    @Test
    fun resetClearsEverything() {
        SilentFail.note("x")
        SilentFail.resetForTest()
        assertEquals("", SilentFail.summary())
        assertEquals(0L, SilentFail.lastTsMs)
    }
}
