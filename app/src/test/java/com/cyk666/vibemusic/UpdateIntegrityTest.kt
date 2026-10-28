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
import java.io.File

/**
 * 自更新完整性回归（CODE-REVIEW-2026-10-28 UPD-2 / UPD-3 / UPD-4）。
 *
 * 这三条的共同前提：**release 元数据是外部输入**，签名门只证明"同一个开发者"，
 * 不证明"同一个版本 / 同一份字节 / 同一个来源"。所以版本号、sha256、host
 * 三道门都必须各自成立，不能互相替代。
 */
@RunWith(RobolectricTestRunner::class)
class UpdateIntegrityTest {

    private fun release(tag: String, apkUrl: String = "https://gitee.com/a.apk") =
        GithubRelease(tag = tag, name = tag, body = "", apkUrl = apkUrl)

    // ---- UPD-3：两源取版本大者，相等优先 Gitee ----

    @Test
    fun pickBest_githubNewerWins() {
        val picked = pickBestRelease(release("v2.0.18-ai"), release("v2.0.19-ai"))
        assertEquals(UpdateSource.GITHUB, picked?.first)
        assertEquals("v2.0.19-ai", picked?.second?.tag)
    }

    @Test
    fun pickBest_giteeNewerWins() {
        val picked = pickBestRelease(release("v2.0.19-ai"), release("v2.0.18-ai"))
        assertEquals(UpdateSource.GITEE, picked?.first)
        assertEquals("v2.0.19-ai", picked?.second?.tag)
    }

    @Test
    fun pickBest_equalTagsPreferGitee() {
        val picked = pickBestRelease(release("v2.0.19-ai"), release("v2.0.19-ai"))
        assertEquals(UpdateSource.GITEE, picked?.first)
    }

    @Test
    fun pickBest_singleSourceStillWorks() {
        assertEquals(UpdateSource.GITHUB, pickBestRelease(null, release("v2.0.19-ai"))?.first)
        assertEquals(UpdateSource.GITEE, pickBestRelease(release("v2.0.19-ai"), null)?.first)
        assertNull(pickBestRelease(null, null))
    }

    /** 端到端合并：Gitee 滞后一个版本时不能再"首个成功者胜"。 */
    @Test
    fun fetchBestOf_takesNewerAcrossSources() = runBlocking {
        val rel = fetchBestOf(
            gitee = { release("v2.0.18-ai") },
            github = { release("v2.0.19-ai") }
        )
        assertEquals("v2.0.19-ai", rel.tag)
    }

    @Test
    fun fetchBestOf_oneSourceFailingStillSucceeds() = runBlocking {
        val rel = fetchBestOf(
            gitee = { throw RuntimeException("gitee down") },
            github = { release("v2.0.19-ai") }
        )
        assertEquals("v2.0.19-ai", rel.tag)
    }

    // ---- UPD-4：下载 host 白名单 ----

    @Test
    fun host_allowlistAcceptsOfficialHosts() {
        assertTrue(isAllowedUpdateHost("https://gitee.com/a/b/releases/download/v1/app.apk"))
        assertTrue(isAllowedUpdateHost("https://github.com/c/vibemusic-native/releases/download/v1/app.apk"))
        assertTrue(isAllowedUpdateHost("https://objects.githubusercontent.com/abc?sig=1"))
        // 官方 CDN/接口常在子域上
        assertTrue(isAllowedUpdateHost("https://api.github.com/x"))
    }

    @Test
    fun host_allowlistRejectsLookalikesAndForeignHosts() {
        assertFalse(isAllowedUpdateHost("https://github.com.evil.com/app.apk"))
        assertFalse(isAllowedUpdateHost("https://notgitee.com/app.apk"))
        assertFalse(isAllowedUpdateHost("https://evil.com/app.apk"))
        assertFalse(isAllowedUpdateHost("http://127.0.0.1:8080/app.apk"))
        assertFalse(isAllowedUpdateHost(""))
        assertFalse(isAllowedUpdateHost("not a url"))
    }

    @Test
    fun parse_rejectsForeignAssetHostAndSaysWhy() {
        val json = """{"tag_name":"v2.0.19-ai","body":"","assets":[
            {"name":"app.apk","browser_download_url":"https://evil.com/app.apk"}
        ]}"""
        try {
            parseLatestRelease(json)
            org.junit.Assert.fail("foreign host must not be accepted")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").contains("no .apk asset"))
            // 必须能看出是 host 被拒，否则线上只会看到"没有 APK"
            assertTrue((e.message ?: "").contains("host not allowed: evil.com"))
        }
    }

