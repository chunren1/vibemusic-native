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
class PlaylistManageTest {

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
    fun parseCreateResult_fullDataParsesId() {
        val r = VibeApi.parseCreateResult(
            """{"code":200,"message":"ok","data":{"id":42,"name":"深夜","songCount":0}}"""
        )
        assertEquals("42", r.id)
        assertEquals("深夜", r.name)
        assertFalse(r.duplicate)
    }

    @Test
    fun parseCreateResult_duplicateFlagSurvives() {
        val r = VibeApi.parseCreateResult(
            """{"code":200,"message":"ok","data":{"id":7,"name":"深夜","duplicate":true}}"""
        )
        assertEquals("7", r.id)
        assertTrue(r.duplicate)
    }

    @Test
    fun parseCreateResult_missingDataThrows() {
        try {
            VibeApi.parseCreateResult("""{"code":200,"message":"ok"}""")
            fail("expected RuntimeException for missing data")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").contains("missing data"))
        }
    }

    @Test
    fun parseCreateResult_non200Throws() {
        try {
            VibeApi.parseCreateResult("""{"code":400,"message":"歌单名称不能为空","data":null}""")
            fail("expected RuntimeException for code!=200")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").isNotBlank())
        }
    }

    @Test
    fun parseDeleteBatchCount_returnsCount() {
        assertEquals(3, VibeApi.parseDeleteBatchCount("""{"code":200,"message":"已删除 3 个歌单","data":3}"""))
    }

    @Test
    fun parseDeleteBatchCount_zeroOk() {
        assertEquals(0, VibeApi.parseDeleteBatchCount("""{"code":200,"message":"ok","data":0}"""))
    }

    @Test
    fun parseDeleteBatchCount_missingDataThrows() {
        try {
            VibeApi.parseDeleteBatchCount("""{"code":200,"message":"ok"}""")
            fail("expected RuntimeException for missing count")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").isNotBlank())
        }
    }

    @Test
    fun parseImportResult_fullData() {
        val r = VibeApi.parseImportResult(
            """{"code":200,"message":"ok","data":{"imported":8,"total":10,"name":"华语热歌"}}"""
        )
        assertEquals(8, r.imported)
        assertEquals(10, r.total)
        assertEquals("华语热歌", r.name)
    }

    @Test
    fun parseImportResult_missingTotalFallsBackToImported() {
        val r = VibeApi.parseImportResult(
            """{"code":200,"message":"ok","data":{"imported":5,"name":"爵士"}}"""
        )
        assertEquals(5, r.imported)
        assertEquals(5, r.total)
        assertEquals("爵士", r.name)
    }

    @Test
    fun parseImportResult_missingNameStaysBlank() {
        val r = VibeApi.parseImportResult(
            """{"code":200,"message":"ok","data":{"imported":1,"total":1}}"""
        )
        assertEquals("", r.name)
    }

    @Test
    fun parseImportResult_missingDataThrows() {
        try {
            VibeApi.parseImportResult("""{"code":200,"message":"ok"}""")
            fail("expected RuntimeException for missing data")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").contains("missing data"))
        }
    }

    @Test
    fun parseAddSongResult_trueAndFalse() {
        assertTrue(VibeApi.parseAddSongResult("""{"code":200,"message":"ok","data":true}"""))
        assertFalse(VibeApi.parseAddSongResult("""{"code":200,"message":"ok","data":false}"""))
    }

    @Test
    fun buildReorderBody_assignsIndexSortOrder() {
        val body = JSONObject(VibeApi.buildReorderBody(listOf("11", "22", "33")))
        val arr = body.getJSONArray("order")
        assertEquals(3, arr.length())
        assertEquals(11L, arr.getJSONObject(0).getLong("playlistId"))
        assertEquals(0, arr.getJSONObject(0).getInt("sortOrder"))
        assertEquals(22L, arr.getJSONObject(1).getLong("playlistId"))
        assertEquals(1, arr.getJSONObject(1).getInt("sortOrder"))
        assertEquals(33L, arr.getJSONObject(2).getLong("playlistId"))
        assertEquals(2, arr.getJSONObject(2).getInt("sortOrder"))
    }

