package com.cyk666.vibemusic

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Signed stream URL expiry: netease/migu upstream URLs are time-signed, so
 * resuming a hours-old paused item replays a dead URL. The error path retries
 * the SAME index ONCE with a rebuilt MediaItem before auto-skip — exactly one
 * retry per item per error episode, reset on successful play + index change.
 */
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
}
