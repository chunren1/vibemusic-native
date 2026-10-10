package com.cyk666.vibemusic

import androidx.media3.common.Player
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

    // ---- 焦点被拒补试阶梯（2026-10-10 二版）：只补"用户点了播放却被拒"，不补"播放中被打断" ----

    @Test
    fun deniedPlay_afterUserTap_startsLadder() {
        // 用户点按被拒（isRetryProbe=false）→ 开启新轮，用阶梯第 0 档
        assertEquals(0, nextFocusRetryIndex(playedBefore = false, retriesDone = 0, isRetryProbe = false))
    }

    @Test
    fun interruptionWhilePlaying_neverRetries() {
        // 播放中被抢（wasPlayingBeforeFocusChange=true）→ 不自动补试，两种来源都是 -1
        assertEquals(-1, nextFocusRetryIndex(playedBefore = true, retriesDone = 0, isRetryProbe = false))
        assertEquals(-1, nextFocusRetryIndex(playedBefore = true, retriesDone = 3, isRetryProbe = true))
    }

    @Test
    fun ladderAdvancesWithinEpisode() {
        // 补试探测被拒（isRetryProbe=true）→ 沿阶梯推进
        assertEquals(1, nextFocusRetryIndex(playedBefore = false, retriesDone = 1, isRetryProbe = true))
        assertEquals(
            FOCUS_RETRY_DELAYS_MS.lastIndex,
            nextFocusRetryIndex(playedBefore = false, retriesDone = FOCUS_RETRY_DELAYS_MS.size - 1, isRetryProbe = true)
        )
    }

    @Test
    fun ladderExhaustedWithinEpisode_returnsMinusOne() {
        assertEquals(-1, nextFocusRetryIndex(playedBefore = false, retriesDone = FOCUS_RETRY_DELAYS_MS.size, isRetryProbe = true))
    }

    @Test
    fun newUserTap_resetsMidLadder() {
        // 用户中途再点按（isRetryProbe=false）→ 无视已推进的计数，重置为新一轮 0 档
        assertEquals(0, nextFocusRetryIndex(playedBefore = false, retriesDone = 3, isRetryProbe = false))
        // 阶梯已用尽后用户再点 → 同样重开
        assertEquals(0, nextFocusRetryIndex(playedBefore = false, retriesDone = FOCUS_RETRY_DELAYS_MS.size, isRetryProbe = false))
    }

    @Test
    fun fullFailedSequence_advancesMonotonicallyToExhaustion() {
        // 钉死首版计时器缺陷（距上次拒绝 >15s 误判为新点按 → 24s 档自我清零死循环）：
        // 连续补试探测全部被拒（isRetryProbe=true）时，无论阶梯间隔多长（最长 60s），
        // 都必须严格单调推进到用尽 -1，绝不能被清零重开。
        var retries = 0
        FOCUS_RETRY_DELAYS_MS.indices.forEach { round ->
            val idx = nextFocusRetryIndex(playedBefore = false, retriesDone = retries, isRetryProbe = true)
            assertEquals("第 $round 次探测不得跳档/回卷", round, idx)
            retries = idx + 1
        }
        assertEquals(-1, nextFocusRetryIndex(playedBefore = false, retriesDone = retries, isRetryProbe = true))
    }

    @Test
    fun ladderSpansAboutTenMinutes() {
        val total = FOCUS_RETRY_DELAYS_MS.sum()
        assertTrue("阶梯总时长应 ≈10 分钟，实际 ${total}ms", total in 480_000..720_000)
    }

    // ---- 补试意愿门（2026-10-11：被打断停歌后未点按不得自动续播）----

    @Test
    fun probeGate_neverFiresWithoutUserTap() {
        // 本进程用户从未点过播放 → 禁止开火（无论多安静）
        assertFalse(shouldFireRetryProbe(userPlayMs = 0L, userPauseMs = 0L, isPlaying = false, playWhenReady = false))
    }

    @Test
    fun probeGate_userPauseAfterTapCancelsProbe() {
        // 点过播、随后按了暂停 → pending 探测作废
        assertFalse(shouldFireRetryProbe(userPlayMs = 1000L, userPauseMs = 2000L, isPlaying = false, playWhenReady = false))
        // 点过播、没按过暂停 → 放行（等对端放手的合法补试）
        assertTrue(shouldFireRetryProbe(userPlayMs = 1000L, userPauseMs = 0L, isPlaying = false, playWhenReady = false))
    }

    @Test
    fun probeGate_alreadyAudibleNeverFires() {
        // 已经在响 / 想播为 true → 不需要补试
        assertFalse(shouldFireRetryProbe(userPlayMs = 1000L, userPauseMs = 0L, isPlaying = true, playWhenReady = false))
        assertFalse(shouldFireRetryProbe(userPlayMs = 1000L, userPauseMs = 0L, isPlaying = false, playWhenReady = true))
    }

    // ---- 播放救援看门狗（2026-10-10：通知中心"点了播放卡死"自愈）----

    @Test
    fun rescue_triggersOnlyWhenWantsPlayButSilent() {
        // 想播、READY、有队列、没出声、未超限 → 救
        assertTrue(shouldRescuePlayback(true, false, Player.STATE_READY, 3, 0))
        // 已出声 → 不救
        assertFalse(shouldRescuePlayback(true, true, Player.STATE_READY, 3, 0))
        // 用户已暂停（不想播）→ 不救
        assertFalse(shouldRescuePlayback(false, false, Player.STATE_READY, 3, 0))
    }

    @Test
    fun rescue_neverInterferesWithLegitWaiting() {
        // 网络缓冲中：合法等待 → 不救
        assertFalse(shouldRescuePlayback(true, false, Player.STATE_BUFFERING, 3, 0))
        // IDLE（含进程被杀后 resumption 恢复流程，player 为空）→ 不救
        assertFalse(shouldRescuePlayback(true, false, Player.STATE_IDLE, 3, 0))
        // 自然播完 ENDED → 不救
        assertFalse(shouldRescuePlayback(true, false, Player.STATE_ENDED, 3, 0))
        // 空队列 → 不救
        assertFalse(shouldRescuePlayback(true, false, Player.STATE_READY, 0, 0))
    }

    @Test
    fun rescue_boundedAttempts() {
        assertTrue(shouldRescuePlayback(true, false, Player.STATE_READY, 3, PLAY_RESCUE_MAX_ATTEMPTS - 1))
        assertFalse(shouldRescuePlayback(true, false, Player.STATE_READY, 3, PLAY_RESCUE_MAX_ATTEMPTS))
    }

    @Test
    fun rescue_disabledByDefault_untilRootCauseLocated() {
        // 2026-10-11 审查：真机未验证成功且触发条件与两大致因互斥，默认关闭；
        // 诊断时手动置 true，改动此断言即代表"已验证可开"。
        assertFalse(PLAY_RESCUE_ENABLED)
    }
}
