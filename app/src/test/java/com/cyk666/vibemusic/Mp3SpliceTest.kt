package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MP3 拼接探测（2026-11-11「梦醒」现场：前半段正确、后半段另一首歌）。
 *
 * 用合成帧构造：MPEG1 Layer III / 192kbps / 48kHz → 每帧 576 字节，帧头 FF FB B4。
 * 正常单曲 = 开头一个 ID3 + 第一帧一个 Xing/Info；拼接副本 = 中途再出现任一者。
 */
class Mp3SpliceTest {

    private val frameLen = 576

    private fun frame(xing: Boolean = false): ByteArray {
        val f = ByteArray(frameLen) { 0x11 }
        f[0] = 0xFF.toByte()
        f[1] = 0xFB.toByte()
        f[2] = 0xB4.toByte()
        f[3] = 0x00
        if (xing) "Info".toByteArray().copyInto(f, 4)
        return f
    }

    private fun id3(payload: Int = 16): ByteArray {
        val tag = ByteArray(10 + payload)
        tag[0] = 'I'.code.toByte()
        tag[1] = 'D'.code.toByte()
        tag[2] = '3'.code.toByte()
        tag[3] = 4
        tag[6] = ((payload shr 21) and 0x7F).toByte()
        tag[7] = ((payload shr 14) and 0x7F).toByte()
        tag[8] = ((payload shr 7) and 0x7F).toByte()
        tag[9] = (payload and 0x7F).toByte()
        return tag
    }

    private fun frames(n: Int, xingFirst: Boolean = true): ByteArray {
        val out = ByteArray(frameLen * n)
        for (i in 0 until n) frame(i == 0 && xingFirst).copyInto(out, i * frameLen)
        return out
    }

    @Test
    fun frameLen_parsesMpeg1Layer3() {
        assertEquals(576, mpeg3FrameLen(frame(), 0))
    }

    @Test
    fun frameLen_rejectsGarbage() {
        assertEquals(-1, mpeg3FrameLen(ByteArray(8) { 0x00 }, 0))
    }

    @Test
    fun id3Size_parsesSynchsafe() {
        assertEquals(10 + 16, id3v2TagSize(id3(16), 0))
    }

    @Test
    fun singleTrack_isNotSpliced() {
        val bytes = id3() + frames(200)
        assertEquals(-1, findMp3SpliceOffset(bytes))
    }

    @Test
    fun secondId3Tag_isSplicePoint() {
        val first = id3() + frames(100)
        val bytes = first + id3() + frames(100)
        assertEquals(first.size, findMp3SpliceOffset(bytes))
    }

    @Test
    fun secondXingFrame_isSplicePoint() {
        val first = frames(100)
        val bytes = first + frame(xing = true) + frames(99, xingFirst = false)
        assertEquals(first.size, findMp3SpliceOffset(bytes))
    }

    @Test
    fun nonMp3_isNotJudged() {
        assertEquals(-1, findMp3SpliceOffset(ByteArray(MIN_AUDIO_BYTES + 100) { 0x00 }))
        val ftyp = ByteArray(MIN_AUDIO_BYTES + 100) { 0x00 }
        "ftyp".toByteArray().copyInto(ftyp, 4)
        assertEquals(-1, findMp3SpliceOffset(ftyp))
    }

    @Test
    fun truncatedPayload_isNotJudged() {
        // 帧同步断裂（数据被截断/垃圾填充）时宁可漏检，不误删
        val bytes = id3() + frames(50) + ByteArray(MIN_AUDIO_BYTES) { 0x7F }
        assertTrue(findMp3SpliceOffset(bytes) < 0)
    }
}
