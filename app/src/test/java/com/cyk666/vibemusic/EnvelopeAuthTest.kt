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
}
