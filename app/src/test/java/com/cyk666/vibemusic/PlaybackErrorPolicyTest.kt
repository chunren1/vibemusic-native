package com.cyk666.vibemusic

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-11-03「上一首还没播完就莫名跳下一首」的判据：
 * 播放**中途**的 IO 失败要就地停住（保留播放点），不许走 `seekToNextMediaItem()`；
 * 歌根本没起来（坏源）或内容类错误（解析/解码）仍然照旧跳歌。
 */
class PlaybackErrorPolicyTest {

    @Test
    fun ioErrors_areRecognised() {
        val io = listOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_TIMEOUT
        )
        for (code in io) assertTrue("$code 应算 IO 类", isIoLikeError(code))
    }

    @Test
    fun contentErrors_neverPauseInsteadOfSkipping() {
        val content = listOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_UNSPECIFIED
        )
        for (code in content) {
            assertFalse("$code 是内容类，应照旧跳歌", isIoLikeError(code))
            assertFalse(
                "$code 放到一半也不该就地停",
                shouldStopInsteadOfSkip(120_000L, 240_000L, code)
            )
        }
    }

    @Test
    fun failureAtSongStart_stillSkips() {
        val io = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        assertFalse(shouldStopInsteadOfSkip(0L, 240_000L, io))
        assertFalse(shouldStopInsteadOfSkip(3_000L, 240_000L, io))
        // 阈值边界：恰好 10s 算中途
        assertFalse(shouldStopInsteadOfSkip(MID_SONG_MIN_POSITION_MS - 1, 240_000L, io))
        assertTrue(shouldStopInsteadOfSkip(MID_SONG_MIN_POSITION_MS, 240_000L, io))
    }

    @Test
    fun failureMidSong_stops() {
        val io = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        assertTrue(shouldStopInsteadOfSkip(90_000L, 240_000L, io))
        // 时长未知（无 Content-Length 的流）：只知道已经放了一段时间 → 也算中途
        assertTrue(shouldStopInsteadOfSkip(90_000L, C.TIME_UNSET, io))
        assertTrue(shouldStopInsteadOfSkip(90_000L, 0L, io))
    }

    @Test
    fun failureInTheTail_stillSkips() {
        val io = PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        // 距结尾不足 5s：本来就放完了，跳走无感
        assertFalse(shouldStopInsteadOfSkip(236_000L, 240_000L, io))
        // 位置越界（>duration）也按"已结束"处理
        assertFalse(shouldStopInsteadOfSkip(300_000L, 240_000L, io))
    }

    // ---- 2026-11-06：位置越界型错误的重试起点 ----

    @Test
    fun readPositionOutOfRange_mustRestartFromStart() {
        assertTrue(
            shouldRestartFromStart(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        )
        // 其余 IO 错误只是"这一下没拿到字节"，位置仍然有效 → 保持原来中途续播的行为
        assertFalse(shouldRestartFromStart(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(shouldRestartFromStart(PlaybackException.ERROR_CODE_TIMEOUT))
        assertFalse(shouldRestartFromStart(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
    }

    @Test
    fun retryAfterOutOfRange_dropsResumePosition() {
        // 中途、位置有效（>0）——但错误码说明该位置在新资源里不存在，不能 seek 回去
        assertFalse(
            shouldRestorePositionAfterRetry(
                midSong = true,
                positionMs = 120_000L,
                errorCode = PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
            )
        )
    }

    @Test
    fun retryAfterOrdinaryDropout_keepsResumePosition() {
        // 普通中途断流：位置仍有效，回原位置才不会"又从头播一遍"
        assertTrue(
            shouldRestorePositionAfterRetry(
                midSong = true,
                positionMs = 120_000L,
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
            )
        )
        assertTrue(
            shouldRestorePositionAfterRetry(
                midSong = true,
                positionMs = 120_000L,
                errorCode = PlaybackException.ERROR_CODE_TIMEOUT
            )
        )
        // 没到中途 / 本来就在开头：默认值 0 即可，不需要 seek
        assertFalse(
            shouldRestorePositionAfterRetry(
                midSong = false,
                positionMs = 120_000L,
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
            )
        )
        assertFalse(
            shouldRestorePositionAfterRetry(
                midSong = true,
                positionMs = 0L,
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
            )
        )
    }
}
