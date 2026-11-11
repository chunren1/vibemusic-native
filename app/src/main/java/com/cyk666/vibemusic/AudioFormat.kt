package com.cyk666.vibemusic

/**
 * 「这段字节到底是不是音频」的唯一判定，从 `OfflineStore.kt` 拆出（2026-11-03）。
 *
 * 拆的理由：下载校验（`saveBytes`）、启动审计（`isAudioFileIntact`）、
 * 纯函数审计（`auditDownloads`）三处共用一个判定，但它跟存储路径 / meta 文件
 * 格式毫无关系；混在 `OfflineStore` 里既撑大文件，也让人以为改格式要动这里。
 *
 * 兼容的容器：ID3 头 / MPEG 帧同步 / FLAC / Ogg / WAV / ISO-BMFF(fMP4)。
 * 最后一个不是可选 —— B 站音轨就是 fMP4。
 */

/**
 * Offline-poisoning guard: the stream endpoint returns HTTP 200 even for
 * errors, so a failed stream (JSON error body, stub, truncated bytes) must
 * never be persisted as a "song". Floor kills JSON bodies and stubs;
 * real 30s+ snippets are ~500KB+, so 100KB is a safe floor.
 */
const val MIN_AUDIO_BYTES = 100_000

/**
 * Pure magic-bytes check: ID3 ("ID3") or MPEG frame sync (0xFF Ex) or
 * fLaC / OggS / RIFF (WAV) / ISO-BMFF (`ftyp` at offset 4). Anything else
 * (JSON, text, truncated header) is not playable audio.
 *
 * ISO-BMFF 这一条是**必须的**：B 站音轨是 fMP4（实测 `... ftypiso5`，2026-11-03
 * 线上取样），只看第 0-3 字节会把每一首 B 站歌曲判成"非音频" —— 播放正常、
 * 下载永远失败，而且启动审计还会把它当成损坏文件删掉。
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
    // ISO-BMFF (MP4 / M4A / fMP4)：前 4 字节是 box 长度，`ftyp` 落在 4..7。
    // B 站音轨走这条（实测 ftypiso5），没有它 → B 站歌曲永远下载失败。
    if (bytes.size >= 8 &&
        bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
        bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()
    ) return true // "ftyp"
    return false
}

/** Pure: a download is worth persisting only when big enough AND audio-shaped. */
fun isValidDownload(bytes: ByteArray): Boolean =
    bytes.size >= MIN_AUDIO_BYTES && looksLikeAudio(bytes)

/**
 * Pure: 下载内容实际时长是否与歌曲元数据相符（2026-11-11，防「换源错配/试听片段」入库）。
 *
 * 现场：某首歌下载下来的音频是**另一首歌**（换源错配的历史副本），或只有几十秒的
 * 试听片段——字节是合法音频（magic 校验拦不住），但内容和歌名对不上。用解码器读到
 * 的实际时长与元数据比对：偏差超过 max(15s, 20%) 即判「不是这首歌」，拒绝入库。
 *
 * - [expectedSec] <= 0（元数据缺失）：放行，无从校验。
 * - [actualMs] 读不出（null/<=0）：放行——宁可入库也不误拒（个别容器解析不出时长）。
 * - 15s/20% 的宽容量给正常版本差异（前奏剪辑、编码误差），不误杀。
 */
fun durationMatches(expectedSec: Int, actualMs: Long?): Boolean {
    if (expectedSec <= 0) return true
    if (actualMs == null || actualMs <= 0L) return true
    val expectedMs = expectedSec * 1000L
    val diff = kotlin.math.abs(actualMs - expectedMs)
    return diff <= maxOf(15_000L, (expectedMs * 0.20).toLong())
}

/**
 * Pure: 修复 ID3 标签的 Latin-1/GBK 错码（微信/网盘下载的 MP3 常见）。
 *
 * MediaMetadataRetriever 对 GBK 编码的标签返回「GBK 字节按 ISO-8859-1 解码」的
 * mojibake（每个汉字变两三个 Latin-1 字符）。还原：按 ISO-8859-1 取回原始字节，
 * 再按 GBK 解码；若得到中文且原串无中文，采用修复结果，否则原样返回（英文标签、
 * 已正确的标签、以及信息已丢失的 U+FFFD 形态都不会被误改）。
 */
fun fixTagMojibake(raw: String): String {
    if (raw.isEmpty()) return raw
    if (raw.any { it.code in 0x4E00..0x9FFF }) return raw
    if (raw.any { it.code > 0xFF }) return raw
    return try {
        val gbk = String(raw.toByteArray(Charsets.ISO_8859_1), charset("GBK"))
        if (gbk.any { it.code in 0x4E00..0x9FFF }) gbk else raw
    } catch (_: Exception) {
        raw
    }
}
