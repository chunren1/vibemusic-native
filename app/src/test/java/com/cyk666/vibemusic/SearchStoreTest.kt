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

    // ---- 前缀折叠（2026-10-27 用户反馈：输入「第三」→ 再搜「第三人称」出现两条） ----

    @Test
    fun mergeHistory_foldsOlderPrefixWhenRefining(): Unit {
        assertEquals(
            listOf("第三人称"),
            mergeSearchHistory(listOf("第三"), "第三人称")
        )
    }

    @Test
    fun mergeHistory_foldsOnlyWhenLongerIsNewer(): Unit {
        // 反向：先搜「第三人称」再搜「第三」——两次不同意图，不能因为"包含"就删
        assertEquals(
            listOf("第三", "第三人称"),
            mergeSearchHistory(listOf("第三人称"), "第三")
        )
    }

    @Test
    fun mergeHistory_foldsRefinedVariant(): Unit {
        // 已知取舍：同一次意图的细化会折叠（接受，见 foldPrefixHistory 注释）
        assertEquals(
            listOf("晴天 live"),
            mergeSearchHistory(listOf("晴天"), "晴天 live")
        )
    }

    @Test
    fun mergeHistory_keepsUnrelatedSiblings(): Unit {
        assertEquals(
            listOf("晴天", "下雨天", "周杰伦"),
            mergeSearchHistory(listOf("下雨天", "周杰伦"), "晴天")
        )
    }

    @Test
    fun foldPrefix_singlePassKeepsNewestOfChain(): Unit {
        // 递进输入留下的三条「第」「第三」「第三人称」→ 只留最完整那条
        assertEquals(
            listOf("第三人称"),
            foldPrefixHistory(listOf("第三人称", "第三", "第"))
        )
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
