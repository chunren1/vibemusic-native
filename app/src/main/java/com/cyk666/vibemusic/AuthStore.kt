package com.cyk666.vibemusic

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(
    name = AuthStore.DATASTORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** Synchronous in-memory holder read by the OkHttp interceptor. Never log its content. */
object AuthToken {
    @Volatile var token: String = ""
    @Volatile var refreshToken: String = ""
    @Volatile var username: String = ""

    /**
     * Set by logout / auth-loss, cleared by the next successful login.
     *
     * Why it exists: the silent-refresh path deliberately falls back to the
     * HttpOnly `VIBE_REFRESH` cookie in [VibeApi.cookieJar] when the persisted
     * refreshToken is blank (cookie-only sessions). After a logout that jar
     * still holds the cookie, so an in-flight 401 could refresh successfully
     * and write a fresh access token back to disk — "退出登录" would silently
     * undo itself on the next cold start. This flag is the gate that makes
     * "logged out" distinguishable from "cookie-only session".
     */
    @Volatile var sessionEnded: Boolean = false

    /**
     * 会话代次：每次「登出 / 清空 / 登录成功」自增。
     *
     * 为什么需要：静默续期的网络往返可以卡到 45s（connect 15 + read 30）。旧实现只在
     * 拿锁时判一次 `sessionEnded`，于是「401 → refresh 卡住 → 用户退出登录 → refresh
     * 返回 200」会让这次迟到的成功把 token 写回内存和磁盘，下次冷启动 `/me` 直接成功
     * = 退出登录被静默撤销（2026-11-03 审计 P1）。代次在写回那一刻再比一次，跨代的
     * 结果整体丢弃。
     */
    private val epochCounter = java.util.concurrent.atomic.AtomicLong(0L)

    /** 读代次：`endSession` 与登录可能并发，用原子读避免丢更新（丢更新会让闸门失效）。 */
    val sessionEpoch: Long get() = epochCounter.get()

    fun bumpSessionEpoch() {
        epochCounter.incrementAndGet()
    }
}

/** Token-only persistence. Password is never stored. */
object AuthStore {
    /** Preferences DataStore name; file is datastore/<name>.preferences_pb. Mirrored in backup rules. */
    const val DATASTORE_NAME = "auth"
    private val KEY_TOKEN = stringPreferencesKey("token")
    private val KEY_REFRESH = stringPreferencesKey("refresh_token")
    private val KEY_USERNAME = stringPreferencesKey("username")
    private val KEY_NICKNAME = stringPreferencesKey("nickname")

    data class Snapshot(
        val token: String,
        val username: String,
        val nickname: String,
        val refreshToken: String = ""
    )

    suspend fun save(
        context: Context,
        token: String,
        username: String,
        nickname: String,
        refreshToken: String = ""
    ) {
        context.authDataStore.edit { p ->
            p[KEY_TOKEN] = token
            p[KEY_USERNAME] = username
            p[KEY_NICKNAME] = nickname
            // **整体替换**：登录/注册响应当次没带 refreshToken（cookie-only 会话）时，
            // 绝不能沿用上一个账号的续期凭据 —— 否则新账号的续期会挂到旧账号上，
            // 退出再登录还可能被旧 cookie 静默拉回（CODE-REVIEW-2026-10-28 API-4）。
            // 轮转语义（后端只回新 refresh 才替换）只在 saveTokens 里。
            if (refreshToken.isNotBlank()) p[KEY_REFRESH] = refreshToken else p.remove(KEY_REFRESH)
        }
        AuthToken.token = token
        AuthToken.username = username
        AuthToken.refreshToken = refreshToken
        // A successful login/register re-arms silent refresh.
        AuthToken.sessionEnded = false
        // 换号/登录也换代次：登录前发出的静默续期若迟到返回，绝不能覆盖新账号的凭据。
        AuthToken.bumpSessionEpoch()
    }

    /** 同代写回（Kotlin 侧常用入口）。跨代/已登出时返回 false 且不落盘。 */
    suspend fun saveTokens(context: Context, token: String, refreshToken: String): Boolean =
        applyRefreshedTokens(context, token, refreshToken, AuthToken.sessionEpoch)

    /**
     * 静默续期的唯一写回口。[epoch] 是进入续期时抓的 [AuthToken.sessionEpoch] 快照。
     *
     * 两道门都必须在**写的时候**判，而不是在拿锁时判一次：
     * - 磁盘：`edit` 与登出的 [clear]（同样是 `edit`）由 DataStore 串行执行，把代次
     *   检查放进 `edit` 内部 → 登出先落地则本次写入整体跳过，不会出现「行删了又写回」。
     * - 内存：写盘后同代再写 `AuthToken`，避免登出后内存又被喂回旧 token。
     *
     * 返回 false = 结果被丢弃（已登出/已换号）。调用方按 NETWORK_FAIL 处理：这次请求
     * 失败，但绝不复活会话。
     */
    suspend fun applyRefreshedTokens(
        context: Context,
        token: String,
        refreshToken: String,
        epoch: Long
    ): Boolean {
        fun current() = AuthToken.sessionEpoch == epoch && !AuthToken.sessionEnded
        var applied = false
        try {
            context.authDataStore.edit { p ->
                if (!current()) return@edit
                p[KEY_TOKEN] = token
                if (refreshToken.isNotBlank()) p[KEY_REFRESH] = refreshToken
                applied = true
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 写盘失败（磁盘满/DataStore IO 异常）不埋掉这次续期：内存里换上新 token
            // 让当前进程继续可用，冷启动后旧值只是 401 一次，与旧行为一致。
            if (!current()) return false
            AuthToken.token = token
            if (refreshToken.isNotBlank()) AuthToken.refreshToken = refreshToken
            return true
        }
        if (applied && current()) {
            AuthToken.token = token
            if (refreshToken.isNotBlank()) AuthToken.refreshToken = refreshToken
        }
        return applied
    }

    /**
     * 只改昵称一个键。
     *
     * 为什么不用 `load()` + `save()`：资料更新拿的是**读**时刻的快照，回写会把期间
     * 静默续期刚落盘的新 token/refreshToken 整体覆盖回旧值，`save` 还会顺手把
     * `sessionEnded` 清零（2026-11-03 审计 P2）。
     */
    suspend fun updateNickname(context: Context, nickname: String) {
        context.authDataStore.edit { p -> p[KEY_NICKNAME] = nickname }
    }

    suspend fun clear(context: Context) {
        context.authDataStore.edit { p ->
            p.remove(KEY_TOKEN)
            p.remove(KEY_REFRESH)
            p.remove(KEY_USERNAME)
            p.remove(KEY_NICKNAME)
        }
        AuthToken.token = ""
        AuthToken.refreshToken = ""
        AuthToken.username = ""
        AuthToken.sessionEnded = true
        AuthToken.bumpSessionEpoch()
    }

    suspend fun load(context: Context): Snapshot {
        val snap = context.authDataStore.data.map { p ->
            Snapshot(
                token = p[KEY_TOKEN].orEmpty(),
                username = p[KEY_USERNAME].orEmpty(),
                nickname = p[KEY_NICKNAME].orEmpty(),
                refreshToken = p[KEY_REFRESH].orEmpty()
            )
        }.first()
        // Populate the synchronous holder so the interceptor works without DataStore reads.
        AuthToken.token = snap.token
        AuthToken.refreshToken = snap.refreshToken
        AuthToken.username = snap.username
        return snap
    }
}
