package com.cyk666.vibemusic

import com.squareup.moshi.Moshi
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface VibeService {
    @GET("api/songs/search")
    fun search(
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): Call<ResponseBody>
}

data class SearchResult(val list: List<Song>, val total: Int)

data class LoggedInUser(
    val userId: String,
    val username: String,
    val nickname: String,
    val avatar: String,
    val bgImage: String = "",
    val gender: String = "",
    val birthday: String = ""
)

data class LoginResult(val token: String, val user: LoggedInUser, val refreshToken: String = "")

data class RefreshResult(val token: String, val refreshToken: String)

/** Pure: POST /api/auth/refresh body (refreshToken in body takes precedence server-side). */
fun buildRefreshBody(refreshToken: String): String =
    JSONObject().put("refreshToken", refreshToken).toString()

/**
 * Pure: a /me or login envelope whose data carries guest==true (boolean true,
 * string "true"/"1", or numeric 1) is NOT a user — backend /me without a
 * token returns {guest:true}. Callers treat guest as "not logged in".
 */
fun isGuestPayload(data: JSONObject): Boolean {
    if (data.isNull("guest")) return false
    return when (val g = data.opt("guest")) {
        is Boolean -> g
        is Number -> g.toInt() != 0
        is String -> g.equals("true", ignoreCase = true) || g == "1"
        else -> false
    }
}

/**
 * Pure: parse a POST /api/auth/refresh response. Returns null on any failure
 * (non-200 envelope, missing data/blank token, garbage/empty body).
 *
 * Null alone is NOT "re-login": only [isDecisiveRefreshRejection] decides
 * whether the null means the credential is dead (401) or the network blipped
 * (garbage/empty/5xx → keep tokens, retry later). See postRefresh.
 */
fun parseRefreshResult(json: String): RefreshResult? {
    return try {
        val root = JSONObject(json)
        if (root.optInt("code", -1) != 200) return null
        val data = root.optJSONObject("data") ?: return null
        val token = data.optString("token").ifBlank { data.optString("accessToken") }
        if (token.isBlank()) return null
        RefreshResult(token, data.optString("refreshToken"))
    } catch (_: Exception) {
        null
    }
}

/**
 * Pure: did the server DECISIVELY reject the refresh credential?
 *
 * Only an explicit 401 — envelope `code==401`, or HTTP 401 status — counts.
 * Everything else (garbage HTML from tunnel blips, empty body, unparseable
 * JSON, envelope 5xx / missing code, other HTTP statuses) is transient:
 * the 7-day refreshToken is probably still valid (backend is JWT-stateless;
 * restarts never invalidate it), so the caller must keep the tokens and let
 * the next authed call retry. A single network blip must never log the user
 * out.
 */
fun isDecisiveRefreshRejection(httpCode: Int, raw: String): Boolean {
    if (httpCode == 401) return true
    if (raw.isBlank()) return false
    return try {
        val root = JSONObject(raw)
        root.has("code") && root.optInt("code", -1) == 401
    } catch (_: Exception) {
        false
    }
}

/**
 * Pure single-flight re-check: a waiter queued behind an in-flight refresh
 * reuses the new access token when it differs from the one that just 401'd.
 */
fun shouldReuseRefreshedToken(currentToken: String, failedToken: String): Boolean =
    currentToken.isNotBlank() && currentToken != failedToken

data class LyricLine(val timeSec: Double, val text: String, val words: List<WordTimed>? = null)

data class Playlist(
    val id: String,
    val name: String,
    val coverUrl: String,
    val songCount: Int,
    // Optional metadata: the list endpoint may omit these (blank = hide in
    // UI, never invented). Parsed defensively in myPlaylists.
    val description: String = "",
    val creator: String = ""
)

class AuthException(message: String) : RuntimeException(message)

/**
 * Transient-connectivity auth failure: the access token 401'd but the
 * refresh POST never got a decisive answer (timeout/DNS/VPN blip), so the
 * stored 7-day refreshToken is probably still valid. Deliberately NOT an
 * AuthException subclass, so existing `catch (e: AuthException)` handlers
 * (which nuke tokens) can never catch it.
 */
class NetworkAuthException(message: String) : RuntimeException(message)

/**
 * Tri-state silent-refresh outcome. The old Boolean conflated "server said
 * the credential is dead" with "the network blipped", and every caller
 * treated both as "clear tokens" — a transient blip then permanently
 * destroyed a still-valid refreshToken (re-login after every bad-network
 * day / update-day cold start on flaky VPN).
 */
enum class RefreshOutcome {
    REFRESHED,
    INVALID_TOKEN,
    NETWORK_FAIL
}

/**
 * Pure policy: only truly-invalid credentials may nuke stored tokens.
 * Network blips (and anything else) keep them; the next authed call
 * retries the refresh naturally.
 */
fun shouldClearTokensOnFailure(t: Throwable): Boolean = t is AuthException

/** Refresh-credential cookie the backend sets/reads (HttpOnly, Path=/api/auth/refresh, 7d). */
internal const val REFRESH_COOKIE_NAME = "VIBE_REFRESH"

/**
 * Minimal in-memory CookieJar (~30 lines, no new dependency): stores
 * Set-Cookie across the process so the HttpOnly VIBE_REFRESH cookie — which
 * the backend NEVER echoes in a JSON body — is replayed on the refresh POST
 * (the backend reads the credential from Cookie only; Web reaches parity via
 * withCredentials). Matching/expiry semantics come from OkHttp's [Cookie]
 * ([matches] covers domain/path/secure); the jar only keeps the store
 * bounded (replace on same name+domain+path, never store expired, evict
 * expired on read).
 */
internal class InMemoryCookieJar : CookieJar {
    private val lock = Any()
    private val store = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            for (c in cookies) {
                store.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                if (c.expiresAt > now) store.add(c)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            store.removeAll { it.expiresAt <= now }
            return store.filter { it.matches(url) }
        }
    }

    /** Live value of a named cookie for [url] ("" when absent/expired/mismatched). */
    internal fun valueFor(url: HttpUrl, name: String): String =
        loadForRequest(url).firstOrNull { it.name == name }?.value.orEmpty()

    internal fun clear() {
        synchronized(lock) { store.clear() }
    }
}

private val HTTP_STATUS_IN_MESSAGE = Regex("HTTP\\s+(\\d{3})")

/**
 * Pure: translate raw network/HTTP failures (e.g. "Request failed:
 * connection closed" on VPN trickle bandwidth) into short Chinese
 * Snackbar copy. Anything unrecognized passes through untouched.
 */
fun friendlyNetworkMessage(t: Throwable): String {
    val parts = generateSequence(t as Throwable?) { it.cause }
        .map { it.message.orEmpty() + " " + it.javaClass.simpleName }
        .toList()
    val lower = parts.joinToString(" | ").lowercase()
    val offlineHints = listOf(
        "connection closed",
        "timeout",
        "timed out",
        "unable to resolve host",
        "unknownhostexception",
        "connectexception",
        "sockettimeoutexception",
        "network is unreachable",
        "no address associated",
        "ehostunreach"
    )
    if (offlineHints.any { it in lower }) return "网络连接断开，请检查网络"
    val code = HTTP_STATUS_IN_MESSAGE.find(parts.joinToString(" "))?.groupValues
        ?.getOrNull(1)?.toIntOrNull()
    if (code != null) {
        if (code in 500..599) return "服务器开小差，请稍后重试"
        if (code == 404) return "资源不存在，可能已下架"
    }
    return t.message ?: t.javaClass.simpleName
}

