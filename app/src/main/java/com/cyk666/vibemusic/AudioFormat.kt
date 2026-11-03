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
