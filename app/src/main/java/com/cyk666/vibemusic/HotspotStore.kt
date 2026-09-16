package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Disk-backed hotspot cache (2026-09-17): the in-memory SWR layer in
// HotspotCache.kt is unchanged — this only adds a cold-start seed so process
// death (vivo/OriginOS cleanup) no longer forces a full reload every launch.
//
// Layout: filesDir/hotspot/<key>.json (raw payload, overwritten in place —
// no growth) + <key>.ts (epoch ms). A missing/blank/corrupt pair reads as a
// cache miss; the loader then falls back to the existing network path.
// Playlists are per-user: the payload carries the userId and a mismatch
// reads as a miss (never show another account's lists).

object HotspotStore {
    private val ALLOWED_KEYS = setOf("banners", "daily", "guess", "hot", "playlists")

    private fun dir(context: Context) = File(context.filesDir, "hotspot")

    /** (timestampMs, payload) or null on miss/corruption. Never throws. */
    suspend fun read(context: Context, key: String): Pair<Long, String>? =
        withContext(Dispatchers.IO) {
            if (key !in ALLOWED_KEYS) return@withContext null
            try {
                val d = dir(context)
                val data = File(d, "$key.json")
                val stamp = File(d, "$key.ts")
                if (!data.isFile || !stamp.isFile) return@withContext null
                val ts = stamp.readText().trim().toLongOrNull() ?: return@withContext null
                if (ts <= 0L) return@withContext null
                val payload = data.readText()
                if (payload.isBlank()) return@withContext null
                ts to payload
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Overwrite-in-place write. Payload lands first, stamp last — a torn
     * write reads as a miss (payload without stamp) instead of serving
     * half-written data. Tmp+rename keeps readers from seeing partial files.
     */
    suspend fun write(context: Context, key: String, payload: String) =
        withContext(Dispatchers.IO) {
            if (key !in ALLOWED_KEYS) return@withContext
            if (payload.isBlank()) return@withContext
            try {
                val d = dir(context)
                if (!d.isDirectory && !d.mkdirs()) return@withContext
                val tmp = File(d, "$key.json.tmp")
                tmp.writeText(payload)
                val target = File(d, "$key.json")
                if (!tmp.renameTo(target)) {
                    target.writeText(payload)
                    tmp.delete()
                }
                File(d, "$key.ts").writeText(System.currentTimeMillis().toString())
            } catch (_: Exception) {
            }
        }
}

// ---- pure payload codecs (decode null = cache miss) ----

fun encodeBanners(list: List<DiscoverBanner>): String {
    val arr = JSONArray()
    for (b in list) {
        arr.put(
            JSONObject()
                .put("name", b.name)
                .put("coverUrl", b.coverUrl)
                .put("desc", b.desc)
                .put("playCount", b.playCount)
        )
    }
    return arr.toString()
}

fun decodeBanners(json: String): List<DiscoverBanner>? = try {
    val arr = JSONArray(json)
    val out = ArrayList<DiscoverBanner>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out.add(
            DiscoverBanner(
                name = o.optString("name"),
                coverUrl = o.optString("coverUrl"),
                desc = o.optString("desc"),
                playCount = o.optLong("playCount", 0L)
            )
        )
    }
    out
} catch (_: Exception) {
    null
}

fun encodeRecommendPlaylists(list: List<RecommendPlaylist>): String {
    val arr = JSONArray()
    for (p in list) {
        arr.put(
            JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("picUrl", p.picUrl)
                .put("copywriter", p.copywriter)
                .put("playCount", p.playCount)
        )
    }
    return arr.toString()
}

fun decodeRecommendPlaylists(json: String): List<RecommendPlaylist>? = try {
    val arr = JSONArray(json)
    val out = ArrayList<RecommendPlaylist>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out.add(
            RecommendPlaylist(
                id = o.optString("id"),
                name = o.optString("name"),
                picUrl = o.optString("picUrl"),
                copywriter = o.optString("copywriter"),
                playCount = o.optLong("playCount", 0L)
            )
        )
    }
    out
} catch (_: Exception) {
    null
}

/** Per-user payload: a userId mismatch decodes to null (cache miss). */
fun encodePlaylists(userId: String, list: List<Playlist>): String {
    val arr = JSONArray()
    for (p in list) {
        arr.put(
            JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("coverUrl", p.coverUrl)
                .put("songCount", p.songCount)
                .put("description", p.description)
                .put("creator", p.creator)
        )
    }
    return JSONObject().put("userId", userId).put("list", arr).toString()
}

fun decodePlaylists(json: String, expectedUserId: String): List<Playlist>? {
    val root = try {
        JSONObject(json)
    } catch (_: Exception) {
        return null
    }
    if (root.optString("userId") != expectedUserId) return null
    val arr = root.optJSONArray("list") ?: return null
    return try {
        val out = ArrayList<Playlist>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Playlist(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    coverUrl = o.optString("coverUrl"),
                    songCount = o.optInt("songCount"),
                    description = o.optString("description"),
                    creator = o.optString("creator")
                )
            )
        }
        out
    } catch (_: Exception) {
        null
    }
}

/** Daily payload carries the reason caption alongside the songs. */
fun encodeDaily(reason: String, songs: List<Song>): String =
    JSONObject().put("reason", reason).put("songs", JSONArray(songsToJson(songs))).toString()

fun decodeDaily(json: String): Pair<String, List<Song>>? = try {
    val root = JSONObject(json)
    val songs = songsFromJson(root.optJSONArray("songs")?.toString() ?: "")
    if (songs == null) null else (root.optString("reason") to songs)
} catch (_: Exception) {
    null
}
