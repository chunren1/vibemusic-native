package com.cyk666.vibemusic

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.searchDataStore by preferencesDataStore(
    name = "search",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

const val SEARCH_HISTORY_MAX = 10

/** Neutral hardcoded hotwords shown when the query box is blank. */
val SEARCH_HOTWORDS: List<String> = listOf(
    "予以",
    "来不及爱你",
    "周杰伦",
    "林俊杰",
    "邓紫棋",
    "陈奕迅"
)

/**
 * Pure helper: prepend trimmed [query] latest-first, dedup, cap at [cap].
 * Blank queries leave [existing] untouched.
 */
fun mergeSearchHistory(
    existing: List<String>,
    query: String,
    cap: Int = SEARCH_HISTORY_MAX
): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return existing
    val out = ArrayList<String>(cap + 1)
    out.add(q)
    for (h in existing) {
        if (h != q && !out.contains(h)) out.add(h)
        if (out.size >= cap) break
    }
    return out
}

/** Search history (max 10, dedup latest-first) + persisted sort choice. New "search" file. */
object SearchStore {
    private val KEY_HISTORY = stringPreferencesKey("history_json")
    private val KEY_SORT = stringPreferencesKey("sort")

    private fun encode(history: List<String>): String {
        val arr = JSONArray()
        for (h in history) arr.put(h)
        return arr.toString()
    }

    private fun decode(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty() && !out.contains(s)) out.add(s)
            }
            out.take(SEARCH_HISTORY_MAX)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun loadHistory(context: Context): List<String> =
        context.searchDataStore.data.map { p -> decode(p[KEY_HISTORY].orEmpty()) }.first()

    /** Records a successful non-blank search; returns the new history. */
    suspend fun addHistory(context: Context, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return loadHistory(context)
        var next: List<String> = emptyList()
        context.searchDataStore.edit { p ->
            next = mergeSearchHistory(decode(p[KEY_HISTORY].orEmpty()), q)
            p[KEY_HISTORY] = encode(next)
        }
        return next
    }

    suspend fun removeHistory(context: Context, query: String): List<String> {
        var next: List<String> = emptyList()
        context.searchDataStore.edit { p ->
            next = decode(p[KEY_HISTORY].orEmpty()).filter { it != query.trim() }
            p[KEY_HISTORY] = encode(next)
        }
        return next
    }

    suspend fun clearHistory(context: Context) {
        context.searchDataStore.edit { p -> p.remove(KEY_HISTORY) }
    }

    suspend fun loadSort(context: Context): SearchSort =
        context.searchDataStore.data.map { p ->
            SearchSort.fromName(p[KEY_SORT])
        }.first()

    suspend fun saveSort(context: Context, sort: SearchSort) {
        context.searchDataStore.edit { p -> p[KEY_SORT] = sort.name }
    }
}
