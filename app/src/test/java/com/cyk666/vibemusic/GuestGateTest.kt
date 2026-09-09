package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GuestGateTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private fun guestData(guest: Any?): JSONObject {
        val data = JSONObject().put("username", "").put("nickname", "")
        if (guest != null) data.put("guest", guest)
        return data
    }

    private fun envelope(data: JSONObject): String =
        JSONObject().put("code", 200).put("data", data).toString()

    @Test
    fun isGuestPayload_boolTrueIsGuest() {
        assertTrue(isGuestPayload(guestData(true)))
    }

    @Test
    fun isGuestPayload_stringTrueIsGuest() {
        assertTrue(isGuestPayload(guestData("true")))
    }

    @Test
    fun isGuestPayload_intOneIsGuest() {
        assertTrue(isGuestPayload(guestData(1)))
    }

    @Test
    fun isGuestPayload_missingGuestIsNotGuest() {
        assertFalse(isGuestPayload(guestData(null)))
    }

    @Test
    fun isGuestPayload_intZeroIsNotGuest() {
        assertFalse(isGuestPayload(guestData(0)))
    }

    @Test
    fun isGuestPayload_boolFalseIsNotGuest() {
        assertFalse(isGuestPayload(guestData(false)))
    }

    @Test
    fun isGuestPayload_stringFalseIsNotGuest() {
        assertFalse(isGuestPayload(guestData("false")))
    }

    @Test
    fun parseLogin_guestRejected() {
        try {
            VibeApi.parseLogin(envelope(guestData(true)))
            fail("guest login envelope must be rejected")
        } catch (e: RuntimeException) {
            assertTrue(e.message.orEmpty().contains("guest", ignoreCase = true))
        }
    }

    @Test
    fun parseMe_guestReturnsNull() {
        assertNull(VibeApi.parseMe(envelope(guestData(true))))
        assertNull(VibeApi.parseMe(envelope(guestData("true"))))
        assertNull(VibeApi.parseMe(envelope(guestData(1))))
    }

    @Test
    fun parseMe_realUserReturnsUser() {
        val data = JSONObject()
            .put("userId", "7")
            .put("username", "alice")
            .put("nickname", "Alice")
        val u = VibeApi.parseMe(envelope(data))!!
        assertEquals("7", u.userId)
        assertEquals("alice", u.username)
        assertEquals("Alice", u.nickname)
    }

    @Test
    fun decideStartRoute_blankTokenNeverLaunchedGoesLogin() {
        assertEquals(StartRoute.LOGIN, decideStartRoute(tokenBlank = true, launchedBefore = false))
    }

    @Test
    fun decideStartRoute_blankTokenLaunchedStaysGuestSearch() {
        assertEquals(StartRoute.SEARCH_GUEST, decideStartRoute(tokenBlank = true, launchedBefore = true))
    }

    @Test
    fun decideStartRoute_presentTokenRestores() {
        assertEquals(StartRoute.RESTORE, decideStartRoute(tokenBlank = false, launchedBefore = false))
        assertEquals(StartRoute.RESTORE, decideStartRoute(tokenBlank = false, launchedBefore = true))
    }

    @Test
    fun hasLaunchedFlag_roundTrip(): Unit = runBlocking {
        assertFalse(QueueStore.loadHasLaunchedBefore(context()))
        QueueStore.saveHasLaunchedBefore(context())
        assertTrue(QueueStore.loadHasLaunchedBefore(context()))
    }
}
