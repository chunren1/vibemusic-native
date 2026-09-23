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
