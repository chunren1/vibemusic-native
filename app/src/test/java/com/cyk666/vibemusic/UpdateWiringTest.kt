package com.cyk666.vibemusic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Top-5 / Top-6 / Top-10 接线回归：手动检查分支文案、签名门文案、
 * 对源回退 URL 选择、复用门（走真实 isDownloadComplete）。
 */
@RunWith(RobolectricTestRunner::class)
class UpdateWiringTest {

    @Test
    fun manualCheck_updateAvailableHasNoToast() {
        assertNull(manualUpdateCheckMessage(UpdateDecision.UPDATE_AVAILABLE))
    }

    @Test
    fun manualCheck_upToDateKeepsHistoricCopy() {
        assertEquals("已是最新版本", manualUpdateCheckMessage(UpdateDecision.UP_TO_DATE))
    }

    @Test
    fun manualCheck_failedAlwaysToasts() {
        assertEquals(
            "检查更新失败，请稍后重试",
            manualUpdateCheckMessage(UpdateDecision.CHECK_FAILED)
        )
    }

    @Test
    fun signatureMismatch_copyNamesReinstall() {
        assertTrue(UPDATE_SIGNATURE_MISMATCH_MESSAGE.contains("卸载重装"))
        assertTrue(UPDATE_SIGNATURE_MISMATCH_MESSAGE.contains("签名"))
    }

    /**
     * UPD-2 回归：回退源必须是**同 tag** 端点。原来回退到 `.../latest`（正式版，
     * 排除 prerelease），测试通道下载失败时会把正式版 APK 装给测试版用户。
     */
    @Test
    fun otherSource_giteePrimaryFallsBackToGithubSameTag() {
        assertEquals(
            UPDATE_GITHUB_TAG_URL_BASE + "v2.0.19-ai",
            otherUpdateSourceUrl(
                "https://gitee.com/green-leavesQAQ/vibemusic-native/releases/download/v2.0.19-ai/app.apk",
                "v2.0.19-ai"
            )
        )
    }

    @Test
    fun otherSource_githubPrimaryFallsBackToGiteeSameTag() {
        assertEquals(
            UPDATE_GITEE_TAG_URL_BASE + "v2.0.19-ai",
            otherUpdateSourceUrl(
                "https://github.com/chunren1/vibemusic-native/releases/download/v2.0.19-ai/app.apk",
                "v2.0.19-ai"
            )
        )
    }

    @Test
    fun reuseGate_nonEmptyLandedApkReused() {
        val dir = java.io.File(RuntimeEnvironment.getApplication().cacheDir, "wiring-test")
        dir.mkdirs()
        val apk = java.io.File(dir, "v.apk")
        apk.writeBytes(ByteArray(16) { it.toByte() })
        // release 元数据无大小字段时传 -1L：沿用非空门（经真实 helper）。
        assertTrue(isDownloadComplete(apk, -1L))
        apk.delete()
    }

    @Test
    fun reuseGate_missingApkRedownloads() {
        val apk = java.io.File(
            RuntimeEnvironment.getApplication().cacheDir,
            "wiring-test/no-such.apk"
        )
        assertFalse(isDownloadComplete(apk, -1L))
    }

    // ---- fetchFallbackApkUrl over loopback (raw ServerSocket stub, no new deps) ----

    private class StubReleaseServer(val body: String, val code: Int = 200) {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort
        @Volatile
        private var running = true
        private val thread = kotlin.concurrent.thread(isDaemon = true, name = "stub-release") {
            while (running) {
                try {
                    handle(server.accept())
                } catch (_: Exception) {
                }
            }
        }

        private fun handle(s: Socket) {
            try {
                s.use { sock ->
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                    try {
                        var line: String?
                        do {
                            line = reader.readLine()
                        } while (line != null && line.isNotEmpty())
                    } catch (_: Exception) {
                    }
                    val out = sock.getOutputStream()
                    val payload = body.toByteArray()
                    if (code != 200) {
                        out.write("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    } else {
                        out.write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                "Content-Length: ${payload.size}\r\nConnection: close\r\n\r\n").toByteArray()
                        )
                        out.write(payload)
                    }
                    out.flush()
                }
            } catch (_: Exception) {
            }
        }

        fun url(): String = "http://127.0.0.1:$port/releases/latest"

        fun stop() {
            running = false
            try {
                server.close()
            } catch (_: Exception) {
            }
            try {
                thread.join(1000)
            } catch (_: Exception) {
            }
        }
    }

    private fun releaseJson(apkUrl: String): String =
        """{"tag_name":"v1.0.41-ai","name":"v1.0.41-ai","body":"- fix","assets":[{"name":"app.apk","browser_download_url":"$apkUrl"}]}"""

    @Test
    fun fallback_otherSourceApkResolved() {
        // apk URL 用白名单内的 host（UPD-4 之后回环地址会被拒）；真正被请求的
        // 只有 server.url() 这个检查端点。
        val server = StubReleaseServer(releaseJson("https://gitee.com/other/app.apk"))
        try {
            val apk = runBlocking { fetchFallbackApkUrl(server.url()) }
            assertEquals("https://gitee.com/other/app.apk", apk)
        } finally {
            server.stop()
        }
    }

    @Test
    fun fallback_failedCheckYieldsNull() {
        val server = StubReleaseServer("", code = 500)
        try {
            val apk = runBlocking { fetchFallbackApkUrl(server.url()) }
            assertNull(apk)
        } finally {
            server.stop()
        }
    }

    @Test
    fun fallback_garbageCheckYieldsNull() {
        val server = StubReleaseServer("{not json")
        try {
            val apk = runBlocking { fetchFallbackApkUrl(server.url()) }
            assertNull(apk)
        } finally {
            server.stop()
        }
    }
}
