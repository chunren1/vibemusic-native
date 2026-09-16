package com.cyk666.vibemusic

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File
import org.json.JSONObject

const val HIGH_FREQ_THRESHOLD = 3

fun playKey(platform: String, sourceId: String): String =
    "${platform.ifBlank { "netease" }}:$sourceId"

fun playKey(song: Song): String = playKey(song.platform, song.sourceId)

fun shouldAutoSave(count: Int): Boolean = count == HIGH_FREQ_THRESHOLD

fun parsePlayCounts(json: String): Map<String, Int> {
    if (json.isBlank()) return emptyMap()
    return try {
        val o = JSONObject(json)
        val out = LinkedHashMap<String, Int>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = o.optInt(k, 0).coerceAtLeast(0)
        }
        out
    } catch (_: Exception) {
        emptyMap()
    }
}

fun renderPlayCounts(counts: Map<String, Int>): String {
    val o = JSONObject()
    for ((k, v) in counts) o.put(k, v)
    return o.toString()
}

fun bumpPlayCount(counts: Map<String, Int>, key: String): Pair<Map<String, Int>, Int> {
    val next = (counts[key] ?: 0) + 1
    val mutable = counts.toMutableMap()
    mutable[key] = next
    return Pair(mutable, next)
}

private val FILENAME_SAFE = Regex("[^A-Za-z0-9_-]")

/**
 * Offline-poisoning guard: the stream endpoint returns HTTP 200 even for
 * errors, so a failed stream (JSON error body, stub, truncated bytes) must
 * never be persisted as a "song". Floor kills JSON bodies and stubs;
 * real 30s+ snippets are ~500KB+, so 100KB is a safe floor.
 */
const val MIN_AUDIO_BYTES = 100_000

/**
 * Pure magic-bytes check: ID3 ("ID3") or MPEG frame sync (0xFF Ex) or
 * fLaC / OggS / RIFF (WAV). Anything else (JSON, text, truncated header)
 * is not playable audio.
 */
fun looksLikeAudio(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    val b2 = bytes[2].toInt() and 0xFF
    val b3 = bytes[3].toInt() and 0xFF
    // ID3v2 tag
    if (b0 == 0x49 && b1 == 0x44 && b2 == 0x33) return true // "ID3"
    // MPEG audio frame sync: 0xFF followed by top 3 bits set
    if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) return true
    // FLAC
    if (b0 == 0x66 && b1 == 0x4C && b2 == 0x61 && b3 == 0x43) return true // "fLaC"
    // Ogg
    if (b0 == 0x4F && b1 == 0x67 && b2 == 0x67 && b3 == 0x53) return true // "OggS"
    // WAV
    if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46) return true // "RIFF"
    return false
}

/** Pure: a download is worth persisting only when big enough AND audio-shaped. */
fun isValidDownload(bytes: ByteArray): Boolean =
    bytes.size >= MIN_AUDIO_BYTES && looksLikeAudio(bytes)

/**
 * Startup download-audit probe: one persisted meta entry plus a snapshot of
 * its on-disk file state. File ops stay inside [OfflineStore]; [auditDownloads]
 * only decides keep vs purge, so it is unit-testable without Context.
 */
data class MetaLike(
    val meta: OfflineMeta,
    val fileExists: Boolean,
    val fileSize: Long,
    val head: ByteArray = byteArrayOf()
)

/**
 * Pure startup-audit decision: keep only entries whose file exists, meets
 * [MIN_AUDIO_BYTES], and is audio-shaped. Returns the kept entries; the
 * caller purges the rest (file + meta). Never touches the rolling cache.
 */
fun auditDownloads(entries: List<MetaLike>): List<MetaLike> =
    entries.filter {
        it.fileExists && it.fileSize >= MIN_AUDIO_BYTES && looksLikeAudio(it.head)
    }

fun sanitizeFilePart(raw: String): String {
    val s = raw.replace(FILENAME_SAFE, "_")
    return s.ifBlank { "_" }.take(80)
}

fun offlineBaseName(platform: String, sourceId: String): String =
    "${sanitizeFilePart(platform.ifBlank { "netease" })}_${sanitizeFilePart(sourceId)}"

fun offlineBaseName(song: Song): String = offlineBaseName(song.platform, song.sourceId)

data class OfflineMeta(
    val sourceId: String,
    val name: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val durationSec: Int,
    val platform: String,
    val downloadedAt: Long
) {
    fun toJson(): String = JSONObject()
        .put("sourceId", sourceId)
        .put("name", name)
        .put("artist", artist)
        .put("album", album)
        .put("coverUrl", coverUrl)
        .put("durationSec", durationSec)
        .put("platform", platform)
        .put("downloadedAt", downloadedAt)
        .toString()

    fun toSong(): Song = Song(
        sourceId = sourceId,
        name = name,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        durationSec = durationSec,
        platform = platform
    )

    companion object {
        fun fromJson(json: String): OfflineMeta? = try {
            val o = JSONObject(json)
            OfflineMeta(
                sourceId = o.optString("sourceId"),
                name = o.optString("name"),
                artist = o.optString("artist"),
                album = o.optString("album"),
                coverUrl = o.optString("coverUrl"),
                durationSec = o.optInt("durationSec", 0),
                platform = o.optString("platform").ifBlank { "netease" },
                downloadedAt = o.optLong("downloadedAt", 0L)
            )
        } catch (_: Exception) {
            null
        }

        fun of(song: Song, downloadedAt: Long): OfflineMeta = OfflineMeta(
            sourceId = song.sourceId,
            name = song.name,
            artist = song.artist,
            album = song.album,
            coverUrl = song.coverUrl,
            durationSec = song.durationSec,
            platform = song.platform.ifBlank { "netease" },
            downloadedAt = downloadedAt
        )
    }
}

