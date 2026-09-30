package com.cyk666.vibemusic

/** Client-side sort orders for search results. RELEVANCE = backend order, untouched. */
enum class SearchSort {
    RELEVANCE,
    DURATION_ASC,
    DURATION_DESC,
    ARTIST_NAME;

    companion object {
        fun fromName(name: String?): SearchSort = try {
            if (name.isNullOrBlank()) RELEVANCE else valueOf(name)
        } catch (_: IllegalArgumentException) {
            RELEVANCE
        }
    }
}

/** Distinct artist names in first-seen order, capped (default 8) for filter chips. */
fun distinctArtists(songs: List<Song>, cap: Int = 8): List<String> {
    if (songs.isEmpty() || cap <= 0) return emptyList()
    val out = ArrayList<String>(cap)
    for (s in songs) {
        val a = s.artist.trim()
        if (a.isEmpty() || out.contains(a)) continue
        out.add(a)
        if (out.size >= cap) break
    }
    return out
}

/**
 * Pure client-side filter + sort. No network, no side effects.
 * @param artistFilter null/blank = All artists, otherwise exact match on trimmed name.
 */
fun filterAndSortSongs(
    songs: List<Song>,
    artistFilter: String?,
    sort: SearchSort
): List<Song> {
    val filter = artistFilter?.trim().orEmpty()
    val filtered = if (filter.isEmpty()) songs
    else songs.filter { it.artist.trim() == filter }
    return when (sort) {
        SearchSort.RELEVANCE -> filtered
        SearchSort.DURATION_ASC -> filtered.sortedBy { it.durationSec }
        SearchSort.DURATION_DESC -> filtered.sortedByDescending { it.durationSec }
        SearchSort.ARTIST_NAME -> filtered.sortedWith(
            compareBy({ it.artist.trim() }, { it.name.trim() }, { it.sourceId })
        )
    }
}

/**
 * Generation-counter guard: only the latest search completion may update UI.
 * @return true when [completedGen] is stale and its result must be dropped.
 */
fun isStaleSearchResult(completedGen: Int, latestGen: Int): Boolean =
    completedGen != latestGen

/**
 * Clearable search UI state snapshot. Tapping a search result plays it AND
 * resets this whole snapshot (see [clearedSearchAfterPlay]) so Back lands
 * on a clean search page — never the stale query/results.
 */
data class SearchViewState(
    val query: String = "",
    val results: List<Song> = emptyList(),
    val total: Int = 0,
    val searched: Boolean = false,
    val liveQuery: String = "",
    val artistFilter: String? = null,
    val suggestVisible: Boolean = true,
    val error: String? = null
)

/**
 * Pure: search-play navigation — clear query + results + suggestions in one
 * step. History chips/hotwords (built from persisted stores, not this state)
 * are untouched, so the clean page still offers entry points.
 */
fun clearedSearchAfterPlay(state: SearchViewState): SearchViewState = state.copy(
    query = "",
    results = emptyList(),
    total = 0,
    searched = false,
    liveQuery = "",
    artistFilter = null,
    suggestVisible = false,
    error = null
)

/**
 * R4-A1 gate-before-clear: pure verdict for the search-result tap handler.
 * The search snapshot clears ONLY when the tap will actually start playback
 * (playability gate passed + generation fresh); a stale landing or a failed
 * gate (e.g. offline tap on an unplayable result) keeps [current] intact so
 * the search page stays restorable. Callers must still early-return on the
 * stale/gate-fail paths — this helper only decides the state, never acts.
 */
fun searchStateAfterPlayTap(
    current: SearchViewState,
    gatePlayable: Boolean,
    isStale: Boolean
): SearchViewState =
    if (!isStale && gatePlayable) clearedSearchAfterPlay(current) else current

/** Search body branch: mirrors the SearchScreen when-chain (single source of truth for tests). */
enum class SearchBody {
    LOADING,
    HISTORY,
    IDLE,
    ERROR_RETRY,
    STALE_WITH_ERROR,
    RESULTS,
    NO_RESULT,
    FILTER_EMPTY
}

/**
 * Pure selector for the search body branch.
 * error + no results → ERROR_RETRY (DiscoverRetryRow);
 * error + stale results → STALE_WITH_ERROR (error line above rows).
 */
