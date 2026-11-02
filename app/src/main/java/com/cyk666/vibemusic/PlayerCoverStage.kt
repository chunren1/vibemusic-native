package com.cyk666.vibemusic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 播放页封面区（从 PlayerScreen.kt 抽出：那个文件靠棘轮卡 933 纯 LOC）。
 *
 * 修的是「旋转的封面偶尔挡住上方『词』按钮」：`VinylCover` 转的是**圆角方块**，
 * 边长为 S 时外接包围盒恒为 S·√2（任意角度都一样，45° 是最坏情况），
 * 四边各外溢 0.207·S。原先按可用宽度取 0.62× 边长（360dp 屏约 193dp 见方），
 * 上溢约 40dp，正好顶进上方 48dp 的按钮行；同时下溢 40dp 逼近频谱条与歌名。
 *
 * 现在改为按**高度预算反算边长**（见 [vinylSideDp]）：先扣掉上下预留的间隙，
 * 再除以 √2 得到「转起来也不外溢」的最长边。360×800 / 393×873 这类常见竖屏
 * 由宽度约束主导（边长几乎不变，视觉零变化）；只有矮屏才真正缩小 —— 那是
 * 几何上躲不开的（可用高度装不下包围盒时，不缩就只能溢出）。
 *
 * 另外按用户要求整体**下移 5dp**（[PLAYER_COVER_SHIFT_DP]）：上方间隙 16dp
 * （与阴影 blur 同高，不然投影会糊到按钮上），下方只留 6dp —— 下沉后
 * 上下余量分别是 base+5 / base−5，视觉上就是「稍微往下挪了一点」。
 */

/** 旋转包围盒 / 边长：方块转到任意角度的最大对角投影恒为 S·√2。 */
const val VINYL_BBOX_RATIO: Float = 1.4142135f

/** 封面包围盒到上方按钮行的最小间隙 = `shadow(16.dp)` 的 blur，再小阴影会糊上按钮。 */
const val PLAYER_COVER_GAP_TOP_DP: Float = 16f

/** 下方间隙：只需不与频谱条/歌名贴死，因此比上方小，差额就是整体下沉量。 */
const val PLAYER_COVER_GAP_BOTTOM_DP: Float = 6f

/** 整体下沉 = 上下间隙差的一半（重心移动，两侧余量同时满足）。 */
const val PLAYER_COVER_SHIFT_DP: Float = (PLAYER_COVER_GAP_TOP_DP - PLAYER_COVER_GAP_BOTTOM_DP) / 2f

/** 竖屏封面上限 = 可用宽度的 0.62（与原视觉一致，不是缩小后的新口径）。 */
const val PLAYER_COVER_WIDTH_FRACTION: Float = 0.62f

/** 紧凑布局（横屏/分屏）封面固定边长。 */
val PLAYER_COMPACT_COVER_DP = 180.dp

/**
 * 紧凑布局的封面舞台高度：180·√2 ≈ 254.6 的包围盒 + 上下间隙 22 ≈ 277，
 * 取 278dp。这一支本来就在 verticalScroll 里（多占高度不挤压任何兄弟节点），
 * 所以把舞台撑高比把封面缩到 116dp（−36%）更划算。
 */
val PLAYER_COMPACT_COVER_STAGE_DP = 278.dp

/**
 * 纯函数：给定可用宽高、宽度上限与上下间隙，算出封面**边长**（dp）。
 *
 * 保证 `side·√2 + gapTop + gapBottom ≤ availH`，即任意旋转角度下包围盒都装得下；
 * 同时在宽屏上退回宽度上限（不无故缩小）。
 * 输入为 0 或负（首帧未测量 / 极端分屏）时返回 0，调用方据此跳过绘制。
 */
fun vinylSideDp(
    availW: Float,
    availH: Float,
    widthCap: Float,
    gapTop: Float = PLAYER_COVER_GAP_TOP_DP,
    gapBottom: Float = PLAYER_COVER_GAP_BOTTOM_DP
): Float {
    val heightBound = (availH - gapTop - gapBottom) / VINYL_BBOX_RATIO
    return minOf(widthCap, availW, heightBound).coerceAtLeast(0f)
}

/**
 * 封面舞台：按可用空间算边长 → 下沉 5dp → 拖拽/点击手势 → [VinylCover]。
 *
 * [modifier] 由调用方给：竖屏 `weight(1f).fillMaxWidth()`（吃掉剩余高度），
 * 紧凑布局 `fillMaxWidth().height(PLAYER_COMPACT_COVER_STAGE_DP)`。
 * [onDrag] 收到的是**手势总位移的 dp 值**，判定仍由 [resolvePlayerGesture] 做。
 */
@Composable
fun PlayerCoverStage(
    coverUrl: String?,
    isPlaying: Boolean,
    spinKey: Any?,
    scale: Float,
    cornerDp: Dp,
    compact: Boolean,
    onDrag: (dxDp: Float, dyDp: Float) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val availW = maxWidth.value
        val availH = maxHeight.value
        val widthCap = if (compact) {
            PLAYER_COMPACT_COVER_DP.value
        } else {
            availW * PLAYER_COVER_WIDTH_FRACTION
        }
        val side = vinylSideDp(availW, availH, widthCap)
        // VinylCover 是「按父宽取边长」的（fillMaxWidth(fraction) + aspectRatio），
        // 所以把算出来的边长换回比例；availW 为 0 时避开除零。
        val fraction = if (availW > 0f) (side / availW).coerceIn(0f, 1f) else 1f
        Box(
            modifier = Modifier
                .offset(y = PLAYER_COVER_SHIFT_DP.dp)
                .pointerInput(spinKey) {
                    var tx = 0f
                    var ty = 0f
                    detectDragGestures(
                        onDragStart = { tx = 0f; ty = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            tx += amount.x
                            ty += amount.y
                        },
                        onDragEnd = {
                            onDrag(
                                with(density) { tx.toDp().value },
                                with(density) { ty.toDp().value }
                            )
                        }
                    )
                }
                .clickable { onClick() }
        ) {
            VinylCover(
                coverUrl = coverUrl,
                isPlaying = isPlaying,
                spinKey = spinKey,
                scale = scale,
                cornerDp = cornerDp,
                fraction = fraction
            )
        }
    }
}
