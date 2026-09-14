package com.cyk666.vibemusic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

@RunWith(RobolectricTestRunner::class)
class UpdateCheckTest {

    // ---- isNewerVersion table ----

    @Test
    fun newer_tagWithVPrefixAndSuffix() {
        assertTrue(isNewerVersion("1.0.16", "v1.0.17-ai"))
    }

    @Test
    fun newer_currentWithSuffix() {
        assertTrue(isNewerVersion("1.0.16-ai", "v1.0.17-ai"))
    }

    @Test
    fun equal_sameStringNotNewer() {
        assertFalse(isNewerVersion("1.0.16-ai", "1.0.16-ai"))
    }

    @Test
    fun equal_ignoresVPrefixAndSuffix() {
        assertFalse(isNewerVersion("v1.0.16", "1.0.16-ai"))
    }

    @Test
    fun newerCurrent_notNewer() {
        assertFalse(isNewerVersion("1.0.17-ai", "v1.0.16"))
    }

    @Test
    fun suffixOnlyDiff_notNewer() {
        assertFalse(isNewerVersion("1.0.17", "1.0.17-ai"))
        assertFalse(isNewerVersion("1.0.17-ai", "1.0.17"))
    }

    @Test
    fun missingParts_treatedAsZero() {
        assertTrue(isNewerVersion("1.0", "1.0.1"))
        assertFalse(isNewerVersion("1.0.1", "1.0"))
        assertFalse(isNewerVersion("1.0", "1.0.0-ai"))
    }

    @Test
    fun upperVPrefix() {
        assertTrue(isNewerVersion("1.0.16-ai", "V1.0.17"))
    }

    @Test
    fun differingLengths_majorBump() {
        assertTrue(isNewerVersion("1.9.9-ai", "2.0"))
        assertFalse(isNewerVersion("2.0-ai", "1.99.99"))
    }

    // ---- 同 numeric core 的 beta 后缀比较 ----

    @Test
    fun beta_sameCoreBeta1ToBeta2IsNewer() {
        // 同 core：beta.1 -> beta.2 视为有更新（线上"已是最新版本" bug 复现）
        assertTrue(isNewerVersion("1.0.42-ai-beta.1", "v1.0.42-ai-beta.2"))
    }

    @Test
    fun beta_sameCoreBeta2ToBeta1NotNewer() {
        // 同 core：高 beta -> 低 beta 不是更新
        assertFalse(isNewerVersion("1.0.42-ai-beta.2", "v1.0.42-ai-beta.1"))
    }

    @Test
    fun beta_sameCoreSameBetaNotNewer() {
        // 同 core 同 beta：不是更新（避免重复提示）
        assertFalse(isNewerVersion("1.0.42-ai-beta.1", "v1.0.42-ai-beta.1"))
    }

    @Test
    fun beta_sameCoreStableOverBetaIsNewer() {
        // 同 core：正式版覆盖 beta 视为更新
        assertTrue(isNewerVersion("1.0.42-ai-beta.2", "v1.0.42-ai"))
    }

    @Test
    fun beta_sameCoreBetaOverStableNotNewer() {
        // 同 core：beta 覆盖正式版不是更新
        assertFalse(isNewerVersion("1.0.42-ai", "v1.0.42-ai-beta.2"))
    }

    @Test
    fun beta_numericCoreDiffStillWins() {
        // numeric core 不一致时仍按数字比较，beta 后缀不翻转结果
        assertTrue(isNewerVersion("1.0.41-ai-beta.9", "v1.0.42-ai-beta.1"))
        assertFalse(isNewerVersion("1.0.42-ai-beta.2", "v1.0.41"))
    }

    // ---- parseLatestRelease ----

    private fun releaseJson(assets: String?): String {
        val assetsPart = if (assets == null) "" else ""","assets":[$assets]"""
        return """{"tag_name":"v1.0.17-ai","name":"1.0.17-ai","body":"- fix x\n- feat y"$assetsPart}"""
    }

    @Test
    fun parseRelease_fullPicksApk() {
        val assets = """{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},""" +
            """{"name":"app.apk","browser_download_url":"https://x/app.apk"}"""
        val r = parseLatestRelease(releaseJson(assets))
        assertEquals("v1.0.17-ai", r.tag)
        assertEquals("1.0.17-ai", r.name)
        assertTrue(r.body.contains("fix x"))
        assertEquals("https://x/app.apk", r.apkUrl)
    }

