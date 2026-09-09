package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AuthRefreshTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private fun loginJson(token: String, refreshToken: String?): String {
        val data = JSONObject()
            .put("token", token)
            .put("username", "alice")
            .put("nickname", "Alice")
        if (refreshToken != null) data.put("refreshToken", refreshToken)
        return JSONObject().put("code", 200).put("data", data).toString()
    }

    @Test
    fun parseLogin_readsRefreshToken() {
        val r = VibeApi.parseLogin(loginJson("acc1", "ref1"))
        assertEquals("acc1", r.token)
        assertEquals("ref1", r.refreshToken)
    }

    @Test
    fun parseLogin_missingRefreshTokenDefaultsBlank() {
        val r = VibeApi.parseLogin(loginJson("acc1", null))
        assertEquals("acc1", r.token)
        assertEquals("", r.refreshToken)
    }

    @Test
    fun buildRefreshBody_carriesToken() {
        val o = JSONObject(buildRefreshBody("ref-abc"))
        assertEquals("ref-abc", o.optString("refreshToken"))
    }

    @Test
    fun parseRefreshResult_okReturnsRotatedPair() {
        val json = JSONObject()
            .put("code", 200)
            .put(
                "data", JSONObject()
                    .put("token", "new-acc")
                    .put("refreshToken", "new-ref")
            )
            .toString()
        val r = parseRefreshResult(json)!!
        assertEquals("new-acc", r.token)
        assertEquals("new-ref", r.refreshToken)
    }

    @Test
    fun parseRefreshResult_401IsNull() {
        val json = JSONObject()
            .put("code", 401)
            .put("message", "refresh expired")
            .toString()
        assertNull(parseRefreshResult(json))
    }

    @Test
    fun parseRefreshResult_missingDataIsNull() {
        assertNull(parseRefreshResult(JSONObject().put("code", 200).toString()))
        assertNull(parseRefreshResult("garbage{{{"))
        assertNull(parseRefreshResult(""))
    }

    @Test
    fun reuseCheck_newTokenAfterFailureIsReused() {
        assertTrue(shouldReuseRefreshedToken("token-B", "token-A"))
    }

    @Test
    fun reuseCheck_sameTokenIsNotReused() {
        assertFalse(shouldReuseRefreshedToken("token-A", "token-A"))
    }

    @Test
    fun reuseCheck_blankCurrentIsNotReused() {
        assertFalse(shouldReuseRefreshedToken("", "token-A"))
        assertFalse(shouldReuseRefreshedToken("", ""))
    }

    @Test
    fun authStore_refreshTokenRoundTrip(): Unit = runBlocking {
        AuthStore.save(context(), "acc1", "alice", "Alice", "ref1")
        val snap = AuthStore.load(context())
        assertEquals("ref1", snap.refreshToken)
        assertEquals("ref1", AuthToken.refreshToken)
    }

    @Test
    fun authStore_clearWipesRefreshToken(): Unit = runBlocking {
        AuthStore.save(context(), "acc1", "alice", "Alice", "ref1")
        AuthStore.clear(context())
        val snap = AuthStore.load(context())
        assertEquals("", snap.refreshToken)
        assertEquals("", AuthToken.refreshToken)
    }

    @Test
    fun authStore_saveTokensRotatesPairKeepsIdentity(): Unit = runBlocking {
        AuthStore.save(context(), "acc1", "alice", "Alice", "ref1")
        AuthStore.saveTokens(context(), "acc2", "ref2")
        val snap = AuthStore.load(context())
        assertEquals("acc2", snap.token)
        assertEquals("ref2", snap.refreshToken)
        assertEquals("alice", snap.username)
        assertEquals("Alice", snap.nickname)
    }

    @Test
    fun parseSleepMinutes_edges() {
        assertEquals(5, parseSleepMinutes("5"))
        assertEquals(180, parseSleepMinutes("180"))
        assertEquals(30, parseSleepMinutes(" 30 "))
        assertEquals(null, parseSleepMinutes("4"))
        assertEquals(null, parseSleepMinutes("181"))
        assertEquals(null, parseSleepMinutes("abc"))
        assertEquals(null, parseSleepMinutes(""))
        assertEquals(null, parseSleepMinutes("   "))
    }

    @Test
    fun sleepMinutes_persistRoundTrip(): Unit = runBlocking {
        QueueStore.saveSleepMinutes(context(), 45)
        assertEquals(45, QueueStore.loadSleepMinutes(context()))
        QueueStore.saveSleepMinutes(context(), 0)
        assertEquals(0, QueueStore.loadSleepMinutes(context()))
    }
}
