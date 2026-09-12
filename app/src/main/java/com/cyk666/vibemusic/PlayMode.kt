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

/**
 * Manual Next/Prev action at a queue boundary (pure truth table, top-level
 * testable). Fixes the queue-end bug where `seekTo(0L)` only rewinds the
 * current track instead of wrapping:
 * - mid-queue: ADVANCE (plain seekToNext/PreviousMediaItem).
 * - at end/start in 顺序/列表循环/随机: wrap to first/last track.
 * - at end/start in 单曲循环: STAY (explicit no-op + user-visible hint).
 */
enum class BoundaryAction { ADVANCE, WRAP_TO_FIRST, WRAP_TO_LAST, STAY }

fun nextBoundaryAction(mode: PlayMode, hasNext: Boolean): BoundaryAction = when {
    hasNext -> BoundaryAction.ADVANCE
    mode == PlayMode.SINGLE_LOOP -> BoundaryAction.STAY
    else -> BoundaryAction.WRAP_TO_FIRST
}

fun prevBoundaryAction(mode: PlayMode, hasPrevious: Boolean): BoundaryAction = when {
    hasPrevious -> BoundaryAction.ADVANCE
    mode == PlayMode.SINGLE_LOOP -> BoundaryAction.STAY
    else -> BoundaryAction.WRAP_TO_LAST
}

/** Icon-only mode button glyph per mode (player action row, no text label). */
fun playModeIconKind(mode: PlayMode): AppIconKind = when (mode) {    PlayMode.SEQUENTIAL -> AppIconKind.MODE_SEQUENTIAL
    PlayMode.LIST_LOOP -> AppIconKind.MODE_LOOP
    PlayMode.SINGLE_LOOP -> AppIconKind.MODE_SINGLE
    PlayMode.SHUFFLE -> AppIconKind.MODE_SHUFFLE
}

/** Transient Snackbar text on mode change ("已切换到：随机播放"). */
fun playModeAnnouncement(mode: PlayMode): String = "已切换到：" + mode.label + "播放"

/**
 * Slim meta line under the player controls: cache badge + active sleep
 * timer as muted text (no buttons). Null when there is nothing to show so
 * callers emit no dead whitespace.
 */
fun buildPlayerMetaLine(
    isCached: Boolean,
    sleepActive: Boolean,
    sleepLabel: String
): String? {
    val parts = buildList {
        if (isCached) add("已缓存")
        if (sleepActive) add(sleepLabel)
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}
