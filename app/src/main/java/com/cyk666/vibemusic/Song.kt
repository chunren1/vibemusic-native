package com.cyk666.vibemusic

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

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
                .setArtworkUri(if (coverUrl.isBlank()) null else Uri.parse(coverUrl))
                .setExtras(android.os.Bundle().apply { putString("platform", platform) })
                .build()
        )
        .build()
}

fun formatDuration(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
