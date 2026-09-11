package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiagnosableErrorTest {

    @Test
    fun envelopeFailure_keepsRawServerMessage() {
        val e = RuntimeException("Create playlist failed: code=403 forbidden")
        assertEquals(
            "新建歌单失败: Create playlist failed: code=403 forbidden",
            diagnosableError("新建歌单失败", e)
        )
    }

    @Test
    fun offlineHint_appendsRawForDiagnosis() {
        val e = RuntimeException("Request failed: connection closed")
        val msg = diagnosableError("收藏失败", e)
        assertTrue(msg.startsWith("收藏失败: 网络连接断开，请检查网络"))
        assertTrue(msg.contains("Request failed: connection closed"))
    }

    @Test
    fun serverBusy_appendsRawForDiagnosis() {
        val e = RuntimeException("Request HTTP 503 Service Unavailable")
        val msg = diagnosableError("收藏加载失败", e)
        assertTrue(msg.startsWith("收藏加载失败: 服务器开小差，请稍后重试"))
        assertTrue(msg.contains("Request HTTP 503"))
    }

    @Test
    fun identicalRaw_noDuplication() {
        val e = RuntimeException("网络连接断开，请检查网络")
        assertEquals(
            "收藏失败: 网络连接断开，请检查网络",
            diagnosableError("收藏失败", e)
        )
    }

    @Test
    fun blankRaw_friendlyOnly() {
        assertEquals(
            "新建歌单失败: RuntimeException",
            diagnosableError("新建歌单失败", RuntimeException(null as String?))
        )
    }

    @Test
    fun toggleEnvelopeFailure_keepsRaw() {
        val e = RuntimeException("Favorites failed: code=500 internal error")
        val msg = diagnosableError("收藏失败", e)
        assertTrue(msg.startsWith("收藏失败: "))
        assertTrue(msg.contains("code=500 internal error"))
    }
}
