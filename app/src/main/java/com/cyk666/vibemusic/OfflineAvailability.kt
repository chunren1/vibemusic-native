package com.cyk666.vibemusic

import android.content.Context
import androidx.media3.common.MediaItem
import com.cyk666.vibemusic.MediaCache.toCachedMediaItem
import com.cyk666.vibemusic.OfflineStore.toLocalMediaItem

/**
 * In-memory "locally-available" index for a queue snapshot.
 *
 * Review item 5: offline-error probing (`OfflineStore.isAudioFileIntact` +
 * `MediaCache.cachedBytes` per song) and timeline builds (`queue.map {
 * toPlayMediaItem }` with 2-3 stats per song) ran on the main thread — twice
 * (Service + Activity duplicates) — stalling point-play on 200+ song lists.
 *
 * This index collapses those per-song probes into ONE off-main pass
 * ([buildOfflineAvailability], always on Dispatchers.IO); every hot path
 * afterwards is a table lookup with zero file/cache I/O:
 * - [isOfflinePlayable] feeds [selectNextOfflineIndex] (single owner:
 *   PlaybackService transports, Activity only reports).
 * - [isGatePlayable] mirrors [isLocalOrCachedPlayable] for the point-play gate.
 * - [Song.toPlayMediaItem] picks LOCAL vs STREAM from the snapshot; only
 *   path construction runs on the caller thread (no disk access).
 *
 * Offline semantics are unchanged: intact-file / local-download / cache
 * triage is the same [selectPlaySource] + `cachedBytes > 0` bar, just
 * evaluated once off-main instead of per-song on-main.
 */
data class OfflineAvailability(
    /** [offlineBaseName] entries whose download counts as LOCAL. */
    val localBases: Set<String> = emptySet(),
    /** [MediaCache.cacheKey] entries with bytes on device. */
    val cachedKeys: Set<String> = emptySet()
)

/**
 * BLOCKING file/cache I/O — call ONLY on Dispatchers.IO. Single pass over
 * [songs]: one [selectPlaySource] verdict + one [MediaCache.cachedBytes]
 * probe per song, instead of 2-3 stats per song per call site on-main.
 */
fun buildOfflineAvailability(context: Context, songs: List<Song>): OfflineAvailability {
    val app = try {
        context.applicationContext ?: context
    } catch (_: Exception) {
        context
    }
    val local = HashSet<String>()
    val cached = HashSet<String>()
    for (s in songs) {
        if (s.sourceId.isBlank()) continue
        try {
            val f = OfflineStore.audioFile(app, s)
            var exists = false
            var size = 0L
            try {
                exists = f.exists()
                if (exists) size = f.length()
            } catch (_: Exception) {
            }
            val downloaded = try {
                OfflineStore.isDownloaded(app, s)
            } catch (_: Exception) {
                false
            }
            if (selectPlaySource(downloaded, exists, size) == PlaySource.LOCAL) {
                local.add(offlineBaseName(s))
            }
        } catch (_: Exception) {
        }
        try {
            if (MediaCache.cachedBytes(app, s.streamUrl()) > 0L) {
                cached.add(MediaCache.cacheKey(s))
            }
        } catch (_: Exception) {
        }
    }
    return OfflineAvailability(local, cached)
}

/** Pure: this song counts as a local download in the snapshot. */
fun OfflineAvailability.isLocal(song: Song): Boolean =
    song.sourceId.isNotBlank() && offlineBaseName(song) in localBases

/** Pure: this song has cached bytes in the snapshot. */
fun OfflineAvailability.isCached(song: Song): Boolean =
    song.sourceId.isNotBlank() && MediaCache.cacheKey(song) in cachedKeys

/**
 * Pure offline-playable verdict for error-path probing (same bar as the old
 * inline `isAudioFileIntact || cachedBytes > 0` loops: LOCAL here already
 * implies the intact-file bar via [selectPlaySource]).
 */
fun OfflineAvailability.isOfflinePlayable(song: Song): Boolean {
    if (song.sourceId.isBlank()) return false
    return isLocal(song) || isCached(song)
}

/**
 * Pure point-play gate, mirroring [isLocalOrCachedPlayable]: online always
 * plays (stream); offline needs a local download or cached bytes.
 */
fun OfflineAvailability.isGatePlayable(song: Song, isOnline: Boolean): Boolean =
    isOnline || isOfflinePlayable(song)

/**
 * Timeline item from a precomputed snapshot — zero file/cache I/O (path
 * construction only, no disk access). Falls back to stream on any error,
 * same as the Context-only overload.
 */
fun Song.toPlayMediaItem(context: Context, avail: OfflineAvailability): MediaItem {
    return try {
        if (sourceId.isBlank()) return with(MediaCache) { toCachedMediaItem() }
        if (avail.isLocal(this)) with(OfflineStore) { toLocalMediaItem(context) }
        else with(MediaCache) { toCachedMediaItem() }
    } catch (_: Exception) {
        try {
            with(MediaCache) { toCachedMediaItem() }
        } catch (_: Exception) {
            toMediaItem()
        }
    }
}
