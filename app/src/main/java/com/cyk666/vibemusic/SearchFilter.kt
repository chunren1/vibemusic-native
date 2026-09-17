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
    var liveAdded = 0
    for (s in liveResults) {
        if (liveAdded >= LIVE_SUGGEST_MAX || out.size >= SUGGEST_TOTAL_MAX) break
        val before = out.size
        add(s.name, SuggestSource.LIVE)
        if (out.size > before) liveAdded++
    }
    return out
}
