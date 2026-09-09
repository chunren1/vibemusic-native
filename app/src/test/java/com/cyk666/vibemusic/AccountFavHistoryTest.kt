package com.cyk666.vibemusic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountFavHistoryTest {

    private val demoSong = Song(
        sourceId = "1895330088",
        name = "予以",
        artist = "队长",
        album = "",
        coverUrl = "https://cover.example/x.jpg",
        durationSec = 231,
        platform = "netease"
    )

    @Test
    fun parseLogin_jsonNullBecomesBlank() {
        val data = JSONObject()
            .put("token", "t")
            .put("username", "bob")
            .put("gender", JSONObject.NULL)
            .put("birthday", JSONObject.NULL)
        val u = VibeApi.parseLogin(JSONObject().put("code", 200).put("data", data).toString()).user
        assertEquals("", u.gender)
        assertEquals("", u.birthday)
    }

    @Test
    fun parseLogin_nullStringBecomesBlank() {
        val data = JSONObject()
            .put("token", "t")
            .put("username", "bob")
            .put("nickname", "null")
            .put("avatar", "null")
            .put("gender", "保密")
            .put("birthday", "null")
        val u = VibeApi.parseLogin(JSONObject().put("code", 200).put("data", data).toString()).user
        assertEquals("bob", u.nickname)
        assertEquals("", u.avatar)
        assertEquals("保密", u.gender)
        assertEquals("", u.birthday)
    }

    private fun registerJson(token: String, refreshToken: String): String {
        val data = JSONObject()
            .put("token", token)
            .put("refreshToken", refreshToken)
            .put("username", "bob")
            .put("nickname", "Bob")
        return JSONObject().put("code", 200).put("data", data).toString()
    }

    @Test
    fun buildRegisterBody_withNickname() {
        val o = JSONObject(VibeApi.buildRegisterBody("bob", "password123", "Bob"))
        assertEquals("bob", o.getString("username"))
        assertEquals("password123", o.getString("password"))
        assertEquals("Bob", o.getString("nickname"))
    }

    @Test
    fun buildRegisterBody_blankNicknameOmitted() {
        assertFalse(JSONObject(VibeApi.buildRegisterBody("bob", "password123", null)).has("nickname"))
        assertFalse(JSONObject(VibeApi.buildRegisterBody("bob", "password123", "  ")).has("nickname"))
    }

    @Test
    fun parseRegister_sameShapeAsLogin() {
        val r = VibeApi.parseRegister(registerJson("tok1", "ref1"))
        assertEquals("tok1", r.token)
        assertEquals("ref1", r.refreshToken)
        assertEquals("bob", r.user.username)
        assertEquals("Bob", r.user.nickname)
    }

    @Test
    fun buildChangePasswordBody_exactKeys() {
        val o = JSONObject(VibeApi.buildChangePasswordBody("old12345", "new12345"))
        assertEquals("old12345", o.getString("oldPassword"))
        assertEquals("new12345", o.getString("newPassword"))
    }

    @Test
    fun buildProfileBody_onlyProvidedFields() {
        val nick = JSONObject(VibeApi.buildProfileBody(nickname = "新昵称"))
        assertEquals("新昵称", nick.getString("nickname"))
        assertFalse(nick.has("gender"))
        assertFalse(nick.has("birthday"))

        val full = JSONObject(
            VibeApi.buildProfileBody(nickname = "N", gender = "男", birthday = "2000-01-02")
        )
        assertEquals("N", full.getString("nickname"))
        assertEquals("男", full.getString("gender"))
        assertEquals("2000-01-02", full.getString("birthday"))

        assertEquals(0, JSONObject(VibeApi.buildProfileBody()).length())
    }

    @Test
    fun avatarPartName_matchesBackendFileParam() {
        assertEquals("file", VibeApi.AVATAR_PART_NAME)
    }

    @Test
    fun parseAvatarResult_avatarUrlKey() {
        val data = JSONObject()
            .put("username", "bob")
            .put("nickname", "Bob")
            .put("avatarUrl", "/uploads/avatars/avatar_1_2.jpg")
        val json = JSONObject().put("code", 200).put("data", data).toString()
        val r = VibeApi.parseAvatarResult(json, bg = false)
        assertEquals("/uploads/avatars/avatar_1_2.jpg", r.url)
    }

    @Test
    fun parseAvatarResult_bgImageUrlKey() {
        val data = JSONObject()
            .put("username", "bob")
            .put("bgImageUrl", "/uploads/avatars/bg_1_2.png")
        val json = JSONObject().put("code", 200).put("data", data).toString()
        val r = VibeApi.parseAvatarResult(json, bg = true)
        assertEquals("/uploads/avatars/bg_1_2.png", r.url)
    }

    @Test
    fun parseAvatarResult_missingUrlThrows() {
        val json = JSONObject().put("code", 200).put("data", JSONObject()).toString()
        try {
            VibeApi.parseAvatarResult(json, bg = false)
            fail("expected RuntimeException for missing url")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").isNotBlank())
        }
    }

    @Test
    fun buildToggleBody_exactKeys() {
        val o = JSONObject(VibeApi.buildToggleBody(demoSong))
        assertEquals("1895330088", o.getString("sourceId"))
        assertEquals("予以", o.getString("songName"))
        assertEquals("队长", o.getString("artist"))
        assertEquals("https://cover.example/x.jpg", o.getString("coverUrl"))
    }

    @Test
    fun buildToggleRequest_carriesIdempotencyHeader() {
        val id = VibeApi.newRequestId()
        val req = VibeApi.buildToggleRequest(demoSong, id)
        assertEquals(id, req.header(VibeApi.FAV_IDEMPOTENCY_HEADER))
        assertEquals("X-Request-Id", VibeApi.FAV_IDEMPOTENCY_HEADER)
        assertTrue(req.url.toString().endsWith("api/favorites/toggle"))
    }

    @Test
    fun newRequestId_uniqueUuidFormat() {
        val a = VibeApi.newRequestId()
        val b = VibeApi.newRequestId()
        assertTrue(a != b)
        assertTrue(a.matches(Regex("[0-9a-f-]{36}")))
    }

    @Test
    fun parseToggleResult_trueAndFalse() {
        assertTrue(VibeApi.parseToggleResult("""{"code":200,"message":"已收藏","data":true}"""))
        assertFalse(VibeApi.parseToggleResult("""{"code":200,"message":"已取消","data":false}"""))
    }

    @Test
    fun parseFavIds_setOfSourceIds() {
        val ids = VibeApi.parseFavIds("""{"code":200,"message":"ok","data":["1","2","3"]}""")
        assertEquals(setOf("1", "2", "3"), ids)
    }

    @Test
    fun parseFavIds_emptyOk() {
        assertTrue(VibeApi.parseFavIds("""{"code":200,"message":"ok","data":[]}""").isEmpty())
    }

    @Test
    fun parseFavList_defensiveSongNameMapping() {
        val json = """{"code":200,"message":"ok","data":[
            {"sourceId":"99","songName":"夜曲","artist":"周杰伦","coverUrl":"https://c/x.jpg"}
        ]}"""
        val list = VibeApi.parseFavList(json)
        assertEquals(1, list.size)
        assertEquals("99", list[0].sourceId)
        assertEquals("夜曲", list[0].name)
        assertEquals("netease", list[0].platform)
    }

    @Test
    fun buildFavRemoveBatchBody_exactKey() {
        val o = JSONObject(VibeApi.buildFavRemoveBatchBody(listOf("1", "2")))
        val arr = o.getJSONArray("sourceIds")
        assertEquals(2, arr.length())
        assertEquals("1", arr.getString(0))
        assertEquals("2", arr.getString(1))
    }

    @Test
    fun parseHistoryList_songPlusPlayedAt() {
        val json = """{"code":200,"message":"ok","data":[
            {"sourceId":"7","songName":"晴天","artist":"周杰伦","coverUrl":"","playedAt":"2026-09-09T10:00:00"}
        ]}"""
        val list = VibeApi.parseHistoryList(json)
        assertEquals(1, list.size)
        assertEquals("7", list[0].song.sourceId)
        assertEquals("晴天", list[0].song.name)
        assertEquals("2026-09-09T10:00:00", list[0].playedAt)
    }

    @Test
    fun buildHistoryRemoveBody_exactKey() {
        val o = JSONObject(VibeApi.buildHistoryRemoveBody(listOf("7")))
        assertEquals("7", o.getJSONArray("sourceIds").getString(0))
    }

    @Test
    fun shouldReportPlay_rule() {
        assertTrue(VibeApi.shouldReportPlay(true, "netease:1", null))
        assertTrue(VibeApi.shouldReportPlay(true, "netease:2", "netease:1"))
        assertFalse(VibeApi.shouldReportPlay(true, "netease:1", "netease:1"))
        assertFalse(VibeApi.shouldReportPlay(false, "netease:1", null))
        assertFalse(VibeApi.shouldReportPlay(true, "", null))
    }

    @Test
    fun buildPlayReportPath_params() {
        val path = VibeApi.buildPlayReportPath(demoSong)
        assertTrue(path.startsWith("api/songs/play?"))
        assertTrue(path.contains("sourceId=1895330088"))
        assertTrue(path.contains("artist="))
        assertTrue(path.contains("coverUrl="))
    }

    @Test
    fun absImgUrl_relativeBecomesAbsolute() {
        assertEquals(
            "https://vibe.cyk666.top/uploads/avatars/a.jpg",
            absImgUrl("/uploads/avatars/a.jpg")
        )
        assertEquals("https://c/x.jpg", absImgUrl("https://c/x.jpg"))
        assertEquals("", absImgUrl("  "))
    }
}
