package com.cyk666.vibemusic

import android.content.Context
import androidx.core.net.toUri
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File
import org.json.JSONObject

const val HIGH_FREQ_THRESHOLD = 6

fun playKey(platform: String, sourceId: String): String =
    "${platform.ifBlank { "netease" }}:$sourceId"

fun playKey(song: Song): String = playKey(song.platform, song.sourceId)

/**
 * 常听到第 6 次开始自动离线（2026-11-03 按用户要求从 3 调到 6：3 次太容易触发，
 * 只是随便听两遍的歌就被自动存了）。
 *
 * `count == THRESHOLD` 只有一个窗口：命中那一次断网、或内容校验失败（`saveBytes`
 * 返回 false）后计数已经变成 7，此后再播多少次都不会再试（用户看到的是「常听却
 * 一直不下载」）。`>=` 保证每次播放都会重试，`isDownloaded` 仍然挡住重复下载。
 *
 * ⚠️ 老用户已存的计数不回滚：原本 3~5 次的歌要再听到 6 次才自动存；
 * 已经存过的照旧（`isDownloaded` 挡住重复下载）。
 */
fun shouldAutoSave(count: Int): Boolean = count >= HIGH_FREQ_THRESHOLD

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
            // 12 字节：ISO-BMFF 的 `ftyp` 落在 4..7，只读 4 字节会把 B 站音轨判成损坏
            val head = ByteArray(12)
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

    /** 扫盘 + 按 playKey 去重 + 清理重复 `.meta`（见 OfflineDownloads.kt）；这里只绑路径。 */
    fun listDownloads(context: Context): List<OfflineMeta> = readDownloads(dir(context))

    /**
     * Startup audit: re-validate every persisted download against the same
     * bar as [isAudioFileIntact] (exists + size floor + audio head) and
     * delete invalid file + meta (legacy poison from the pre-1.0.9 era still
     * sits on devices). Also reclaims orphans: `*.tmp` left by a killed
     * download, and `.mp3` no `.meta` points at — neither is reachable from
     * any screen, so without this they are undeletable bytes on disk.
     * Returns the deleted file count. Never touches MediaCache.
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
                        SilentFail.note("OfflineStore.auditInvalid:259")
                    }
                }
            }
            for (f in orphanDownloads(dir(context), System.currentTimeMillis())) {
                try {
                    if (f.delete()) removed++
                } catch (_: Exception) {
                    SilentFail.note("OfflineStore.auditInvalid:orphan")
                }
            }
        } catch (_: Exception) {
            SilentFail.note("OfflineStore.auditInvalid:264")
        }
        return removed
    }

    /**
     * 下载一首歌并落盘（**IO 线程调用**）。true = 已保存；false = 内容非法/写盘失败。
     * 网络异常原样抛出 —— 三个调用点的失败文案不同（手动下载要提示、常听自动离线
     * 只记诊断），所以错误处理留在调用方。
     *
     * 三处（手动下载 `downloadSong` / 自愈重下 `redownloadSong` / 常听自动离线
     * `maybeCountPlay`）原来各写一份 `fetchStreamBytes + saveBytes + now`，
     * 改一处漏两处（CODE-REVIEW-2026-10-28 CODE-3）。
     */
    suspend fun downloadToStore(context: Context, song: Song): Boolean {
        val bytes = VibeApi.fetchStreamBytes(song.streamUrl())
        return saveBytes(context, song, bytes, System.currentTimeMillis())
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
                SilentFail.note("OfflineStore.saveBytes:290")
            }
            tmpFile.writeBytes(bytes)
            // Re-validate what actually landed on disk: a VPN-trickle
            // truncation must never become the playable file.
            val landed = tmpFile.readBytes()
            if (landed.size != bytes.size || !looksLikeAudio(landed)) {
                try {
                    tmpFile.delete()
                } catch (_: Exception) {
                    SilentFail.note("OfflineStore.saveBytes:300")
                }
                return false
            }
            // Atomic rename (same dir, same fs) replaces any previous file.
            if (!tmpFile.renameTo(finalFile)) {
                try {
                    tmpFile.delete()
                } catch (_: Exception) {
                    SilentFail.note("OfflineStore.saveBytes:309")
                }
                return false
            }
            renamed = true
            // Meta goes last, only once audio is verified on disk. 原子写：直接
            // writeText 是就地截断，进程在写一半时被杀会留下半截 JSON → 记录消失、
            // 音频变孤儿。
            writeTextAtomically(metaFile(context, song), OfflineMeta.of(song, nowMs).toJson())
            true
        } catch (_: Exception) {
            try {
                tmpFile.delete()
            } catch (_: Exception) {
                SilentFail.note("OfflineStore.saveBytes:321")
            }
            if (renamed) {
                try {
                    finalFile.delete()
                } catch (_: Exception) {
                    SilentFail.note("OfflineStore.saveBytes:327")
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
            // 音频删不掉就**保留 meta**并报失败：先删 meta 会让列表里这一行消失，
            // 而 mp3 变成任何界面都删不掉的孤儿（只有卸载 App 能回收）。
            val audio = audioFile(context, probe)
            if (audio.exists() && !audio.delete()) return false
            val metaTarget = metaFile(context, probe)
            !metaTarget.exists() || metaTarget.delete()
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
                    .setArtworkUri(absImgUrl(coverUrl).ifBlank { null }?.toUri())
                    .setExtras(android.os.Bundle().apply { putString("platform", plat) })
                    .build()
            )
            .build()
    }
}
