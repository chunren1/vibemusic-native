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
 * Launch rule: the search screen starts with a BLANK query and never
 * auto-searches on cold start. Only a non-blank user-entered query may
 * trigger a search (manual IME action, history/hotword tap, or the 500ms
 * debounce on keystrokes). Blank input keeps history chips + hotwords +
 * the search-tab 猜你喜欢 section instead.
 */
fun shouldAutoSearchOnLaunch(query: String): Boolean = query.trim().isNotEmpty()

/** Suggestion source tag for the 联想 dropdown (history first, then hotwords, then live). */
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
