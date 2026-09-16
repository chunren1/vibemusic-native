package com.cyk666.vibemusic

import android.net.Uri
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
        .authority("vibe.cyk666.top")
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
                .setArtworkUri(absImgUrl(coverUrl).ifBlank { null }?.let { Uri.parse(it) })
                .setExtras(android.os.Bundle().apply { putString("platform", platform) })
                .build()
        )
        .build()
}

fun formatDuration(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

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
