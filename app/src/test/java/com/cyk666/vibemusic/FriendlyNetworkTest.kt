package com.cyk666.vibemusic

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FriendlyNetworkTest {

    @Test
    fun unknownHost_mapsToOffline() {
        assertEquals(
            "网络连接断开，请检查网络",
            friendlyNetworkMessage(UnknownHostException("Unable to resolve host"))
        )
    }

    @Test
    fun socketTimeout_mapsToOffline() {
        assertEquals(
            "网络连接断开，请检查网络",
            friendlyNetworkMessage(SocketTimeoutException("Read timed out"))
        )
    }

    @Test
    fun connectFailure_mapsToOffline() {
        assertEquals(
            "网络连接断开，请检查网络",
            friendlyNetworkMessage(ConnectException("Failed to connect"))
        )
    }

    @Test
    fun connectionClosedBody_mapsToOffline() {
        assertEquals(
            "网络连接断开，请检查网络",
            friendlyNetworkMessage(RuntimeException("Request failed: connection closed"))
        )
    }

    @Test
    fun connectionClosedCaseInsensitive_mapsToOffline() {
        assertEquals(
            "网络连接断开，请检查网络",
            friendlyNetworkMessage(RuntimeException("CONNECTION CLOSED by peer"))
        )
    }

    @Test
    fun dnsHint_mapsToOffline() {
        assertEquals(
            "网络连接断开，请检查网络",
            friendlyNetworkMessage(RuntimeException("Unable to resolve host \"vibe.cyk666.top\""))
        )
    }

    @Test
    fun causeChain_mapsToOffline() {
        val wrapped = RuntimeException(
            "Download failed",
            UnknownHostException("vibe.cyk666.top")
        )
        assertEquals("网络连接断开，请检查网络", friendlyNetworkMessage(wrapped))
    }

    @Test
    fun timeoutWordInChain_mapsToOffline() {
        val wrapped = RuntimeException(
            "Request failed",
            RuntimeException("Read timed out after 30s")
        )
        assertEquals("网络连接断开，请检查网络", friendlyNetworkMessage(wrapped))
    }

    @Test
    fun http5xx_mapsToServerBusy() {
        assertEquals(
            "服务器开小差，请稍后重试",
            friendlyNetworkMessage(RuntimeException("Request HTTP 503 Service Unavailable"))
        )
        assertEquals(
            "服务器开小差，请稍后重试",
            friendlyNetworkMessage(RuntimeException("下载失败：HTTP 500 Internal Error"))
        )
        assertEquals(
            "服务器开小差，请稍后重试",
            friendlyNetworkMessage(RuntimeException("Request HTTP 502 Bad Gateway"))
        )
    }

    @Test
    fun http404_mapsToGone() {
        assertEquals(
            "资源不存在，可能已下架",
            friendlyNetworkMessage(RuntimeException("Request HTTP 404 Not Found"))
        )
    }

    @Test
    fun authExpiry_passesThrough() {
        val m = "密码错/登录过期，请重登 (HTTP 401)"
        assertEquals(m, friendlyNetworkMessage(AuthException(m)))
    }

    @Test
    fun unrelatedEnvelopeError_passesThrough() {
        val m = "Search failed: code=403 forbidden"
        assertEquals(m, friendlyNetworkMessage(RuntimeException(m)))
    }

    @Test
    fun otherHttpCode_passesThrough() {
        val m = "Request HTTP 400 Bad Request"
        assertEquals(m, friendlyNetworkMessage(RuntimeException(m)))
    }

    @Test
    fun nullMessage_fallsBackToClassName() {
        assertEquals(
            "RuntimeException",
            friendlyNetworkMessage(RuntimeException(null as String?))
        )
    }
}