    @Test
    fun parseRelease_missingAssetsThrows() {
        try {
            parseLatestRelease(releaseJson(null))
            fail("expected RuntimeException for missing assets")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").contains("no assets"))
        }
    }

    @Test
    fun parseRelease_noApkAssetThrows() {
        val assets = """{"name":"notes.txt","browser_download_url":"https://x/notes.txt"}"""
        try {
            parseLatestRelease(releaseJson(assets))
            fail("expected RuntimeException for no apk asset")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").contains("no .apk asset"))
        }
    }

    @Test
    fun parseRelease_malformedThrows() {
        try {
            parseLatestRelease("{not json")
            fail("expected RuntimeException for malformed JSON")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").contains("malformed"))
        }
    }

    @Test
    fun parseRelease_blankTagThrows() {
        try {
            parseLatestRelease("""{"tag_name":"","assets":[]}""")
            fail("expected RuntimeException for blank tag")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").contains("tag_name"))
        }
    }

    // ---- shouldCheckUpdate ----

    @Test
    fun throttle_neverCheckedAllows() {
        assertTrue(shouldCheckUpdate(1_000_000L, 0L))
    }

    @Test
    fun throttle_recentBlocks() {
        val now = 1_750_000_000_000L
        assertFalse(shouldCheckUpdate(now, now - 3_600_000L))
    }

    @Test
    fun throttle_boundaryAllows() {
        val now = 1_750_000_000_000L
        assertTrue(shouldCheckUpdate(now, now - UPDATE_CHECK_INTERVAL_MS))
        assertTrue(shouldCheckUpdate(now, now - UPDATE_CHECK_INTERVAL_MS - 1L))
    }

    @Test
    fun throttle_intervalIs24h() {
        assertEquals(24 * 60 * 60 * 1000L, UPDATE_CHECK_INTERVAL_MS)
    }

    // ---- part-file staging ----

    @Test
    fun partFile_sameDirWithPartSuffix() {
        val dest = java.io.File("/tmp/updates", "vibemusic-v1.0.41-ai.apk")
        val part = partFileFor(dest)
        assertEquals("vibemusic-v1.0.41-ai.apk.part", part.name)
        assertEquals(dest.parentFile, part.parentFile)
    }

    // ---- size-gate reuse decision ----

    private fun tmpApk(name: String, bytes: ByteArray?): java.io.File {
        val dir = java.io.File(RuntimeEnvironment.getApplication().cacheDir, "upd-test")
        dir.mkdirs()
        val f = java.io.File(dir, name)
        try {
            f.delete()
        } catch (_: Exception) {
        }
        if (bytes != null) f.writeBytes(bytes)
        return f
    }

    @Test
    fun sizeGate_missingFileNeverReused() {
        val f = java.io.File(
            RuntimeEnvironment.getApplication().cacheDir,
            "upd-test/no-such.apk"
        )
        assertFalse(isDownloadComplete(f, 100L))
        assertFalse(isDownloadComplete(f, -1L))
    }

    @Test
    fun sizeGate_emptyFileNeverReused() {
        val f = tmpApk("empty.apk", ByteArray(0))
        assertFalse(isDownloadComplete(f, 100L))
        assertFalse(isDownloadComplete(f, -1L))
        f.delete()
    }

    @Test
    fun sizeGate_truncatedHalfPackageRejected() {
        val full = ByteArray(4096) { it.toByte() }
        val f = tmpApk("half.apk", full.copyOf(2048))
        assertFalse(isDownloadComplete(f, 4096L))
        f.delete()
    }

    @Test
    fun sizeGate_exactSizeAccepted() {
        val full = ByteArray(4096) { it.toByte() }
        val f = tmpApk("full.apk", full)
        assertTrue(isDownloadComplete(f, 4096L))
        f.delete()
    }

    @Test
    fun sizeGate_unknownSizeFallsBackToNonEmpty() {
        val f = tmpApk("legacy.apk", byteArrayOf(1, 2, 3))
        assertTrue(isDownloadComplete(f, -1L))
        assertTrue(isDownloadComplete(f, 0L))
        f.delete()
    }

