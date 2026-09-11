package com.cyk666.vibemusic

/** QQ-style playlist detail pure logic (no Compose, unit-tested). */

/** Pure: number of VIP-flagged songs (flags come from the backend only). */
fun countVipSongs(songs: List<Song>): Int = songs.count { it.vip }

/**
 * Pure: 播放全部 row label — "N首", appending " · 含M首VIP" only when the
 * backend actually flagged VIP songs (M == 0 shows nothing invented).
 */
fun buildPlayAllLabel(total: Int, vipCount: Int): String {
    val base = "${total.coerceAtLeast(0)}首"
    return if (vipCount > 0) "$base · 含${vipCount}首VIP" else base
}

/**
 * Pure: resolved song count for the detail header — the loaded list wins
 * once it settles (non-loading, possibly empty); otherwise the playlist's
 * stored count.
 */
fun resolveDetailSongCount(
    loadedSongs: List<Song>,
    songsLoading: Boolean,
    storedCount: Int
): Int = if (!songsLoading) loadedSongs.size else storedCount.coerceAtLeast(0)

/** Max songs listed by name in the share text (keeps the sheet short). */
const val SHARE_SONG_PREVIEW_MAX = 5

/**
 * Pure: system share-sheet text — playlist name plus the first songs as
 * "name - artist" lines. No counts/stats beyond the real list.
 */
fun buildShareText(playlistName: String, songs: List<Song>): String {
    val title = playlistName.ifBlank { "(untitled)" }
    val preview = songs.take(SHARE_SONG_PREVIEW_MAX)
        .filter { it.name.isNotBlank() }
        .joinToString("\n") { s ->
            if (s.artist.isNotBlank()) "${s.name} - ${s.artist}" else s.name
        }
    return if (preview.isBlank()) "分享歌单「$title」"
    else "分享歌单「$title」：\n$preview"
}
