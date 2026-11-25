package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 「其他版本」的解析与展示口径。
 *
 * 服务端已经筛过（标题一致、无 DJ/伴奏/翻唱词），客户端这边只保证：
 * 形状坏了要抛错（不能静默变空，否则"没有其他版本"和"接口坏了"分不清），
 * 以及差异标签能翻译成人话。
 */
@RunWith(RobolectricTestRunner::class)
class AlternativesTest {

    private fun payload(candidates: String) = """
        {"code":200,"message":"success","data":{"name":"再见莫妮卡","artist":"彭席彦",
         "durationMs":180117,"candidates":[$candidates]}}
    """.trimIndent()

    @Test
    fun parse_readsCandidatesAndWarnings() {
        val list = parseAlternatives(
            payload(
                """{"platform":"qq","sourceId":"002P8IhI04Y05q","name":"再见莫妮卡",
                    "artists":"彭席严","durationMs":236000,"quality":"320","hasUrl":true,
                    "warnings":["artist_mismatch","duration_gap_56s"],"score":0.82}"""
            )
        )
        assertEquals(1, list.size)
        val c = list[0]
        assertEquals("qq", c.platform)
        assertEquals(236000L, c.durationMs)
        assertTrue(c.warningText().contains("歌手不同"))
        assertTrue(c.warningText().contains("时长差 56s"))
    }

    @Test
    fun parse_missingCandidatesThrows() {
        // data 里没有 candidates = 协议坏了，必须抛错而不是当"没有其他版本"
        try {
            parseAlternatives("""{"code":200,"data":{"name":"x"}}""")
            throw AssertionError("应当抛错")
        } catch (expected: RuntimeException) {
            assertTrue(expected.message!!.contains("candidates"))
        }
    }

    @Test
    fun toSong_keepsPlatformAndSourceId() {
        val song = AlternativeCandidate("migu", "600123", "再见莫妮卡", "某歌手", 200000L, "HQ", emptyList(), 0.5).toSong()
        assertEquals("migu", song.platform)
        assertEquals("600123", song.sourceId)
        assertEquals(200, song.durationSec)
    }

    @Test
    fun path_encodesNameAndOmitsUnknownDuration() {
        val p1 = alternativesPath("再见 莫妮卡", "彭席彦", 180117L)
        assertTrue(p1.startsWith("api/songs/alternatives?"))
        assertTrue(p1.contains("duration=180117"))
        val p2 = alternativesPath("x", "", null)
        assertFalse(p2.contains("artist="))
        assertFalse(p2.contains("duration="))
    }

    @Test
    fun formatDuration_mmss() {
        assertEquals("3:56", formatDurationMs(236000L))
        assertEquals("0:05", formatDurationMs(5000L))
        assertEquals("0:00", formatDurationMs(-1L))
    }
}
