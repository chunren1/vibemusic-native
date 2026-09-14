package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Top-7 回归：睡眠定时持久化到期时间戳，重启后恢复真实剩余。
 *
 * 迁移策略：旧版本只存 Int 总分钟数（sleep_timer_min）。新版本同时写
 * deadline（sleep_deadline_ms）+ minutes（兼容降级回读）。启动时以
 * deadline 为准；无 deadline 但有旧 minutes 时，按"全新完整时长"重启一轮
 * 并立即改写为 deadline 形式（恰好一次）；过期 deadline 直接清零。
 */
@RunWith(RobolectricTestRunner::class)
class SleepDeadlineTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    @Test
    fun deadline_zeroMinutesMeansOff() {
        assertEquals(0L, computeSleepDeadlineMs(1_000_000L, 0))
        assertEquals(0L, computeSleepDeadlineMs(1_000_000L, -5))
    }

    @Test
    fun deadline_fiveMinutesAdds300s() {
        val now = 1_750_000_000_000L
        assertEquals(now + 300_000L, computeSleepDeadlineMs(now, 5))
        assertEquals(now + 1_800_000L, computeSleepDeadlineMs(now, 30))
    }

    @Test
    fun remaining_futureDeadlineReadsSeconds() {
        val now = 1_750_000_000_000L
        assertEquals(300L, sleepRemainingSec(now + 300_000L, now))
        assertEquals(59L, sleepRemainingSec(now + 59_500L, now))
    }

    @Test
    fun remaining_expiredOrMissingReadsZero() {
        val now = 1_750_000_000_000L
        assertEquals(0L, sleepRemainingSec(now - 1L, now))
        assertEquals(0L, sleepRemainingSec(now, now))
        assertEquals(0L, sleepRemainingSec(0L, now))
    }

    @Test
    fun restore_liveDeadlineWinsWithTrueRemaining() {
        // 设 30 分钟、5 分钟后被杀：恢复剩余 ≈25 分钟，而非整段 30 分钟。
        val now = 1_750_000_000_000L
        val deadline = now - 300_000L + 1_800_000L
        val r = resolveSleepRestore(deadline, 30, now)
        assertEquals(30, r.minutes)
        assertEquals(deadline, r.deadlineMs)
        assertEquals(1_500L, r.leftSec)
    }

    @Test
    fun restore_expiredDeadlineClears() {
        val now = 1_750_000_000_000L
        val r = resolveSleepRestore(now - 1_000L, 30, now)
        assertEquals(SleepRestore(0, 0L, 0L), r)
    }

    @Test
    fun restore_legacyMinutesRestartFullWindowOnce() {
        // 旧版本升级：无 deadline 但有 minutes=30 → 全新 30 分钟一轮，
        // 调用方随即以 deadline 形式持久化。
        val now = 1_750_000_000_000L
        val r = resolveSleepRestore(0L, 30, now)
        assertEquals(30, r.minutes)
        assertEquals(now + 1_800_000L, r.deadlineMs)
        assertEquals(1_800L, r.leftSec)
    }

    @Test
    fun restore_liveDeadlineWithoutLegacyCeilsMinutes() {
        val now = 1_750_000_000_000L
        val r = resolveSleepRestore(now + 90_000L, 0, now)
        assertEquals(2, r.minutes)
        assertEquals(90L, r.leftSec)
    }

    @Test
    fun restore_allZeroStaysOff() {
        assertEquals(SleepRestore(0, 0L, 0L), resolveSleepRestore(0L, 0, 1_000L))
        assertEquals(SleepRestore(0, 0L, 0L), resolveSleepRestore(0L, -3, 1_000L))
    }

    @Test
    fun store_deadlineRoundTrip(): Unit = runBlocking {
        val ctx = context()
        QueueStore.saveSleepDeadline(ctx, 1_750_000_100_000L)
        assertEquals(1_750_000_100_000L, QueueStore.loadSleepDeadline(ctx))
        QueueStore.saveSleepDeadline(ctx, 0L)
        assertEquals(0L, QueueStore.loadSleepDeadline(ctx))
    }

    @Test
    fun store_legacyMinutesStillReadableForMigration(): Unit = runBlocking {
        val ctx = context()
        QueueStore.saveSleepMinutes(ctx, 30)
        QueueStore.saveSleepDeadline(ctx, 0L)
        assertEquals(30, QueueStore.loadSleepMinutes(ctx))
        // 迁移输入：旧 minutes 可读，resolve 给出全新一轮 + deadline。
        val r = resolveSleepRestore(
            QueueStore.loadSleepDeadline(ctx).takeIf { it > 0L } ?: 0L,
            QueueStore.loadSleepMinutes(ctx),
            System.currentTimeMillis()
        )
        assertTrue(r.minutes == 30 && r.leftSec == 1_800L && r.deadlineMs > 0L)
        QueueStore.saveSleepMinutes(ctx, 0)
        QueueStore.saveSleepDeadline(ctx, 0L)
    }
}
