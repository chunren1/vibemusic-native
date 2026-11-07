package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * AI 推荐状态（AiPicks.kt）：
 * ① 首次加载成功/空结果；
 * ② 「换一批」的两种失败都不许清掉已有列表（429 = 配额，其它 = 网络）；
 * ③ autoLoaded 只自动跑一次；
 * ④ 冷启动缓存优先，网络空结果不覆盖已还原内容。
 *
 * 用 [Dispatchers.Unconfined] + 注入的 fetch/缓存，整条链路同步完成，
 * 不依赖 kotlinx-coroutines-test（本仓库没有这个依赖）。
 */
@RunWith(RobolectricTestRunner::class)
class AiRecommendStateTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private val old = listOf(
        Song("s1", "旧歌", "某人", "专辑", "https://c/1.jpg", 200, "netease")
    )

    private fun state(
        cache: MutableMap<String, String> = mutableMapOf(),
        fetch: suspend (Boolean) -> PersonalizedResult
    ): AiRecommendState = AiRecommendState(
        fetch = fetch,
        readCache = { _, key -> cache[key]?.let { 1L to it } },
        writeCache = { _, key, payload -> cache[key] = payload }
    )

    @Test
    fun loadIfNeeded_success_fillsSongsAndClearsLoading() {
        val st = state { PersonalizedResult(old, "因为你在听民谣", "", "ai") }
        st.loadIfNeeded(scope, context(), "u1")
        assertEquals(old, st.songs)
        assertEquals("因为你在听民谣", st.reason)
        assertFalse(st.loading)
        assertNull(st.notice)
    }

    @Test
    fun loadIfNeeded_success_persistsToCache() {
        val cache = mutableMapOf<String, String>()
        val st = state(cache) { PersonalizedResult(old, "理由", "", "ai") }
        st.loadIfNeeded(scope, context(), "u1")
        val decoded = decodeDaily(cache.getValue(AI_CACHE_KEY))
        assertEquals(old, decoded?.second)
        assertEquals("理由", decoded?.first)
    }

    @Test
    fun loadIfNeeded_emptyResult_staysEmptyWithoutNotice() {
        val st = state { PersonalizedResult(emptyList(), "", "", "ai") }
        st.loadIfNeeded(scope, context(), "u1")
        assertTrue(st.songs.isEmpty())
        assertFalse(st.loading)
        assertNull(st.notice)
    }

    @Test
    fun loadIfNeeded_runsOncePerProcess() {
        var calls = 0
        val st = state {
            calls += 1
            PersonalizedResult(old, "", "", "ai")
        }
        st.loadIfNeeded(scope, context(), "u1")
        st.loadIfNeeded(scope, context(), "u1")
        assertEquals(1, calls)
    }

    /**
     * 游客（未登录）不请求也不展示：服务端对游客只会返回空，
     * 请求一次既浪费又会让板块闪一下骨架；磁盘缓存也不许给游客看。
     */
    @Test
    fun loadIfNeeded_guest_doesNotFetchNorShowStaleCache() {
        var calls = 0
        val cache = mutableMapOf(AI_CACHE_KEY to encodeDaily("上个号的理由", old))
        val st = state(cache) {
            calls += 1
            PersonalizedResult(old, "", "", "ai")
        }
        st.loadIfNeeded(scope, context(), null)
        assertEquals(0, calls)
        assertTrue(st.songs.isEmpty())
        assertEquals("", st.reason)
    }

    /** 游客→登录：登录态变化必须重新加载（否则要等下次冷启动才看到板块）。 */
    @Test
    fun loadIfNeeded_loginAfterGuest_loadsForNewUser() {
        var calls = 0
        val st = state {
            calls += 1
            PersonalizedResult(old, "登录后", "", "ai")
        }
        st.loadIfNeeded(scope, context(), null)
        assertEquals(0, calls)
        st.loadIfNeeded(scope, context(), "u1")
        assertEquals(1, calls)
        assertEquals(old, st.songs)
        assertEquals("登录后", st.reason)
    }

    @Test
    fun loadIfNeeded_seedsFromCache_networkEmptyKeepsSeed() {
        val cache = mutableMapOf(AI_CACHE_KEY to encodeDaily("上次的理由", old))
        val st = state(cache) { PersonalizedResult(emptyList(), "", "", "ai") }
        st.loadIfNeeded(scope, context(), "u1")
        assertEquals(old, st.songs)
        assertEquals("上次的理由", st.reason)
    }

    @Test
    fun refresh_quotaExhausted_keepsOldSongsAndSaysQuota() {
        val st = state { refresh ->
            if (!refresh) {
                PersonalizedResult(old, "", "", "ai")
            } else {
                throw RuntimeException(
                    "Request HTTP 429 今天的 AI 推荐次数已用完（3 次/天），明天再来"
                )
            }
        }
        st.loadIfNeeded(scope, context(), "u1")
        st.refresh(scope, context())
        assertEquals(old, st.songs)
        assertTrue(st.notice.orEmpty().contains("次数用完"))
        assertFalse(st.loading)
    }

    @Test
    fun refresh_networkFailure_keepsOldSongsWithFriendlyNotice() {
        val st = state { refresh ->
            if (!refresh) {
                PersonalizedResult(old, "", "", "ai")
            } else {
                throw RuntimeException("Request failed: timeout")
            }
        }
        st.loadIfNeeded(scope, context(), "u1")
        st.refresh(scope, context())
        assertEquals(old, st.songs)
        assertEquals(AiRecommendState.FAIL_NOTICE, st.notice)
        assertFalse(st.loading)
    }

    @Test
    fun refresh_exhaustedEnvelope_withoutHttpStatus_stillDetected() {
        // 200 信封里带 code=429（后端不加 @ResponseStatus 时就会这样）：
        // parsePersonalized 抛 "Personalized failed: code=429 …"。
        val st = state { refresh ->
            if (!refresh) {
                PersonalizedResult(old, "", "", "ai")
            } else {
                throw RuntimeException("Personalized failed: code=429 次数用完")
            }
        }
        st.loadIfNeeded(scope, context(), "u1")
        st.refresh(scope, context())
        assertEquals(AiRecommendState.QUOTA_NOTICE, st.notice)
    }

    @Test
    fun consumeNotice_clearsIt() {
        val st = state { refresh ->
            if (refresh) throw RuntimeException("Request HTTP 429 次数用完")
            else PersonalizedResult(old, "", "", "ai")
        }
        st.loadIfNeeded(scope, context(), "u1")
        st.refresh(scope, context())
        assertEquals(AiRecommendState.QUOTA_NOTICE, st.notice)
        st.consumeNotice()
        assertNull(st.notice)
    }

    /**
     * 回归（2026-11-07）：有列表时「换一批」连点只花 1 次配额。
     *
     * 旧实现只在 songs 为空时置 loading，于是有列表时按钮既不置灰、也没有重入闸门，
     * 连点 N 下会把当天 3 次配额一次点光。现在 loading 无条件置位并挡住重入。
     */
    @Test
    fun refresh_isReentrancyGuarded_whileInFlight() {
        val gate = CompletableDeferred<PersonalizedResult>()
        var calls = 0
        val st = state { refresh ->
            if (!refresh) {
                PersonalizedResult(old, "", "", "ai")
            } else {
                calls += 1
                gate.await()
            }
        }
        st.loadIfNeeded(scope, context(), "u1")
        assertEquals(old, st.songs)

        st.refresh(scope, context())
        assertTrue("飞行中 loading 必须为 true（按钮置灰 + 重入闸门）", st.loading)
        // 连点 3 次：都应该被 loading 挡掉，不发请求
        st.refresh(scope, context())
        st.refresh(scope, context())
        st.refresh(scope, context())
        assertEquals("连点不得重复消耗配额", 1, calls)

        gate.complete(PersonalizedResult(old, "新理由", "", "ai"))
        assertFalse(st.loading)
        assertEquals("新理由", st.reason)
    }

    @Test
    fun visibleSongs_dropsCoverlessTracks() {
        val withCover = old.first()
        val noCover = withCover.copy(sourceId = "s2", name = "无封面", coverUrl = "")
        val st = state {
            PersonalizedResult(listOf(withCover, noCover), "", "", "ai")
        }
        st.loadIfNeeded(scope, context(), "u1")
        assertEquals(2, st.songs.size)
        assertEquals(listOf(withCover), st.visibleSongs)
    }
}
