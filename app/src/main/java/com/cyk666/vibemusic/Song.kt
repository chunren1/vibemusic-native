package com.cyk666.vibemusic

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

data class Song(
    val sourceId: String,
    val name: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val durationSec: Int,
    val platform: String,
    // VIP flag: backend SongDTOs may omit it (defaults false — never
    // invented). Parsed defensively in VibeApi.parsePlaylistSong.
    val vip: Boolean = false
) {
    fun streamUrl(): String = Uri.Builder()
        .scheme("https")
        // 域名唯一来源见 ApiConfig.kt（CODE-6）：写死两处会漏改 ——
        // 登录正常但所有播放流 404 的经典事故。
        .authority(API_HOST)
        .appendPath("api")
        .appendPath("songs")
        .appendPath("stream")
        .appendQueryParameter("sourceId", sourceId)
        .appendQueryParameter("name", name)
        .appendQueryParameter("artist", artist)
        .appendQueryParameter("platform", platform)
        .build()
        .toString()

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(streamUrl())
        .setMediaId(sourceId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(artist)
                .setAlbumTitle(album.ifBlank { null })
                .setArtworkUri(absImgUrl(coverUrl).ifBlank { null }?.toUri())
                .setExtras(android.os.Bundle().apply { putString("platform", platform) })
                .build()
        )
        .build()
}

/**
 * Pure: 这首能不能进"播放上报 / 播放计数"（CODE-4）。
 *
 * 两个调用点（`maybeReportPlay` / `maybeCountPlay`）原本各写一遍
 * `sourceId.isBlank() || isExternalSong(song)`，规则一样、顺序不同。
 * 外部导入文件（`content:` / `file:`）没有曲库身份：上报会把本地路径写进
 * 「最近播放」，计数也没有意义 —— 所以整条链路直接排除。
 */
fun librarySongOrNull(song: Song?): Song? {
    if (song == null || song.sourceId.isBlank()) return null
    if (LocalAudioIntent.isExternalSong(song)) return null
    return song
}

fun formatDuration(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Stable `LazyColumn` key for a song row: `"<prefix>-<index>-<sourceId>-<platform>"`.
 *
 * The index is **mandatory**, not decoration: `randomSongs` / aggregated search
 * do not guarantee `sourceId + platform` is unique within one list, and a
 * duplicate key makes `LazyColumn` throw `IllegalStateException: Key was
 * already used`. Four call sites had drifted to the index-less form and only
 * one had the fix — hence this single helper (see CODE-REVIEW-2026-10-28 UI-2).
 */
fun songRowKey(index: Int, song: Song, prefix: String = "song"): String =
    "$prefix-$index-${song.sourceId}-${song.platform}"

// Shared [Song] JSON codec (2026-09-17): single implementation for both the
// QueueStore snapshot and the hotspot disk cache — a second copy would drift.
// Field set matches the queue snapshot contract exactly (playSave/restore).

fun songToJsonObject(s: Song): JSONObject = JSONObject()
    .put("sourceId", s.sourceId)
    .put("name", s.name)
    .put("artist", s.artist)
    .put("album", s.album)
    .put("coverUrl", s.coverUrl)
    .put("durationSec", s.durationSec)
    .put("platform", s.platform)
    .put("vip", s.vip)

fun songFromJsonObject(o: JSONObject): Song = Song(
    sourceId = o.optString("sourceId"),
    name = o.optString("name"),
    artist = o.optString("artist"),
    album = o.optString("album"),
    coverUrl = o.optString("coverUrl"),
    durationSec = o.optInt("durationSec"),
    platform = o.optString("platform").ifBlank { "netease" },
    vip = o.optBoolean("vip", false)
)

fun songsToJson(songs: List<Song>): String {
    val arr = JSONArray()
    for (s in songs) arr.put(songToJsonObject(s))
    return arr.toString()
}

/** Null on malformed JSON — callers treat it as a cache miss and refetch. */
fun songsFromJson(json: String): List<Song>? = try {
    val arr = JSONArray(json)
    val out = ArrayList<Song>(arr.length())
    for (i in 0 until arr.length()) out.add(songFromJsonObject(arr.getJSONObject(i)))
    out
} catch (_: Exception) {
    null
}
