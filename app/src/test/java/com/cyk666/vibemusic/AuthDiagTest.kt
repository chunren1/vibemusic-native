package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AuthDiagTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    @Test
    fun map_401IsInvalid() {
        assertEquals(
            "ME_401_INVALID",
            mapAuthFailureToCode(AuthException("密码错/登录过期，请重登"))
        )
        assertEquals(
            "ME_401_INVALID",
            mapAuthFailureToCode(AuthException("密码错/登录过期，请重登 (HTTP 401)"))
        )
    }

    @Test
    fun map_networkAuthIsNetwork() {
        assertEquals(
            "ME_NETWORK",
            mapAuthFailureToCode(NetworkAuthException("网络连接断开，登录态保留"))
        )
    }

    @Test
    fun map_http500IsServer() {
        assertEquals(
            "ME_500",
            mapAuthFailureToCode(RuntimeException("Request HTTP 500 Internal Server Error"))
        )
        assertEquals(
            "ME_500",
            mapAuthFailureToCode(RuntimeException("Request HTTP 503 Service Unavailable"))
        )
    }

    @Test
    fun map_transportBlipIsNetwork() {
        assertEquals(
            "ME_NETWORK",
            mapAuthFailureToCode(
                RuntimeException(
                    "Request failed: timeout",
                    java.net.SocketTimeoutException("timed out")
                )
            )
        )
        assertEquals(
            "ME_NETWORK",
            mapAuthFailureToCode(java.net.UnknownHostException("Unable to resolve host"))
        )
    }

    @Test
    fun map_unknownCarriesSimpleName() {
        assertEquals(
            "RESTORE_EXCEPTION:IllegalStateException",
            mapAuthFailureToCode(IllegalStateException("boom"))
        )
    }

    @Test
    fun labels_chinesePerCode() {
        assertEquals("正常", authDiagLabel("OK"))
        assertEquals("未登录（无 token）", authDiagLabel("NO_TOKEN"))
        assertEquals("登录过期（401）", authDiagLabel("ME_401_INVALID"))
        assertEquals("网络失败（登录态保留）", authDiagLabel("ME_NETWORK"))
        assertEquals("访客态（token 保留）", authDiagLabel("ME_GUEST"))
        assertEquals("服务器异常", authDiagLabel("ME_500"))
        assertEquals("用户主动退出", authDiagLabel("USER_LOGOUT"))
        assertEquals("恢复异常（IOException）", authDiagLabel("RESTORE_EXCEPTION:IOException"))
        assertEquals("暂无记录", authDiagLabel(""))
    }

    @Test
    fun diagTime_relativeChinese() {
        val now = 1_700_000_000_000L
        assertEquals("刚刚", formatDiagTime(now - 30_000L, now))
        assertEquals("5分钟前", formatDiagTime(now - 5 * 60_000L, now))
        assertEquals("3小时前", formatDiagTime(now - 3 * 3_600_000L, now))
        assertEquals("未知时间", formatDiagTime(0L, now))
        assertEquals("未知时间", formatDiagTime(-5L, now))
    }

    @Test
    fun format_twoLinesWithChineseLabels() {
        val now = 1_700_000_000_000L
        val s = formatAuthDiag("ME_401_INVALID", now - 60_000L, now - 120_000L, now)
        val lines = s.split("\n")
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("登录过期（401）"))
        assertTrue(lines[0].contains("1分钟前"))
        assertTrue(lines[1].contains("进度已保存"))
        assertTrue(lines[1].contains("2分钟前"))
    }

    @Test
    fun format_noHistoryYet() {
        val now = 1_700_000_000_000L
        val s = formatAuthDiag("", 0L, 0L, now)
        assertTrue(s.contains("暂无记录"))
    }

    @Test
    fun store_authDiagRoundTrip(): Unit = runBlocking {
        val ctx = context()
        QueueStore.saveAuthDiag(ctx, "ME_NETWORK")
        val (code, ts) = QueueStore.loadAuthDiag(ctx)
        assertEquals("ME_NETWORK", code)
        assertTrue(ts > 0L)
        QueueStore.saveAuthDiag(ctx, "USER_LOGOUT")
        val (code2, _) = QueueStore.loadAuthDiag(ctx)
        assertEquals("USER_LOGOUT", code2)
    }

    @Test
    fun seekRestoreTarget_usesRequestedIndex() {
        val q = listOf(
            Song("a", "A", "", "", "", 200, "netease"),
            Song("b", "B", "", "", "", 200, "netease"),
            Song("c", "C", "", "", "", 200, "netease")
        )
        // Stale controller index 0, user tapped 2 -> song c, never song a.
        assertEquals("c", selectSeekRestoreTarget(q, 2)?.sourceId)
        assertNull(selectSeekRestoreTarget(q, 9))
        assertNull(selectSeekRestoreTarget(emptyList(), 0))
    }
}
