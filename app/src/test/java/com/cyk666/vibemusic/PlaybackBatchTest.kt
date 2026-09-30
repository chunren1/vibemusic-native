package com.cyk666.vibemusic

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBatchTest {

    private fun songs(n: Int): List<Song> = (0 until n).map { i ->
        Song("id$i", "name$i", "artist", "", "", 180, "netease")
    }

    // ---- (a) prefetch ----

    @Test
    fun `prefetch_中间曲预热下一首`() {
        assertEquals(2, selectPrefetchIndex(songs(5), 1, false, false))
    }

    @Test
    fun `prefetch_末尾无循环不预热`() {
        assertNull(selectPrefetchIndex(songs(5), 4, false, false))
    }

    @Test
    fun `prefetch_末尾列表循环回绕到0`() {
        assertEquals(0, selectPrefetchIndex(songs(5), 4, false, true))
    }

    @Test
    fun `prefetch_单曲循环预热本曲`() {
        assertEquals(2, selectPrefetchIndex(songs(5), 2, true, false))
    }

    @Test
    fun `prefetch_空队列与越界返回null`() {
        assertNull(selectPrefetchIndex(emptyList(), 0, false, true))
        assertNull(selectPrefetchIndex(songs(3), -1, false, true))
        assertNull(selectPrefetchIndex(songs(3), 7, false, true))
    }

    @Test
    fun `prefetch_单首列表循环不回绕自己`() {
        assertNull(selectPrefetchIndex(songs(1), 0, false, true))
    }

    @Test
    fun `fastSwitch_预算内命中`() {
        assertTrue(isFastSwitch(0L))
        assertTrue(isFastSwitch(499L))
        assertTrue(isFastSwitch(500L))
    }

    @Test
    fun `fastSwitch_超预算与非法值不命中`() {
        assertFalse(isFastSwitch(501L))
        assertFalse(isFastSwitch(5_000L))
        assertFalse(isFastSwitch(-1L))
    }

    @Test
    fun `loadControl_快启动构造成功`() {
        assertNotNull(playbackLoadControl())
    }

    // ---- (b) weak-network backoff ----

    @Test
    fun `weakDelay_指数退避`() {
        assertEquals(800L, weakRetryDelayMs(0))
        assertEquals(1_600L, weakRetryDelayMs(1))
        assertEquals(3_200L, weakRetryDelayMs(2))
    }

    @Test
    fun `weakDelay_封顶5秒且负数按0算`() {
        assertEquals(5_000L, weakRetryDelayMs(3))
        assertEquals(5_000L, weakRetryDelayMs(10))
        assertEquals(800L, weakRetryDelayMs(-2))
    }

    @Test
    fun `weakError_仅超时建连类`() {
        assertTrue(isWeakNetworkError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(isWeakNetworkError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertTrue(isWeakNetworkError(PlaybackException.ERROR_CODE_TIMEOUT))
    }

    @Test
    fun `weakError_应用层错误不走弱网路径`() {
        assertFalse(isWeakNetworkError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(isWeakNetworkError(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertFalse(isWeakNetworkError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
    }

    @Test
    fun `weakBudget_最多补两次`() {
        assertTrue(shouldRetryWeakNetwork(0))
        assertTrue(shouldRetryWeakNetwork(1))
        assertFalse(shouldRetryWeakNetwork(2))
        assertFalse(shouldRetryWeakNetwork(9))
    }

    // ---- (c) keepalive self-check ----

    @Test
    fun `oemGuide_vivo保持存量原文`() {
        assertEquals(KEEP_ALIVE_VENDOR_HINT, oemGuideFor("vivo"))
        assertEquals(KEEP_ALIVE_VENDOR_HINT, oemGuideFor("VIVO"))
    }

    @Test
    fun `oemGuide_各厂商命中关键字`() {
        assertTrue(oemGuideFor("Xiaomi").contains("无限制"))
        assertTrue(oemGuideFor("Redmi").contains("小米"))
        assertTrue(oemGuideFor("HUAWEI").contains("应用启动管理"))
        assertTrue(oemGuideFor("honor").contains("华为"))
        assertTrue(oemGuideFor("OPPO").contains("后台运行"))
        assertTrue(oemGuideFor("OnePlus").contains("OPPO"))
        assertTrue(oemGuideFor("samsung").contains("从不休眠"))
    }

    @Test
    fun `oemGuide_未知与空走通用版`() {
        assertTrue(oemGuideFor("Google").contains("通用"))
        assertTrue(oemGuideFor("").contains("通用"))
        assertTrue(oemGuideFor("   ").contains("通用"))
    }

    @Test
    fun `selfCheck_全过才算过`() {
        assertTrue(keepAliveAllOk(KeepAliveCheck(true, true)))
        assertFalse(keepAliveAllOk(KeepAliveCheck(false, true)))
        assertFalse(keepAliveAllOk(KeepAliveCheck(true, false)))
        assertFalse(keepAliveAllOk(KeepAliveCheck(false, false)))
    }

    @Test
    fun `selfCheck_汇总文案四态`() {
        assertTrue(keepAliveCheckSummary(KeepAliveCheck(true, true)).contains("自查通过"))
        assertTrue(keepAliveCheckSummary(KeepAliveCheck(false, true)).contains("白名单"))
        assertTrue(keepAliveCheckSummary(KeepAliveCheck(true, false)).contains("通知"))
        val both = keepAliveCheckSummary(KeepAliveCheck(false, false))
        assertTrue(both.contains("2 项"))
    }

    @Test
    fun `selfCheck_通知行文案`() {
        assertTrue(notificationCheckLabel(true).contains("已开启"))
        assertTrue(notificationCheckLabel(false).contains("未开启"))
    }
}
