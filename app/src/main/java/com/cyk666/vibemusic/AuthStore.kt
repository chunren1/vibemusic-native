package com.cyk666.vibemusic

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth")

/** Synchronous in-memory holder read by the OkHttp interceptor. Never log its content. */
object AuthToken {
    @Volatile var token: String = ""
    @Volatile var refreshToken: String = ""
    @Volatile var username: String = ""
}

/** Token-only persistence. Password is never stored. */
object AuthStore {
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
            if (refreshToken.isNotBlank()) p[KEY_REFRESH] = refreshToken
        }
        AuthToken.token = token
        AuthToken.username = username
        if (refreshToken.isNotBlank()) AuthToken.refreshToken = refreshToken
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
