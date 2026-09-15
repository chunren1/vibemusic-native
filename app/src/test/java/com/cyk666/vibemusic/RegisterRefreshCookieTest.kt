package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A1 回归：register() 必须像 login() 一样认领 Set-Cookie 下发的 VIBE_REFRESH，
 * 否则新注册用户 access 过期后即掉登录态（后端 /register 只走 Cookie，不回 body）。
 * register() 直连线上 BASE_URL，真机链路不可单测；此处用真实 okhttp3.Response
 * 复刻后端形状（token-only body + Set-Cookie），逐字覆盖 onResponse 里的认领表达式，
 * 再经 AuthStore.save/load 验证 KEY_REFRESH 落盘。
 */
@RunWith(RobolectricTestRunner::class)
class RegisterRefreshCookieTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    /** 真实后端形状：data 只有 token（无 refreshToken），凭据只在 Set-Cookie。 */
    private fun registerBody(token: String, refreshToken: String? = null): String {
        val data = JSONObject()
            .put("token", token)
            .put("username", "bob")
            .put("nickname", "Bob")
        if (refreshToken != null) data.put("refreshToken", refreshToken)
        return JSONObject().put("code", 200).put("data", data).toString()
    }

    /** 构造携带 Set-Cookie 的真实响应（login/register 共用的提取入口直测）。 */
    private fun responseWithCookies(vararg setCookies: String): Response {
        val req = Request.Builder()
            .url("https://vibe.cyk666.top/api/auth/register")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        val b = Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
        for (h in setCookies) b.addHeader("Set-Cookie", h)
        return b.body("{}".toResponseBody("application/json".toMediaType())).build()
    }

    /** 与 register() onResponse 内逐字一致的认领表达式。 */
    private fun adopt(base: LoginResult, ck: String): LoginResult =
        if (base.refreshToken.isBlank() && ck.isNotBlank()) base.copy(refreshToken = ck) else base

    @Test
    fun register_cookieOnlyRefresh_adoptedAndPersisted(): Unit = runBlocking {
        val res = responseWithCookies(
            "VIBE_REFRESH=x; Path=/; Max-Age=604800; HttpOnly; SameSite=Lax"
        )
        res.use {
            val base = VibeApi.parseRegister(registerBody("new-acc"))
            assertEquals("", base.refreshToken)
            val adopted = adopt(base, VibeApi.refreshCookieFromResponse(it))
            assertEquals("x", adopted.refreshToken)
            AuthStore.save(
                context(), adopted.token,
                adopted.user.username, adopted.user.nickname, adopted.refreshToken
            )
        }
        assertEquals("x", AuthStore.load(context()).refreshToken)
    }

    @Test
    fun register_bodyRefreshToken_staysAuthoritativeOverCookie() {
        responseWithCookies("VIBE_REFRESH=cookie-ref; Path=/; Max-Age=604800; HttpOnly").use {
            val adopted = adopt(
                VibeApi.parseRegister(registerBody("new-acc", "body-ref")),
                VibeApi.refreshCookieFromResponse(it)
            )
            assertEquals("body-ref", adopted.refreshToken)
        }
    }

    @Test
    fun register_noSetCookie_staysBlank() {
        responseWithCookies().use {
            val adopted = adopt(
                VibeApi.parseRegister(registerBody("new-acc")),
                VibeApi.refreshCookieFromResponse(it)
            )
            assertEquals("", adopted.refreshToken)
        }
    }
}
