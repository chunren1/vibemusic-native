package com.cyk666.vibemusic

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory tally of swallowed exceptions.
 *
 * This app swallows failures on purpose in many best-effort paths (cache
 * writes, cache reads, optional UI work), but until now a swallowed failure
 * left no trace at all — "it silently did nothing" was untriageable. This is
 * the cheap evidence layer for exactly those catch blocks.
 *
 * Why not [PlayDiag]: that one parses a JSON ring and writes SharedPreferences
 * on every call. Putting it inside hot catch paths would turn a swallowed
 * exception into disk I/O, i.e. the cure would be worse than the disease.
 * This keeps two in-memory structures only:
 *
 *  - a per-code counter (what failed, how often)
 *  - a bounded ring of the last [RING_MAX] codes (ordering for crash triage)
 *
 * Nothing is persisted. The value is realized in-process: Bugly's crash
 * callback reads [summary] at crash time (see `VibeApp.initBugly`), so a crash
 * report carries "which silent paths had already been failing" without any
 * steady-state cost. Allocation is one counter increment plus a short string
 * constant; safe from any thread, never throws, never blocks on disk.
 */
object SilentFail {

    /** Bounded ordering buffer: enough to see a crash's immediate lead-up. */
    const val RING_MAX = 24

    /** Distinct codes kept before the counter map is considered saturated. */
    const val DISTINCT_MAX = 64

    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val ring = ArrayDeque<String>(RING_MAX)
    private val lock = Any()

    /** Wall-clock ms of the most recent note; 0 when nothing was swallowed yet. */
    @Volatile
    var lastTsMs: Long = 0L
        private set

    /**
     * Record one swallowed failure. [code] should be stable and greppable,
     * e.g. `"PlaybackService.restoreQueue"` — not a free-form message.
     */
    fun note(code: String) {
        if (code.isBlank()) return
        val counter = counts[code]
        when {
            counter != null -> counter.incrementAndGet()
            counts.size < DISTINCT_MAX -> counts.putIfAbsent(code, AtomicInteger(1))
            // Saturated: drop entirely rather than half-record it (a code in the
            // ring but absent from the counters would make the summary lie).
            else -> return
        }
        lastTsMs = System.currentTimeMillis()
        synchronized(lock) {
            if (ring.size >= RING_MAX) ring.removeFirst()
            ring.addLast(code)
        }
    }

    /**
     * One-line triage summary for crash reports:
     * `total=7 distinct=3 top=A x4,B x2,C x1 recent=A>B>C`.
     * Empty string when nothing has been swallowed (keeps Bugly payload clean).
     */
    fun summary(): String {
        if (counts.isEmpty()) return ""
        val total = counts.values.sumOf { it.get() }
        val top = counts.entries
            .sortedByDescending { it.value.get() }
            .take(6)
            .joinToString(",") { "${it.key} x${it.value.get()}" }
        val recent = synchronized(lock) { ring.joinToString(">") }
        return buildString {
            append("total=").append(total)
            append(" distinct=").append(counts.size)
            append(" top=").append(top)
            if (recent.isNotEmpty()) append(" recent=").append(recent)
        }
    }

    /** Test hook: clear all state so cases stay independent. */
    fun resetForTest() {
        counts.clear()
        synchronized(lock) { ring.clear() }
        lastTsMs = 0L
    }
}
