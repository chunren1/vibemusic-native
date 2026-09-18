package com.cyk666.vibemusic

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * Signed stream URL expiry: netease/migu upstream URLs are time-signed, so
 * resuming a hours-old paused item replays a dead URL. The error path retries
 * the SAME index ONCE with a rebuilt MediaItem before auto-skip — exactly one
 * retry per item per error episode, reset on successful play + index change.
 *
 * Robolectric：最后三条用例要构造 media3 的 LoadErrorInfo（内含 android.net.Uri）。
 */
@RunWith(RobolectricTestRunner::class)
class StreamRetryTest {

    private val ioError = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

    @Test
    fun firstFailure_retries() {
        assertTrue(shouldRetrySameItem("abc123", ioError, 2, null, -1))
    }

    @Test
    fun identicalRepeat_noRetry() {
        val key = streamRetryKey("abc123", ioError)
        assertFalse(shouldRetrySameItem("abc123", ioError, 2, key, 2))
    }

    @Test
    fun newErrorCode_retriesAgain() {
        val key = streamRetryKey("abc123", ioError)
        assertTrue(
            shouldRetrySameItem(
                "abc123",
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                2,
                key,
                2
            )
        )
    }

    @Test
    fun indexChange_retriesAgain() {
        val key = streamRetryKey("abc123", ioError)
        assertTrue(shouldRetrySameItem("abc123", ioError, 3, key, 2))
    }

    @Test
    fun differentItem_retries() {
        val key = streamRetryKey("abc123", ioError)
        assertTrue(shouldRetrySameItem("xyz789", ioError, 2, key, 2))
    }

    @Test
    fun localItem_neverRetries() {
        assertFalse(shouldRetrySameItem("local:netease:abc123", ioError, 2, null, -1))
    }

    @Test
    fun blankMediaId_neverRetries() {
        assertFalse(shouldRetrySameItem("", ioError, 2, null, -1))
    }

    @Test
    fun negativeIndex_neverRetries() {
        assertFalse(shouldRetrySameItem("abc123", ioError, -1, null, -1))
    }

    @Test
    fun retryKey_combinesMediaIdAndErrorCode() {
        assertEquals("abc123|$ioError", streamRetryKey("abc123", ioError))
    }

    // ---- 快失败预算（2026-09-17 后台长时间静默修复）：把 ExoPlayer 默认的
    //      3 次重试 + 1s/2s/4s 指数退避，收敛为 1 次重试 + 固定 800ms。----

    @Test
    fun loadErrorPolicy_retriesOnlyOnce() {
        val policy = streamLoadErrorHandlingPolicy()
        assertEquals(STREAM_LOAD_RETRY_COUNT, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
    }

    @Test
    fun loadErrorPolicy_fixedShortDelay() {
        val policy = streamLoadErrorHandlingPolicy()
        val info = LoadErrorHandlingPolicy.LoadErrorInfo(
            LoadEventInfo(0L, DataSpec(Uri.EMPTY), 0L),
            MediaLoadData(C.DATA_TYPE_MEDIA),
            IOException("bad http status"),
            1
        )
        assertEquals(STREAM_LOAD_RETRY_DELAY_MS, policy.getRetryDelayMsFor(info))
    }

    @Test
    fun loadErrorPolicy_delayIsBounded() {
        // 单次失败（含 1 次重试）应远小于默认策略的 ≈9s，保证坏歌能快速跳到下一首
        assertTrue(STREAM_LOAD_RETRY_DELAY_MS <= 1000L)
    }
}
