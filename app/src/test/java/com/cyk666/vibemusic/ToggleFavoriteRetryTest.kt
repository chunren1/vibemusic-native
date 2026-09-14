package com.cyk666.vibemusic

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Top-2 回归：toggleFavorite 的 HTTP 401 与信封 401 都必须进静默续期后
 * 只重试一次；反复 401 时最多打两次请求（无自递归），第二次直接抛给
 * 调用方（MainActivity 按 NetworkAuthException 保活 / AuthException 清退）。
 * 走 JDK 顺序 stub（零新依赖），refresh 用 lambda 注入。
 */
@RunWith(RobolectricTestRunner::class)
class ToggleFavoriteRetryTest {

    private val demoSong = Song(
        sourceId = "1895330088",
        name = "予以",
        artist = "队长",
        album = "",
        coverUrl = "https://cover.example/x.jpg",
        durationSec = 231,
        platform = "netease"
    )

    private val testClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    @Before
    fun seedTokens() {
        AuthToken.token = "old-acc"
        AuthToken.refreshToken = "old-ref"
        AuthToken.username = "alice"
    }

    /** 顺序响应 stub：按序返回，耗尽后重复最后一个；记录每次的 Authorization 头。 */
    private class SeqStub(private val script: List<Pair<Int, String>>) {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val hits = AtomicInteger(0)
        val authHeaders: MutableList<String?> =
            Collections.synchronizedList(mutableListOf())

        fun start() {
            Thread {
                try {
                    while (!server.isClosed) {
                        val sock = server.accept()
                        Thread {
                            sock.use { handle(it) }
                        }.apply { isDaemon = true; start() }
                    }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }
        }

        private fun handle(sock: java.net.Socket) {
            // 字节级读取：toggle body 含中文（Content-Length 按字节计），
            // 经 Reader 按字符读会多等字节而饿死响应；此处只读字节。
            val input = sock.getInputStream()
            fun readLine(): String? {
                val buf = StringBuilder()
                var prev = -1
                while (true) {
                    val b = input.read()
                    if (b == -1) return if (buf.isEmpty()) null else buf.toString()
                    if (prev == '\r'.code && b == '\n'.code) {
                        buf.deleteCharAt(buf.length - 1)
                        return buf.toString()
                    }
                    buf.append(b.toChar())
                    prev = b
                }
            }
            var contentLength = 0
            var auth: String? = null
            var line = readLine()
            while (!line.isNullOrEmpty()) {
                Regex("(?i)content-length:\\s*(\\d+)").find(line)?.let {
                    contentLength = it.groupValues[1].toInt()
                }
                Regex("(?i)authorization:\\s*(.+)").find(line)?.let {
                    auth = it.groupValues[1].trim()
                }
                line = readLine()
            }
            var remaining = contentLength
            val drain = ByteArray(1024)
            while (remaining > 0) {
                val n = input.read(drain, 0, minOf(drain.size, remaining))
                if (n <= 0) break
                remaining -= n
            }
            authHeaders.add(auth)
            val idx = hits.getAndIncrement().coerceAtMost(script.size - 1)
            val (code, body) = script[idx]
            val bytes = body.toByteArray()
            val reason = if (code == 401) "Unauthorized" else "OK"
            val out = sock.getOutputStream()
            out.write(
                ("HTTP/1.1 $code $reason\r\nContent-Type: application/json\r\n" +
                    "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
                    .toByteArray()
            )
            out.write(bytes)
            out.flush()
        }

        fun baseUrl(): String = "http://127.0.0.1:${server.localPort}/"

        fun stop() {
            try {
                server.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun toggleTrueJson(): String =
        JSONObject().put("code", 200).put("message", "已收藏").put("data", true).toString()

    private fun toggleFalseJson(): String =
        JSONObject().put("code", 200).put("message", "已取消").put("data", false).toString()

    private fun envelope401Json(): String =
        JSONObject().put("code", 401).put("message", "登录过期").toString()

    @Test
    fun http401_renewsOnceThenSucceedsWithRotatedToken(): Unit = runBlocking {
        val stub = SeqStub(listOf(401 to "", 200 to toggleTrueJson()))
        stub.start()
        try {
            var refreshCalls = 0
            val faved = VibeApi.toggleFavoriteWith(
                demoSong, "req-1", stub.baseUrl(), testClient
            ) { failed ->
                refreshCalls++
                assertEquals("old-acc", failed)
                AuthToken.token = "new-acc"
                RefreshOutcome.REFRESHED
            }
            assertTrue(faved)
            assertEquals(2, stub.hits.get())
            assertEquals(1, refreshCalls)
            assertEquals("Bearer old-acc", stub.authHeaders[0])
            assertEquals("Bearer new-acc", stub.authHeaders[1])
        } finally {
            stub.stop()
        }
    }

    @Test
    fun envelope401_renewsOnceThenSucceeds(): Unit = runBlocking {
        val stub = SeqStub(listOf(200 to envelope401Json(), 200 to toggleFalseJson()))
        stub.start()
        try {
            var refreshCalls = 0
            val faved = VibeApi.toggleFavoriteWith(
                demoSong, "req-2", stub.baseUrl(), testClient
            ) {
                refreshCalls++
                AuthToken.token = "new-acc"
                RefreshOutcome.REFRESHED
            }
            assertEquals(false, faved)
            assertEquals(2, stub.hits.get())
            assertEquals(1, refreshCalls)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun persistent401_retriesAtMostOnceThenRethrows(): Unit = runBlocking {
        val stub = SeqStub(listOf(401 to ""))
        stub.start()
        try {
            var refreshCalls = 0
            try {
                VibeApi.toggleFavoriteWith(
                    demoSong, "req-3", stub.baseUrl(), testClient
                ) {
                    refreshCalls++
                    AuthToken.token = "new-acc-$refreshCalls"
                    RefreshOutcome.REFRESHED
                }
                fail("expected AuthException after single retry")
            } catch (e: AuthException) {
                assertTrue((e.message ?: "").isNotBlank())
            }
            assertEquals(2, stub.hits.get())
            assertEquals(1, refreshCalls)
        } finally {
            stub.stop()
        }
    }

    @Test
    fun http401_refreshNetworkFailThrowsNetworkAuthExceptionKeepsLogin(): Unit = runBlocking {
        val stub = SeqStub(listOf(401 to ""))
        stub.start()
        try {
            try {
                VibeApi.toggleFavoriteWith(
                    demoSong, "req-4", stub.baseUrl(), testClient
                ) { RefreshOutcome.NETWORK_FAIL }
                fail("expected NetworkAuthException")
            } catch (e: NetworkAuthException) {
                assertEquals("网络连接断开，登录态保留", e.message)
            }
            assertEquals(1, stub.hits.get())
        } finally {
            stub.stop()
        }
    }
}
