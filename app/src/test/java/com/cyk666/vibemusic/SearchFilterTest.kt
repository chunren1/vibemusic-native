package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {

    private fun song(name: String, artist: String, duration: Int, id: String = name): Song =
        Song(
            sourceId = id,
            name = name,
            artist = artist,
            album = "",
            coverUrl = "",
            durationSec = duration,
            platform = "netease"
        )

    private val songs = listOf(
        song("来不及爱你", "筷子兄弟", 280, "1"),
        song("予以", "队长", 231, "2"),
        song("予以 Cover", "新人歌手", 200, "3"),
        song("长歌", "队长", 300, "4")
    )

    @Test
    fun distinctArtists_firstSeenOrderAndCap() {
        assertEquals(
            listOf("筷子兄弟", "队长", "新人歌手"),
            distinctArtists(songs)
        )
        assertEquals(listOf("筷子兄弟", "队长"), distinctArtists(songs, cap = 2))
    }

    @Test
    fun distinctArtists_blankArtistsSkipped() {
        val list = listOf(song("x", "  ", 10, "a"), song("y", "队长", 10, "b"))
        assertEquals(listOf("队长"), distinctArtists(list))
    }

    @Test
    fun filter_nullOrBlankKeepsAllInBackendOrder() {
        assertEquals(songs, filterAndSortSongs(songs, null, SearchSort.RELEVANCE))
        assertEquals(songs, filterAndSortSongs(songs, "  ", SearchSort.RELEVANCE))
    }

    @Test
    fun filter_exactArtistMatchOnly() {
        val out = filterAndSortSongs(songs, "队长", SearchSort.RELEVANCE)
        assertEquals(listOf(songs[1], songs[3]), out)
    }

    @Test
    fun sort_durationAsc() {
        val out = filterAndSortSongs(songs, null, SearchSort.DURATION_ASC)
        assertEquals(listOf(200, 231, 280, 300), out.map { it.durationSec })
    }

    @Test
    fun sort_durationDesc() {
        val out = filterAndSortSongs(songs, null, SearchSort.DURATION_DESC)
        assertEquals(listOf(300, 280, 231, 200), out.map { it.durationSec })
    }

    @Test
    fun sort_artistName() {
        val out = filterAndSortSongs(songs, null, SearchSort.ARTIST_NAME)
        val artists = out.map { it.artist }
        assertEquals(artists.sorted(), artists)
        assertTrue(out.indexOfFirst { it.artist == "队长" } < out.indexOfLast { it.artist == "队长" } ||
            out.count { it.artist == "队长" } == 2)
    }

    @Test
    fun filterAndSort_combined() {
        val out = filterAndSortSongs(songs, "队长", SearchSort.DURATION_DESC)
        assertEquals(listOf(songs[3], songs[1]), out)
    }
}
