package com.cyk666.vibemusic

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * 播放**中途**出错时该不该跳下一首（2026-11-03 用户报「上一首还没播完就莫名跳下一首」）。
 *
 * 修复前的阶梯：`onPlayerError` → 同曲换 URL 重试一次 → `shouldAutoSkip` 立刻
 * `seekToNextMediaItem()`。而 `consecFails` 只在**真的出声之后**才清零，所以一条队列里
 * 任何时候一次瞬断（走过的 WiFi 切换、锁屏 Doze、CDN 掐流、代理超时）都会在两三秒内
 * 把歌跳走 —— 哪怕已经放到 3 分半。听感就是"上一首还没播完就自己去下一首"，
 * 而且用户往往连 toast 都没看到（锁屏）。
 *
 * 判据（纯函数，可单测）：
 * - 位置 < [MID_SONG_MIN_POSITION_MS]：歌根本没起来，跳下一首是对的（换掉坏源）。
 * - 距结尾 < [MID_SONG_TAIL_MS] 且时长已知：本来就放完了，跳走无感。
 * - 其余算「中途」：IO 类错误就地停住（保留播放点），等用户点播放再重试；
 *   内容类错误（解析/解码/格式不支持）不在此列 —— 那是这首歌真的坏了，跳走才对。
 */
const val MID_SONG_MIN_POSITION_MS = 10_000L
const val MID_SONG_TAIL_MS = 5_000L

/**
 * IO 类（可自愈/与曲目内容无关）的错误码：网络断开、超时、HTTP 状态、读越界等。
 * 这些错误代表"这一下没拿到字节"，不代表"这首歌是坏的"。
 */
fun isIoLikeError(errorCode: Int): Boolean = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
    PlaybackException.ERROR_CODE_TIMEOUT -> true
    else -> false
}

/**
 * 纯函数：中途 IO 失败 → 停止而不是跳歌。
 * [positionMs] 出错时的播放位置，[durationMs] 已知时长（未知传 [C.TIME_UNSET] 或 ≤0）。
 */
fun shouldStopInsteadOfSkip(positionMs: Long, durationMs: Long, errorCode: Int): Boolean {
    if (!isIoLikeError(errorCode)) return false
    if (positionMs < MID_SONG_MIN_POSITION_MS) return false
    if (durationMs > 0 && positionMs >= durationMs - MID_SONG_TAIL_MS) return false
    return true
}

/**
 * 该 IO 错误说明"请求的位置已超出当前资源"，位置本身不可复用，重试必须从头开始。
 *
 * 现场（2026-11-06 用户报「暂停数小时后点播放，报 READ_POSITION_OUT_OF_RANGE 且再点也没用」）：
 * 服务端 `/api/songs/stream` 会把上游 CDN 的 416 伪装成 `200 + 202 字节 HTML`，
 * Media3 收到「200 + 非零 position」会自己 skip 掉 position 个字节 → 直接 EOF →
 * 抛这个错误码。它的含义不是"这一次没读到"，而是**"你问的起点在这个资源里不存在"**：
 * 同一位置再请求多少次都是同一个答案。所以只重试不换起点等于白试。
 *
 * 注意与"中途断流回原位置"的区别（见 [shouldRestorePositionAfterRetry]）：断流是
 * 暂时没拿到字节，原位置仍然有效；这个错误是位置本身失效，必须回 0。
 */
fun shouldRestartFromStart(errorCode: Int): Boolean =
    errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE

/**
 * 纯函数：这次同曲重试要不要把播放点带回 [positionMs]。
 *
 * 保住播放点只在"位置仍然有效"时才是体验优化：
 * - [midSong] = false（歌根本没起来/已到结尾）：默认值 0 就对了。
 * - [positionMs] <= 0：本来就在开头，无需 seek。
 * - [shouldRestartFromStart]：位置已越界（典型是重取到更短的音质文件，风控/VIP
 *   掉档后原位置永远超出新文件长度）→ 必须放弃原位置，否则二次 416，用户再点
 *   播放也永远失败。这是本条规则存在的全部理由，别为了"少一次 seek"把它删了。
 */
fun shouldRestorePositionAfterRetry(midSong: Boolean, positionMs: Long, errorCode: Int): Boolean =
    midSong && positionMs > 0L && !shouldRestartFromStart(errorCode)

/** Player 侧薄封装：读位置/时长失败按"没在中途"处理（宁可沿用旧行为，不误停）。 */
fun isMidSongFailure(player: Player, errorCode: Int): Boolean {
    val pos = try {
        player.currentPosition
    } catch (_: Exception) {
        return false
    }
    val dur = try {
        player.duration
    } catch (_: Exception) {
        C.TIME_UNSET
    }
    return shouldStopInsteadOfSkip(pos, dur, errorCode)
}

/**
 * 诊断码：错误名 + 位置/时长（`error:ERROR_CODE_IO_BAD_HTTP_STATUS@123456/231000`）。
 * 现场复现"中途跳歌"时，一条记录就能判定是"中途出错被跳"还是"提前 EOF 自动切轨"。
 */
fun errorDiagCode(player: Player, error: PlaybackException): String {
    val pos = try {
        player.currentPosition
    } catch (_: Exception) {
        -1L
    }
    val dur = try {
        player.duration
    } catch (_: Exception) {
        -1L
    }
    return "error:${error.errorCodeName}@$pos/$dur"
}
