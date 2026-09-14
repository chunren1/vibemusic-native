package com.cyk666.vibemusic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 双通道 release 解析测试：正式通道走 .../latest（天然排除 prerelease），
 * 测试通道走 .../releases?per_page=N 列表（含 prerelease，取首个带 .apk 的条目）。
 */
@RunWith(RobolectricTestRunner::class)
class UpdateChannelTest {

    private fun listEntry(tag: String, apkUrl: String?, prerelease: Boolean): String {
        val assets = if (apkUrl == null) {
            """[{"name":"notes.txt","browser_download_url":"https://example.invalid/notes.txt"}]"""
        } else {
            """[{"name":"notes.txt","browser_download_url":"https://example.invalid/notes.txt"},{"name":"app.apk","browser_download_url":"$apkUrl"}]"""
        }
        return """{"tag_name":"$tag","name":"$tag","body":"- $tag","prerelease":$prerelease,"assets":$assets}"""
    }

    @Test
    fun list_picksFirstApkIncludingPrerelease() {
        val json = "[" + listEntry("v1.0.41-ai-beta.1", "https://example.invalid/beta.apk", true) + "," +
            listEntry("v1.0.40-ai", "https://example.invalid/stable.apk", false) + "]"
        val r = parseReleaseList(json)
            ?: throw AssertionError("expected a release")
        assertEquals("v1.0.41-ai-beta.1", r.tag)
        assertEquals("https://example.invalid/beta.apk", r.apkUrl)
        assertTrue(r.prerelease)
    }

    @Test
    fun list_skipsEntriesWithoutApk() {
        val json = "[" + listEntry("v1.0.42-ai-beta.1", null, true) + "," +
            listEntry("v1.0.40-ai", "https://example.invalid/stable.apk", false) + "]"
        val r = parseReleaseList(json)
            ?: throw AssertionError("expected fallback to stable")
        assertEquals("v1.0.40-ai", r.tag)
        assertFalse(r.prerelease)
    }

    @Test
    fun list_emptyOrNoApkReturnsNull() {
        assertNull(parseReleaseList("[]"))
        assertNull(parseReleaseList("[" + listEntry("v1.0.42-ai-beta.1", null, true) + "]"))
    }

    @Test
    fun list_malformedThrows() {
        try {
            parseReleaseList("not-json")
            fail("expected RuntimeException")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").contains("malformed"))
        }
    }

    @Test
    fun latest_capturesPrereleaseFlag() {
        val beta = """{"tag_name":"v1.0.41-ai-beta.1","name":"x","body":"","prerelease":true,"assets":[{"name":"app.apk","browser_download_url":"https://example.invalid/b.apk"}]}"""
        assertTrue(parseLatestRelease(beta).prerelease)
        val stable = """{"tag_name":"v1.0.40-ai","name":"x","body":"","assets":[{"name":"app.apk","browser_download_url":"https://example.invalid/s.apk"}]}"""
        assertFalse(parseLatestRelease(stable).prerelease)
    }

    @Test
    fun channel_parseDefaultsToStable() {
        assertEquals(UpdateChannel.STABLE, parseUpdateChannel(null))
        assertEquals(UpdateChannel.STABLE, parseUpdateChannel(""))
        assertEquals(UpdateChannel.STABLE, parseUpdateChannel("  "))
        assertEquals(UpdateChannel.STABLE, parseUpdateChannel("STABLE"))
        assertEquals(UpdateChannel.STABLE, parseUpdateChannel("garbage"))
    }

    @Test
    fun channel_parseBetaCaseInsensitive() {
        assertEquals(UpdateChannel.BETA, parseUpdateChannel("BETA"))
        assertEquals(UpdateChannel.BETA, parseUpdateChannel("beta"))
    }

    @Test
    fun channel_persistRoundTrip() {
        val ctx = RuntimeEnvironment.getApplication()
        runBlocking {
            QueueStore.saveUpdateChannel(ctx, UpdateChannel.BETA)
            assertEquals(UpdateChannel.BETA, QueueStore.loadUpdateChannel(ctx))
            QueueStore.saveUpdateChannel(ctx, UpdateChannel.STABLE)
            assertEquals(UpdateChannel.STABLE, QueueStore.loadUpdateChannel(ctx))
        }
    }

    @Test
    fun channel_stableFetchKeepsLatestSource() {
        val stable = GithubRelease("v1.0.40-ai", "", "", "https://example.invalid/s.apk")
        val beta = GithubRelease("v1.0.41-ai-beta.1", "", "", "https://example.invalid/b.apk", true)
        val got = runBlocking {
            fetchLatestForChannel(
                UpdateChannel.STABLE,
                stableFetch = { stable },
                betaFetch = { beta }
            )
        }
        assertEquals("v1.0.40-ai", got.tag)
        assertFalse(got.prerelease)
    }

    @Test
    fun channel_betaFetchSeesPrerelease() {
        val stable = GithubRelease("v1.0.40-ai", "", "", "https://example.invalid/s.apk")
        val beta = GithubRelease("v1.0.41-ai-beta.1", "", "", "https://example.invalid/b.apk", true)
        val got = runBlocking {
            fetchLatestForChannel(
                UpdateChannel.BETA,
                stableFetch = { stable },
                betaFetch = { beta }
            )
        }
        assertEquals("v1.0.41-ai-beta.1", got.tag)
        assertTrue(got.prerelease)
    }

    @Test
    fun channel_labelsMatchSettingsCopy() {
        assertEquals("正式版", updateChannelLabel(UpdateChannel.STABLE))
        assertEquals("测试版", updateChannelLabel(UpdateChannel.BETA))
    }

    @Test
    fun channel_betaDialogNotesPrerelease() {
        val note = betaReleaseNote(true)
            ?: throw AssertionError("expected a beta note")
        assertTrue(note.contains("测试版"))
        assertNull(betaReleaseNote(false))
    }
}
