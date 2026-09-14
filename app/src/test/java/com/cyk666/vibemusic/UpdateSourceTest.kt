package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Dual-source (Gitee primary, GitHub fallback) update tests.
 *
 * Ordering rule: FIRST successful source wins. A reachable Gitee release is
 * used as-is even if GitHub hosts a newer tag (no cross-source merge — one
 * round trip, predictable behavior).
 */
@RunWith(RobolectricTestRunner::class)
class UpdateSourceTest {

    private fun giteeJson(assets: String?): String {
        val assetsPart = if (assets == null) "" else ""","assets":[$assets]"""
        return """{"tag_name":"v1.0.23-ai","name":"v1.0.23-ai","body":"- gitee mirror"$assetsPart}"""
    }

    @Test
    fun gitee_fullPicksApk() {
        val assets = """{"name":"notes.txt","browser_download_url":"https://gitee.example/notes.txt"},""" +
            """{"name":"app.apk","browser_download_url":"https://gitee.example/app.apk"}"""
        val r = parseLatestRelease(giteeJson(assets))
        assertEquals("v1.0.23-ai", r.tag)
        assertEquals("https://gitee.example/app.apk", r.apkUrl)
    }

    @Test
    fun gitee_downloadUrlFieldAccepted() {
        val assets = """{"name":"app.apk","download_url":"https://gitee.example/app.apk"}"""
        val r = parseLatestRelease(giteeJson(assets))
        assertEquals("https://gitee.example/app.apk", r.apkUrl)
    }

    @Test
    fun gitee_missingAssetsThrows() {
        try {
            parseLatestRelease(giteeJson(null))
            fail("expected RuntimeException for missing assets")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").contains("no assets"))
        }
    }

    @Test
    fun gitee_noApkAssetThrows() {
        val assets = """{"name":"notes.txt","browser_download_url":"https://gitee.example/notes.txt"}"""
        try {
            parseLatestRelease(giteeJson(assets))
            fail("expected RuntimeException for no apk asset")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").contains("no .apk asset"))
        }
    }

    @Test
    fun gitee_malformedThrows() {
        try {
            parseLatestRelease("{not json")
            fail("expected RuntimeException for malformed JSON")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").contains("malformed"))
        }
    }

    @Test
    fun source_bothOk_giteeWinsFirst() {
        assertEquals(UpdateSource.GITEE, resolveUpdateSource(giteeOk = true, githubOk = true))
    }

    @Test
    fun source_giteeFail_githubUsed() {
        assertEquals(UpdateSource.GITHUB, resolveUpdateSource(giteeOk = false, githubOk = true))
    }

    @Test
    fun source_giteeOkGithubDown_giteeUsed() {
        assertEquals(UpdateSource.GITEE, resolveUpdateSource(giteeOk = true, githubOk = false))
    }

    @Test
    fun source_bothFail_nullMapsToCheckFailed() {
        assertNull(resolveUpdateSource(giteeOk = false, githubOk = false))
        assertEquals(
            UpdateDecision.CHECK_FAILED,
            compareAndDecide("1.0.23-ai", null, fetchOk = false)
        )
    }

    @Test
    fun source_giteeUrls_pointAtMirror() {
        assertTrue(UPDATE_GITEE_LATEST_URL.contains("gitee.com"))
        assertTrue(UPDATE_GITEE_LATEST_URL.contains("green-leavesQAQ/vibemusic-native"))
        assertTrue(UPDATE_LATEST_URL.contains("api.github.com"))
    }

    // ---- download source selection (Gitee→GitHub fallback for download too) ----

    @Test
    fun downloadSources_primaryOnly() {
        assertEquals(
            listOf("https://gitee.example/app.apk"),
            resolveDownloadSources("https://gitee.example/app.apk")
        )
    }

    @Test
    fun downloadSources_fallbackAppendedInOrder() {
        assertEquals(
            listOf("https://gitee.example/app.apk", "https://github.example/app.apk"),
            resolveDownloadSources("https://gitee.example/app.apk", "https://github.example/app.apk")
        )
    }

    @Test
    fun downloadSources_blankFallbackDropped() {
        assertEquals(
            listOf("https://gitee.example/app.apk"),
            resolveDownloadSources("https://gitee.example/app.apk", "  ")
        )
    }

    @Test
    fun downloadSources_duplicateTriedOnce() {
        assertEquals(
            listOf("https://gitee.example/app.apk"),
            resolveDownloadSources("https://gitee.example/app.apk", "https://gitee.example/app.apk")
        )
    }

    @Test
    fun downloadSources_emptyPrimaryFallsThroughToFallback() {
        assertEquals(
            listOf("https://github.example/app.apk"),
            resolveDownloadSources("", "https://github.example/app.apk")
        )
    }

    @Test
    fun downloadSources_bothBlankIsEmpty() {
        assertTrue(resolveDownloadSources("", null).isEmpty())
        assertTrue(resolveDownloadSources("  ", " ").isEmpty())
    }
}
