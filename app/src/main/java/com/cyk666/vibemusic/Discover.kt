package com.cyk666.vibemusic

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Phase 9: Discover tab data layer.
 *
 * Backend contracts (read-only, confirmed from controller/DTO source):
 * - GET /api/songs/banner → data [{coverUrl, playCount, name, desc}] (no id; display-only)
 * - GET /api/recommend/personalized?refresh= → data RecommendResult {songs, greeting, reason, type}
 * - GET /api/songs/random?count=8 → data [SongDTO...] {sourceId,name,artist,album,coverUrl,duration,platform}
 * - GET /api/playlists/recommend → data [{id,name,coverUrl,desc,count,source}]
 *   (controller remaps the cached {picUrl,copywriter,playCount} shape before
 *   returning — parse BOTH key variants defensively).
 *
 * Covers load directly via https URLs with Coil (direct CDN works in-app today;
 * GET /api/image-proxy?url= exists as fallback but is NOT used unless a load fails).
 * Payloads are cached in memory only (Activity state, 10-min TTL); no DataStore.
 */

/** Banner item: display-only (backend provides no id/link). */
data class DiscoverBanner(
    val name: String,
    val coverUrl: String,
    val desc: String = "",
    val playCount: Long = 0L
)

/** GET /api/recommend/personalized result (guest OK, auth-aware server-side). */
data class PersonalizedResult(
    val songs: List<Song>,
    val reason: String = "",
    val greeting: String = "",
    val type: String = ""
)

/** GET /api/playlists/recommend item (tap → import confirm, source=netease). */
data class RecommendPlaylist(
    val id: String,
    val name: String,
    val picUrl: String,
    val copywriter: String = "",
    val playCount: Long = 0L
)

/** In-memory TTL: reload on tab revisit only when older than 10 min. */
const val DISCOVER_CACHE_TTL_MS = 10 * 60 * 1000L

/**
 * Banner width/height ratio (NetEase web-banner ratio 2.35:1). Fixed ratio
 * by design: measuring Coil intrinsic sizes per image would relayout the
 * carousel on every load (jank + per-region skeleton mismatch), so the
 * skeleton and the pager share this constant. If a measured ratio is ever
 * available, [selectBannerAspect] prefers it with this as fallback.
 */
const val BANNER_ASPECT_RATIO = 2.35f

/** Pure: measured width/height wins when finite and positive, else [BANNER_ASPECT_RATIO]. */
fun selectBannerAspect(measuredRatio: Float?): Float =
    if (measuredRatio != null && measuredRatio.isFinite() && measuredRatio > 0f) {
        measuredRatio
    } else {
        BANNER_ASPECT_RATIO
    }

/** Pure: true when never loaded or older than [DISCOVER_CACHE_TTL_MS]. */
fun isDiscoverStale(lastLoadedMs: Long, nowMs: Long): Boolean {
    if (lastLoadedMs <= 0L) return true
    return nowMs - lastLoadedMs >= DISCOVER_CACHE_TTL_MS
}

/** Pure: GET /api/recommend/personalized path (refresh=true bypasses server cache). */
fun buildPersonalizedPath(refresh: Boolean): String =
    "api/recommend/personalized?refresh=$refresh"

/** Pure: GET /api/songs/random path. */
fun buildRandomPath(count: Int): String =
    "api/songs/random?count=" + count.coerceAtLeast(1)

/**
 * Pure cover gate: a HOME track renders only when it has a cover URL.
 * Applied ONLY to HOME rows (Daily/Encounter/Treasure + 新歌速递);
 * search/playlist pages keep their default-note placeholder path and
 * must NOT use this filter.
 */
fun hasCover(song: Song): Boolean = song.coverUrl.isNotBlank()

/** Pure: HOME-visible tracks = cover-gated, order preserved. */
fun homeVisibleSongs(songs: List<Song>): List<Song> = songs.filter(::hasCover)

/** Pure: a HOME treasure cell renders only when it has a cover URL. */
fun hasPlaylistCover(playlist: RecommendPlaylist): Boolean = playlist.picUrl.isNotBlank()

/** Pure: HOME-visible treasure playlists = cover-gated, order preserved. */
fun homeVisiblePlaylists(playlists: List<RecommendPlaylist>): List<RecommendPlaylist> =
    playlists.filter(::hasPlaylistCover)

/** Pure: compact play-count copy (125000 → "12.5万"). */
fun formatPlayCount(count: Long): String {
    if (count < 0) return "0"
    if (count < 10_000) return count.toString()
    if (count < 100_000_000) {
        val s = "%.1f".format(Locale.US, count / 10_000.0).trimEnd('0').trimEnd('.')
        return s + "万"
    }
    val s = "%.1f".format(Locale.US, count / 100_000_000.0).trimEnd('0').trimEnd('.')
    return s + "亿"
}

