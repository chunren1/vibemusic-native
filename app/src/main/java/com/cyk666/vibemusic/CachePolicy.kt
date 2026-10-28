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

    /**
     * Poisoned local file + offline: Activity only reports; **PlaybackService**
     * owns the advance (`selectNextOfflineIndex`). Heal cannot do anything
     * offline, so if nobody advanced here one truncated download would stall
     * the whole queue (CODE-REVIEW-2026-10-28 PB-1).
     */
    ERROR_MESSAGE,

    /** Stream/cached item: keep the existing auto-skip path. */
    SKIP
}

fun localErrorDecision(mediaId: String, isOnline: Boolean): LocalErrorAction = when {
    !mediaId.startsWith("local:") -> LocalErrorAction.SKIP
    isOnline -> LocalErrorAction.DELETE_AND_STREAM
    else -> LocalErrorAction.ERROR_MESSAGE
}

/**
 * 服务端是否把当前错误**完全交给** Activity 的自愈路径（只有"在线 + local 项"）。
 *
 * 在线时自愈能真做事（删坏文件 + 转流 + 重下），服务端若同时跳歌就是两边抢队列；
 * 离线时自愈什么也做不了，服务端必须自己推进，否则整条队列停在第一个坏文件上
 * （CODE-REVIEW-2026-10-28 PB-1）。
 */
fun defersLocalErrorToHeal(mediaId: String, isOnline: Boolean): Boolean =
    mediaId.startsWith("local:") && isOnline

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

/**
 * 连续失败到这个次数后**不再相信** `isAudioFileIntact` 的结论，一律按确认中毒处理。
 *
 * 为什么需要它（CODE-REVIEW-2026-10-28 PB-2）：探针只读文件头 4 字节 + 大小门，
 * **截断体能通过**；旧版 `fileValid` 分支排在阈值之前，于是这种文件永远走
 * KEEP_STREAM_ONCE、永远删不掉，到第 5 次只弹一句"联网后重新下载"就再也没人管
 * —— 而重下从未发生。连续失败越线 = 探针不可信，删掉重下才让文案成真。
 */
const val LOCAL_HEAL_FORCE_DELETE_AFTER = 5

fun healAction(fileValid: Boolean, consecFails: Int, isOnline: Boolean): HealAction = when {
    !isOnline -> HealAction.ERROR_MESSAGE
    // 探针不可信 → 先于 fileValid 判断（否则头部合法的截断体永远删不掉）
    consecFails >= LOCAL_HEAL_FORCE_DELETE_AFTER -> HealAction.DELETE_AND_STREAM
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
