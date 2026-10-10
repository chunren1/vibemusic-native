package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知播放键"点了没反应"的真机诊断（格式化）与焦点被拒补试策略。
 *
 * 诊断设计意图：复现一次后只看设置页那一行就能定位卡在哪环——
 * 没有新记录=事件没到 App；cold=进程被清；resume-*=官方恢复钩子结果；
 * focus-blocked=音频焦点被拒；playing=真的出声。
 */
class PlayDiagTest {

    @Test
    fun emptyDiag_showsPlaceholder() {
        assertTrue(playDiagLabel("", 0L).contains("暂无记录"))
    }

    @Test
    fun diagLabel_mapsCodeAndTimestamp() {
        val label = playDiagLabel("focus-blocked", 1_700_000_000_000L)
        assertTrue(label.contains("音频焦点拒绝"))
        assertTrue(label.contains(":")) // HH:mm:ss
    }

    @Test
    fun diagCode_coldStartExplained() {
        assertEquals("服务冷启动（进程曾被杀）", mapPlayDiagCode("cold"))
    }

    @Test
    fun diagCode_resumeOkIsReadable() {
        assertEquals("已交回队列快照，等待播放", mapPlayDiagCode("resume-ok:20@3"))
    }

    @Test
    fun diagCode_resumeFailIsReadable() {
        assertEquals("队列快照交回失败", mapPlayDiagCode("resume-fail:no-snapshot"))
    }

    @Test
    fun diagCode_unknownPassthrough() {
        assertEquals("whatever", mapPlayDiagCode("whatever"))
    }

    // ---- 焦点被拒补试阶梯（2026-10-10 重做）：只补"用户点了播放却被拒"，不补"播放中被打断" ----

    @Test
    fun deniedPlay_afterUserTap_startsLadder() {
        // 首轮（距上次拒绝远）→ 用阶梯第 0 档
        assertEquals(0, nextFocusRetryIndex(playedBefore = false, retriesDone = 0, msSinceLastDenial = Long.MAX_VALUE))
    }

    @Test
    fun interruptionWhilePlaying_neverRetries() {
        assertEquals(-1, nextFocusRetryIndex(playedBefore = true, retriesDone = 0, msSinceLastDenial = Long.MAX_VALUE))
    }

    @Test
    fun ladderAdvancesWithinEpisode() {
        // 同一轮内沿阶梯推进
        assertEquals(1, nextFocusRetryIndex(playedBefore = false, retriesDone = 1, msSinceLastDenial = 5_000))
        assertEquals(FOCUS_RETRY_DELAYS_MS.lastIndex, nextFocusRetryIndex(playedBefore = false, retriesDone = FOCUS_RETRY_DELAYS_MS.size - 1, msSinceLastDenial = 5_000))
    }

    @Test
    fun ladderExhaustedWithinEpisode_returnsMinusOne() {
        assertEquals(-1, nextFocusRetryIndex(playedBefore = false, retriesDone = FOCUS_RETRY_DELAYS_MS.size, msSinceLastDenial = 5_000))
    }

    @Test
    fun newTapAfterGap_resetsEpisode() {
        // 距上次拒绝超过 15s 的新拒绝 = 用户新点按 → 计数清零重来（旧版死锁的修复点）
        assertEquals(0, nextFocusRetryIndex(playedBefore = false, retriesDone = FOCUS_RETRY_DELAYS_MS.size, msSinceLastDenial = 16_000))
    }

    @Test
    fun ladderSpansAboutTwoMinutes() {
        val total = FOCUS_RETRY_DELAYS_MS.sum()
        assertTrue("阶梯总时长应 ≈2 分钟，实际 ${total}ms", total in 60_000..180_000)
    }
}
