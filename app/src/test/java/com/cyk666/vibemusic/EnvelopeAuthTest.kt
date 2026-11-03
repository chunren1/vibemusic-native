package com.cyk666.vibemusic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 信封级 401（HTTP 200 + body `code:401`）的契约（CODE-REVIEW-2026-10-28 API-3）。
 *
 * 为什么值得单独钉：后端的 401 有**两条**表达方式 —— 未认证走
 * `SecurityConfig` 的 entry point（HTTP 401），而 permitAll 路径里的
 * `PlayHistoryController` 直接 `return Result.error(401, "请先登录")`（**HTTP 200**）。
 * 如果只在解析阶段（authed 之外）发现 401，就会直接清盘登出，而不是先静默续期一次。
 */
@RunWith(RobolectricTestRunner::class)
class EnvelopeAuthTest {

    @Test
    fun envelope401_isTreatedAsAuthError() {
        try {
            VibeApi.throwIfEnvelopeAuth("""{"code":401,"message":"请先登录"}""")
            fail("code:401 must throw AuthException")
        } catch (_: AuthException) {
            // expected
        }
    }

    @Test
    fun otherCodesAndNonJsonPassThrough() {
        VibeApi.throwIfEnvelopeAuth("""{"code":200,"data":[]}""")
        VibeApi.throwIfEnvelopeAuth("""{"code":500,"message":"boom"}""")
        // 非 JSON 不该在这里制造假 401，交给下游解析报错
        VibeApi.throwIfEnvelopeAuth("not json at all")
    }

    /**
     * 机制断言：信封级 401 在 [VibeApi.authedWith] 内部抛出 → 先续期一次再重试，
     * 而不是把 401 直接抛给调用方（那会走登出）。
     */
    @Test
    fun envelope401_triggersSilentRefreshAndRetries() = runBlocking {
        var attempts = 0
        var refreshes = 0
        val out = VibeApi.authedWith(
            refresh = { _: String ->
                refreshes += 1
                RefreshOutcome.REFRESHED
            }
        ) {
            attempts += 1
            if (attempts == 1) {
                VibeApi.throwIfEnvelopeAuth("""{"code":401,"message":"请先登录"}""")
            }
            "ok"
        }
        assertEquals("ok", out)
        assertEquals(2, attempts)
        assertEquals(1, refreshes)
    }

    /**
     * 续期拿到的必须是**本次请求真正携带**的 token。
     *
     * 旧实现用 catch 时刻的 `AuthToken.token`：请求拿 token-A 发出去，期间并发续期
     * 已换成 token-B，这次旧 401 才到 —— 于是 `failed = token-B` 与当前值相同，
     * `shouldReuseRefreshedToken` 判成「新 token 也 401」，白刷一轮（后端轮转即作废
     * 旧 refresh，每次多余轮转都在放大失效面）。
     */
    @Test
    fun lateUnauthorized401ReportsTokenTheRequestCarried() = runBlocking {
        AuthToken.token = "token-A"
        var attempts = 0
        var seenFailed: String? = null
        val out = VibeApi.authedWith(
            refresh = { failed: String ->
                seenFailed = failed
                RefreshOutcome.REFRESHED
            }
        ) {
            attempts += 1
            if (attempts == 1) {
                // 模拟并发续期已经换了 token，随后这次旧 token 的 401 才到。
                AuthToken.token = "token-B"
                throw AuthException("密码错/登录过期，请重登 (HTTP 401)")
            }
            "ok"
        }
        assertEquals("ok", out)
        assertEquals(2, attempts)
        assertEquals("token-A", seenFailed)
    }
}
