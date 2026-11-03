package com.cyk666.vibemusic

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-11-03 播放链路审计的两道门（纯逻辑）：
 * - 自动重试/跳歌/自愈不许撤销用户刚按下的暂停（`shouldResumePlayback`）；
 * - 播完（STATE_ENDED）后按播放键必须先归位，否则 `play()` 零变化（`needsRewindBeforePlay`）。
 */
class PlaybackIntentTest {

    @Test
    fun resumeOnlyWhenUserStillWantsPlayback() {
        assertTrue(shouldResumePlayback(playWhenReady = true, isPlaying = true))
        // 缓冲/重建中 isPlaying 短暂为 false，意愿还在 → 恢复
        assertTrue(shouldResumePlayback(playWhenReady = true, isPlaying = false))
        // 用户按了暂停（playWhenReady=false, isPlaying=false）→ 不许替他恢复
        assertFalse(shouldResumePlayback(playWhenReady = false, isPlaying = false))
        assertTrue(shouldResumePlayback(playWhenReady = false, isPlaying = true))
    }

    @Test
    fun endedStateNeedsRewindBeforePlay() {
        assertTrue(needsRewindBeforePlay(Player.STATE_ENDED))
        assertFalse(needsRewindBeforePlay(Player.STATE_IDLE))
        assertFalse(needsRewindBeforePlay(Player.STATE_BUFFERING))
        assertFalse(needsRewindBeforePlay(Player.STATE_READY))
    }
}
