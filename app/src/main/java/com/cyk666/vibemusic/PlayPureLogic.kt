package com.cyk666.vibemusic

// 播放/恢复/睡眠定时纯逻辑（从 MainActivity 拆出；round6 T5）

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.media3.session.MediaController

/**
 * Reconnect policy (pure): rebind the MediaController when it is missing
 * (never connected / released after process death) or when the ON_START
 * probe threw (binder died with the service process while the reference
 * stayed non-null). A live controller is never rebound — rebinding it
 * would drop the listener and flicker playback state for no gain.
 */
fun shouldReconnectController(controllerNull: Boolean, probeFailed: Boolean): Boolean =
    controllerNull || probeFailed

/**
 * Connection-failure toast copy (pure): keeps the raw reason diagnosable
 * and tells the user the one thing that actually fixes OEM background
 * kills — self-start + ignore-battery-optimization（设置页"后台保活"一键直达，
 * 厂商路径见 KEEP_ALIVE_VENDOR_HINT）。The OS kill itself is outside app control
 * (see PlaybackService onTaskRemoved); this message is the documented guidance.
 */
fun controllerFailureMessage(reason: String): String =
    "播放器连接失败: $reason。后台常被杀会致通知栏播放键失灵，请到「我的→设置→后台保活」一键开启"

/**
 * Cold-start transport guard (pure): after process death the Activity restores
 * the queue LIST from DataStore but the fresh service player has an EMPTY
 * timeline (mediaItemCount == 0). Play/Next/Prev then operate on silence.
 * Materialize the player timeline from the Activity queue on demand.
 */
fun needsMaterialize(mediaItemCount: Int, queueSize: Int): Boolean =
    mediaItemCount == 0 && queueSize > 0

/**
 * Start position for (re)materialization (pure): with a known duration defer
 * to the standard mid-track restore window; duration unknown pre-prepare
 * (controller TIME_UNSET + no metadata) → restore when past the 5s intro and
 * let the player clamp naturally at the real duration.
 */
fun resolveMaterializePosition(savedMs: Long, durationMs: Long): Long {
    if (durationMs > 0L) return if (shouldRestorePosition(savedMs, durationMs)) savedMs else 0L
    return if (savedMs > POSITION_RESTORE_MIN_MS) savedMs else 0L
}

/**
 * Pure landing guard for playback transport (same idiom as
 * [isStalePlaylistSongs]): drop an async result that belongs to a
 * superseded playAt / queueSeekTo / queueRemoveAt (generation mismatch),
 * so a rapid double-tap on two songs can't cross-seek.
 */
fun isStalePlayGen(completedGen: Int, latestGen: Int): Boolean =
    completedGen != latestGen

/**
 * Pure: the queued song at [index] must still be [target] before an async
 * restore seek lands — guards the window where a list swap kept the
 * generation but moved the rows.
 */
fun isRestoreTargetCurrent(queue: List<Song>, index: Int, target: Song): Boolean =
    queue.getOrNull(index)?.sourceId == target.sourceId

/**
 * Pure: queue-page display list. playAt assigns timelineSongs alongside
 * queue, so the page follows list swaps; the empty fallback covers the
 * cold-start window before any timeline exists.
 */
fun resolveQueueTimeline(queue: List<Song>, timelineSongs: List<Song>): List<Song> =
    timelineSongs.ifEmpty { queue }

/** Placed single-enqueue result: new queue + index of the inserted song. */
data class EnqueueSingle(val queue: List<Song>, val index: Int)

/**
 * Pure: tap-a-search-result inserts ONLY that song right after the current
 * index (documented choice: it plays next, existing order otherwise
 * untouched). Empty queue → single-item queue at 0; out-of-range index
 * clamps to the tail. Never drops or reorders existing items.
 */
fun enqueueSingleAfterCurrent(
    queue: List<Song>,
    currentIndex: Int,
    song: Song
): EnqueueSingle {
    if (queue.isEmpty()) return EnqueueSingle(listOf(song), 0)
    val at = (currentIndex.coerceIn(queue.indices) + 1).coerceIn(0, queue.size)
    val out = ArrayList<Song>(queue.size + 1)
    out.addAll(queue.subList(0, at))
    out.add(song)
    out.addAll(queue.subList(at, queue.size))
    return EnqueueSingle(out, at)
}

/** Pure: absolute sleep-timer expiry (persisted form); non-positive minutes mean off. */
fun computeSleepDeadlineMs(nowMs: Long, minutes: Int): Long =
    if (minutes <= 0) 0L else nowMs + minutes * 60_000L

/** Pure: whole remaining seconds until [deadlineMs]; expired/missing deadlines read 0. */
fun sleepRemainingSec(deadlineMs: Long, nowMs: Long): Long =
    ((deadlineMs - nowMs) / 1000L).coerceAtLeast(0L)

/** Resolved sleep state at startup: total minutes shown, persisted deadline, live countdown. */
data class SleepRestore(val minutes: Int, val deadlineMs: Long, val leftSec: Long)

/**
 * Pure: resolve startup sleep state from the persisted deadline plus the
 * legacy total-minutes value. A live deadline wins (remaining = deadline -
 * now; expired deadline clears); a legacy minutes value with no deadline
 * (pre-deadline installs) restarts one fresh full-length window once, and
 * the caller overwrites it in deadline form.
 */
fun resolveSleepRestore(deadlineMs: Long, legacyMinutes: Int, nowMs: Long): SleepRestore {
    val legacy = legacyMinutes.coerceAtLeast(0)
    if (deadlineMs > nowMs) {
        val left = sleepRemainingSec(deadlineMs, nowMs)
        val minutes = if (legacy > 0) legacy else ((left + 59L) / 60L).toInt().coerceAtLeast(1)
        return SleepRestore(minutes, deadlineMs, left)
    }
    // A past deadline means the timer already fired: clear even when the
    // legacy minutes value lingers (its zero-write may have lost the race
    // with process death). Only a never-set deadline (0) with legacy
    // minutes migrates to one fresh full-length window.
    if (deadlineMs > 0L) return SleepRestore(0, 0L, 0L)
    if (legacy > 0) {
        return SleepRestore(legacy, computeSleepDeadlineMs(nowMs, legacy), legacy * 60L)
    }
    return SleepRestore(0, 0L, 0L)
}

const val SLEEP_CUSTOM_MIN = 5
const val SLEEP_CUSTOM_MAX = 180
val SLEEP_PRESETS = listOf(15, 30, 60)

fun parseSleepMinutes(input: String): Int? {
    val v = input.trim().toIntOrNull() ?: return null
    return if (v in SLEEP_CUSTOM_MIN..SLEEP_CUSTOM_MAX) v else null
}
