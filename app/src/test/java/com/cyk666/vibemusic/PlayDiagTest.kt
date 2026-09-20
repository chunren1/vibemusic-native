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

    // ---- 焦点被拒补试：只补"用户点了播放却被拒"，不补"播放中被打断" ----

    @Test
    fun deniedPlay_afterUserTap_retries() {
        assertTrue(shouldRetryDeniedPlay(playedBefore = false, retriesDone = 0))
    }

    @Test
    fun interruptionWhilePlaying_neverRetries() {
        assertFalse(shouldRetryDeniedPlay(playedBefore = true, retriesDone = 0))
    }

    @Test
    fun retriesAreBounded() {
        assertFalse(shouldRetryDeniedPlay(playedBefore = false, retriesDone = FOCUS_RETRY_MAX))
    }
}
