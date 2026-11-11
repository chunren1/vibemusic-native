package com.cyk666.vibemusic

import android.content.Context
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 播放/下载内容的完整性校验与自愈（2026-11-11）。
 *
 * 现场：某首歌下载下来是**另一首歌**（换源错配的历史副本）或截断片段——字节是
 * 合法音频（magic 校验拦不住），但内容和歌名对不上，听感是「没播完就切」。
 * 这里的三个工具共同保证这类副本不可入库、不可复用：
 *
 * - [rejectIfDurationMismatch]：下载落盘前用解码器读实际时长与元数据比对，
 *   偏差超 max(15s, 20%) 即拒绝（防新增）。
 * - [observeEarlyAutoAndMaybePurge]：播放时 AUTO 切轨且上一首明显没放到结尾
 *   （实际 < 元数据 80%）→ 删除可重下的副本，下次播放自动走在线流（清存量）。
 * - [isBadLocalCopy]：上述判定的纯函数版本，便于单测钉住阈值。
 *
 * 只处理 OfflineStore 目录（platform_sourceId 命名的**可重下**副本）；
 * 用户本地导入的文件不在其中，任何路径都不会删它们。
 */

/** 读音频文件时长（ms）；读不出/异常返回 null。阻塞 IO，调用方须在 IO 线程。 */
internal fun readAudioDurationMs(file: File): Long? = try {
    val r = android.media.MediaMetadataRetriever()
    try {
        r.setDataSource(file.absolutePath)
        r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
    } finally {
        try {
            r.release()
        } catch (_: Exception) {
            SilentFail.note("AudioIntegrity.readAudioDurationMs:release")
        }
    }
} catch (_: Exception) {
    null
}

/**
 * 下载内容时长与元数据不符时：清理 tmp、记一条诊断、返回 true（调用方中止保存）。
 * 合法音频但「不是这首歌」的副本绝不能进入离线库。
 */
internal fun rejectIfDurationMismatch(tmpFile: File, song: Song): Boolean {
    if (durationMatches(song.durationSec, readAudioDurationMs(tmpFile))) return false
    try {
        tmpFile.delete()
    } catch (_: Exception) {
        SilentFail.note("AudioIntegrity.rejectIfDurationMismatch:delete")
    }
    SilentFail.note("AudioIntegrity.duration-mismatch:" + song.sourceId)
    return true
}

/** 删除某首歌的下载副本（音频 + meta），返回是否真的删到了文件。 */
fun deleteDownloadedBySong(context: Context, song: Song): Boolean = try {
    val audio = OfflineStore.audioFile(context, song)
    val meta = OfflineStore.metaFile(context, song)
    var hit = false
    if (audio.exists()) hit = audio.delete() || hit
    if (meta.exists()) hit = meta.delete() || hit
    hit
} catch (_: Exception) {
    false
}

/** 纯函数：提前 EOF 是否判定为「坏本地副本」（实际 < 元数据 80%）。 */
fun isBadLocalCopy(positionMs: Long, metaDurationSec: Int): Boolean =
    metaDurationSec > 0 && positionMs < metaDurationSec * 800L

/**
 * AUTO 切轨时的「提前结束」观察与自愈。
 *
 * 观察：上一首明显没放到结尾（提前 EOF / 静默截断，无 onPlayerError），
 * 记一条 PlayDiag，用于区分「出错被跳」与「流被截断当正常结束」。
 *
 * 自愈：判定为坏本地副本时删除可重下的下载副本（见文件头说明）。
 */
fun observeEarlyAutoAndMaybePurge(
    context: Context,
    scope: CoroutineScope,
    reason: Int,
    positionMs: Long,
    durationMs: Long,
    songProvider: () -> Song?
) {
    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
    if (durationMs <= 0 || positionMs <= 0) return
    if (positionMs >= durationMs - 5_000) return
    PlayDiag.observe(context, "early-auto:$positionMs/$durationMs")
    val song = songProvider() ?: return
    if (!isBadLocalCopy(positionMs, song.durationSec)) return
    scope.launch(Dispatchers.IO) {
        try {
            if (deleteDownloadedBySong(context, song)) {
                PlayDiag.observe(context, "bad-local-removed:" + song.sourceId)
            }
        } catch (_: Exception) {
            SilentFail.note("AudioIntegrity.observeEarlyAutoAndMaybePurge")
        }
    }
}