    @Test
    fun buildReorderBody_swappedPairSendsFullList() {
        val body = JSONObject(VibeApi.buildReorderBody(listOf("22", "11")))
        val arr = body.getJSONArray("order")
        assertEquals(2, arr.length())
        assertEquals(22L, arr.getJSONObject(0).getLong("playlistId"))
        assertEquals(11L, arr.getJSONObject(1).getLong("playlistId"))
    }

    @Test
    fun buildDeleteBatchBody_numericIdsOnly() {
        val body = JSONObject(VibeApi.buildDeleteBatchBody(listOf("5", "9")))
        val arr = body.getJSONArray("ids")
        assertEquals(2, arr.length())
        assertEquals(5L, arr.getLong(0))
        assertEquals(9L, arr.getLong(1))
    }

    @Test
    fun buildUpdateBody_onlyProvidedFields() {
        val rename = JSONObject(VibeApi.buildUpdateBody("12", name = "新名"))
        assertEquals(12L, rename.getLong("playlistId"))
        assertEquals("新名", rename.getString("name"))
        assertFalse(rename.has("description"))
        assertFalse(rename.has("coverUrl"))

        val desc = JSONObject(VibeApi.buildUpdateBody("12", description = "简介"))
        assertFalse(desc.has("name"))
        assertEquals("简介", desc.getString("description"))
    }

    @Test
    fun buildAddSongBody_carriesFullSongFields() {
        val body = JSONObject(VibeApi.buildAddSongBody("12", demoSong))
        assertEquals(12L, body.getLong("playlistId"))
        assertEquals("1895330088", body.getString("sourceId"))
        assertEquals("予以", body.getString("songName"))
        assertEquals("队长", body.getString("artist"))
        assertEquals("https://cover.example/x.jpg", body.getString("coverUrl"))
        assertEquals(231, body.getInt("duration"))
        assertEquals("netease", body.getString("platform"))
    }

    @Test
    fun buildAddSongBody_blankPlatformDefaultsNetease() {
        val body = JSONObject(VibeApi.buildAddSongBody("12", demoSong.copy(platform = "")))
        assertEquals("netease", body.getString("platform"))
    }

    @Test
    fun buildCreateBody_nameRequiredDescriptionOptional() {
        val full = JSONObject(VibeApi.buildCreateBody("深夜", "emo"))
        assertEquals("深夜", full.getString("name"))
        assertEquals("emo", full.getString("description"))

        val minimal = JSONObject(VibeApi.buildCreateBody("深夜", null))
        assertEquals("深夜", minimal.getString("name"))
        assertFalse(minimal.has("description"))
    }

    @Test
    fun extractPlaylistId_bareDigits() {
        assertEquals("19723756", VibeApi.extractPlaylistId("19723756"))
        assertEquals("19723756", VibeApi.extractPlaylistId("  19723756  "))
    }

    @Test
    fun extractPlaylistId_fullLinks() {
        assertEquals(
            "19723756",
            VibeApi.extractPlaylistId("https://music.163.com/#/playlist?id=19723756")
        )
        assertEquals(
            "19723756",
            VibeApi.extractPlaylistId("https://music.163.com/playlist?id=19723756&userid=123")
        )
        assertEquals(
            "19723756",
            VibeApi.extractPlaylistId("y.qq.com/n/ryqq/playlist/123?abc=1&id=19723756")
        )
    }

    @Test
    fun extractPlaylistId_garbageIsEmpty() {
        assertEquals("", VibeApi.extractPlaylistId(""))
        assertEquals("", VibeApi.extractPlaylistId("   "))
        assertEquals("", VibeApi.extractPlaylistId("abcdef"))
        assertEquals("", VibeApi.extractPlaylistId("https://music.163.com/#/playlist"))
        assertEquals("", VibeApi.extractPlaylistId("id=abc"))
    }

    @Test
    fun buildImportBody_numericIdStaysNumeric() {
        val body = JSONObject(VibeApi.buildImportBody("netease", "19723756"))
        assertEquals("netease", body.getString("source"))
        assertEquals(19723756L, body.getLong("id"))

        val qq = JSONObject(VibeApi.buildImportBody("qq", "abc"))
        assertEquals("qq", qq.getString("source"))
        assertEquals("abc", qq.getString("id"))
    }
}
