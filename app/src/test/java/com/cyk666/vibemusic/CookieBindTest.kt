package com.cyk666.vibemusic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CookieBindTest {

    private fun statusEnvelope(netease: String, extra: String = ""): String = """
        {
          "code": 200,
          "message": "ok",
          "data": {
            "netease": $netease,
            "bili": {"reserved": true}$extra
          }
        }
    """.trimIndent()

    @Test
    fun saveBody_carriesCookieField() {
        val body = VibeApi.buildSaveCookieBody("MUSIC_U=abc123; __csrf=xyz")
        assertEquals("MUSIC_U=abc123; __csrf=xyz", JSONObject(body).optString("cookie"))
    }

    @Test
    fun status_boundValid_noRebind() {
        val s = VibeApi.parseCookieStatus(
            statusEnvelope("""{"has": true, "valid": true, "updatedAt": "2026-09-14T00:00:00Z"}""")
        )
        assertEquals(VibeApi.CookieStatus(has = true, valid = true, needsRebind = false), s)
    }

    @Test
    fun status_expiredWithExplicitRebind() {
        val s = VibeApi.parseCookieStatus(
            statusEnvelope(
                """{"has": true, "valid": false, "updatedAt": "2026-09-01T00:00:00Z"}""",
                """, "needsRebind": true"""
            )
        )
        assertTrue(s.has)
        assertFalse(s.valid)
        assertTrue(s.needsRebind)
    }

    @Test
    fun status_expiredWithoutFlag_derivesRebind() {
        val s = VibeApi.parseCookieStatus(
            statusEnvelope("""{"has": true, "valid": false}""")
        )
        assertTrue(s.has)
        assertFalse(s.valid)
        assertTrue(s.needsRebind)
    }

    @Test
    fun status_notSet_noRebind() {
        val s = VibeApi.parseCookieStatus(
            statusEnvelope("""{"has": false, "valid": false}""")
        )
        assertEquals(VibeApi.CookieStatus(has = false, valid = false, needsRebind = false), s)
    }

    @Test
    fun status_nestedRebindFlag_honored() {
        val s = VibeApi.parseCookieStatus(
            statusEnvelope("""{"has": true, "valid": false, "needsRebind": true}""")
        )
        assertTrue(s.needsRebind)
    }

    @Test(expected = RuntimeException::class)
    fun status_non200Envelope_throws() {
        VibeApi.parseCookieStatus("""{"code": 500, "message": "boom"}""")
    }

    @Test(expected = AuthException::class)
    fun status_401Envelope_routesToAuth() {
        VibeApi.parseCookieStatus("""{"code": 401, "message": "unauthorized"}""")
    }

    @Test(expected = RuntimeException::class)
    fun status_missingData_throws() {
        VibeApi.parseCookieStatus("""{"code": 200, "message": "ok"}""")
    }

    @Test
    fun subtitle_boundExpiredUnset() {
        assertEquals("已绑定", cookieRowSubtitle(has = true, valid = true))
        assertEquals("已过期，请重新绑定", cookieRowSubtitle(has = true, valid = false))
        assertEquals("未绑定", cookieRowSubtitle(has = false, valid = false))
    }

    @Test
    fun subtitle_explicitRebindWins() {
        assertEquals(
            "已过期，请重新绑定",
            cookieRowSubtitle(has = true, valid = true, needsRebind = true)
        )
    }

    @Test
    fun settingsRows_defaultHasNoCookieRow() {
        val rows = buildSettingsRows(
            cacheLabel = "c",
            storageLabel = "s",
            versionLabel = "v1"
        )
        assertEquals(listOf("theme", "cache", "storage", "about"), rows.map { it.id })
        assertTrue(rows.none { it.id == "cookie" })
    }

    @Test
    fun settingsRows_withCookieLabel_insertsCookieRow() {
        val rows = buildSettingsRows(
            cacheLabel = "c",
            storageLabel = "s",
            versionLabel = "v1",
            cookieLabel = "已绑定"
        )
        assertEquals(listOf("theme", "cookie", "cache", "storage", "about"), rows.map { it.id })
        val cookie = rows.first { it.id == "cookie" }
        assertEquals("网易 Cookie", cookie.title)
        assertEquals("已绑定", cookie.subtitle)
        assertTrue(rows.none { it.id.contains("quality") || it.title.contains("音质") })
    }
}