private fun checkDiscoverEnvelope(root: JSONObject, what: String): JSONObject {
    val code = root.optInt("code", -1)
    if (code == 401) throw AuthException("密码错/登录过期，请重登")
    if (code != 200) {
        throw RuntimeException(
            "$what failed: code=$code ${root.optString("message", "unknown error")}"
        )
    }
    return root
}

private fun optLongFlexible(o: JSONObject, vararg keys: String): Long {
    for (k in keys) {
        if (!o.has(k) || o.isNull(k)) continue
        when (val v = o.opt(k)) {
            is Number -> return v.toLong()
            is String -> v.toLongOrNull()?.let { return it }
            else -> {}
        }
    }
    return 0L
}

private fun discoverId(o: JSONObject): String {
    if (!o.has("id") || o.isNull("id")) return ""
    val v = o.opt("id")?.toString().orEmpty()
    return if (v.isBlank() || v == "null") "" else v
}

/** Pure: parse GET /api/songs/banner (missing desc → "", missing playCount → 0). */
fun parseDiscoverBanners(json: String): List<DiscoverBanner> {
    val root = JSONObject(json)
    checkDiscoverEnvelope(root, "Banner")
    val arr: JSONArray = root.optJSONArray("data") ?: return emptyList()
    val out = ArrayList<DiscoverBanner>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(
            DiscoverBanner(
                name = o.optString("name"),
                coverUrl = o.optString("coverUrl").ifBlank { o.optString("picUrl") },
                desc = o.optString("desc"),
                playCount = optLongFlexible(o, "playCount")
            )
        )
    }
    return out
}

/**
 * Pure: parse GET /api/recommend/personalized defensively.
 * Missing reason/greeting/type → ""; missing songs → empty list (never throws
 * for absent optionals; throws only on non-200 envelope / absent data).
 */
fun parsePersonalized(json: String): PersonalizedResult {
    val root = JSONObject(json)
    checkDiscoverEnvelope(root, "Personalized")
    val data = root.optJSONObject("data")
    if (data == null) {
        // Tolerate a bare-array data shape (song list without reason).
        val arr = root.optJSONArray("data")
            ?: throw RuntimeException("Personalized: missing data")
        return PersonalizedResult(songs = arr.toDiscoverSongs())
    }
    val songsArr = data.optJSONArray("songs")
        ?: data.optJSONArray("list")
        ?: JSONArray()
    return PersonalizedResult(
        songs = songsArr.toDiscoverSongs(),
        reason = data.optString("reason"),
        greeting = data.optString("greeting"),
        type = data.optString("type")
    )
}

private fun JSONArray.toDiscoverSongs(): List<Song> {
    val out = ArrayList<Song>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        // SongDTO shape ≈ playlist-song shape (name/artist/album/coverUrl/
        // duration/platform); reuse the same defensive mapping: missing
        // duration → 0, missing platform → "netease" (streamUrl requires it).
        out.add(VibeApi.parsePlaylistSong(o))
    }
    return out
}

/** Pure: parse GET /api/songs/random (SongDTO list → Song, same defaults as above). */
fun parseRandomSongs(json: String): List<Song> {
    val root = JSONObject(json)
    checkDiscoverEnvelope(root, "Random")
    val arr: JSONArray = root.optJSONArray("data")
        ?: root.optJSONObject("data")?.optJSONArray("list")
        ?: JSONArray()
    return arr.toDiscoverSongs()
}

/**
 * Pure: parse GET /api/playlists/recommend, accepting both the returned
 * {coverUrl,desc,count} keys and the cached {picUrl,copywriter,playCount} keys.
 * Missing copywriter → "".
 */
fun parseRecommendPlaylists(json: String): List<RecommendPlaylist> {
    val root = JSONObject(json)
    checkDiscoverEnvelope(root, "Recommend playlists")
    val arr: JSONArray = root.optJSONArray("data")
        ?: root.optJSONObject("data")?.optJSONArray("list")
        ?: JSONArray()
    val out = ArrayList<RecommendPlaylist>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(
            RecommendPlaylist(
                id = discoverId(o),
                name = o.optString("name"),
                picUrl = o.optString("picUrl").ifBlank { o.optString("coverUrl") },
                copywriter = o.optString("copywriter").ifBlank { o.optString("desc") },
                playCount = optLongFlexible(o, "playCount", "count")
            )
        )
    }
    return out
}
