package com.cyk666.vibemusic

import androidx.media3.common.Player

/**
 * Phase 7: playback modes. Cycle order: 顺序 → 列表循环 → 单曲循环 → 随机.
 *
 * Mapping (single source of truth, pure + top-level testable):
 * - 顺序 (SEQUENTIAL)  = repeat OFF  + shuffle OFF
 * - 列表循环 (LIST_LOOP) = repeat ALL  + shuffle OFF
 * - 单曲循环 (SINGLE_LOOP) = repeat ONE  (shuffle forced OFF for a clean truth table)
 * - 随机 (SHUFFLE)     = repeat ALL  + shuffle ON
 */
enum class PlayMode(val label: String) {
    SEQUENTIAL("顺序"),
    LIST_LOOP("列表循环"),
    SINGLE_LOOP("单曲循环"),
    SHUFFLE("随机");

    fun next(): PlayMode = when (this) {
        SEQUENTIAL -> LIST_LOOP
        LIST_LOOP -> SINGLE_LOOP
        SINGLE_LOOP -> SHUFFLE
        SHUFFLE -> SEQUENTIAL
    }
}

data class RepeatShuffle(val repeatMode: Int, val shuffleOn: Boolean)

fun PlayMode.toRepeatShuffle(): RepeatShuffle = when (this) {
    PlayMode.SEQUENTIAL -> RepeatShuffle(Player.REPEAT_MODE_OFF, false)
    PlayMode.LIST_LOOP -> RepeatShuffle(Player.REPEAT_MODE_ALL, false)
    PlayMode.SINGLE_LOOP -> RepeatShuffle(Player.REPEAT_MODE_ONE, false)
    PlayMode.SHUFFLE -> RepeatShuffle(Player.REPEAT_MODE_ALL, true)
}

fun playModeFrom(repeatMode: Int, shuffleOn: Boolean): PlayMode = when {
    repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.SINGLE_LOOP
    shuffleOn && repeatMode == Player.REPEAT_MODE_ALL -> PlayMode.SHUFFLE
    repeatMode == Player.REPEAT_MODE_ALL -> PlayMode.LIST_LOOP
    else -> PlayMode.SEQUENTIAL
}