fun selectSearchBody(
    loading: Boolean,
    queryBlank: Boolean,
    searched: Boolean,
    error: String?,
    hasResults: Boolean,
    hasVisible: Boolean
): SearchBody = when {
    loading -> SearchBody.LOADING
    queryBlank -> SearchBody.HISTORY
    !searched && error == null -> SearchBody.IDLE
    error != null && !hasResults -> SearchBody.ERROR_RETRY
    error != null -> SearchBody.STALE_WITH_ERROR
    !hasVisible && hasResults -> SearchBody.FILTER_EMPTY
    !hasVisible -> SearchBody.NO_RESULT
    else -> SearchBody.RESULTS
}

/**
 * Launch rule: the search screen starts with a BLANK query and never
 * auto-searches on cold start. Only a non-blank user-entered query may
 * trigger a search (manual IME action, history/hotword tap, or the 500ms
 * debounce on keystrokes). Blank input keeps history chips + hotwords +
 * the search-tab 猜你喜欢 section instead.
 */
fun shouldAutoSearchOnLaunch(query: String): Boolean = query.trim().isNotEmpty()

/** Repeat searches within this window render instantly from memory, no network. */
const val SEARCH_CACHE_TTL_MS = 5 * 60 * 1000L

/** Process-lifetime repeat-search entries; beyond this the oldest drops. */
const val SEARCH_CACHE_MAX_ENTRIES = 20

/** One cached search: the exact list/total the network returned, plus write time. */
data class CachedSearch(val songs: List<Song>, val total: Int, val atMs: Long)

/**
 * Process-lifetime LRU of recent search results. Key is the trimmed keyword
 * ([VibeApi.search] always runs page 1 / size 20, so no page dim needed).
 * Fav hearts stay live: rows render from the ambient favIds set, never from
 * the cached Song objects. Pass [nowMs] in tests; production uses the clock.
 */
class SearchResultCache(
    private val maxEntries: Int = SEARCH_CACHE_MAX_ENTRIES,
    private val ttlMs: Long = SEARCH_CACHE_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val map = LinkedHashMap<String, CachedSearch>(maxEntries, 0.75f, true)

    fun get(keyword: String, nowMs: Long = clock()): CachedSearch? {
        val entry = map[keyword.trim()] ?: return null
        if (nowMs - entry.atMs > ttlMs) {
            map.remove(keyword.trim())
            return null
        }
        return entry
    }

    fun put(keyword: String, songs: List<Song>, total: Int, nowMs: Long = clock()) {
        val key = keyword.trim()
        if (key.isEmpty()) return
        map[key] = CachedSearch(songs, total, nowMs)
        while (map.size > maxEntries) {
            map.remove(map.keys.first())
        }
    }

    fun clear() {
        map.clear()
    }

    fun size(): Int = map.size
}

/**
 * 联想 overlay 可见性事件（dismiss/re-show 触发器，纯状态机）：
 * SELECT = 点选一条联想/历史项（立即隐藏，结果干净露出）；
 * SEARCH_PRESS = 显式按下搜索按钮/IME Search（唯一重现路径）；
 * QUERY_CHANGE = 键入改字（沿用 500ms debounce 自动搜，但绝不重现 overlay）。
 */
enum class SuggestOverlayEvent {
    QUERY_CHANGE,
    SELECT,
    SEARCH_PRESS,
}

/**
 * Pure 联想 overlay 可见性归约：SELECT 直接灭，SEARCH_PRESS 重现，
 * QUERY_CHANGE 保持现状（结果可见时键入不会把 overlay 带回来）。
 */
fun reduceSuggestOverlayVisible(
    current: Boolean,
    event: SuggestOverlayEvent
): Boolean = when (event) {
    SuggestOverlayEvent.QUERY_CHANGE -> current
    SuggestOverlayEvent.SELECT -> false
    SuggestOverlayEvent.SEARCH_PRESS -> true
}

/**
 * Pure 联想 overlay 显示门：flag 开且确有候选项才盖住结果区。
 * 空 query 本就无候选（buildSuggestions 回空），此处只管 flag 门。
 */
fun shouldShowSuggestOverlay(visibleFlag: Boolean, hasSuggestions: Boolean): Boolean =
    visibleFlag && hasSuggestions
