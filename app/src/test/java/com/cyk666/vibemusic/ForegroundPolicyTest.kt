package com.cyk666.vibemusic

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Contract for the 2026-09-17 notification-play-button incident: a paused
 * queue must keep the service in the foreground (otherwise process reclaim
 * kills the session and the pending-intent play button no-ops). Foreground
 * is dropped ONLY when there is nothing left to recover — empty queue, or
 * IDLE after error / explicit stop (that path belongs to the Activity heal).
 */
@RunWith(RobolectricTestRunner::class)
class ForegroundPolicyTest {

    @Test
    fun isPlaying_staysForeground() {
        assertTrue(
            shouldStayForeground(
                isPlaying = true,
                playWhenReady = true,
                mediaItemCount = 1,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun preparing_prefersForeground() {
        // playWhenReady alone wins: user intent to play beats everything else.
        assertTrue(
            shouldStayForeground(
                isPlaying = false,
                playWhenReady = true,
                mediaItemCount = 1,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun pausedWithQueue_ready_staysForeground() {
        // The core regression: paused, queue loaded, notification must stay live.
        assertTrue(
            shouldStayForeground(
                isPlaying = false,
                playWhenReady = false,
                mediaItemCount = 5,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun pausedWithQueue_buffering_staysForeground() {
        assertTrue(
            shouldStayForeground(
                isPlaying = false,
                playWhenReady = false,
                mediaItemCount = 3,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun endOfQueueNotIdle_staysForeground() {
        // Played to the end, queue still loaded: replayable, keep the process.
        assertTrue(
            shouldStayForeground(
                isPlaying = false,
                playWhenReady = false,
                mediaItemCount = 2,
                playbackState = Player.STATE_ENDED
            )
        )
    }

    @Test
    fun pausedEmptyQueue_dropsForeground() {
        assertFalse(
            shouldStayForeground(
                isPlaying = false,
                playWhenReady = false,
                mediaItemCount = 0,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun idleWithQueue_dropsForeground() {
        // Error / explicit stop: Activity heal owns this path, no keep-alive.
        assertFalse(
            shouldStayForeground(
                isPlaying = false,
                playWhenReady = false,
                mediaItemCount = 4,
                playbackState = Player.STATE_IDLE
            )
        )
    }

    @Test
    fun idleEmptyQueue_dropsForeground() {
        assertFalse(
            shouldStayForeground(
                isPlaying = false,
                playWhenReady = false,
                mediaItemCount = 0,
                playbackState = Player.STATE_IDLE
            )
        )
    }
}
