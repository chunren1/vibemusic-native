package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParseTest {

    private fun backendEnvelope(): String = """
        {
          "code": 200,
          "message": "ok",
          "data": {
            "total": 2,
            "list": [
              {
                "sourceId": "1895330088",
                "name": "予以",
                "artist": "队长",
                "album": "予以",
                "coverUrl": "https://cover.example/x.jpg",
                "duration": 231,
                "platform": "netease",
                "vip": false
              },
              {
                "sourceId": "999",
                "name": "bare",
                "artist": "anon"
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun parseSearch_backendEnvelopeWithVipAndMissingOptionals() {
        val r = VibeApi.parseSearch(backendEnvelope())
        assertEquals(2, r.total)
        assertEquals(2, r.list.size)
        val full = r.list[0]
        assertEquals("1895330088", full.sourceId)
        assertEquals("予以", full.name)
        assertEquals("队长", full.artist)
        assertEquals("予以", full.album)
        assertEquals("https://cover.example/x.jpg", full.coverUrl)
        assertEquals(231, full.durationSec)
        assertEquals("netease", full.platform)
        val bare = r.list[1]
        assertEquals("999", bare.sourceId)
        assertEquals("bare", bare.name)
        assertEquals("", bare.album)
        assertEquals("", bare.coverUrl)
        assertEquals(0, bare.durationSec)
        assertEquals("", bare.platform)
    }

    @Test
    fun parseSearch_non200CodeThrows() {
        try {
            VibeApi.parseSearch("""{"code":401,"message":"expired","data":{}}""")
            fail("expected RuntimeException for code!=200")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").isNotBlank())
        }
    }

    @Test
    fun parseSearch_missingDataThrows() {
        try {
            VibeApi.parseSearch("""{"code":200,"message":"ok"}""")
            fail("expected RuntimeException for missing data")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").contains("missing data"))
        }
    }

    @Test
    fun parseSearch_malformedJsonThrows() {
        try {
            VibeApi.parseSearch("{not json")
            fail("expected exception for malformed JSON")
        } catch (_: Exception) {
        }
    }

    private fun playlistEnvelope(vararg items: String): String =
        """{"code":200,"message":"ok","data":[${items.joinToString(",")}]}"""

    @Test
    fun parsePlaylistSongs_fullV6ItemParsesAllFields() {
        val item = """
            {
              "sourceId": "1895330088",
              "name": "予以",
              "songName": "予以-legacy",
              "artist": "队长",
              "album": "予以",
              "coverUrl": "https://cover.example/x.jpg",
              "duration": 231,
              "platform": "netease",
              "addedAt": "2026-09-08T10:00:00Z"
            }
        """.trimIndent()
        val songs = VibeApi.parsePlaylistSongs(playlistEnvelope(item))
        assertEquals(1, songs.size)
        val s = songs[0]
        assertEquals("1895330088", s.sourceId)
        assertEquals("予以", s.name)
        assertEquals("队长", s.artist)
        assertEquals("予以", s.album)
        assertEquals("https://cover.example/x.jpg", s.coverUrl)
        assertEquals(231, s.durationSec)
        assertEquals("netease", s.platform)
        assertTrue(s.streamUrl().contains("platform=netease"))
    }

    @Test
    fun parsePlaylistSongs_legacyItemFallsBackToSongNameAndNetease() {
        val item = """
            {
              "sourceId": "777",
              "songName": "老歌名",
              "artist": "老歌手",
              "duration": 180
            }
        """.trimIndent()
        val songs = VibeApi.parsePlaylistSongs(playlistEnvelope(item))
        assertEquals(1, songs.size)
        val s = songs[0]
        assertEquals("777", s.sourceId)
        assertEquals("老歌名", s.name)
        assertEquals("老歌手", s.artist)
        assertEquals("netease", s.platform)
        assertTrue(s.streamUrl().contains("platform=netease"))
    }

    @Test
    fun parsePlaylistSongs_blankNameStaysBlankForUiFallback() {
        // Parser keeps name blank; UI layer shows "(untitled)":
        // SearchScreen/playlist rows use `song.name.ifBlank { "(untitled)" }`
        // (MainActivity SearchScreen + playlist detail rows).
        val item = """{"sourceId": "0", "artist": "anon", "platform": "netease"}"""
        val songs = VibeApi.parsePlaylistSongs(playlistEnvelope(item))
        assertEquals(1, songs.size)
        assertEquals("", songs[0].name)
        assertEquals("netease", songs[0].platform)
    }

    @Test
    fun parsePlaylistSongs_nameBeatsSongNameBeatsTitle() {
        val both = """{"sourceId":"1","name":"n","songName":"legacy","title":"t","platform":"qq"}"""
        val legacyOnly = """{"sourceId":"2","songName":"legacy","title":"t"}"""
        val titleOnly = """{"sourceId":"3","title":"titled"}"""
        val songs = VibeApi.parsePlaylistSongs(playlistEnvelope(both, legacyOnly, titleOnly))
        assertEquals(3, songs.size)
        assertEquals("n", songs[0].name)
        assertEquals("qq", songs[0].platform)
        assertEquals("legacy", songs[1].name)
        assertEquals("netease", songs[1].platform)
        assertEquals("titled", songs[2].name)
        assertEquals("netease", songs[2].platform)
    }
}