enum class SuggestSource(val label: String) {
    HISTORY("历史"),
    HOTWORD("热搜"),
    LIVE("相关")
}

data class Suggestion(val text: String, val source: SuggestSource)

/** Max live-result suggestions taken from the latest completed search. */
const val LIVE_SUGGEST_MAX = 5

/** Max total rows in the 联想 dropdown. */
const val SUGGEST_TOTAL_MAX = 8

/**
 * 拼音全拼/首字母 → 标准热词（与后端 SearchQueryHints.PINYIN_ALIASES 同源；
 * 热词增删时两边同步。全量拼音库因包体积暂缓，此处仅覆盖热词）。
 */
val SEARCH_PINYIN_ALIASES: Map<String, String> = mapOf(
    "zhoujielun" to "周杰伦",
    "zjl" to "周杰伦",
    "chenyixun" to "陈奕迅",
    "cyx" to "陈奕迅",
    "linjunjie" to "林俊杰",
    "ljj" to "林俊杰",
    "dengziqi" to "邓紫棋",
    "dzq" to "邓紫棋",
    "qingtian" to "晴天",
    "qt" to "晴天",
    "daoxiang" to "稻香",
    "dx" to "稻香",
    "yequ" to "夜曲",
    "yq" to "夜曲",
    "qilixiang" to "七里香",
    "qlx" to "七里香",
    "gaobaiqiqiu" to "告白气球",
    "gbqq" to "告白气球",
    "rege" to "热歌",
    "rg" to "热歌"
)

/**
 * 归一化搜索输入：去首尾空白、转小写、压缩连续空白（CJK 原样保留）。
 * 全半角折叠由后端 NFKC 承担，此处只做联想匹配用的轻归一。
 */
fun normalizeSearchInput(input: String): String =
    input.trim().lowercase().replace(Regex("\\s+"), " ")

/**
 * 拼音/首字母解析：命中别名返回标准词，否则 null（大小写与空格不敏感）。
 */
fun resolveSearchAlias(input: String): String? {
    val key = normalizeSearchInput(input).replace(" ", "")
    if (key.isEmpty()) return null
    return SEARCH_PINYIN_ALIASES[key]
}

/** 分源重查平台（code 走后端 platform 参数，label 仅展示）。 */
val SEARCH_PLATFORMS: List<Pair<String, String>> = listOf(
    "netease" to "网易云",
    "qq" to "QQ",
    "migu" to "咪咕",
    "kugou" to "酷狗",
    "bilibili" to "B站"
)

/**
 * Pure 联想 builder: history substring matches first, then hotword substring
 * matches, then live top-5 song names (the caller gates [liveResults] to the
 * latest completed search via the generation counter — only pass them when
 * the input still equals the query that produced them). Blank input returns
 * empty (the blank box keeps its history/hotword chips instead).
 */
fun buildSuggestions(
    history: List<String>,
    hotwords: List<String>,
    liveResults: List<Song>,
    input: String
): List<Suggestion> {
    val q = input.trim()
    if (q.isEmpty()) return emptyList()
    val out = ArrayList<Suggestion>(SUGGEST_TOTAL_MAX)
    val seen = HashSet<String>()
    fun add(text: String, source: SuggestSource) {
        if (out.size >= SUGGEST_TOTAL_MAX) return
        val t = text.trim()
        if (t.isEmpty() || !seen.add(t)) return
        out.add(Suggestion(t, source))
    }
    for (h in history) {
        if (h.contains(q, ignoreCase = true)) add(h, SuggestSource.HISTORY)
    }
    for (w in hotwords) {
        if (w.contains(q, ignoreCase = true)) add(w, SuggestSource.HOTWORD)
    }
    // 拼音/首字母直达（如 zjl → 周杰伦）：无匹配时不加行，有则去重并入
    resolveSearchAlias(q)?.let { add(it, SuggestSource.HOTWORD) }
    var liveAdded = 0
    for (s in liveResults) {
        if (liveAdded >= LIVE_SUGGEST_MAX || out.size >= SUGGEST_TOTAL_MAX) break
        val before = out.size
        add(s.name, SuggestSource.LIVE)
        if (out.size > before) liveAdded++
    }
    return out
}
