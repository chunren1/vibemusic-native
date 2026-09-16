package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 2026-09-17 cover incident: B站/酷狗 covers arrive as relative
 * /api/image-proxy paths and the backend's String.valueOf(null) legacy emits
 * the literal "null" — both used to slip past a mere isNotBlank() check and
 * reach Coil raw. These pin the shared normalization contract.
 */
@RunWith(RobolectricTestRunner::class)
class CoverUrlTest {

    private val base = VibeApi.BASE_URL.trimEnd('/')

    // ---- absImgUrl: normalization + base join ----

    @Test
    fun abs_blankAndNullLiteral_becomeEmpty() {
        assertEquals("", absImgUrl(""))
        assertEquals("", absImgUrl("   "))
        assertEquals("", absImgUrl("null"))
        assertEquals("", absImgUrl(" null "))
    }

    @Test
    fun abs_absoluteUrls_passThrough() {
        assertEquals("https://cdn/x.jpg", absImgUrl("https://cdn/x.jpg"))
        assertEquals("http://cdn/x.jpg", absImgUrl("http://cdn/x.jpg"))
    }

    @Test
    fun abs_relativePath_getsBaseJoined() {
        assertEquals("$base/api/image-proxy?url=abc", absImgUrl("/api/image-proxy?url=abc"))
        assertEquals("$base/api/image-proxy", absImgUrl("api/image-proxy"))
    }

    @Test
    fun abs_isIdempotent() {
        val once = absImgUrl("/api/image-proxy?url=x")
        assertEquals(once, absImgUrl(once))
    }

    // ---- coverModel ----

    @Test
    fun coverModel_unloadable_becomeNull() {
        assertNull(coverModel(""))
        assertNull(coverModel("null"))
        assertNull(coverModel("  "))
    }

    @Test
    fun coverModel_relativeProxy_resolvesAbsolute() {
        assertEquals("$base/api/image-proxy?url=x", coverModel("/api/image-proxy?url=x"))
    }

    // ---- hasCoverUrl gate ----

    @Test
    fun gate_absoluteAndRelative_pass() {
        assertTrue(hasCoverUrl("https://cdn/x.jpg"))
        assertTrue(hasCoverUrl("/api/image-proxy?url=x"))
    }

    @Test
    fun gate_blankAndNullLiteral_rejected() {
        assertFalse(hasCoverUrl(""))
        assertFalse(hasCoverUrl("null"))
    }

    // ---- hasCover(song) ----

    @Test
    fun songGate_relativeCover_passes() {
        assertTrue(hasCover(Song("1", "n", "a", "al", "/api/image-proxy?url=x", 10, "bilibili")))
    }

    @Test
    fun songGate_nullLiteralCover_rejected() {
        assertFalse(hasCover(Song("1", "n", "a", "al", "null", 10, "netease")))
    }
}
