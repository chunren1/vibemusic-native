package com.cyk666.vibemusic

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * 「原版不可播」时的近似版本候选（人工确认）。
 *
 * 服务端只会返回标题一致、且已排除 DJ/伴奏/翻唱等版本词的候选；歌手与时长可能不同，
 * 差异以 [warnings] 如实返回。**必须由用户点选才播放**——自动匹配仍然保持严格，
 * 这条路不能反过来影响点歌逻辑。
 */
data class AlternativeCandidate(
    val platform: String,
    val sourceId: String,
    val name: String,
    val artists: String,
    val durationMs: Long,
    val quality: String,
    val warnings: List<String>,
    val score: Double
) {
    /** 转成可直接进播放队列的 Song（时长按秒，与 Song.durationSec 口径一致）。 */
    fun toSong(): Song = Song(
        sourceId = sourceId,
        name = name,
        artist = artists,
        album = "",
        coverUrl = "",
        durationSec = (durationMs / 1000L).toInt(),
        platform = platform,
        vip = false
    )

    /** 一行差异说明：歌手不同 / 时长差 56s / 低于 320k。 */
    fun warningText(): String = warnings.joinToString(" · ") { w ->
        when {
            w == "artist_mismatch" -> "歌手不同"
            w == "quality_below_320k" -> "低于 320k"
            w.startsWith("duration_gap_") -> "时长差 " + w.removePrefix("duration_gap_")
            else -> w
        }
    }
}

/** Pure：GET /api/songs/alternatives 的查询串。 */
fun alternativesPath(name: String, artist: String, durationMs: Long?): String {
    val b = Uri.Builder()
        .appendPath("api").appendPath("songs").appendPath("alternatives")
        .appendQueryParameter("name", name)
    if (artist.isNotBlank()) b.appendQueryParameter("artist", artist)
    if (durationMs != null && durationMs > 0) b.appendQueryParameter("duration", durationMs.toString())
    return b.build().toString().trimStart('/')
}

/**
 * Pure：解析候选列表。
 *
 * <p>形状漂移一律抛错而不是静默变空——空列表代表"确实没有其他版本"，
 * 与"协议坏了"必须区分开（用户看到的是同一句"没找到"，排查时却是两回事）。</p>
 */
fun parseAlternatives(json: String): List<AlternativeCandidate> {
    val root = JSONObject(json)
    if (root.optInt("code", 200) != 200) throw RuntimeException("Alternatives: bad code")
    val data = root.optJSONObject("data") ?: throw RuntimeException("Alternatives: missing data")
    val arr: JSONArray = data.optJSONArray("candidates")
        ?: throw RuntimeException("Alternatives: missing candidates")
    val out = ArrayList<AlternativeCandidate>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val id = o.optString("sourceId")
        if (id.isBlank()) continue
        val ws = o.optJSONArray("warnings")
        val warnings = ArrayList<String>()
        if (ws != null) for (j in 0 until ws.length()) warnings.add(ws.optString(j))
        out.add(
            AlternativeCandidate(
                platform = o.optString("platform"),
                sourceId = id,
                name = o.optString("name"),
                artists = o.optString("artists"),
                durationMs = o.optLong("durationMs", 0L),
                quality = o.optString("quality"),
                warnings = warnings,
                score = o.optDouble("score", 0.0)
            )
        )
    }
    return out
}

/** 网络获取（失败抛错，由界面显示"查询失败"）。 */
suspend fun fetchAlternatives(
    name: String,
    artist: String,
    durationMs: Long?
): List<AlternativeCandidate> =
    parseAlternatives(VibeApi.rawGet(alternativesPath(name, artist, durationMs)))