    @Test
    fun parse_skipsForeignAssetAndTakesNextAllowedOne() {
        val json = """{"tag_name":"v2.0.19-ai","body":"","assets":[
            {"name":"app.apk","browser_download_url":"https://evil.com/app.apk"},
            {"name":"app.apk","browser_download_url":"https://gitee.com/ok/app.apk"}
        ]}"""
        assertEquals("https://gitee.com/ok/app.apk", parseLatestRelease(json).apkUrl)
    }

    // ---- UPD-4：body 里的 sha256 ----

    @Test
    fun sha256_parsedFromCiReleaseNotes() {
        val body = "VibeMusic 原生版 v2.0.19-ai（CI 自动构建签名）\n\n" +
            "APK sha256: `2CF24DBA5FB0A30E26E83B2AC5B9E29E1B161E5C1FA7425E73043362938B9824`"
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            parseSha256FromBody(body)
        )
    }

    @Test
    fun sha256_absentInOldReleases() {
        assertNull(parseSha256FromBody("VibeMusic 原生版 v2.0.16-ai"))
        assertNull(parseSha256FromBody(""))
    }

    @Test
    fun parse_attachesSha256ToRelease() {
        val json = """{"tag_name":"v2.0.19-ai","body":"APK sha256: `abc`","assets":[
            {"name":"app.apk","browser_download_url":"https://gitee.com/app.apk"}
        ]}"""
        // 32 hex 不是合法 sha256 → 不该被当成校验值
        assertNull(parseLatestRelease(json).sha256)
    }

    @Test
    fun verifyApkSha256_matchesAndMismatches() {
        val dir = File(RuntimeEnvironment.getApplication().cacheDir, "sha-test").apply { mkdirs() }
        val f = File(dir, "hello.apk")
        f.writeText("hello")
        val good = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertTrue(verifyApkSha256(f, good))
        assertTrue(verifyApkSha256(f, good.uppercase()))
        // release 没写 sha256 → 跳过（可选字段，不能变成硬要求）
        assertTrue(verifyApkSha256(f, null))
        assertTrue(verifyApkSha256(f, ""))
        assertFalse(verifyApkSha256(f, "0".repeat(64)))
    }

    @Test
    fun verifyApkSha256_missingFileFailsClosed() {
        val missing = File(RuntimeEnvironment.getApplication().cacheDir, "nope-${System.nanoTime()}.apk")
        assertFalse(verifyApkSha256(missing, "0".repeat(64)))
    }

    // ---- UPD-2：versionCode 安装门 ----

    @Test
    fun installGate_rejectsDowngradeOnly() {
        assertTrue(isInstallableUpdate(97L, 97L))
        assertTrue(isInstallableUpdate(98L, 97L))
        assertFalse(isInstallableUpdate(96L, 97L))
        // 读不到当前 versionCode 时不拦（交给签名门兜底）
        assertTrue(isInstallableUpdate(1L, -1L))
        assertTrue(isInstallableUpdate(1L, 0L))
    }

    @Test
    fun readApkVersionCode_unparsableFileIsNull() {
        val bogus = File(RuntimeEnvironment.getApplication().cacheDir, "not-an-apk.apk")
        bogus.writeText("definitely not a zip")
        assertNull(readApkVersionCode(RuntimeEnvironment.getApplication(), bogus))
    }

    // ---- UPD-2：回退源必须带同 tag ----

    @Test
    fun fallbackSource_carriesSameTag() {
        val fromGitee = otherUpdateSourceUrl(
            "https://gitee.com/green-leavesQAQ/vibemusic-native/releases/download/v2.0.19-ai/app.apk",
            "v2.0.19-ai"
        )
        assertEquals(UPDATE_GITHUB_TAG_URL_BASE + "v2.0.19-ai", fromGitee)

        val fromGithub = otherUpdateSourceUrl(
            "https://github.com/chunren1/vibemusic-native/releases/download/v2.0.19-ai/app.apk",
            "v2.0.19-ai"
        )
        assertEquals(UPDATE_GITEE_TAG_URL_BASE + "v2.0.19-ai", fromGithub)
    }

    @Test
    fun fallbackSource_neverPointsAtLatest() {
        // 回归：`.../latest` 是正式版，测试通道回退会装错版本
        val url = otherUpdateSourceUrl("https://gitee.com/x/app.apk", "v2.0.19-ai")
        assertFalse(url.endsWith("/latest"))
        assertTrue(url.contains("/tags/v2.0.19-ai"))
    }
}
