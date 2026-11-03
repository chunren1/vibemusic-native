package com.cyk666.vibemusic

import androidx.media3.common.Player

/**
 * 自动重试 / 自动跳歌 / 自愈重放落地前的"意愿门"。
 *
 * 背景（2026-11-03 播放链路审计 P2）：只有弱网退避那一条路径（PB-3）问过"用户此刻
 * 还想播吗"，签名 URL 重试、在线跳歌、离线跳歌、Activity 自愈全是无条件
 * `prepare() + play()` —— 用户在错误回投的那几百毫秒里按了暂停，播放会自行恢复。
 *
 * 语义与 `shouldFireRetryProbe` 同源：`playWhenReady` 是"意愿"，`isPlaying` 是"事实"
 * （缓冲中/重建中 isPlaying 可能短暂为 false，但意愿还在）。
 */
fun shouldResumePlayback(playWhenReady: Boolean, isPlaying: Boolean): Boolean =
    playWhenReady || isPlaying

/** Player 侧薄封装：读标志失败（controller 断开）时按"不恢复"处理，宁可静音不抢用户意图。 */
fun playerWantsPlay(player: Player): Boolean = try {
    shouldResumePlayback(player.playWhenReady, player.isPlaying)
} catch (_: Exception) {
    false
}

/**
 * `BasePlayer.play()` 的字节码就是 `setPlayWhenReady(true)` —— 播完（STATE_ENDED）后
 * `playWhenReady` 仍为 true，直接 play() 状态零变化，用户点播放键毫无反应。
 * 所以"播完再按播放"必须先归位一次。
 */
fun needsRewindBeforePlay(playbackState: Int): Boolean =
    playbackState == Player.STATE_ENDED
