package com.cyk666.vibemusic

// Song ↔ MediaItem 映射（从 MainActivity 拆出；round6 T5）

import androidx.media3.common.MediaItem
import com.cyk666.vibemusic.MediaCache.toCachedMediaItem
import com.cyk666.vibemusic.OfflineStore.toLocalMediaItem


fun songFromMediaItem(mi: MediaItem): Song {
    val md = mi.mediaMetadata
    val raw = mi.mediaId.orEmpty()
    val extrasPlat = md.extras?.getString("platform")
    var sid = raw
    var plat: String? = extrasPlat
    if (raw.startsWith("local:")) {
        val rest = raw.removePrefix("local:")
        val idx = rest.indexOf(':')
        if (idx >= 0) {
            plat = rest.substring(0, idx).ifBlank { extrasPlat }
            sid = rest.substring(idx + 1)
        } else {
            sid = rest
        }
    }
    return Song(
        sourceId = sid,
        name = (md.title?.toString().orEmpty()),
        artist = (md.artist?.toString().orEmpty()),
        album = (md.albumTitle?.toString().orEmpty()),
        coverUrl = md.artworkUri?.toString().orEmpty(),
        durationSec = 0,
        platform = plat.ifNullOrBlankDefault()
    )
}

/**
 * 同曲重试前的物化：按 [shouldEvictCacheOnRetry] 决定是否先删掉这条 URL 的缓存，
 * 再重建带稳定 cache key 的 MediaItem（[MediaCache.toCachedMediaItem]）。
 *
 * 位置越界与"中途的内容类错误"都要清缓存（缓存里可能躺着上游的 HTML 错误页或
 * 一段被中断的写入，见 [MediaCache.remove] 的 KDoc），不清就还会从缓存读到同一段坏字节。
 * 普通断流的缓存字节是好的，删掉等于把已经下过的音频扔掉，重试反而更慢。
 *
 * [MediaCache.remove] 自身绝不抛：清理只是加固手段，不能演变成"不重试"。
 * `songFromMediaItem` 取不到 Song 时的行为与改造前一致 —— 抛给调用方，由重试分支统一兜住。
 */
fun retryMediaItem(
    context: android.content.Context,
    item: MediaItem,
    errorCode: Int,
    midSong: Boolean
): MediaItem {
    val song = songFromMediaItem(item)
    if (shouldEvictCacheOnRetry(errorCode, midSong)) MediaCache.remove(context, song.streamUrl())
    return song.toCachedMediaItem()
}

fun Song.toPlayMediaItem(context: android.content.Context): MediaItem {
    return try {
        val file = OfflineStore.audioFile(context, this)
        val source = selectPlaySource(
            OfflineStore.isDownloaded(context, this),
            file.exists(),
            try {
                file.length()
            } catch (_: Exception) {
                0L
            }
        )
        if (source == PlaySource.LOCAL) toLocalMediaItem(context)
        else toCachedMediaItem()
    } catch (_: Exception) {
        toCachedMediaItem()
    }
}

private fun String?.ifNullOrBlankDefault(default: String = "netease"): String =
    if (this.isNullOrBlank()) default else this
