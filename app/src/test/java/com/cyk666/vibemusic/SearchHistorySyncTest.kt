package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SearchHistorySyncTest {

    @Test
    fun merge_unionLocalFirstDedup() {
        val out = mergeSearchHistories(
            local = listOf("b", "a"),
            remote = listOf("c", "a")
        )
        assertEquals(listOf("b", "a", "c"), out)
    }

    @Test
    fun merge_blankAndWhitespaceDropped() {
        val out = mergeSearchHistories(
            local = listOf("  ", "b"),
            remote = listOf("", "c", " b ")
        )
        assertEquals(listOf("b", "c"), out)
    }

    @Test
    fun merge_bothEmptyStaysEmpty() {
        assertTrue(mergeSearchHistories(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun merge_capsAtTenLocalWins() {
        val local = (1..10).map { "l$it" }
        val out = mergeSearchHistories(local, listOf("r1", "r2"))
        assertEquals(10, out.size)
        assertEquals(local, out)
    }

    @Test
    fun merge_remoteFillsUpToCap() {
        val out = mergeSearchHistories(
            local = listOf("a", "b"),
            remote = (1..12).map { "r$it" }
        )
        assertEquals(10, out.size)
        assertEquals(listOf("a", "b"), out.take(2))
        assertTrue(out.none { it == "r9" })
    }

    @Test
    fun merge_customCapRespected() {
        val out = mergeSearchHistories(listOf("a"), listOf("b", "c"), cap = 2)
        assertEquals(listOf("a", "b"), out)
    }

    @Test
    fun buildBody_trimsDedupsCaps() {
        val body = JSONObject(VibeApi.buildSearchHistoryBody(listOf(" b ", "a", "b", "")))
        val arr = body.getJSONArray("keywords")
        assertEquals(2, arr.length())
        assertEquals("b", arr.getString(0))
        assertEquals("a", arr.getString(1))
    }

    @Test
    fun parseList_plainArray() {
        val out = VibeApi.parseSearchHistoryList(
            """{"code":200,"data":["a","b"],"message":"ok"}"""
        )
        assertEquals(listOf("a", "b"), out)
    }

    @Test
    fun parseList_objectShapes() {
        for (key in listOf("list", "keywords", "history")) {
            val out = VibeApi.parseSearchHistoryList(
                """{"code":200,"data":{"$key":["x"]},"message":"ok"}"""
            )
            assertEquals(listOf("x"), out)
        }
    }

    @Test
    fun parseList_missingDataIsEmpty() {
        val out = VibeApi.parseSearchHistoryList(
            """{"code":200,"message":"ok"}"""
        )
        assertTrue(out.isEmpty())
    }

    @Test(expected = RuntimeException::class)
    fun parseList_non200Throws() {
        VibeApi.parseSearchHistoryList(
            """{"code":401,"data":[],"message":"unauthorized"}"""
        )
    }

}

@RunWith(RobolectricTestRunner::class)
class SearchHistorySaveStoreTest {

    private fun context(): Context =
        RuntimeEnvironment.getApplication()

    @Test
    fun saveHistory_bulkReplaceRoundTrip(): Unit = runBlocking {
        SearchStore.clearHistory(context())
        val saved = SearchStore.saveHistory(
            context(),
            listOf("b", "a", "b", "  ", "c")
        )
        assertEquals(listOf("b", "a", "c"), saved)
        assertEquals(listOf("b", "a", "c"), SearchStore.loadHistory(context()))
    }

    @Test
    fun saveHistory_capsAtTen(): Unit = runBlocking {
        SearchStore.clearHistory(context())
        SearchStore.saveHistory(context(), (1..12).map { "q$it" })
        val loaded = SearchStore.loadHistory(context())
        assertEquals(10, loaded.size)
        assertEquals("q1", loaded.first())
        assertTrue(loaded.none { it == "q11" })
    }
}
