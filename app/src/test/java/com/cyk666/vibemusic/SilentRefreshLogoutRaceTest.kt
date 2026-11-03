package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 2026-11-03 审计 P1 回归：静默续期卡住期间用户退出登录，迟到的 200 不许把会话写回来。
 *
 * 旧实现只在 `refreshMutex` 入口判一次 `sessionEnded`，refresh 的往返（connect 15s +
 * read 30s）里发生的登出完全看不见 —— 新 token 照样写内存 + 写盘，下次冷启动 `/me`
 * 成功 = 退出登录被静默撤销。
 *
 * 用 JDK ServerSocket 做「先扣住请求、再由测试放行」的闸门 stub（零新依赖）。
 */
@RunWith(RobolectricTestRunner::class)
class SilentRefreshLogoutRaceTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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
        // 别把 sessionEnded 留给同进程里的其它测试类。
        AuthToken.sessionEnded = false
        AuthToken.bumpSessionEpoch()
    }

    /** 请求到达即通知测试，响应体等测试放行后才写。 */
    private class GatedStub(private val body: String) {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val arrived = CountDownLatch(1)
        private val release = CountDownLatch(1)

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
                                // 请求已收全：告诉测试"refresh 已在飞行中"，然后等放行。
                                arrived.countDown()
                                release.await(15, TimeUnit.SECONDS)
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

        fun awaitRequest(): Boolean = arrived.await(15, TimeUnit.SECONDS)

        fun release() = release.countDown()

        fun endpoint(): String = "http://127.0.0.1:${server.localPort}/api/auth/refresh"

        fun stop() {
            release.countDown()
            try {
                server.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun tokenOnlyBody(token: String): String =
        JSONObject().put("code", 200)
            .put("data", JSONObject().put("token", token)).toString()

    @Test
    fun logoutWhileRefreshInFlightIsNotUndone(): Unit = runBlocking {
        val stub = GatedStub(tokenOnlyBody("late-acc"))
        stub.start()
        try {
            val job = async(Dispatchers.IO) {
                VibeApi.trySilentRefreshWith("old-acc", stub.endpoint(), client)
            }
            assertTrue("refresh 请求没到达 stub", stub.awaitRequest())

            // 用户在 refresh 飞行中点「退出登录」：与 doLogout/endLocalSession 同序
            // —— 先同步关闸，再清凭据。
            VibeApi.endSession()
            AuthStore.clear(context())
            stub.release()

            assertEquals(RefreshOutcome.NETWORK_FAIL, job.await())
            assertEquals("", AuthToken.token)
            assertEquals("", AuthToken.refreshToken)
            assertTrue(AuthToken.sessionEnded)
            // 磁盘也不能被写回：下次冷启动 /me 不该成功。
            val snap = AuthStore.load(context())
            assertEquals("", snap.token)
            assertEquals("", snap.refreshToken)
        } finally {
            stub.stop()
        }
    }

    /** 对照组：没登出时同一条链路照常续期（闸门不能把正常续期也关掉）。 */
    @Test
    fun refreshWithoutLogoutStillSucceeds(): Unit = runBlocking {
        val stub = GatedStub(tokenOnlyBody("fresh-acc"))
        stub.start()
        try {
            val job = async(Dispatchers.IO) {
                VibeApi.trySilentRefreshWith("old-acc", stub.endpoint(), client)
            }
            assertTrue(stub.awaitRequest())
            stub.release()

            assertEquals(RefreshOutcome.REFRESHED, job.await())
            assertEquals("fresh-acc", AuthToken.token)
            assertFalse(AuthToken.sessionEnded)
            assertEquals("fresh-acc", AuthStore.load(context()).token)
        } finally {
            stub.stop()
        }
    }
}