    // ---- signature gate (fail-closed negatives; positive needs a real apk) ----

    @Test
    fun signature_missingFileIsFalse() {
        val ctx = RuntimeEnvironment.getApplication()
        val f = java.io.File(ctx.cacheDir, "upd-test/no-such.apk")
        assertFalse(isSameSignature(ctx, f))
    }

    @Test
    fun signature_garbageFileIsFalse() {
        val ctx = RuntimeEnvironment.getApplication()
        val f = tmpApk("garbage.apk", "not an apk".toByteArray())
        assertFalse(isSameSignature(ctx, f))
        f.delete()
    }

    // ---- streaming download over loopback (raw ServerSocket stub, no new deps) ----

    private class StubApkServer(val bytes: ByteArray, val code: Int = 200) {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort
        @Volatile
        private var running = true
        private val thread = kotlin.concurrent.thread(isDaemon = true, name = "stub-apk") {
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
                    if (code != 200) {
                        out.write("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    } else {
                        out.write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\n" +
                                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()
                        )
                        out.write(bytes)
                    }
                    out.flush()
                }
            } catch (_: Exception) {
            }
        }

        fun url(): String = "http://127.0.0.1:$port/app.apk"

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

    private fun serveApp(bytes: ByteArray, code: Int = 200): StubApkServer =
        StubApkServer(bytes, code)

    private fun loopbackUrl(server: StubApkServer): String = server.url()

    @Test
    fun download_streamsToAtomicLanding_noPartLeft() {
        val payload = ByteArray(64 * 1024) { (it * 31).toByte() }
        val server = serveApp(payload)
        try {
            val dir = java.io.File(RuntimeEnvironment.getApplication().cacheDir, "upd-test/dl1")
            val dest = java.io.File(dir, "v.apk")
            val landed: java.io.File = runBlocking { downloadApk(loopbackUrl(server), dest) }
            assertArrayEquals(payload, landed.readBytes())
            assertFalse(partFileFor(dest).exists())
        } finally {
            server.stop()
        }
    }

    @Test
    fun download_sizeMismatchThrowsAndLeavesNothing() {
        val payload = ByteArray(1024) { it.toByte() }
        val server = serveApp(payload)
        try {
            val dir = java.io.File(RuntimeEnvironment.getApplication().cacheDir, "upd-test/dl2")
            val dest = java.io.File(dir, "v.apk")
            try {
                runBlocking { downloadApk(loopbackUrl(server), dest, expectedBytes = 2048L) }
                fail("expected incomplete-file RuntimeException")
            } catch (e: RuntimeException) {
                assertTrue((e.message ?: "").contains("incomplete"))
            }
            assertFalse(dest.exists())
            assertFalse(partFileFor(dest).exists())
        } finally {
            server.stop()
        }
    }

    @Test
    fun download_primary500FallsBackToSecondSource() {
        val payload = ByteArray(2048) { it.toByte() }
        val dead = serveApp(payload, code = 500)
        val good = serveApp(payload)
        try {
            val dir = java.io.File(RuntimeEnvironment.getApplication().cacheDir, "upd-test/dl3")
            val dest = java.io.File(dir, "v.apk")
            val landed: java.io.File = runBlocking {
                downloadApk(loopbackUrl(dead), dest, fallbackApkUrl = loopbackUrl(good))
            }
            assertArrayEquals(payload, landed.readBytes())
            assertFalse(partFileFor(dest).exists())
        } finally {
            dead.stop()
            good.stop()
        }
    }

    @Test
    fun download_allSourcesDownThrows() {
        val dead = serveApp(ByteArray(0), code = 500)
        try {
            val dir = java.io.File(RuntimeEnvironment.getApplication().cacheDir, "upd-test/dl4")
            val dest = java.io.File(dir, "v.apk")
            try {
                runBlocking { downloadApk(loopbackUrl(dead), dest) }
                fail("expected download-failed RuntimeException")
            } catch (e: RuntimeException) {
                assertTrue((e.message ?: "").contains("Download failed"))
            }
            assertFalse(dest.exists())
        } finally {
            dead.stop()
        }
    }
}
