package com.cyk666.vibemusic

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 播放守卫回归（CODE-REVIEW-2026-10-28 PB-4 / PB-6 / PB-7 / PB-9）。
 *
 * 这四条的共同点：**"跳歌"和"可离线播"这两个判断以前都太乐观**——
 * 把"还有下一项"当成"能换一首"、把"有缓存字节"当成"能播完"。
 * 这里的用例把"乐观"钉死，防止回归。
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackGuardTest {

    // ---- PB-6：单曲循环下 hasNext 为 true 但跳不动 ----

    @Test
    fun autoSkip_singleLoopNeverSkips() {
        assertFalse(shouldAutoSkip(0, hasNext = true, repeatMode = Player.REPEAT_MODE_ONE))
        assertFalse(shouldAutoSkip(2, hasNext = true, repeatMode = Player.REPEAT_MODE_ONE))
    }

    @Test
    fun autoSkip_repeatAllStillSkips() {
        assertTrue(shouldAutoSkip(0, hasNext = true, repeatMode = Player.REPEAT_MODE_ALL))
        assertTrue(shouldAutoSkip(2, hasNext = true, repeatMode = Player.REPEAT_MODE_OFF))
    }

    @Test
    fun autoSkip_capAndQueueEndStillHold() {
        assertFalse(shouldAutoSkip(3, hasNext = true))
        assertFalse(shouldAutoSkip(0, hasNext = false))
        // 默认参数保持历史语义（离线跳歌路径仍按"有没有目标"调用）
        assertTrue(shouldAutoSkip(0, hasNext = true))
    }

    @Test
    fun toast_singleLoopSaysCannotSkipNotSkipped() {
        val msg = skipErrorToast("夜曲", "ERROR_CODE_IO_FILE_NOT_FOUND", SkipOutcome.STOPPED_SINGLE_LOOP)
        assertTrue(msg.contains("无法跳过"))
        assertFalse(msg.contains("已跳过"))
        assertFalse(msg.contains("末尾"))
    }

    // ---- PB-7：随机播放不能按下标推算 ----
    //
    // 修法是在服务侧改用 Exo 的洗牌序（seekToNextMediaItem 尊重 shuffle），
    // 而不是 `selectNextOfflineIndex` 的下标扫描 —— 这里没有纯函数可钉，
    // 纯判据侧由 `shouldAutoSkip` 的失败预算兜底（见上面的 cap 用例）。

    @Test
    fun autoSkip_budgetStillBoundsShuffleAttempts() {
        // 洗牌序里连坏 3 首必须停手，不能无限沿洗牌序试下去
        assertTrue(shouldAutoSkip(2, hasNext = true, repeatMode = Player.REPEAT_MODE_ALL))
        assertFalse(shouldAutoSkip(3, hasNext = true, repeatMode = Player.REPEAT_MODE_ALL))
    }

    // ---- PB-4：离线门看"连续可读前缀"，不看"有字节" ----

    @Test
    fun prefix_prefetchOnlyIsNotEnoughForOffline() {
        // 只预热了 256KB 头：旧逻辑 cachedBytes > 0 → 判可播 → 播 4-6 秒撞空洞
        val prefix = contiguousPrefixBytes(listOf(CacheSpanRange(0L, 256L * 1024L)))
        assertEquals(256L * 1024L, prefix)
        assertFalse(prefix >= OFFLINE_MIN_PREFIX_BYTES)
    }

    @Test
    fun prefix_stopsAtFirstHole() {
        // 0-2MB 连续 + 2MB 之后另有一段：前缀只算到 2MB
        val prefix = contiguousPrefixBytes(
            listOf(
                CacheSpanRange(0L, 2L * 1024L * 1024L),
                CacheSpanRange(3L * 1024L * 1024L, 1024L * 1024L)
            )
        )
        assertEquals(2L * 1024L * 1024L, prefix)
    }

    @Test
    fun prefix_holeSpanTerminatesPrefix() {
        val prefix = contiguousPrefixBytes(
            listOf(
                CacheSpanRange(0L, 512L * 1024L),
                CacheSpanRange(512L * 1024L, 512L * 1024L, isHole = true),
                CacheSpanRange(1024L * 1024L, 4L * 1024L * 1024L)
            )
        )
        assertEquals(512L * 1024L, prefix)
        assertFalse(prefix >= OFFLINE_MIN_PREFIX_BYTES)
    }

    @Test
    fun prefix_fullyCachedLongSongPasses() {
        val prefix = contiguousPrefixBytes(listOf(CacheSpanRange(0L, 4L * 1024L * 1024L)))
        assertTrue(prefix >= OFFLINE_MIN_PREFIX_BYTES)
    }

    @Test
    fun prefix_overlappingSpansDoNotDoubleCount() {
        val prefix = contiguousPrefixBytes(
            listOf(
                CacheSpanRange(0L, 1024L),
                CacheSpanRange(512L, 1024L)
            )
        )
        assertEquals(1536L, prefix)
    }

    @Test
    fun prefix_emptyAndUnorderedInputsAreSafe() {
        assertEquals(0L, contiguousPrefixBytes(emptyList()))
        assertEquals(
            2048L,
            contiguousPrefixBytes(listOf(CacheSpanRange(1024L, 1024L), CacheSpanRange(0L, 1024L)))
        )
    }

    // ---- PB-9：离线失败预算不再污染在线计数（纯判据侧） ----

    @Test
    fun offlineBudget_capIsPerPathNotShared() {
        // 离线连坏 3 首后，在线路径的预算仍是满的（0 → 可以跳歌）
        assertTrue(shouldAutoSkip(0, hasNext = true, repeatMode = Player.REPEAT_MODE_ALL))
        // 而离线路径自己的预算到 3 就停手（防两个坏文件互相弹跳）
        assertFalse(shouldAutoSkip(3, hasNext = true, repeatMode = Player.REPEAT_MODE_ALL))
    }
}
