package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AuthStoreTest {

    private fun context(): Context =
        RuntimeEnvironment.getApplication()

    @Test
    fun saveLoad_roundTrip(): Unit = runBlocking {
        AuthStore.save(context(), "tok123", "alice", "Alice")
        val snap = AuthStore.load(context())
        assertEquals("tok123", snap.token)
        assertEquals("alice", snap.username)
        assertEquals("Alice", snap.nickname)
    }

    /**
     * API-4：`save` 是**整体替换**，不是"有就覆盖、没有就保留"。
     *
     * 旧实现在 refreshToken 为空时保留旧值：第二个账号登录响应没带 refreshToken
     * （cookie-only 会话）时，新账号会沿用**上一个账号**的续期凭据 —— 续期把新账号
     * 挂到旧账号上，退出重登还可能被旧 cookie 静默拉回。
     */
    @Test
    fun save_withoutRefreshToken_clearsPreviousAccountsValue(): Unit = runBlocking {
        AuthStore.save(context(), "acc1", "alice", "Alice", "old-ref")
        assertEquals("old-ref", AuthStore.load(context()).refreshToken)

        AuthStore.save(context(), "acc2", "bob", "Bob")

        assertEquals("acc2", AuthStore.load(context()).token)
        assertEquals("", AuthStore.load(context()).refreshToken)
        assertEquals("", AuthToken.refreshToken)
    }

    /** 轮转语义不受影响：`saveTokens` 收到新 refresh 才替换（后端没回就保留）。 */
    @Test
    fun saveTokens_keepsOldValueWhenRotationOmitsIt(): Unit = runBlocking {
        AuthStore.save(context(), "acc1", "alice", "Alice", "ref1")
        AuthStore.saveTokens(context(), "acc1-new", "")
        assertEquals("acc1-new", AuthStore.load(context()).token)
        assertEquals("ref1", AuthStore.load(context()).refreshToken)
    }

    @Test
    fun clear_wipesTokenAndUsername(): Unit = runBlocking {
        AuthStore.save(context(), "tok123", "alice", "Alice")
        AuthStore.clear(context())
        val snap = AuthStore.load(context())
        assertEquals("", snap.token)
        assertEquals("", snap.username)
        assertEquals("", snap.nickname)
    }

    @Test
    fun load_populatesAuthTokenHolder(): Unit = runBlocking {
        AuthStore.save(context(), "tok999", "bob", "Bob")
        AuthToken.token = ""
        AuthToken.username = ""
        AuthStore.load(context())
        assertEquals("tok999", AuthToken.token)
        assertEquals("bob", AuthToken.username)
    }
}