/**
 * Pure: build a failure toast that keeps the friendly Chinese copy but
 * appends the raw server/transport message in parentheses, so the next
 * failure is diagnosable from a screenshot. Blank or identical raw
 * messages add nothing (no duplicated text).
 */
fun diagnosableError(prefix: String, t: Throwable): String {
    val friendly = friendlyNetworkMessage(t)
    val raw = t.message.orEmpty().trim()
    return if (raw.isBlank() || raw == friendly) "$prefix: $friendly"
    else "$prefix: $friendly（$raw）"
}

object VibeApi {
    const val BASE_URL = "https://vibe.cyk666.top/"

    /**
     * Shared jar: login (okHttp) stores Set-Cookie, refresh (refreshHttp)
     * replays it. One instance across both clients — separate jars would
     * strand the login cookie where the refresh POST can never see it.
     * Necessary: documents why two clients must share one jar.
     */
    internal val cookieJar = InMemoryCookieJar()

    private val okHttp = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = AuthToken.token
            val req = if (token.isNotBlank()) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(req)
        }
        .build()

    val moshi: Moshi = Moshi.Builder().build()

    val service: VibeService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttp)
        .build()
        .create(VibeService::class.java)

    private val refreshHttp = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Live VIBE_REFRESH cookie value for [endpoint] ("" when the jar has none). */
    internal fun refreshCookieFor(endpoint: String): String {
        val url = endpoint.toHttpUrlOrNull() ?: return ""
        return cookieJar.valueFor(url, REFRESH_COOKIE_NAME)
    }

    /**
     * VIBE_REFRESH from THIS response's Set-Cookie headers (never stale jar
     * state). Used where the credential must be tied to the response at hand
     * (login enrich, refresh rotation adopt).
     */
    internal fun refreshCookieFromResponse(res: okhttp3.Response): String {
        var found = ""
        for (h in res.headers("Set-Cookie")) {
            val c = try {
                Cookie.parse(res.request.url, h)
            } catch (_: Exception) {
                null
            }
            if (c != null && c.name == REFRESH_COOKIE_NAME) found = c.value
        }
        return found
    }

    internal fun clearCookiesForTest() = cookieJar.clear()

    @Volatile
    private var appContext: android.content.Context? = null

    private val refreshMutex = Mutex()

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    suspend fun trySilentRefresh(failedToken: String): RefreshOutcome =
        trySilentRefreshWith(failedToken, BASE_URL + "api/auth/refresh", refreshHttp)

    /**
     * Endpoint/client are parameters (not globals) so unit tests can point
     * the refresh at a local stub server with zero new dependencies.
     */
    internal suspend fun trySilentRefreshWith(
        failedToken: String,
        endpoint: String,
        client: OkHttpClient
    ): RefreshOutcome {
        refreshMutex.withLock {
            if (shouldReuseRefreshedToken(AuthToken.token, failedToken)) return RefreshOutcome.REFRESHED
            val ctx = appContext ?: return RefreshOutcome.NETWORK_FAIL
            val rt = AuthToken.refreshToken
            // Cookie-only sessions (backend never sends a body refreshToken):
            // the jar cookie alone is a sufficient credential; only "neither
            // jar nor persisted value" is hopeless (probing without any
            // credential would 401 "缺少 refresh token" and misread as dead).
            val jarCookie = refreshCookieFor(endpoint)
            if (rt.isBlank() && jarCookie.isBlank()) return RefreshOutcome.NETWORK_FAIL
            val res = try {
                postRefresh(endpoint, client, rt.ifBlank { jarCookie })
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                return RefreshOutcome.NETWORK_FAIL
            } ?: return RefreshOutcome.INVALID_TOKEN
            if (res.token.isBlank()) return RefreshOutcome.NETWORK_FAIL
            AuthToken.token = res.token
            if (res.refreshToken.isNotBlank()) AuthToken.refreshToken = res.refreshToken
            return try {
                AuthStore.saveTokens(ctx, AuthToken.token, AuthToken.refreshToken)
                RefreshOutcome.REFRESHED
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                RefreshOutcome.NETWORK_FAIL
            }
        }
    }

    /**
     * POST the refresh grant. Returns the decoded pair, null ONLY when the
     * server decisively rejects the credential (envelope code==401 or HTTP
     * 401 → INVALID_TOKEN upstream), and THROWS on anything else that is not
     * a clean success: transport-level failures (IOException/timeout/DNS) as
     * well as garbage/empty/unparseable bodies and non-401 envelopes (tunnel
     * HTML blips, 5xx) all surface as NetworkAuthException → NETWORK_FAIL
     * upstream (tokens kept, next authed call retries).
     */
    private suspend fun postRefresh(
        endpoint: String,
        client: OkHttpClient,
        refreshToken: String
    ): RefreshResult? =
        suspendCoroutine { cont ->
            val builder = Request.Builder()
                .url(endpoint)
                .post(buildRefreshBody(refreshToken).toRequestBody("application/json; charset=utf-8".toMediaType()))
            // The backend reads the credential from Cookie only (body value
            // is ignored server-side). A jar-attached client replays the
            // live cookie automatically (and its value wins over this
            // header); the explicit header is the cold-start bridge — after
            // a process restart the in-memory jar is empty but AuthStore
            // still holds the persisted cookie value — and what makes
            // jar-less injected clients testable.
            if (refreshToken.isNotBlank()) {
                builder.header("Cookie", "$REFRESH_COOKIE_NAME=$refreshToken")
            }
            val req = builder.build()
            client.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    try {
                        cont.resumeWithException(e)
                    } catch (_: IllegalStateException) {
                    }
                }

                override fun onResponse(call: okhttp3.Call, res: okhttp3.Response) {
                    try {
                        res.use {
                            val raw = try {
                                it.body?.string().orEmpty()
                            } catch (e: java.io.IOException) {
                                throw e
                            }
                            val parsed = parseRefreshResult(raw)
                            if (parsed != null) {
                                // Rotation adopt: every refresh re-issues
                                // VIBE_REFRESH as Set-Cookie while the JSON
                                // body carries no refreshToken — persist the
                                // live cookie value so AuthStore keeps the
                                // CURRENT credential, not a stale echo.
                                // Body stays authoritative when present.
                                val rotated = refreshCookieFromResponse(it)
                                cont.resume(
                                    if (parsed.refreshToken.isBlank() && rotated.isNotBlank()) {
                                        parsed.copy(refreshToken = rotated)
                                    } else {
                                        parsed
                                    }
                                )
                            } else if (isDecisiveRefreshRejection(it.code, raw)) {
                                cont.resume(null)
                            } else {
                                cont.resumeWithException(
                                    NetworkAuthException("网络连接断开，登录态保留")
                                )
                            }
                        }
                    } catch (e: Exception) {
                        try {
                            if (e is java.io.IOException) cont.resumeWithException(e)
                            // 保活：非 IO 的意外异常（空体/脏数据/隧道 HTML 等）绝不能
                            // 当作"凭据死亡"(null→INVALID_TOKEN→清 token)；按网络抖动
                            // 处理，7 天 refreshToken 保留，下次鉴权调用自然重试。
                            else cont.resumeWithException(
                                NetworkAuthException("网络连接断开，登录态保留")
                            )
                        } catch (_: IllegalStateException) {
                        }
                    }
                }
            })
        }

    /**
     * Shared retry routing for every AuthException catch site: REFRESHED →
     * return so the caller retries once; INVALID_TOKEN → rethrow the
     * original; NETWORK_FAIL → degraded-but-logged-in (tokens + user kept,
     * next authed call retries naturally).
     */
    internal fun routeAfterRefresh(outcome: RefreshOutcome, original: AuthException) {
        when (outcome) {
            RefreshOutcome.REFRESHED -> return
            RefreshOutcome.INVALID_TOKEN -> throw original
            RefreshOutcome.NETWORK_FAIL ->
                throw NetworkAuthException("网络连接断开，登录态保留")
        }
    }

    private suspend fun <T> authed(block: suspend () -> T): T =
        authedWith(::trySilentRefresh, block)

    /**
     * authed 的可注入变体：生产路径传 ::trySilentRefresh；单测传入 stub
     * refresh，直接断言"401→续期→重试"的次数与顺序。重试语义与 authed
     * 完全一致（最多执行 block 两次，第二次再 401 直接抛给调用方）。
     */
    internal suspend fun <T> authedWith(
        refresh: suspend (String) -> RefreshOutcome,
        block: suspend () -> T
    ): T {
        try {
            return block()
        } catch (e: AuthException) {
            val failed = AuthToken.token
            val outcome = try {
                refresh(failed)
            } catch (e2: kotlinx.coroutines.CancellationException) {
                throw e2
            } catch (_: Exception) {
                RefreshOutcome.NETWORK_FAIL
            }
            routeAfterRefresh(outcome, e)
            return block()
        }
    }

    // Cancellable: coroutine cancellation cancels the in-flight HTTP call (web abort semantics).
    private suspend fun awaitCall(call: Call<ResponseBody>): String =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
            call.enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, res: Response<ResponseBody>) {
                    try {
                        if (!res.isSuccessful) {
                            // Retrofit never auto-closes errorBody(): shut it on every
                            // failure path or the connection leaks.
                            try {
                                res.errorBody()?.close()
                            } catch (_: Exception) {
                            }
                            if (res.code() == 401) {
                                cont.resumeWithException(
                                    AuthException("密码错/登录过期，请重登 (HTTP 401)")
                                )
                            } else {
                                cont.resumeWithException(
                                    RuntimeException("Request HTTP ${res.code()} ${res.message()}")
                                )
                            }
                            return
                        }
                        val body = res.body()?.string()
                            ?: throw RuntimeException("Request HTTP ${res.code()}: empty body")
                        cont.resume(body)
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    try {
                        cont.resumeWithException(
                            RuntimeException("Request failed: ${t.message ?: t.javaClass.simpleName}", t)
                        )
                    } catch (_: IllegalStateException) {
                    }
                }
            })
        }

    private fun checkEnvelope(root: JSONObject, what: String): JSONObject {
        val code = root.optInt("code", -1)
        if (code == 401) throw AuthException("密码错/登录过期，请重登")
        if (code != 200) {
            throw RuntimeException(
                "$what failed: code=$code ${root.optString("message", "unknown error")}"
            )
        }
        return root
    }

    private fun cleanUserStr(o: JSONObject, key: String): String {
        val v = o.optString(key)
        return if (v.isBlank() || v == "null") "" else v
    }

    private fun parseUser(o: JSONObject): LoggedInUser = LoggedInUser(
        userId = o.opt("userId")?.toString().orEmpty().takeIf { it != "null" }.orEmpty(),
        username = cleanUserStr(o, "username"),
        nickname = cleanUserStr(o, "nickname").ifBlank { cleanUserStr(o, "username") },
        avatar = cleanUserStr(o, "avatar"),
        bgImage = cleanUserStr(o, "bgImage"),
        gender = cleanUserStr(o, "gender"),
        birthday = cleanUserStr(o, "birthday")
    )

    private fun jsonId(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            if (!o.has(k) || o.isNull(k)) continue
            val v = o.opt(k)?.toString().orEmpty()
            if (v.isNotBlank() && v != "null") return v
        }
        return ""
    }

    // ---- search (guest untouched: no token -> no header) ----

    suspend fun search(keyword: String, page: Int = 1, size: Int = 20): SearchResult {
        val body = awaitCall(service.search(keyword, page, size))
        return parseSearch(body)
    }

    fun parseSearch(json: String): SearchResult {
        val root = JSONObject(json)
        checkEnvelope(root, "Search")
        val data = root.optJSONObject("data") ?: throw RuntimeException("Search: missing data")
        val arr = data.optJSONArray("list")
        val out = ArrayList<Song>(arr?.length() ?: 0)
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    Song(
                        sourceId = o.optString("sourceId"),
                        name = o.optString("name"),
                        artist = o.optString("artist"),
                        album = o.optString("album"),
                        coverUrl = o.optString("coverUrl"),
                        durationSec = o.optInt("duration", o.optInt("durationSec", 0)),
                        platform = o.optString("platform"),
                        vip = parseVipFlag(o)
                    )
                )
            }
        }
        return SearchResult(out, data.optInt("total", out.size))
    }

    // ---- auth (raw OkHttp so POST JSON stays dependency-free) ----

    private suspend fun rawGet(path: String): String = authed { rawGetInner(path) }

    private suspend fun rawPost(path: String, jsonBody: String): String =
        authed { rawPostInner(path, jsonBody) }

    private suspend fun rawDelete(path: String): String = authed { rawDeleteInner(path) }

    private suspend fun rawPut(path: String, jsonBody: String): String =
        authed { rawPutInner(path, jsonBody) }

    private suspend fun rawPutInner(path: String, jsonBody: String): String =
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder()
                .url(BASE_URL + path)
                .put(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val call = okHttp.newCall(req)
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    try {
                        cont.resumeWithException(
                            RuntimeException("Request failed: ${e.message ?: e.javaClass.simpleName}", e)
                        )
                    } catch (_: IllegalStateException) {
                    }
                }

                override fun onResponse(call: okhttp3.Call, res: okhttp3.Response) {
                    try {
                        res.use {
                            if (!it.isSuccessful) {
                                if (it.code == 401) {
                                    cont.resumeWithException(
                                        AuthException("密码错/登录过期，请重登 (HTTP 401)")
                                    )
                                } else {
                                    val msg = try {
                                        JSONObject(it.body?.string().orEmpty())
                                            .optString("message", it.message)
                                    } catch (_: Exception) {
                                        it.message
                                    }
                                    cont.resumeWithException(
                                        RuntimeException("Request HTTP ${it.code} $msg")
                                    )
                                }
                                return
                            }
                            cont.resume(it.body?.string() ?: throw RuntimeException("Empty body"))
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }

    private suspend fun rawGetInner(path: String): String =
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder().url(BASE_URL + path).get().build()
            val call = okHttp.newCall(req)
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    try {
                        cont.resumeWithException(
                            RuntimeException("Request failed: ${e.message ?: e.javaClass.simpleName}", e)
                        )
                    } catch (_: IllegalStateException) {
                    }
                }

                override fun onResponse(call: okhttp3.Call, res: okhttp3.Response) {
                    try {
                        res.use {
                            if (!it.isSuccessful) {
                                if (it.code == 401) {
                                    cont.resumeWithException(
                                        AuthException("密码错/登录过期，请重登 (HTTP 401)")
                                    )
                                } else {
                                    cont.resumeWithException(
                                        RuntimeException("Request HTTP ${it.code} ${it.message}")
                                    )
                                }
                                return
                            }
                            cont.resume(it.body?.string() ?: throw RuntimeException("Empty body"))
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }

    private suspend fun rawPostInner(path: String, jsonBody: String): String =
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder()
                .url(BASE_URL + path)
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val call = okHttp.newCall(req)
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    try {
                        cont.resumeWithException(
                            RuntimeException("Request failed: ${e.message ?: e.javaClass.simpleName}", e)
                        )
                    } catch (_: IllegalStateException) {
                    }
                }

                override fun onResponse(call: okhttp3.Call, res: okhttp3.Response) {
                    try {
                        res.use {
                            if (!it.isSuccessful) {
                                if (it.code == 401) {
                                    cont.resumeWithException(
                                        AuthException("密码错/登录过期，请重登 (HTTP 401)")
                                    )
                                } else {
                                    val msg = try {
                                        JSONObject(it.body?.string().orEmpty())
                                            .optString("message", it.message)
                                    } catch (_: Exception) {
                                        it.message
                                    }
                                    cont.resumeWithException(
                                        RuntimeException("Request HTTP ${it.code} $msg")
                                    )
                                }
                                return
                            }
                            cont.resume(it.body?.string() ?: throw RuntimeException("Empty body"))
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }

    private suspend fun rawDeleteInner(path: String): String =
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder().url(BASE_URL + path).delete().build()
            val call = okHttp.newCall(req)
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    try {
                        cont.resumeWithException(
                            RuntimeException("Request failed: ${e.message ?: e.javaClass.simpleName}", e)
                        )
                    } catch (_: IllegalStateException) {
                    }
                }

                override fun onResponse(call: okhttp3.Call, res: okhttp3.Response) {
                    try {
                        res.use {
                            if (!it.isSuccessful) {
                                if (it.code == 401) {
                                    cont.resumeWithException(
                                        AuthException("密码错/登录过期，请重登 (HTTP 401)")
                                    )
                                } else {
                                    val msg = try {
                                        JSONObject(it.body?.string().orEmpty())
                                            .optString("message", it.message)
                                    } catch (_: Exception) {
                                        it.message
                                    }
                                    cont.resumeWithException(
                                        RuntimeException("Request HTTP ${it.code} $msg")
                                    )
                                }
                                return
                            }
                            cont.resume(it.body?.string() ?: throw RuntimeException("Empty body"))
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }

    suspend fun login(username: String, password: String): LoginResult =
        suspendCoroutine { cont ->
            val json = JSONObject()
                .put("username", username)
                .put("password", password)
                .toString()
            val req = Request.Builder()
                .url(BASE_URL + "api/auth/login")
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            okHttp.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    try {
                        cont.resumeWithException(
                            RuntimeException("Login failed: ${e.message ?: e.javaClass.simpleName}", e)
                        )
                    } catch (_: IllegalStateException) {
                    }
                }

                override fun onResponse(call: okhttp3.Call, res: okhttp3.Response) {
                    try {
                        res.use {
                            val body = it.body?.string().orEmpty()
                            if (!it.isSuccessful) {
                                if (it.code == 401) {
                                    cont.resumeWithException(AuthException("密码错/登录过期，请重登 (HTTP 401)"))
                                } else {
                                    val msg = try {
                                        JSONObject(body).optString("message", it.message)
                                    } catch (_: Exception) {
                                        it.message
                                    }
                                    cont.resumeWithException(
                                        RuntimeException("Login failed: HTTP ${it.code} $msg")
                                    )
                                }
                                return
                            }
                            try {
                                val base = parseLogin(body)
                                // Cookie-only credential: the login JSON body
                                // never carries refreshToken (HttpOnly
                                // VIBE_REFRESH Set-Cookie only). Adopt THIS
                                // response's cookie value so the existing
                                // AuthStore.save(..., refreshToken) call in
                                // the caller persists it for cold-start
                                // renew. Body stays authoritative when
                                // present; parseLogin itself is untouched.
                                val ck = refreshCookieFromResponse(it)
                                cont.resume(
                                    if (base.refreshToken.isBlank() && ck.isNotBlank()) {
                                        base.copy(refreshToken = ck)
                                    } else {
                                        base
                                    }
                                )
                            } catch (e: Exception) {
                                cont.resumeWithException(e)
                            }
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }

    fun parseLogin(json: String): LoginResult {
        val root = JSONObject(json)
        checkEnvelope(root, "Login")
        val data = root.optJSONObject("data") ?: throw RuntimeException("Login: missing data")
        if (isGuestPayload(data)) throw RuntimeException("Login: guest response is not a user")
        val token = data.optString("token")
            .ifBlank { data.optString("accessToken") }
        if (token.isBlank()) throw RuntimeException("Login: missing token in response")
        return LoginResult(token, parseUser(data), data.optString("refreshToken"))
    }

    /**
     * Pure: parse GET /api/auth/me. Returns null for guest envelopes
     * ({guest:true} when no token) — null means "not logged in", never a
     * blank user. Callers must NOT clear stored tokens on null (transient
     * guest must not nuke a saved token); only 401/AuthException clears.
     */
    fun parseMe(json: String): LoggedInUser? {
        val root = JSONObject(json)
        checkEnvelope(root, "Auth check")
        val data = root.optJSONObject("data") ?: throw RuntimeException("Auth check: missing data")
        if (isGuestPayload(data)) return null
        return parseUser(data)
    }

    suspend fun me(): LoggedInUser? = authed {
        // parseMe rides INSIDE the authed try (isomorphic with
        // toggleFavoriteWith/uploadImage): transport 401 AND envelope 401
        // both route through one silent-renew-then-decide — renew ok retries
        // once, decisive INVALID_TOKEN rethrows for the caller to clear.
        // Guest-null still returns null without any retry or clearing.
        parseMe(rawGetInner("api/auth/me"))
    }

    // ---- playlists (auth required; parse defensively) ----

    suspend fun myPlaylists(): List<Playlist> {
        val body = rawGet("api/playlists/list")
        val root = JSONObject(body)
        checkEnvelope(root, "Playlists")
        val arr: JSONArray = root.optJSONArray("data")
            ?: root.optJSONObject("data")?.optJSONArray("list")
            ?: JSONArray()
        val out = ArrayList<Playlist>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Playlist(
                    id = jsonId(o, "id", "playlistId"),
                    name = o.optString("name").ifBlank { o.optString("title", "(untitled)") },
                    coverUrl = o.optString("coverUrl").ifBlank { o.optString("cover", "") },
                    songCount = o.optInt("songCount", o.optInt("count", o.optInt("total", 0))),
                    description = o.optString("description").ifBlank { o.optString("desc", "") },
                    creator = o.optString("creator")
                        .ifBlank { o.optString("creatorName") }
                        .ifBlank { o.optString("nickname") }
                        .ifBlank { o.optString("username") }
                )
            )
        }
        return out
    }

    // ---- lyrics (guest OK; empty list = no lyrics) ----

    suspend fun lyric(sourceId: String): List<LyricLine> {
        val body = rawGet("api/songs/lyric?sourceId=$sourceId")
        // 解析是纯 CPU 计算：切到 Default，调用方 Main（LaunchedEffect）不阻塞。
        return withContext(Dispatchers.Default) {
            parseLyricBody(body)
        }
    }

    /** Pure lyric 信封解析（lyric() 经 Dispatchers.Default 调用；单测可直调）。 */
    fun parseLyricBody(json: String): List<LyricLine> {
        val root = JSONObject(json)
        checkEnvelope(root, "Lyric")
        val arr: JSONArray = root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<LyricLine>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val t = when (val v = o.opt("time")) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: continue
                else -> continue
            }
            val rawText = o.optString("text")
            val cleaned = stripLyricNoise(rawText)
            if (isCreditLine(cleaned)) continue
            // Word timing is optional: "words" array wins; else inline
            // <mm:ss.xx> tags inside the text; else null → Mode B fallback.
            val tagged = parseWordsJson(o.optJSONArray("words"))
            val inline = if (tagged == null) {
                parseEnhancedLrcLine(cleaned).ifEmpty { null }
            } else {
                null
            }
            val words = tagged ?: inline
            val plain = if (inline != null) stripInlineTags(cleaned) else cleaned
            out.add(LyricLine(timeSec = t, text = plain, words = words))
        }
        out.sortBy { it.timeSec }
        return out
    }

    suspend fun playlistSongs(playlistId: String): List<Song> {
        val body = rawGet("api/playlists/songs?playlistId=$playlistId")
        return parsePlaylistSongs(body)
    }

    fun parsePlaylistSongs(json: String): List<Song> {
        val root = JSONObject(json)
        checkEnvelope(root, "Playlist songs")
        val arr: JSONArray = root.optJSONArray("data")
            ?: root.optJSONObject("data")?.optJSONArray("list")
            ?: JSONArray()
        val out = ArrayList<Song>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(parsePlaylistSong(o))
        }
        return out
    }

    // Phase 4 final contract: getSongs item =
    // {sourceId, name, songName(legacy), artist, album?, coverUrl, duration, platform, addedAt}.
    // Mapping rules: name = name -> songName -> title (never null, may be blank;
    // UI renders blank as "(untitled)"); platform = platform fallback "netease"
    // (never blank — streamUrl requires it).
    fun parsePlaylistSong(o: JSONObject): Song = Song(
        sourceId = o.optString("sourceId").ifBlank { jsonId(o, "id", "songId") },
        name = o.optString("name")
            .ifBlank { o.optString("songName") }
            .ifBlank { o.optString("title") },
        artist = o.optString("artist").ifBlank { o.optString("singer", "") },
        album = o.optString("album"),
        coverUrl = o.optString("coverUrl").ifBlank { o.optString("cover", "") },
        durationSec = o.optInt("duration", o.optInt("durationSec", 0)),
        platform = o.optString("platform").ifBlank { "netease" },
        vip = parseVipFlag(o)
    )

    // VIP flag parsing (pure): true only when the backend explicitly marks
    // the song — boolean vip/paywall, numeric fee==1, or string "1"/"true".
    // Absent/unknown shapes stay false (never invented).
    fun parseVipFlag(o: JSONObject): Boolean {
        if (o.optBoolean("vip", false)) return true
        if (o.optBoolean("paywall", false)) return true
        val fee = o.opt("fee")
        if (fee is Number && fee.toInt() == 1) return true
        if (fee is String && (fee == "1" || fee.equals("true", ignoreCase = true))) return true
        return false
    }

    // ---- Phase 6: playlist management (all auth-required) ----

    data class CreateResult(val id: String, val name: String, val duplicate: Boolean = false)

    data class ImportResult(val imported: Int, val total: Int, val name: String)

    /** Pure builder: POST /api/playlists/create body. */
    fun buildCreateBody(name: String, description: String? = null, coverUrl: String? = null): String {
        val o = JSONObject().put("name", name)
        if (description != null) o.put("description", description)
        if (coverUrl != null) o.put("coverUrl", coverUrl)
        return o.toString()
    }

    /** Pure builder: POST /api/playlists/update body (only provided fields included). */
    fun buildUpdateBody(
        playlistId: String,
        name: String? = null,
        description: String? = null,
        coverUrl: String? = null
    ): String {
        val o = JSONObject()
        o.put("playlistId", playlistId.toLongOrNull() ?: playlistId)
        if (name != null) o.put("name", name)
        if (description != null) o.put("description", description)
        if (coverUrl != null) o.put("coverUrl", coverUrl)
        return o.toString()
    }

    /**
     * Pure builder: POST /api/playlists/reorder body.
     * Reorders PLAYLISTS (not songs): idsInOrder[0] gets sortOrder 0, etc.
     * playlistId is emitted numeric when parseable (backend only accepts numbers).
     */
    fun buildReorderBody(idsInOrder: List<String>): String {
        val arr = JSONArray()
        idsInOrder.forEachIndexed { index, id ->
            arr.put(
                JSONObject()
                    .put("playlistId", id.toLongOrNull() ?: id)
                    .put("sortOrder", index)
            )
        }
        return JSONObject().put("order", arr).toString()
    }

    private val PLAYLIST_ID_PARAM = Regex("[?&]id=(\\d+)")

    /** Pure: accept a pasted playlist link or bare numeric id, return the id or "". */
    fun extractPlaylistId(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return ""
        PLAYLIST_ID_PARAM.find(t)?.let { return it.groupValues[1] }
        return if (t.all { it.isDigit() }) t else ""
    }

    /** Pure builder: POST /api/playlists/delete-batch body (numeric ids only). */
    fun buildDeleteBatchBody(ids: List<String>): String {
        val arr = JSONArray()
        ids.mapNotNull { it.toLongOrNull() }.forEach { arr.put(it) }
        return JSONObject().put("ids", arr).toString()
    }

    /** Pure builder: POST /api/playlists/add-song body with full Song fields. */
    fun buildAddSongBody(playlistId: String, song: Song): String {
        return JSONObject()
            .put("playlistId", playlistId.toLongOrNull() ?: playlistId)
            .put("sourceId", song.sourceId)
            .put("songName", song.name)
            .put("artist", song.artist)
            .put("coverUrl", song.coverUrl)
            .put("duration", song.durationSec)
            .put("platform", song.platform.ifBlank { "netease" })
            .toString()
    }

    /** Pure builder: POST /api/playlists/import body. */
    fun buildImportBody(source: String, id: String): String {
        val num = id.toLongOrNull()
        return JSONObject()
            .put("source", source)
            .put("id", num ?: id)
            .toString()
    }

    fun parseCreateResult(json: String): CreateResult {
        val root = JSONObject(json)
        checkEnvelope(root, "Create playlist")
        val data = root.optJSONObject("data")
            ?: throw RuntimeException("Create playlist: missing data")
        return CreateResult(
            id = jsonId(data, "id", "playlistId"),
            name = data.optString("name"),
            duplicate = data.optBoolean("duplicate", false)
        )
    }

    fun parseDeleteBatchCount(json: String): Int {
        val root = JSONObject(json)
        checkEnvelope(root, "Delete playlists")
        return when (val d = root.opt("data")) {
            is Number -> d.toInt()
            else -> throw RuntimeException("Delete playlists: missing count")
        }
    }

    fun parseImportResult(json: String): ImportResult {
        val root = JSONObject(json)
        checkEnvelope(root, "Import playlist")
        val data = root.optJSONObject("data")
            ?: throw RuntimeException("Import playlist: missing data")
        return ImportResult(
            imported = data.optInt("imported", 0),
            total = data.optInt("total", data.optInt("imported", 0)),
            name = data.optString("name")
        )
    }

    /** Backend dedups silently: data=false means already in playlist (no throw). */
    fun parseAddSongResult(json: String): Boolean {
        val root = JSONObject(json)
        checkEnvelope(root, "Add song")
        if (!root.has("data") || root.isNull("data")) return true
        return when (val d = root.opt("data")) {
            is Boolean -> d
            is Number -> d.toInt() != 0
            else -> true
        }
    }

    suspend fun createPlaylist(name: String, description: String?): CreateResult {
        val body = rawPost("api/playlists/create", buildCreateBody(name, description, null))
        return parseCreateResult(body)
    }

    suspend fun deletePlaylist(playlistId: String) {
        val body = rawDelete("api/playlists/delete?playlistId=$playlistId")
        checkEnvelope(JSONObject(body), "Delete playlist")
    }

    suspend fun deletePlaylists(ids: List<String>): Int {
        val body = rawPost("api/playlists/delete-batch", buildDeleteBatchBody(ids))
        return parseDeleteBatchCount(body)
    }

    suspend fun updatePlaylist(
        playlistId: String,
        name: String? = null,
        description: String? = null,
        coverUrl: String? = null
    ) {
        val body = rawPost(
            "api/playlists/update",
            buildUpdateBody(playlistId, name, description, coverUrl)
        )
        checkEnvelope(JSONObject(body), "Update playlist")
    }

    suspend fun reorderPlaylists(idsInOrder: List<String>) {
        val body = rawPost("api/playlists/reorder", buildReorderBody(idsInOrder))
        checkEnvelope(JSONObject(body), "Reorder playlists")
    }

    /** Returns true=added, false=already in playlist (backend dedups silently). */
    suspend fun addSongToPlaylist(playlistId: String, song: Song): Boolean {
        val body = rawPost("api/playlists/add-song", buildAddSongBody(playlistId, song))
        return parseAddSongResult(body)
    }

    suspend fun removeSongFromPlaylist(playlistId: String, sourceId: String) {
        val enc = java.net.URLEncoder.encode(sourceId, "UTF-8")
        val body = rawDelete("api/playlists/remove-song?playlistId=$playlistId&sourceId=$enc")
        checkEnvelope(JSONObject(body), "Remove song")
    }

    suspend fun importPlaylist(source: String, id: String): ImportResult {
        val body = rawPost("api/playlists/import", buildImportBody(source, id))
        return parseImportResult(body)
    }

    // ---- Phase 9: discover (all GETs are public server-side; authed client
    // sends Bearer when logged in so personalized is auth-aware, guest OK) ----

    suspend fun discoverBanners(): List<DiscoverBanner> {
        val body = rawGet("api/songs/banner")
        return parseDiscoverBanners(body)
    }

    suspend fun personalized(refresh: Boolean = false): PersonalizedResult {
        val body = rawGet(buildPersonalizedPath(refresh))
        return parsePersonalized(body)
    }

    suspend fun randomSongs(count: Int = 8): List<Song> {
        val body = rawGet(buildRandomPath(count))
        return parseRandomSongs(body)
    }

    suspend fun recommendPlaylists(): List<RecommendPlaylist> {
        val body = rawGet("api/playlists/recommend")
        return parseRecommendPlaylists(body)
    }

    // ---- Phase 8: account + favorites + history ----

    /** Multipart part name for POST /api/auth/avatar + /api/auth/bg-image. */
    const val AVATAR_PART_NAME = "file"

    /** Idempotency header for POST /api/favorites/toggle (5-min replay guard). */
    const val FAV_IDEMPOTENCY_HEADER = "X-Request-Id"

    /** Pure builder: POST /api/auth/register body (nickname omitted when blank). */
    fun buildRegisterBody(username: String, password: String, nickname: String? = null): String {
        val o = JSONObject()
            .put("username", username)
            .put("password", password)
        if (!nickname.isNullOrBlank()) o.put("nickname", nickname)
        return o.toString()
    }

    /** Register returns the same envelope shape as login (token + refreshToken + user). */
    fun parseRegister(json: String): LoginResult = parseLogin(json)

    /** Pure builder: POST /api/auth/change-password body. */
    fun buildChangePasswordBody(oldPassword: String, newPassword: String): String =
        JSONObject()
            .put("oldPassword", oldPassword)
            .put("newPassword", newPassword)
            .toString()

    /** Pure builder: PUT /api/auth/profile body (only provided fields included). */
    fun buildProfileBody(
        nickname: String? = null,
        gender: String? = null,
        birthday: String? = null
    ): String {
        val o = JSONObject()
        if (nickname != null) o.put("nickname", nickname)
        if (gender != null) o.put("gender", gender)
        if (birthday != null) o.put("birthday", birthday)
        return o.toString()
    }

    /** Pure builder: POST /api/favorites/toggle body. */
    fun buildToggleBody(song: Song): String =
        JSONObject()
            .put("sourceId", song.sourceId)
            .put("songName", song.name)
            .put("artist", song.artist)
            .put("coverUrl", song.coverUrl)
            .toString()

    /** Pure: fresh idempotency key per tap. */
    fun newRequestId(): String = java.util.UUID.randomUUID().toString()

    /**
     * Pure: build the raw OkHttp toggle request (header + JSON body) so tests
     * can assert the idempotency header without a server. The suspend
     * toggleFavorite() below sends exactly this request through the authed
     * client (Bearer + silent-refresh retry).
     */
    fun buildToggleRequest(song: Song, requestId: String): okhttp3.Request =
        okhttp3.Request.Builder()
            .url(BASE_URL + "api/favorites/toggle")
            .header(FAV_IDEMPOTENCY_HEADER, requestId)
            .post(buildToggleBody(song).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

    /** data Boolean = new fav state (server is source of truth, never flip locally). */
    fun parseToggleResult(json: String): Boolean {
        val root = JSONObject(json)
        checkEnvelope(root, "Toggle favorite")
        if (root.isNull("data")) return false
        return when (val d = root.opt("data")) {
            is Boolean -> d
            is Number -> d.toInt() != 0
            is String -> d.equals("true", ignoreCase = true)
            else -> throw RuntimeException("Toggle favorite: unexpected data type")
        }
    }

    /** GET /api/favorites/ids → data [sourceId...]; match by sourceId only. */
    fun parseFavIds(json: String): Set<String> {
        val root = JSONObject(json)
        checkEnvelope(root, "Favorite ids")
        val arr: JSONArray = root.optJSONArray("data") ?: return emptySet()
        val out = HashSet<String>(arr.length())
        for (i in 0 until arr.length()) {
            val id = arr.optString(i).orEmpty()
            if (id.isNotBlank()) out.add(id)
        }
        return out
    }

    /** GET /api/favorites/list → data song maps (same defensive mapping as playlists). */
    fun parseFavList(json: String): List<Song> {
        val root = JSONObject(json)
        checkEnvelope(root, "Favorites")
        val arr: JSONArray = root.optJSONArray("data")
            ?: root.optJSONObject("data")?.optJSONArray("list")
            ?: JSONArray()
        val out = ArrayList<Song>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(parsePlaylistSong(o))
        }
        return out
    }

    /** Pure builder: POST /api/favorites/remove-batch body. */
    fun buildFavRemoveBatchBody(sourceIds: List<String>): String {
        val arr = JSONArray()
        sourceIds.forEach { arr.put(it) }
        return JSONObject().put("sourceIds", arr).toString()
    }

    data class HistoryItem(val song: Song, val playedAt: String)

    /** GET /api/songs/history?count=N → data list (songName-shaped maps + playedAt). */
    fun parseHistoryList(json: String): List<HistoryItem> {
        val root = JSONObject(json)
        checkEnvelope(root, "History")
        val arr: JSONArray = root.optJSONArray("data")
            ?: root.optJSONObject("data")?.optJSONArray("list")
            ?: JSONArray()
        val out = ArrayList<HistoryItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(HistoryItem(parsePlaylistSong(o), o.optString("playedAt")))
        }
        return out
    }

    /** Pure builder: POST /api/songs/history/remove body. */
    fun buildHistoryRemoveBody(sourceIds: List<String>): String {
        val arr = JSONArray()
        sourceIds.forEach { arr.put(it) }
        return JSONObject().put("sourceIds", arr).toString()
    }

    /** Pure: GET /api/songs/play report path (fire-and-forget on song start). */
    fun buildPlayReportPath(song: Song): String {
        fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
        return "api/songs/play?sourceId=${enc(song.sourceId)}" +
            "&name=${enc(song.name)}" +
            "&artist=${enc(song.artist)}" +
            "&coverUrl=${enc(song.coverUrl)}"
    }

    /**
     * Pure report rule: logged-in + non-blank start key + key differs from the
     * last reported key (seek-restart of the same song must not double-report).
     */
    fun shouldReportPlay(loggedIn: Boolean, songStartKey: String, lastReportedKey: String?): Boolean =
        loggedIn && songStartKey.isNotBlank() && songStartKey != lastReportedKey

    data class AvatarUploadResult(val url: String, val user: LoggedInUser?)

    /**
     * UploadController returns the full user map + avatarUrl/bgImageUrl keys
     * (NOT a bare url string) — parse defensively for both endpoints.
     */
    fun parseAvatarResult(json: String, bg: Boolean = false): AvatarUploadResult {
        val root = JSONObject(json)
        checkEnvelope(root, if (bg) "Upload bg-image" else "Upload avatar")
        val data = root.optJSONObject("data")
            ?: throw RuntimeException("Upload avatar: missing data")
        val url = when {
            bg -> data.optString("bgImageUrl").ifBlank { data.optString("bgImage") }
            else -> data.optString("avatarUrl").ifBlank { data.optString("avatar") }
        }
        if (url.isBlank()) throw RuntimeException("Upload avatar: missing url in response")
        val user = try {
            parseUser(data)
        } catch (_: Exception) {
            null
        }
        return AvatarUploadResult(url, user)
    }

    suspend fun register(username: String, password: String, nickname: String?): LoginResult =
        suspendCoroutine { cont ->
            val req = Request.Builder()
                .url(BASE_URL + "api/auth/register")
                .post(
                    buildRegisterBody(username, password, nickname)
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .build()
            // No Bearer on register (no token yet); no silent-refresh retry.
            okHttp.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    try {
                        cont.resumeWithException(
                            RuntimeException("Register failed: ${e.message ?: e.javaClass.simpleName}", e)
                        )
                    } catch (_: IllegalStateException) {
                    }
                }

                override fun onResponse(call: okhttp3.Call, res: okhttp3.Response) {
                    try {
                        res.use {
                            val body = it.body?.string().orEmpty()
                            if (!it.isSuccessful) {
                                val msg = try {
                                    JSONObject(body).optString("message", it.message)
                                } catch (_: Exception) {
                                    it.message
                                }
                                cont.resumeWithException(
                                    RuntimeException("Register failed: HTTP ${it.code} $msg")
                                )
                                return
                            }
                            try {
                                val base = parseRegister(body)
                                // Cookie-only credential: the register JSON body
                                // never carries refreshToken (HttpOnly
                                // VIBE_REFRESH Set-Cookie only). Adopt THIS
                                // response's cookie value so the existing
                                // AuthStore.save(..., refreshToken) call in
                                // the caller persists it for cold-start
                                // renew. Body stays authoritative when
                                // present; parseRegister itself is untouched.
                                val ck = refreshCookieFromResponse(it)
                                cont.resume(
                                    if (base.refreshToken.isBlank() && ck.isNotBlank()) {
                                        base.copy(refreshToken = ck)
                                    } else {
                                        base
                                    }
                                )
                            } catch (e: Exception) {
                                cont.resumeWithException(e)
                            }
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val body = rawPost("api/auth/change-password", buildChangePasswordBody(oldPassword, newPassword))
        checkEnvelope(JSONObject(body), "Change password")
    }

    suspend fun updateProfile(
        nickname: String? = null,
        gender: String? = null,
        birthday: String? = null
    ): LoggedInUser {
        val body = rawPut("api/auth/profile", buildProfileBody(nickname, gender, birthday))
        val root = JSONObject(body)
        checkEnvelope(root, "Update profile")
        val data = root.optJSONObject("data") ?: throw RuntimeException("Update profile: missing data")
        return parseUser(data)
    }

    // ---- Task 8: BYOC NetEase cookie bind (all auth-required; nothing is
    // persisted locally — the cookie lives server-side only, AuthStore
    // untouched; request bodies are never logged) ----

    /** Pure builder: PUT /api/cookies/netease body. */
    fun buildSaveCookieBody(cookie: String): String =
        JSONObject().put("cookie", cookie).toString()

    data class CookieStatus(val has: Boolean, val valid: Boolean, val needsRebind: Boolean)

    /**
     * Pure: parse GET /api/cookies/status envelope
     * ({netease:{has,valid,updatedAt}, bili:{reserved:true}}, plus
     * needsRebind when invalid). needsRebind is read explicitly from data
     * or data.netease when present, else derived as has && !valid (an
     * invalid bound cookie always wants a re-bind). 401 routes to
     * AuthException via checkEnvelope like every other authed parser.
     */
    fun parseCookieStatus(json: String): CookieStatus {
        val root = JSONObject(json)
        checkEnvelope(root, "Cookie status")
        val data = root.optJSONObject("data")
            ?: throw RuntimeException("Cookie status: missing data")
        val ne = data.optJSONObject("netease")
        val has = ne?.optBoolean("has", false) ?: data.optBoolean("has", false)
        val valid = ne?.optBoolean("valid", false) ?: data.optBoolean("valid", false)
        val needsRebind = when {
            data.has("needsRebind") -> data.optBoolean("needsRebind")
            ne != null && ne.has("needsRebind") -> ne.optBoolean("needsRebind")
            else -> (has && !valid)
        }
        return CookieStatus(has, valid, needsRebind)
    }

    /**
     * PUT /api/cookies/netease. Returns the server bound flag
     * (data.bound, defaults true on a 200 without one).
     */
    suspend fun saveNeteaseCookie(cookie: String): Boolean {
        val body = rawPut("api/cookies/netease", buildSaveCookieBody(cookie))
        val root = JSONObject(body)
        checkEnvelope(root, "Save cookie")
        return when (val d = root.opt("data")) {
            is Boolean -> d
            is JSONObject -> d.optBoolean("bound", true)
            else -> true
        }
    }

    /** DELETE /api/cookies/netease. */
    suspend fun deleteNeteaseCookie() {
        val body = rawDelete("api/cookies/netease")
        checkEnvelope(JSONObject(body), "Delete cookie")
    }

    /** GET /api/cookies/status. */
    suspend fun cookieStatus(): CookieStatus {
        val body = rawGet("api/cookies/status")
        return parseCookieStatus(body)
    }

    private suspend fun uploadImage(
        path: String,
        bytes: ByteArray,
        filename: String,
        contentType: String,
        bg: Boolean
    ): AvatarUploadResult = try {
        uploadImageOnce(path, bytes, filename, contentType, bg)
    } catch (e: AuthException) {
        val failed = AuthToken.token
        val outcome = try {
            trySilentRefresh(failed)
        } catch (e2: kotlinx.coroutines.CancellationException) {
            throw e2
        } catch (_: Exception) {
            RefreshOutcome.NETWORK_FAIL
        }
        routeAfterRefresh(outcome, e)
        uploadImageOnce(path, bytes, filename, contentType, bg)
    }

    private suspend fun uploadImageOnce(
        path: String,
        bytes: ByteArray,
        filename: String,
        contentType: String,
        bg: Boolean
    ): AvatarUploadResult =
        suspendCancellableCoroutine { cont ->
            val mediaType = try {
                contentType.toMediaType()
            } catch (_: Exception) {
                "image/jpeg".toMediaType()
            }
            val multipart = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart(
                    AVATAR_PART_NAME,
                    filename,
                    bytes.toRequestBody(mediaType)
                )
                .build()
            val token = AuthToken.token
            val builder = Request.Builder()
                .url(BASE_URL + path)
                .post(multipart)
            if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
            val call = okHttp.newCall(builder.build())
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    try {
                        cont.resumeWithException(
                            RuntimeException("Upload failed: ${e.message ?: e.javaClass.simpleName}", e)
                        )
                    } catch (_: IllegalStateException) {
                    }
                }

                override fun onResponse(call: okhttp3.Call, res: okhttp3.Response) {
                    try {
                        res.use {
                            val body = it.body?.string().orEmpty()
                            if (!it.isSuccessful) {
                                if (it.code == 401) {
                                    cont.resumeWithException(
                                        AuthException("密码错/登录过期，请重登 (HTTP 401)")
                                    )
                                } else {
                                    val msg = try {
                                        JSONObject(body).optString("message", it.message)
                                    } catch (_: Exception) {
                                        it.message
                                    }
                                    cont.resumeWithException(
                                        RuntimeException("Upload failed: HTTP ${it.code} $msg")
                                    )
                                }
                                return
                            }
                            try {
                                cont.resume(parseAvatarResult(body, bg))
                            } catch (e: Exception) {
                                cont.resumeWithException(e)
                            }
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }

    suspend fun uploadAvatar(bytes: ByteArray, filename: String, contentType: String): AvatarUploadResult =
        uploadImage("api/auth/avatar", bytes, filename, contentType, bg = false)

    suspend fun uploadBgImage(bytes: ByteArray, filename: String, contentType: String): AvatarUploadResult =
        uploadImage("api/auth/bg-image", bytes, filename, contentType, bg = true)

    /** Best-effort server logout: never throws (app always clears local tokens). */
    suspend fun logoutBestEffort(): Boolean = try {
        val token = AuthToken.token
        val builder = Request.Builder()
            .url(BASE_URL + "api/auth/logout")
            .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        val res = suspendCoroutine<okhttp3.Response> { cont ->
            okHttp.newCall(builder.build()).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    try {
                        cont.resumeWithException(e)
                    } catch (_: IllegalStateException) {
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        cont.resume(response)
                    } catch (_: IllegalStateException) {
                        try {
                            response.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            })
        }
        try {
            res.use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    } catch (_: Exception) {
        false
    }

    /** Returns the NEW fav state (server source of truth). Sends X-Request-Id. */
    suspend fun toggleFavorite(song: Song, requestId: String): Boolean =
        toggleFavoriteWith(song, requestId, BASE_URL, okHttp, ::trySilentRefresh)

    /**
     * toggle 可注入变体（baseUrl/client/refresh 均可换 stub，零新依赖）。
     * body 获取与 envelope 解析都在 authedWith 的 try 内：HTTP 401 与信封
     * 401 都走静默续期；重试最多一次（第二次再 401 直接抛，无自递归）。
     */
    internal suspend fun toggleFavoriteWith(
        song: Song,
        requestId: String,
        baseUrl: String,
        client: OkHttpClient,
        refresh: suspend (String) -> RefreshOutcome
    ): Boolean = authedWith(refresh) {
        toggleFavoriteOnce(song, requestId, baseUrl, client)
    }

    private suspend fun toggleFavoriteOnce(
        song: Song,
        requestId: String,
        baseUrl: String,
        client: OkHttpClient
    ): Boolean {
        val token = AuthToken.token
        val req = buildToggleRequest(song, requestId).newBuilder()
            .url(baseUrl.trimEnd('/') + "/api/favorites/toggle")
            .apply {
                if (token.isNotBlank()) header("Authorization", "Bearer $token")
            }.build()
        val body: String = suspendCancellableCoroutine { cont ->
            val call = client.newCall(req)
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    try {
                        cont.resumeWithException(
                            RuntimeException("Request failed: ${e.message ?: e.javaClass.simpleName}", e)
                        )
                    } catch (_: IllegalStateException) {
                    }
                }

                override fun onResponse(call: okhttp3.Call, res: okhttp3.Response) {
                    try {
                        res.use {
                            if (!it.isSuccessful) {
                                if (it.code == 401) {
                                    cont.resumeWithException(
                                        AuthException("密码错/登录过期，请重登 (HTTP 401)")
                                    )
                                } else {
                                    cont.resumeWithException(
                                        RuntimeException("Request HTTP ${it.code} ${it.message}")
                                    )
                                }
                                return
                            }
                            cont.resume(it.body?.string() ?: throw RuntimeException("Empty body"))
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }
        return parseToggleResult(body)
    }

    suspend fun favIds(): Set<String> {
        val body = rawGet("api/favorites/ids")
        return parseFavIds(body)
    }

    suspend fun favList(count: Int = 50): List<Song> {
        val body = rawGet("api/favorites/list?count=$count")
        return parseFavList(body)
    }

    suspend fun removeFavBatch(sourceIds: List<String>): Int {
        if (sourceIds.isEmpty()) return 0
        val body = rawPost("api/favorites/remove-batch", buildFavRemoveBatchBody(sourceIds))
        val root = JSONObject(body)
        checkEnvelope(root, "Remove favorites")
        return when (val d = root.opt("data")) {
            is Number -> d.toInt()
            else -> 0
        }
    }

    suspend fun history(count: Int = 20): List<HistoryItem> {
        val body = rawGet("api/songs/history?count=$count")
        return parseHistoryList(body)
    }

    suspend fun removeHistory(sourceIds: List<String>): Int {
        if (sourceIds.isEmpty()) return 0
        val body = rawPost("api/songs/history/remove", buildHistoryRemoveBody(sourceIds))
        val root = JSONObject(body)
        checkEnvelope(root, "Remove history")
        return when (val d = root.opt("data")) {
            is Number -> d.toInt()
            else -> 0
        }
    }

    /**
     * Fire-and-forget play report (logged-in only; guest callers must skip
     * before calling). Never throws — history reporting must not break playback.
     */
    suspend fun reportPlay(song: Song) {
        try {
            rawGetInner(buildPlayReportPath(song))
        } catch (_: Exception) {
        }
    }

    suspend fun fetchStreamBytes(url: String): ByteArray =
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder().url(url).get().build()
            val call = okHttp.newCall(req)
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    try {
                        cont.resumeWithException(
                            RuntimeException("下载失败：${e.message ?: e.javaClass.simpleName}", e)
                        )
                    } catch (_: IllegalStateException) {
                    }
                }

                override fun onResponse(call: okhttp3.Call, res: okhttp3.Response) {
                    try {
                        res.use {
                            if (!it.isSuccessful) {
                                cont.resumeWithException(
                                    RuntimeException("下载失败：HTTP ${it.code} ${it.message}")
                                )
                                return
                            }
                            val ct = it.header("Content-Type").orEmpty()
                            if (!ct.startsWith("audio/", ignoreCase = true)) {
                                cont.resumeWithException(
                                    RuntimeException("下载失败：非音频内容 ($ct)")
                                )
                                return
                            }
                            val bytes = it.body?.bytes()
                            if (bytes == null || bytes.isEmpty()) {
                                cont.resumeWithException(RuntimeException("下载失败：空内容"))
                                return
                            }
                            if (bytes.size < MIN_AUDIO_BYTES) {
                                cont.resumeWithException(
                                    RuntimeException("下载失败：内容过小，可能不是有效音频")
                                )
                                return
                            }
                            if (!looksLikeAudio(bytes)) {
                                cont.resumeWithException(
                                    RuntimeException("下载失败：内容不是有效音频")
                                )
                                return
                            }
                            cont.resume(bytes)
                        }
                    } catch (e: Exception) {
                        try {
                            cont.resumeWithException(
                                RuntimeException("下载失败：${e.message ?: e.javaClass.simpleName}", e)
                            )
                        } catch (_: IllegalStateException) {
                        }
                    }
                }
            })
        }
}

// Small helper to avoid resuming a cancelled continuation.
private fun kotlin.coroutines.CoroutineContext.isActiveCompat(): Boolean {
    val job = (this[kotlinx.coroutines.Job] as? kotlinx.coroutines.Job) ?: return true
    return job.isActive
}
