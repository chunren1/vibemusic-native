package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
}
