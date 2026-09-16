package com.cyk666.vibemusic

import android.content.Context
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HotspotStoreTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    // ---- codec round-trips ----

    @Test
    fun songs_roundTrip() {
        val songs = listOf(
            Song("1", "n", "a", "al", "https://c", 100, "netease", true),
            Song("2", "n2", "a2", "al2", "", 0, "qq")
        )
        assertEquals(songs, songsFromJson(songsToJson(songs)))
    }

    @Test
    fun recommendPlaylists_roundTrip() {
        val list = listOf(
            RecommendPlaylist("r1", "热歌", "https://p/1.jpg", "大家都在听", 999L)
        )
        assertEquals(list, decodeRecommendPlaylists(encodeRecommendPlaylists(list)))
    }

    @Test
    fun daily_roundTrip_keepsReason() {
        val songs = listOf(Song("1", "n", "a", "al", "", 10, "netease"))
        val rt = decodeDaily(encodeDaily("根据你的口味", songs))
        assertEquals("根据你的口味", rt?.first)
        assertEquals(songs, rt?.second)
    }

    // ---- per-user isolation ----

    @Test
    fun playlists_userMismatch_readsAsMiss() {
        val json = encodePlaylists("userA", listOf(Playlist("1", "pl", "c", 3)))
        assertNull(decodePlaylists(json, "userB"))
        assertEquals(1, decodePlaylists(json, "userA")?.size)
    }

    // ---- corruption tolerance ----

    @Test
    fun decode_garbage_returnsNull() {
        assertNull(decodeDaily("{"))
        assertNull(decodeRecommendPlaylists("[1,2"))
        assertNull(decodePlaylists("{\"userId\":\"u\"}", "u"))
        assertNull(songsFromJson("nope"))
    }

    // ---- file layer ----

    @Test
    fun writeRead_roundTrip() = runBlocking {
        val ctx = context()
        HotspotStore.write(ctx, "guess", songsToJson(listOf(Song("1", "n", "a", "al", "", 10, "netease"))))
        val got = HotspotStore.read(ctx, "guess")
        assertTrue(got != null && got.first > 0L)
        assertEquals("n", songsFromJson(got!!.second)?.first()?.name)
    }

    @Test
    fun read_neverWritten_isMiss() = runBlocking {
        assertNull(HotspotStore.read(context(), "guess"))
    }

    @Test
    fun write_disallowedKey_isNoop() = runBlocking {
        val ctx = context()
        HotspotStore.write(ctx, "../evil", "x")
        assertNull(HotspotStore.read(ctx, "../evil"))
    }

    @Test
    fun write_blankPayload_isNoop() = runBlocking {
        val ctx = context()
        HotspotStore.write(ctx, "hot", "  ")
        assertNull(HotspotStore.read(ctx, "hot"))
    }

    @Test
    fun read_payloadWithoutStamp_isMiss() = runBlocking {
        val ctx = context()
        val d = File(ctx.filesDir, "hotspot")
        d.mkdirs()
        File(d, "hot.json").writeText("[]")
        assertNull(HotspotStore.read(ctx, "hot"))
    }

    @Test
    fun write_overwritesPreviousPayload() = runBlocking {
        val ctx = context()
        HotspotStore.write(ctx, "hot", encodeRecommendPlaylists(listOf(RecommendPlaylist("a", "n", "p"))))
        HotspotStore.write(ctx, "hot", encodeRecommendPlaylists(listOf(RecommendPlaylist("b", "n", "p"))))
        val got = HotspotStore.read(ctx, "hot")
        assertEquals("b", decodeRecommendPlaylists(got!!.second)?.first()?.id)
    }
}
