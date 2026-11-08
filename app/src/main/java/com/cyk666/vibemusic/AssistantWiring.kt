package com.cyk666.vibemusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * 把 [AssistantState.notice] 转成一次性 Snackbar（对齐已删除的 `AiNoticeEffect` 的写法）。
 *
 * 必须消费后归 null：`notice` 是 Compose state，只清屏不清值会在下次重组时
 * 重放同一条提示（老 bug 的成因），所以这里读一次、弹一次、立刻 consume。
 */
@Composable
internal fun AssistantNoticeEffect(
    state: AssistantState,
    onNotice: (String) -> Unit
) {
    val notice = state.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            onNotice(notice)
            state.consumeNotice()
        }
    }
}
