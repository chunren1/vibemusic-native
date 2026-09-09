package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SearchStoreTest {

    private fun context(): Context =
        RuntimeEnvironment.getApplication()

    @Test
    fun mergeHistory_dedupLatestFirst(): Unit {
        val out = mergeSearchHistory(listOf("a", "b"), "b")
        assertEquals(listOf("b", "a"), out)
    }

    @Test
    fun mergeHistory_blankQueryKeepsExisting(): Unit {
        assertEquals(listOf("a"), mergeSearchHistory(listOf("a"), "   "))
    }

    @Test
    fun mergeHistory_capsAtTen(): Unit {
        val existing = (1..10).map { "q$it" }
        val out = mergeSearchHistory(existing, "new")
        assertEquals(10, out.size)
        assertEquals("new", out.first())
        assertTrue(out.none { it == "q10" })
    }

    @Test
    fun store_roundTripAndRemove(): Unit = runBlocking {
        SearchStore.clearHistory(context())
        SearchStore.addHistory(context(), "予以")
        SearchStore.addHistory(context(), "予以")
        SearchStore.addHistory(context(), "来不及爱你")
        assertEquals(
            listOf("来不及爱你", "予以"),
            SearchStore.loadHistory(context())
        )
        SearchStore.removeHistory(context(), "予以")
        assertEquals(listOf("来不及爱你"), SearchStore.loadHistory(context())
        )
    }

    @Test
    fun store_blankQueryNotSaved(): Unit = runBlocking {
        SearchStore.clearHistory(context())
        SearchStore.addHistory(context(), "   ")
        assertTrue(SearchStore.loadHistory(context()).isEmpty())
    }

    @Test
    fun store_historyCapsAtTen(): Unit = runBlocking {
        SearchStore.clearHistory(context())
        for (i in 1..12) SearchStore.addHistory(context(), "q$i")
        val loaded = SearchStore.loadHistory(context())
        assertEquals(10, loaded.size)
        assertEquals("q12", loaded.first())
        assertTrue(loaded.none { it == "q1" })
    }

    @Test
    fun store_sortRoundTrip(): Unit = runBlocking {
        SearchStore.saveSort(context(), SearchSort.DURATION_DESC)
        assertEquals(SearchSort.DURATION_DESC, SearchStore.loadSort(context()))
        SearchStore.saveSort(context(), SearchSort.RELEVANCE)
        assertEquals(SearchSort.RELEVANCE, SearchStore.loadSort(context()))
    }
}
