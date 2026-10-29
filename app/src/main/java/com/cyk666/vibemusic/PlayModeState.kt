package com.cyk666.vibemusic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * C5 第 1 片：`playMode` 的唯一持有者（见 `docs/REFACTOR-mainactivity.md` §8）。
 *
 * 只负责「当前模式是什么」。controller 同步与磁盘持久化仍留在 `MainActivity`，
 * 由 [cycle] 的 [apply] 回调与调用方在返回后各自执行 —— 状态先搬、副作用后搬，
 * 每片可单独回归。
 *
 * [mode] 对外只读（`private set`）：改模式只能走 [cycle] 或 [restore]，
 * 防止再出现「某处直接赋值、绕过了 controller 同步」的写法。
 */
class PlayModeState(initial: PlayMode = PlayMode.SEQUENTIAL) {

    var mode by mutableStateOf(initial)
        private set

    /**
     * 推进到下一模式并返回新模式。
     *
     * 顺序刻意与旧实现一致：**先**把 repeat/shuffle 写进 controller（[apply]），
     * 写成功后才改状态。若 [apply] 抛异常，模式保持不变并由调用方提示 ——
     * 反过来会留下「UI 说随机、播放器还是顺序」的不一致。
     *
     * controller 为 null（尚未连上）时调用方传空 apply，模式照常推进并持久化。
     */
    fun cycle(apply: (RepeatShuffle) -> Unit = {}): PlayMode {
        val next = mode.next()
        apply(next.toRepeatShuffle())
        mode = next
        return next
    }

    /** 用 controller / 磁盘的 repeat+shuffle 真值覆盖（不回写 controller、不持久化）。 */
    fun restore(rs: RepeatShuffle) {
        mode = playModeFrom(rs.repeatMode, rs.shuffleOn)
    }

    /** 随机与列表循环都靠 repeat-ALL 维持「播完最后一首继续」的语义。 */
    fun repeatsAll(): Boolean = mode == PlayMode.LIST_LOOP || mode == PlayMode.SHUFFLE
}
