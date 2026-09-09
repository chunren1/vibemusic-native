package com.cyk666.vibemusic

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Offline-first playback decision (pure — truth table pinned by CachePolicyTest). */
enum class OfflineDecision {
    /** Online: stream normally (CacheDataSource fills the cache in background). */
    STREAM,

    /** Offline but bytes on device: play, CacheDataSource serves from cache. */
    PLAY_CACHED,

    /** Offline with nothing cached: block, show 无网络且未缓存. */
    BLOCK_WITH_MESSAGE
}

fun offlinePlayDecision(isOnline: Boolean, cachedBytes: Long): OfflineDecision = when {
    isOnline -> OfflineDecision.STREAM
    cachedBytes > 0 -> OfflineDecision.PLAY_CACHED
    else -> OfflineDecision.BLOCK_WITH_MESSAGE
}

enum class LocalDecision {
    PLAY_LOCAL,
    STREAM,
    BLOCK_WITH_MESSAGE
}

fun offlineFileDecision(isDownloaded: Boolean, isOnline: Boolean): LocalDecision = when {
    isDownloaded -> LocalDecision.PLAY_LOCAL
    isOnline -> LocalDecision.STREAM
    else -> LocalDecision.BLOCK_WITH_MESSAGE
}

/** Self-heal decision for a playback error on the current item (pure). */
enum class LocalErrorAction {
    /** Poisoned local file + online: delete file+meta, fall back to stream. */
    DELETE_AND_STREAM,

    /** Poisoned local file + offline: show message, stop (no skip-loop). */
    ERROR_MESSAGE,

    /** Stream/cached item: keep the existing auto-skip path. */
    SKIP
}

fun localErrorDecision(mediaId: String, isOnline: Boolean): LocalErrorAction = when {
    !mediaId.startsWith("local:") -> LocalErrorAction.SKIP
    isOnline -> LocalErrorAction.DELETE_AND_STREAM
    else -> LocalErrorAction.ERROR_MESSAGE
}

/** Validate-before-delete self-heal decision for a local-file error (pure). */
enum class HealAction {
    /** File intact (or first strike on a suspect file): keep the file, stream once. */
    KEEP_STREAM_ONCE,

    /** Confirmed poison (repeat failure on an invalid file): delete + stream. */
    DELETE_AND_STREAM,

    /** Offline: cannot stream; show message, keep the file. */
    ERROR_MESSAGE
}

/** Strikes before a suspect file is treated as confirmed poison. */
const val LOCAL_HEAL_DELETE_THRESHOLD = 2

/** Consecutive heal attempts before going quiet (heal not converging). */
const val LOCAL_HEAL_QUIET_AFTER = 4

fun healAction(fileValid: Boolean, consecFails: Int, isOnline: Boolean): HealAction = when {
    !isOnline -> HealAction.ERROR_MESSAGE
    fileValid -> HealAction.KEEP_STREAM_ONCE
    consecFails >= LOCAL_HEAL_DELETE_THRESHOLD -> HealAction.DELETE_AND_STREAM
    else -> HealAction.KEEP_STREAM_ONCE
}

/** Play-time source selection: a download counts only when the file is still on disk. */
enum class PlaySource {
    LOCAL,
    STREAM
}

fun selectPlaySource(isDownloaded: Boolean, fileExists: Boolean, fileSize: Long): PlaySource =
    if (isDownloaded && fileExists && fileSize >= MIN_AUDIO_BYTES) PlaySource.LOCAL
    else PlaySource.STREAM

/** ConnectivityManager probe feeding [offlinePlayDecision]. False on any error. */
fun isNetworkAvailable(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    } catch (_: Exception) {
        false
    }
}