object OfflineStore {
    const val DIR_NAME = "offline"

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    fun audioFile(context: Context, song: Song): File =
        File(dir(context), "${offlineBaseName(song)}.mp3")

    fun metaFile(context: Context, song: Song): File =
        File(dir(context), "${offlineBaseName(song)}.meta")

    fun isDownloaded(context: Context, song: Song): Boolean {
        return try {
            audioFile(context, song).let { it.exists() && it.length() > 0 } &&
                metaFile(context, song).exists()
        } catch (_: Exception) {
            false
        }
    }

    fun isAudioFileIntact(context: Context, song: Song): Boolean {
        return try {
            val f = audioFile(context, song)
            if (!f.exists() || f.length() < MIN_AUDIO_BYTES) return false
            val head = ByteArray(4)
            var read = 0
            f.inputStream().use { ins ->
                while (read < head.size) {
                    val n = ins.read(head, read, head.size - read)
                    if (n < 0) break
                    read += n
                }
            }
            if (read < head.size) return false
            looksLikeAudio(head)
        } catch (_: Exception) {
            false
        }
    }

    fun listDownloads(context: Context): List<OfflineMeta> {
        return try {
            val d = dir(context)
            if (!d.isDirectory) return emptyList()
            d.listFiles { f -> f.isFile && f.name.endsWith(".meta") }
                .orEmpty()
                .mapNotNull {
                    try {
                        OfflineMeta.fromJson(it.readText())
                    } catch (_: Exception) {
                        null
                    }
                }
                .filter { it.sourceId.isNotBlank() }
                .sortedByDescending { it.downloadedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun count(context: Context): Int = listDownloads(context).size

    /**
     * Startup audit: re-validate every persisted download against the same
     * bar as [isAudioFileIntact] (exists + size floor + audio head) and
     * delete invalid file + meta (legacy poison from the pre-1.0.9 era still
     * sits on devices). Returns the deleted count. Never touches MediaCache.
     */
    fun auditInvalid(context: Context): Int {
        var removed = 0
        try {
            for (meta in listDownloads(context)) {
                val intact = try {
                    isAudioFileIntact(context, meta.toSong())
                } catch (_: Exception) {
                    false
                }
                if (!intact) {
                    try {
                        if (delete(context, meta)) removed++
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        return removed
    }

    fun saveBytes(context: Context, song: Song, bytes: ByteArray, nowMs: Long): Boolean {
        if (bytes.isEmpty()) return false
        if (!isValidDownload(bytes)) return false
        val targetDir = try {
            dir(context)
        } catch (_: Exception) {
            return false
        }
        val finalFile = try {
            audioFile(context, song)
        } catch (_: Exception) {
            return false
        }
        val tmpFile = File(targetDir, finalFile.name + ".tmp")
        var renamed = false
        return try {
            if (!targetDir.exists()) targetDir.mkdirs()
            // Drop a stale temp left by a killed process before starting.
            try {
                tmpFile.delete()
            } catch (_: Exception) {
            }
            tmpFile.writeBytes(bytes)
            // Re-validate what actually landed on disk: a VPN-trickle
            // truncation must never become the playable file.
            val landed = tmpFile.readBytes()
            if (landed.size != bytes.size || !looksLikeAudio(landed)) {
                try {
                    tmpFile.delete()
                } catch (_: Exception) {
                }
                return false
            }
            // Atomic rename (same dir, same fs) replaces any previous file.
            if (!tmpFile.renameTo(finalFile)) {
                try {
                    tmpFile.delete()
                } catch (_: Exception) {
                }
                return false
            }
            renamed = true
            // Meta goes last, only once audio is verified on disk.
            metaFile(context, song).writeText(OfflineMeta.of(song, nowMs).toJson())
            true
        } catch (_: Exception) {
            try {
                tmpFile.delete()
            } catch (_: Exception) {
            }
            if (renamed) {
                try {
                    finalFile.delete()
                } catch (_: Exception) {
                }
            }
            false
        }
    }

    fun delete(context: Context, meta: OfflineMeta): Boolean {
        return try {
            val probe = Song(
                sourceId = meta.sourceId,
                name = meta.name,
                artist = meta.artist,
                album = meta.album,
                coverUrl = meta.coverUrl,
                durationSec = meta.durationSec,
                platform = meta.platform
            )
            var ok = true
            try {
                if (audioFile(context, probe).exists() && !audioFile(context, probe).delete()) ok = false
            } catch (_: Exception) {
                ok = false
            }
            try {
                if (metaFile(context, probe).exists() && !metaFile(context, probe).delete()) ok = false
            } catch (_: Exception) {
                ok = false
            }
            ok
        } catch (_: Exception) {
            false
        }
    }

    fun Song.toLocalMediaItem(context: Context): MediaItem {
        val file = audioFile(context, this)
        val plat = platform.ifBlank { "netease" }
        return MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaId("local:$plat:$sourceId")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artist)
                    .setAlbumTitle(album.ifBlank { null })
                    .setArtworkUri(absImgUrl(coverUrl).ifBlank { null }?.let { Uri.parse(it) })
                    .setExtras(android.os.Bundle().apply { putString("platform", plat) })
                    .build()
            )
            .build()
    }
}
