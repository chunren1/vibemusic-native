package com.cyk666.vibemusic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * C5 第 2 步：把「调后端 → 提示 → 重载」动作的三段 catch 梯子收成一处。
 *
 * 此前 `MainActivity` 里 19 处动作各自抄了一遍：
 * ```
 * scope.launch {
 *     try { ...body... }
 *     catch (e: NetworkAuthException) { handleAuthLost(e.message, clearTokens = false) }
 *     catch (e: AuthException)        { handleAuthLost(e.message) }
 *     catch (e: Exception)            { showError(diagnosableError("X失败", e)) }
 * }
 * ```
 * 每处 10 行壳子，改一条口径（比如给诊断信息加 traceId）要动 19 个地方。
 *
 * 三类结局的语义**与旧代码逐条对齐**，不顺手改行为：
 * - [NetworkAuthException]：网络抖动的鉴权失败 → 保留本地会话（`clearTokens = false`）
 * - [AuthException]：服务端判定凭据已死 → 清会话
 * - 其他 [Exception]：默认 `diagnosableError(failureLabel, e)` 提示；
 *   需要自己记状态的调用方用 [onFailure] 覆盖
 *
 * [onFinally] 对应旧代码里各动作的 `finally`（复位 busy/loading 标志）。
 */
class ApiActionRunner(
    private val scope: CoroutineScope,
    private val onAuthLost: (message: String?, clearTokens: Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {

    fun launch(
        failureLabel: String,
        onFinally: () -> Unit = {},
        onFailure: ((Exception) -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        scope.launch {
            try {
                block()
            } catch (e: NetworkAuthException) {
                onAuthLost(e.message, false)
            } catch (e: AuthException) {
                onAuthLost(e.message, true)
            } catch (e: Exception) {
                if (onFailure != null) onFailure(e) else onError(diagnosableError(failureLabel, e))
            } finally {
                onFinally()
            }
        }
    }
}
