package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/**
 * Outcome table for the token-nuking fix: a transient network blip during
 * silent refresh must NEVER destroy the still-valid 7-day refreshToken.
 * Refresh traffic goes to a JDK stub server (zero new dependencies).
 */
@RunWith(RobolectricTestRunner::class)
class AuthRefreshOutcomeTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private val testClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    @Before
    fun seedTokens(): Unit = runBlocking {
        val ctx = context()
        VibeApi.init(ctx)
        AuthToken.token = ""
        AuthToken.refreshToken = ""
        AuthToken.username = ""
        AuthStore.save(ctx, "old-acc", "alice", "Alice", "old-ref")
    }

    /** Minimal single-response HTTP stub over ServerSocket (no new deps). */
    private class StubHttp(private val body: String) {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort

        fun start() {
            Thread {
                try {
                    while (!server.isClosed) {
                        val sock = server.accept()
                        Thread {
                            sock.use { s ->
                                val input = s.getInputStream().bufferedReader()
                                var contentLength = 0
                                var line = input.readLine()
                                while (!line.isNullOrEmpty()) {
                                    Regex("(?i)content-length:\\s*(\\d+)").find(line)?.let {
                                        contentLength = it.groupValues[1].toInt()
                                    }
                                    line = input.readLine()
                                }
                                repeat(contentLength) { input.read() }
                                val bytes = body.toByteArray()
                                val out = s.getOutputStream()
                                out.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
                                        .toByteArray()
                                )
                                out.write(bytes)
                                out.flush()
                            }
                        }.apply { isDaemon = true; start() }
                    }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }
        }

        fun endpoint(): String = "http://127.0.0.1:$port/api/auth/refresh"

        fun stop() {
            try {
                server.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun stubServer(body: String, httpCode: Int = 200): StubHttp =
        StubHttp(body).apply { start() }

    private fun okJson(token: String, refreshToken: String): String =
        JSONObject()
            .put("code", 200)
            .put(
                "data", JSONObject()
                    .put("token", token)
                    .put("refreshToken", refreshToken)
            )
            .toString()

    @Test
    fun refreshOutcome_401InvalidReplyKeepsStoredTokens(): Unit = runBlocking {
        val server = stubServer(
            JSONObject().put("code", 401).put("message", "refresh expired").toString()
        )
        try {
            val outcome = VibeApi.trySilentRefreshWith("old-acc", server.endpoint(), testClient)
            assertEquals(RefreshOutcome.INVALID_TOKEN, outcome)
            val snap = AuthStore.load(context())
            assertEquals("old-acc", snap.token)
            assertEquals("old-ref", snap.refreshToken)
        } finally {
            server.stop()
        }
    }

    @Test
    fun refreshOutcome_garbageReplyKeepsStoredTokens(): Unit = runBlocking {
        val server = stubServer("garbage{{{")
        try {
            val outcome = VibeApi.trySilentRefreshWith("old-acc", server.endpoint(), testClient)
            assertEquals(RefreshOutcome.INVALID_TOKEN, outcome)
            val snap = AuthStore.load(context())
            assertEquals("old-acc", snap.token)
            assertEquals("old-ref", snap.refreshToken)
        } finally {
            server.stop()
        }
    }

    @Test
    fun refreshOutcome_connectionRefusedIsNetworkFailKeepsTokens(): Unit = runBlocking {
        val outcome = VibeApi.trySilentRefreshWith(
            "old-acc",
            "http://127.0.0.1:1/api/auth/refresh",
            testClient
        )
        assertEquals(RefreshOutcome.NETWORK_FAIL, outcome)
        val snap = AuthStore.load(context())
        assertEquals("old-acc", snap.token)
        assertEquals("old-ref", snap.refreshToken)
    }

    @Test
    fun refreshOutcome_missingRefreshTokenIsNetworkFail(): Unit = runBlocking {
        AuthToken.refreshToken = ""
        val outcome = VibeApi.trySilentRefreshWith(
            "old-acc",
            "http://127.0.0.1:1/api/auth/refresh",
            testClient
        )
        assertEquals(RefreshOutcome.NETWORK_FAIL, outcome)
    }

    @Test
    fun refreshOutcome_reusedFreshTokenSkipsNetwork(): Unit = runBlocking {
        AuthToken.token = "token-B"
        val outcome = VibeApi.trySilentRefreshWith(
            "token-A",
            "http://127.0.0.1:1/api/auth/refresh",
            testClient
        )
        assertEquals(RefreshOutcome.REFRESHED, outcome)
    }

    @Test
    fun refreshOutcome_successRotatesAndPersistsPair(): Unit = runBlocking {
        val server = stubServer(okJson("new-acc", "new-ref"))
        try {
            val outcome = VibeApi.trySilentRefreshWith("old-acc", server.endpoint(), testClient)
            assertEquals(RefreshOutcome.REFRESHED, outcome)
            assertEquals("new-acc", AuthToken.token)
            assertEquals("new-ref", AuthToken.refreshToken)
            val snap = AuthStore.load(context())
            assertEquals("new-acc", snap.token)
            assertEquals("new-ref", snap.refreshToken)
            assertEquals("alice", snap.username)
        } finally {
            server.stop()
        }
    }

    @Test
    fun route_invalidTokenRethrowsOriginalAuthException() {
        val original = AuthException("expired")
        try {
            VibeApi.routeAfterRefresh(RefreshOutcome.INVALID_TOKEN, original)
            fail("expected AuthException")
        } catch (e: AuthException) {
            assertSame(original, e)
        }
    }

    @Test
    @Suppress("USELESS_IS_CHECK")
    fun route_networkFailThrowsNetworkAuthExceptionOutsideAuthHierarchy() {
        try {
            VibeApi.routeAfterRefresh(RefreshOutcome.NETWORK_FAIL, AuthException("expired"))
            fail("expected NetworkAuthException")
        } catch (e: NetworkAuthException) {
            assertEquals("网络连接断开，登录态保留", e.message)
            assertFalse(e is AuthException)
        }
    }

    @Test
    fun route_refreshedReturnsSoCallerRetriesOnce() {
        VibeApi.routeAfterRefresh(RefreshOutcome.REFRESHED, AuthException("expired"))
    }

    @Test
    @Suppress("USELESS_IS_CHECK")
    fun networkAuthException_isNeverCaughtAsAuthException() {
        assertFalse(NetworkAuthException("网络连接断开，登录态保留") is AuthException)
    }

    @Test
    fun shouldClearTokens_onlyInvalidCredentialsClear() {
        assertTrue(shouldClearTokensOnFailure(AuthException("expired")))
        assertFalse(shouldClearTokensOnFailure(NetworkAuthException("网络连接断开，登录态保留")))
        assertFalse(shouldClearTokensOnFailure(RuntimeException("boom")))
    }
}
