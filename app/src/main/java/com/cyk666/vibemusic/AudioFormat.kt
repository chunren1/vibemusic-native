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
// ---- MP3 拼接探测（2026-11-11）----
//
// 现场：「梦醒」前半段是正确歌曲、后半段变成另一首歌。线上取样（209s / 192kbps /
// 8736 帧 / 单一 ID3）结构完整，问题出在设备上的历史副本——两段 MP3 首尾相接时，
// 每个段落各自带 ID3v2 头，第二段首帧通常还带 Xing/Info 信息帧。正常单曲只有文件
// 开头一个 ID3、只有第一帧一个 Xing/Info，因此「帧边界上再次遇到 ID3 或第二个
// Xing/Info」就是拼接的确定信号。
//
// 扫描严格沿 MPEG 帧边界前进：压缩数据里偶然出现的 "ID3"/"Xing" 字节序列不会
// 落在帧边界上，不会误判。结构解析不了（非 MP3、帧同步断裂）一律返回 -1（不判定），
// 宁可漏检也不误删。

private val MPEG1_L3_BITRATES = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
private val MPEG2_L3_BITRATES = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
private val MPEG1_RATES = intArrayOf(44100, 48000, 32000)
private val MPEG2_RATES = intArrayOf(22050, 24000, 16000)
private val MPEG25_RATES = intArrayOf(11025, 12000, 8000)

/** Layer III 帧长（含 padding）；非法帧头/非 Layer III 返回 -1。 */
internal fun mpeg3FrameLen(b: ByteArray, off: Int): Int {
    if (off + 4 > b.size) return -1
    val h0 = b[off].toInt() and 0xFF
    val h1 = b[off + 1].toInt() and 0xFF
    val h2 = b[off + 2].toInt() and 0xFF
    if (h0 != 0xFF || (h1 and 0xE0) != 0xE0) return -1
    val ver = (h1 shr 3) and 3      // 3=MPEG1 2=MPEG2 0=MPEG2.5 1=reserved
    val layer = (h1 shr 1) and 3    // 1=Layer III
    if (ver == 1 || layer != 1) return -1
    val brIdx = (h2 shr 4) and 0xF
    val srIdx = (h2 shr 2) and 3
    if (brIdx == 0 || brIdx == 15 || srIdx == 3) return -1
    val pad = (h2 shr 1) and 1
    val br = if (ver == 3) MPEG1_L3_BITRATES[brIdx] else MPEG2_L3_BITRATES[brIdx]
    val sr = when (ver) {
        3 -> MPEG1_RATES[srIdx]
        2 -> MPEG2_RATES[srIdx]
        else -> MPEG25_RATES[srIdx]
    }
    val coef = if (ver == 3) 144 else 72
    val len = coef * br * 1000 / sr + pad
    return if (len >= 24) len else -1
}

/** ID3v2 标签总长（10 字节头 + synchsafe size）；不是 ID3 头返回 -1。 */
internal fun id3v2TagSize(b: ByteArray, off: Int): Int {
    if (off + 10 > b.size) return -1
    if (b[off] != 'I'.code.toByte() || b[off + 1] != 'D'.code.toByte() || b[off + 2] != '3'.code.toByte()) return -1
    val size = ((b[off + 6].toInt() and 0x7F) shl 21) or
        ((b[off + 7].toInt() and 0x7F) shl 14) or
        ((b[off + 8].toInt() and 0x7F) shl 7) or
        (b[off + 9].toInt() and 0x7F)
    if (size <= 0 || size > 8 * 1024 * 1024) return -1
    return 10 + size
}

/** 帧数据区前 36 字节内是否含 Xing/Info 信息帧标记。 */
private fun hasXingOrInfo(b: ByteArray, off: Int, len: Int): Boolean {
    val to = minOf(off + 36, off + len, b.size - 4)
    var p = off + 4
    while (p < to) {
        if (b[p] == 'X'.code.toByte() && b[p + 1] == 'i'.code.toByte() &&
            b[p + 2] == 'n'.code.toByte() && b[p + 3] == 'g'.code.toByte()
        ) return true
        if (b[p] == 'I'.code.toByte() && b[p + 1] == 'n'.code.toByte() &&
            b[p + 2] == 'f'.code.toByte() && b[p + 3] == 'o'.code.toByte()
        ) return true
        p++
    }
    return false
}

/**
 * 返回第二个音频段落的起始偏移；-1 表示未发现拼接（或不是可解析的 MP3）。
 *
 * 纯函数，零 IO；调用方负责把文件读成 ByteArray。
 */
fun findMp3SpliceOffset(bytes: ByteArray): Int {
    if (bytes.size < MIN_AUDIO_BYTES) return -1
    var start = 0
    val firstTag = id3v2TagSize(bytes, 0)
    if (firstTag > 0) start = firstTag
    // ID3 之后可能有零填充，最多探 1KB 找帧同步
    var i = -1
    var probe = start
    val probeEnd = minOf(start + 1024, bytes.size - 4)
    while (probe <= probeEnd) {
        if (mpeg3FrameLen(bytes, probe) > 0) {
            i = probe
            break
        }
        probe++
    }
    if (i < 0) return -1
    var frames = 0
    while (i + 4 <= bytes.size) {
        // 帧边界上出现 ID3v2 头 → 第二段（自带标签）从这开始
        if (frames > 0 && id3v2TagSize(bytes, i) > 0) return i
        val len = mpeg3FrameLen(bytes, i)
        if (len <= 0) {
            // 尾部零填充允许跳过；其它同步断裂视为结构不可解，不做判定
            if (bytes[i].toInt() == 0) {
                i++
                continue
            }
            return -1
        }
        // 第二帧及以后出现 Xing/Info → 第二段的编码信息帧
        if (frames > 0 && hasXingOrInfo(bytes, i, len)) return i
        frames++
        i += len
    }
    return -1
}
