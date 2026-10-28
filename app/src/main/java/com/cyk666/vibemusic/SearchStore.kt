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
 * Pure: 历史条目的统一清洗 —— trim、丢空、去重（保留首次出现）、截断到 [cap]。
 *
 * 全仓四处（本地插入 / 云端两表合并 / 反序列化 / 上报 body）都必须走它：
 * 原来是四份各写各的去重，`distinct()` / `out.contains` / `kept.contains` 三种写法
 * 混用，改一处漏三处（CODE-REVIEW-2026-10-28 CODE-5）。
 *
 * [cap] 传 `Int.MAX_VALUE` 表示不截断（[foldPrefixHistory] 要先折叠再截）。
 */
fun dedupeSearchHistory(list: List<String>, cap: Int = SEARCH_HISTORY_MAX): List<String> {
    val out = ArrayList<String>(minOf(list.size, cap))
    for (item in list) {
        if (out.size >= cap) break
        val t = item.trim()
        if (t.isEmpty() || out.contains(t)) continue
        out.add(t)
    }
    return out
}

/**
 * Pure: 前缀折叠（输入为「最新在前」的列表）。
 *
 * 规则**单向**：只有当「较新的条目」以「较旧的条目」为前缀时，才丢掉较旧的那条。
 * 即"后来搜得更具体"才折叠，反过来不动。
 *
 * ```
 * 第三      → 第三人称     : 丢「第三」（输入法打到一半就搜，历史里全是递进前缀）
 * 第三人称  → 第三         : 两条都留（是两次不同的意图，不能因为"包含"就删）
 * 晴天      → 晴天 live    : 丢「晴天」（同一次意图的细化；已知取舍，见下）
 * ```
 *
 * ⚠ 已知取舍：`晴天` / `晴天 live` 这种"同一个词加限定词"也会被折叠。选择接受它，
 * 因为反向规则（双向包含）会把「周杰伦」和「周杰伦 稻香」都吃掉，误伤更大。
 * 折叠只影响"谁留在历史里"，不影响搜索能力——被折掉的词仍可从联想/热词一键再搜。
 *
 * 2026-10-27 用户反馈：输入「第三」停顿即自动搜索并写历史，再搜「第三人称」就出现
 * 两条。根因是自动搜索也记历史（已改为只记显式搜索），此函数负责清掉已经脏掉的历史。
 */
fun foldPrefixHistory(list: List<String>): List<String> {
    val kept = ArrayList<String>(list.size)
    // 先统一清洗（trim/去重/丢空），再逐条判断前缀 —— 两件事拆开，规则只有一份。
    for (t in dedupeSearchHistory(list, Int.MAX_VALUE)) {   // 最新在前
        // kept 里的都更新；只要有一条更新的以 t 为前缀，t 就是"半成品"
        if (kept.any { it.startsWith(t) }) continue
        kept.add(t)
    }
    return kept
}

/**
 * Pure helper: prepend trimmed [query] latest-first, dedup, 折叠前缀, cap at [cap].
 * Blank queries leave [existing] untouched.
 */
fun mergeSearchHistory(
    existing: List<String>,
    query: String,
    cap: Int = SEARCH_HISTORY_MAX
): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return existing
    val merged = ArrayList<String>(cap + 1)
    merged.add(q)
    for (h in existing) merged.add(h)
    return foldPrefixHistory(merged).take(cap)
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
            val raw2 = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) raw2.add(arr.optString(i, ""))
            // 读侧**不做**前缀折叠（折叠是写侧的事，读侧改了历史却不落盘反而更乱），
            // 只共用 trim/去重/截断这一份规则。
            dedupeSearchHistory(raw2)
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

    /**
     * Bulk-replace history (cloud-sync merge write path). Trims, dedups
     * latest-first, caps at [SEARCH_HISTORY_MAX]. Never throws to callers
     * beyond DataStore itself (sync callers wrap in try/catch).
     */
    suspend fun saveHistory(context: Context, history: List<String>): List<String> {
        var next: List<String> = emptyList()
        context.searchDataStore.edit { p ->
            val seen = LinkedHashSet<String>()
            for (h in history) {
                val t = h.trim()
                if (t.isNotEmpty()) seen.add(t)
                if (seen.size >= SEARCH_HISTORY_MAX) break
            }
            next = seen.toList()
            p[KEY_HISTORY] = encode(next)
        }
        return next
    }

    suspend fun loadSort(context: Context): SearchSort =
        context.searchDataStore.data.map { p ->
            SearchSort.fromName(p[KEY_SORT])
        }.first()

    suspend fun saveSort(context: Context, sort: SearchSort) {
        context.searchDataStore.edit { p -> p[KEY_SORT] = sort.name }
    }
}
