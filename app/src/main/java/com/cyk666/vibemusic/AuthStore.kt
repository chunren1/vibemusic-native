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
    }

    suspend fun saveTokens(context: Context, token: String, refreshToken: String) {
        context.authDataStore.edit { p ->
            p[KEY_TOKEN] = token
            if (refreshToken.isNotBlank()) p[KEY_REFRESH] = refreshToken
        }
        AuthToken.token = token
        if (refreshToken.isNotBlank()) AuthToken.refreshToken = refreshToken
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
