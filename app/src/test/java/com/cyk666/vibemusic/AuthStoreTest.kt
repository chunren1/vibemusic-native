package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /**
     * 2026-11-03 审计 P2：资料更新只能改昵称一个键。
     *
     * 旧实现 `load()` + `save()` 整体回写：弱网下改昵称，同一瞬间别的请求 401 完成静默
     * 续期（新 token/refreshToken 已落盘），随后旧快照被整体写回 —— 新 refreshToken 被
     * 覆盖回旧值（后端轮转后旧值已作废），`save` 还把 `sessionEnded` 清零。
     */
    @Test
    fun updateNickname_leavesTokensAlone(): Unit = runBlocking {
        AuthStore.save(context(), "acc1", "alice", "Alice", "ref1")
        AuthStore.saveTokens(context(), "acc-rotated", "ref2")
        AuthToken.sessionEnded = false

        AuthStore.updateNickname(context(), "新昵称")

        val snap = AuthStore.load(context())
        assertEquals("新昵称", snap.nickname)
        assertEquals("acc-rotated", snap.token)
        assertEquals("ref2", snap.refreshToken)
        assertEquals("alice", snap.username)
        assertEquals("acc-rotated", AuthToken.token)
        assertEquals("ref2", AuthToken.refreshToken)
        assertFalse(AuthToken.sessionEnded)
    }

    /** 跨代写回必须整体丢弃：登出在"网络已回、还没写盘"之间发生时不能复活会话。 */
    @Test
    fun applyRefreshedTokens_dropsResultFromStaleEpoch(): Unit = runBlocking {
        AuthStore.save(context(), "acc1", "alice", "Alice", "ref1")
        val stale = AuthToken.sessionEpoch
        AuthStore.clear(context())

        val applied = AuthStore.applyRefreshedTokens(context(), "acc-late", "ref-late", stale)

        assertFalse(applied)
        assertEquals("", AuthStore.load(context()).token)
        assertEquals("", AuthToken.token)
        assertEquals("", AuthToken.refreshToken)
        assertTrue(AuthToken.sessionEnded)
    }

    /** 同代写回照旧生效（别把闸门做成永久关闭）。 */
    @Test
    fun applyRefreshedTokens_appliesWhenEpochMatches(): Unit = runBlocking {
        AuthStore.save(context(), "acc1", "alice", "Alice", "ref1")
        val epoch = AuthToken.sessionEpoch

        val applied = AuthStore.applyRefreshedTokens(context(), "acc2", "ref2", epoch)

        assertTrue(applied)
        assertEquals("acc2", AuthStore.load(context()).token)
        assertEquals("ref2", AuthStore.load(context()).refreshToken)
        assertEquals("acc2", AuthToken.token)
        assertEquals("ref2", AuthToken.refreshToken)
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
