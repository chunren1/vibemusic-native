package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 合同 #1/#2 回归：后端 refresh 凭据只认 Cookie（VIBE_REFRESH，body 永不回
 * refreshToken），App 侧靠共享 InMemoryCookieJar 续命。
 * 走 JDK stub（零新依赖）；refresh 用 trySilentRefreshWith 直测真实链路。
 */
@RunWith(RobolectricTestRunner::class)
class CookieRefreshTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private val testClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    @Before
    fun seed(): Unit = runBlocking {
        val ctx = context()
        VibeApi.init(ctx)
        VibeApi.clearCookiesForTest()
        AuthToken.token = ""
        AuthToken.refreshToken = ""
        AuthToken.username = ""
        AuthStore.save(ctx, "old-acc", "alice", "Alice", "old-ref")
    }

    @After
    fun unseed() {
        VibeApi.clearCookiesForTest()
    }

    /** 带 Set-Cookie 下发 + Cookie 请求头记录的顺序 stub。 */
    private class CookieStub(
        private val script: List<Triple<Int, List<String>, String>>
    ) {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val hits = AtomicInteger(0)
        val cookieHeaders: MutableList<String?> =
            Collections.synchronizedList(mutableListOf())

        fun start() {
            Thread {
                try {
                    while (!server.isClosed) {
                        val sock = server.accept()
                        Thread {
                            sock.use { s ->
                                val input = s.getInputStream().bufferedReader()
                                var contentLength = 0
                                var cookie: String? = null
                                var line = input.readLine()
                                while (!line.isNullOrEmpty()) {
                                    Regex("(?i)content-length:\\s*(\\d+)").find(line)?.let {
                                        contentLength = it.groupValues[1].toInt()
                                    }
                                    Regex("(?i)^cookie:\\s*(.+)").find(line)?.let {
                                        cookie = it.groupValues[1].trim()
                                    }
                                    line = input.readLine()
                                }
                                repeat(contentLength) { input.read() }
                                cookieHeaders.add(cookie)
                                val idx = hits.getAndIncrement().coerceAtMost(script.size - 1)
                                val (code, headers, body) = script[idx]
                                val bytes = body.toByteArray()
                                val reason = if (code == 401) "Unauthorized" else "OK"
                                val head = StringBuilder("HTTP/1.1 $code $reason\r\n")
                                head.append("Content-Type: application/json\r\n")
                                for (h in headers) head.append(h).append("\r\n")
                                head.append("Content-Length: ${bytes.size}\r\n")
                                head.append("Connection: close\r\n\r\n")
                                val out = s.getOutputStream()
                                out.write(head.toString().toByteArray())
                                out.write(bytes)
                                out.flush()
                            }
                        }.apply { isDaemon = true; start() }
                    }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }
        }

        fun endpoint(): String = "http://127.0.0.1:${server.localPort}/api/auth/refresh"

        fun stop() {
            try {
                server.close()
            } catch (_: Exception) {
            }
        }
    }

    /** 真实后端形状：data 只有 token（无 refreshToken）+ Set-Cookie 轮转。 */
    private fun tokenOnlyBody(token: String): String =
        JSONObject().put("code", 200)
            .put("data", JSONObject().put("token", token)).toString()

    private fun setRefreshCookie(value: String): String =
        "Set-Cookie: VIBE_REFRESH=$value; Path=/api/auth/refresh; Max-Age=604800; HttpOnly; SameSite=Lax"

    // ---- #1a: jar 存取 + 路径隔离 + 过期淘汰（OkHttp Cookie 契约） ----

    @Test
    fun jar_roundTripStoresSendsAndScopesRefreshCookie() {
        val jar = InMemoryCookieJar()
        val refreshUrl = "http://127.0.0.1/api/auth/refresh".toHttpUrl()
        val meUrl = "http://127.0.0.1/api/auth/me".toHttpUrl()
        val loginCookie = Cookie.parse(
            refreshUrl,
            "VIBE_REFRESH=ref-abc; Path=/api/auth/refresh; Max-Age=604800; HttpOnly; SameSite=Lax"
        )!!
        jar.saveFromResponse(refreshUrl, listOf(loginCookie))

        // 存取往返：refresh 路径带回，me 路径按 Path 隔离不带。
        assertEquals("ref-abc", jar.valueFor(refreshUrl, "VIBE_REFRESH"))
        assertEquals("", jar.valueFor(meUrl, "VIBE_REFRESH"))
        assertTrue(jar.loadForRequest(refreshUrl).any { it.name == "VIBE_REFRESH" })
        assertFalse(jar.loadForRequest(meUrl).any { it.name == "VIBE_REFRESH" })

        // 同名同域同路径覆盖（轮转语义）。
        val rotated = Cookie.parse(
            refreshUrl,
            "VIBE_REFRESH=ref-new; Path=/api/auth/refresh; Max-Age=604800; HttpOnly"
        )!!
        jar.saveFromResponse(refreshUrl, listOf(rotated))
        assertEquals("ref-new", jar.valueFor(refreshUrl, "VIBE_REFRESH"))

        // 过期 cookie 永不入库。
        val dead = Cookie.parse(refreshUrl, "VIBE_REFRESH=gone; Path=/api/auth/refresh; Max-Age=0")!!
        jar.saveFromResponse(refreshUrl, listOf(dead))
        assertEquals("", jar.valueFor(refreshUrl, "VIBE_REFRESH"))
    }

    // ---- #1b: 持久化凭据（jar 空，冷启动态）也能刷出新 access ----

    @Test
    fun refresh_persistedCredentialSucceedsOnTokenOnlyBody(): Unit = runBlocking {
        val stub = CookieStub(
            listOf(Triple(200, listOf(setRefreshCookie("new-ref")), tokenOnlyBody("new-acc")))
        )
        stub.start()
        try {
            // jar 为空（冷启动后进程内 jar 已失），只剩 AuthStore 持久值。
            assertEquals("", VibeApi.refreshCookieFor(stub.endpoint()))
            val outcome = VibeApi.trySilentRefreshWith("old-acc", stub.endpoint(), testClient)
            assertEquals(RefreshOutcome.REFRESHED, outcome)
            // stub 亲眼看到 Cookie 头（后端读 Cookie 的唯一通道）。
            assertEquals("VIBE_REFRESH=old-ref", stub.cookieHeaders.single())
            // 新 access 生效 + 本次响应 Set-Cookie 轮转值已持久化。
            assertEquals("new-acc", AuthToken.token)
            val snap = AuthStore.load(context())
            assertEquals("new-acc", snap.token)
            assertEquals("new-ref", snap.refreshToken)
        } finally {
            stub.stop()
        }
    }

    // ---- #1c: body 凭据为空时 jar cookie 独立续命（修复前恒 NETWORK_FAIL） ----

    @Test
    fun refresh_jarCookieAloneSucceedsWhenBodyCredentialBlank(): Unit = runBlocking {
        val stub = CookieStub(
            listOf(Triple(200, listOf(setRefreshCookie("new-jar-ref")), tokenOnlyBody("new-acc")))
        )
        stub.start()
        try {
            val url = stub.endpoint().toHttpUrl()
            val seed = Cookie.parse(
                url, "VIBE_REFRESH=jar-ref; Path=/api/auth/refresh; Max-Age=604800; HttpOnly"
            )!!
            VibeApi.cookieJar.saveFromResponse(url, listOf(seed))
            AuthToken.refreshToken = ""
            val outcome = VibeApi.trySilentRefreshWith("old-acc", stub.endpoint(), testClient)
            assertEquals(RefreshOutcome.REFRESHED, outcome)
            assertEquals("VIBE_REFRESH=jar-ref", stub.cookieHeaders.single())
            assertEquals("new-acc", AuthToken.token)
            assertEquals("new-jar-ref", AuthStore.load(context()).refreshToken)
        } finally {
            stub.stop()
        }
    }

    // ---- #2a: me 续期成功（401→REFRESHED→重试一次→parseMe 命中） ----

    @Test
    fun me_renewThenSucceedsWithSingleRetry(): Unit = runBlocking {
        var calls = 0
        var refreshCalls = 0
        val meJson = JSONObject().put("code", 200).put(
            "data", JSONObject()
                .put("userId", "1")
                .put("username", "alice")
                .put("nickname", "Alice")
        ).toString()
        // me() 真实形态：authed{ transport/envelope 解析 }，401 进续期只重试一次。
        val user = VibeApi.authedWith({ failed ->
            refreshCalls++
            assertEquals("old-acc", failed)
            AuthToken.token = "new-acc"
            RefreshOutcome.REFRESHED
        }) {
            calls++
            if (calls == 1) throw AuthException("密码错/登录过期，请重登 (HTTP 401)")
            VibeApi.parseMe(meJson)
        }
        assertEquals("Alice", user?.nickname)
        assertEquals(2, calls)
        assertEquals(1, refreshCalls)
    }

    // ---- #2b: me 决断性 401 清退，网络抖动保活（冷启动分支语义） ----

    @Test
    fun me_decisive401ClearsWhileNetworkFailKeeps(): Unit = runBlocking {
        // 决断性拒绝：原 AuthException 上抛 → shouldClear → 调用方清退。
        try {
            VibeApi.authedWith({ RefreshOutcome.INVALID_TOKEN }) {
                throw AuthException("密码错/登录过期，请重登 (HTTP 401)")
            }
            fail("expected AuthException")
        } catch (e: AuthException) {
            assertTrue(shouldClearTokensOnFailure(e))
        }
        AuthStore.clear(context())
        assertEquals("", AuthStore.load(context()).token)

        // 网络抖动：NetworkAuthException 上抛 → 不清 → 下次鉴权自然重试。
        AuthStore.save(context(), "old-acc", "alice", "Alice", "old-ref")
        try {
            VibeApi.authedWith({ RefreshOutcome.NETWORK_FAIL }) {
                throw AuthException("密码错/登录过期，请重登 (HTTP 401)")
            }
            fail("expected NetworkAuthException")
        } catch (e: NetworkAuthException) {
            assertFalse(shouldClearTokensOnFailure(e))
        }
        val snap = AuthStore.load(context())
        assertEquals("old-acc", snap.token)
        assertEquals("old-ref", snap.refreshToken)
    }
}
