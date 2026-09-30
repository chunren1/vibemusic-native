package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val SEARCH_HISTORY_PUSH_DEBOUNCE_MS = 800L

/**
 * Pure union: local-first (local is freshest), remote fills gaps,
 * dedup by trimmed keyword, most-recent-first, capped.
 */
fun mergeSearchHistories(
    local: List<String>,
    remote: List<String>,
    cap: Int = SEARCH_HISTORY_MAX
): List<String> {
    val out = ArrayList<String>(cap)
    fun addAll(list: List<String>) {
        for (w in list) {
            if (out.size >= cap) return
            val t = w.trim()
            if (t.isEmpty() || out.contains(t)) continue
            out.add(t)
        }
    }
    addAll(local)
    addAll(remote)
    return out
}

/**
 * Offline-first search-history cloud sync. Every entry point swallows all
 * exceptions: network failure never blocks UI and never throws to callers.
 * No-op for guests (blank token). Never logs tokens (keywords only).
 */
object SearchHistorySync {
    @Volatile
    private var pushJob: Job? = null

    fun schedulePush(scope: CoroutineScope, snapshot: List<String>) {
        if (AuthToken.token.isBlank()) return
        pushJob?.cancel()
        val copy = snapshot.toList()
        pushJob = scope.launch {
            try {
                delay(SEARCH_HISTORY_PUSH_DEBOUNCE_MS)
                VibeApi.pushSearchHistory(copy)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun pullAndMerge(context: Context): List<String>? {
        if (AuthToken.token.isBlank()) return null
        return try {
            val remote = VibeApi.pullSearchHistory()
            val local = try {
                SearchStore.loadHistory(context)
            } catch (_: Exception) {
                emptyList()
            }
            val merged = mergeSearchHistories(local, remote)
            try {
                SearchStore.saveHistory(context, merged)
            } catch (_: Exception) {
            }
            try {
                VibeApi.pushSearchHistory(merged)
            } catch (_: Exception) {
            }
            merged
        } catch (_: Exception) {
            null
        }
    }
}
