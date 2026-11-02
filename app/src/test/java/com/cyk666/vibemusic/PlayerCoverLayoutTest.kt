package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放页封面边长几何的真值表（`PlayerCoverStage.kt` 的 [vinylSideDp]）。
 *
 * 防的是回归：封面是**旋转的圆角方块**，边长 S 的包围盒恒为 S·√2。只要有人
 * 把边长改回「纯按宽度取 0.62×」，上溢的 0.207·S 就会重新顶进「词」按钮行。
 * 纯 JUnit4，不依赖 Robolectric / Compose 运行时。
 */
class PlayerCoverLayoutTest {

    private val delta = 0.05f

    @Test
    fun widthBoundKeepsSixtyTwoPercentOfWidth() {
        // 360×800：可用宽 360−48=312，封面 Box 约 320 高 —— 宽度先到顶，不缩
        val side = vinylSideDp(availW = 312f, availH = 320f, widthCap = 312f * 0.62f)
        assertEquals(193.44f, side, delta)
        // 393×873：可用宽 345，Box 约 393 高 —— 同样宽度主导
        val wide = vinylSideDp(availW = 345f, availH = 393f, widthCap = 345f * 0.62f)
        assertEquals(213.9f, wide, delta)
    }

    @Test
    fun heightBoundClampsSoTheRotatedBoxStillFits() {
        // 360×640：封面 Box 只剩约 160 高，宽度上限（193）装不下包围盒，只能缩
        val availW = 312f
        val availH = 160f
        val side = vinylSideDp(availW, availH, availW * 0.62f)
        assertEquals((availH - 22f) / VINYL_BBOX_RATIO, side, delta)
        assertTrue(side * VINYL_BBOX_RATIO + 22f <= availH + delta)
    }

    @Test
    fun compactStageKeepsFixedSideWithoutOverflow() {
        // 横屏 640×360：紧凑舞台高度 278dp，180dp 的包围盒 254.6 + 上下 22 = 276.6 ≤ 278
        val stage = PLAYER_COMPACT_COVER_STAGE_DP.value
        val side = vinylSideDp(
            availW = 592f,
            availH = stage,
            widthCap = PLAYER_COMPACT_COVER_DP.value
        )
        assertEquals(180f, side, delta)
        assertTrue(side * VINYL_BBOX_RATIO + 22f <= stage + delta)
    }

    @Test
    fun neverOverflowsHeightAcrossScreenSizes() {
        // 任意尺寸都不许超出高度预算（含极矮屏，此时边长会一路缩到 0 附近）
        val heights = listOf(120f, 160f, 240f, 320f, 393f, 600f)
        for (h in heights) {
            val side = vinylSideDp(availW = 312f, availH = h, widthCap = 193f)
            assertTrue(
                "availH=$h side=$side 超出高度预算",
                side * VINYL_BBOX_RATIO + 22f <= h + delta
            )
        }
    }

    @Test
    fun degenerateInputsReturnZero() {
        assertEquals(0f, vinylSideDp(availW = 0f, availH = 320f, widthCap = 0f), delta)
        assertEquals(0f, vinylSideDp(availW = 312f, availH = 20f, widthCap = 193f), delta)
        assertEquals(0f, vinylSideDp(availW = 312f, availH = 22f, widthCap = 193f), delta)
    }
}
