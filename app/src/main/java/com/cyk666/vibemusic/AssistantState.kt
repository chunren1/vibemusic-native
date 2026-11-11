package com.cyk666.vibemusic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class ChatRole { USER, ASSISTANT }

/**
 * 一条对话消息。
 *
 * `songs` 只有助手消息可能有：`/api/assistant/chat` 的工具调用把真实搜到的歌
 * 单独放在 `data.songs` 里（不是塞进 reply 文本），UI 据此在气泡下方渲染歌曲行。
 * `failed` 标记这条是错误提示（UI 用弱化配色，不当作正常回复）。
 */
data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val songs: List<Song> = emptyList(),
    val failed: Boolean = false,
    val actions: List<AssistantAction> = emptyList()
)

/**
 * AI 助手对话状态（普通 class + `mutableStateOf`，依赖全部从构造注入，方便单测；
 * 不持有 Context / Activity）。
 *
 * 重入闸门是 [sending]：它同时是"正在想…"指示与连点保护——后端限流是每分钟 10 次，
 * 手快连点几下就能把额度打光，所以按钮禁用之外这里必须再挡一层。
 *
 * 不做本地持久化：对话记忆在后端（`chatMemoryService`），冷启动会由后端灌回历史，
 * App 再存一份只会两边不一致（清空不同步、跨账号串味）。
 */
class AssistantState(
    private val deviceId: String = "",
    private val send: suspend (String) -> AssistantReply = { msg ->
        VibeApi.assistantChat(msg, deviceId)
    },
    private val clearRemote: suspend () -> Unit = { VibeApi.clearAssistantHistory(deviceId) },
    /** 冷启动拉服务端历史（注入以便单测；失败静默，见 [restore]）。 */
    private val restoreRemote: suspend () -> List<AssistantHistoryMessage> = { VibeApi.assistantHistory(deviceId) },
    /** 服务端歌单被改动时通知宿主刷新本地缓存（AI 是「外部写入」，界面自己感知不到）。 */
    private val onWriteAction: () -> Unit = {},
    /** 收藏类写操作（AI 收藏/取消收藏后同步界面收藏态）。 */
    private val onFavoriteAction: (sourceId: String, favorited: Boolean) -> Unit = { _, _ -> }
) {
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    var sending by mutableStateOf(false)
        private set

    /** 给 Snackbar 的一次性提示；消费后必须归 null（见 [consumeNotice]）。 */
    var notice by mutableStateOf<String?>(null)
        private set

    /** 服务端历史只拉一次：进入页面、清空之后都不再重复拉（清空语义优先）。 */
    private var restored = false

    /**
     * 发一条消息。空白消息直接忽略；[sending] 期间的重入一律丢弃（不是排队：
     * 连点产生的重复问题是脏输入，不该被当成用户意图补发）。
     */
    fun send(scope: CoroutineScope, raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        if (sending) {
            // 静默丢弃会让用户以为发出去了；给一句明确反馈，输入框内容由界面保留
            notice = BUSY_NOTICE
            return
        }
        sending = true
        messages = messages + ChatMessage(ChatRole.USER, text)
        scope.launch {
            try {
                val reply = send(text)
                messages = messages + ChatMessage(
                    role = ChatRole.ASSISTANT,
                    text = reply.reply.ifBlank { FALLBACK_REPLY },
                    songs = reply.songs,
                    actions = reply.actions
                )
                if (reply.actions.any { it.ok && it.touchesPlaylists() }) onWriteAction()
                // 收藏是局部状态（favIds），按条同步给界面做增量更新
                reply.actions.filter { it.ok && it.touchesFavorites() }
                    .forEach { onFavoriteAction(it.sourceId, it.favorited) }
            } catch (e: Exception) {
                SilentFail.note("AssistantState.send:${e.javaClass.simpleName}")
                val rateLimited = isRateLimited(e)
                messages = messages + ChatMessage(
                    role = ChatRole.ASSISTANT,
                    text = if (rateLimited) RATE_NOTICE else FAIL_NOTICE,
                    failed = true
                )
                notice = if (rateLimited) RATE_NOTICE else FAIL_NOTICE
            } finally {
                sending = false
            }
        }
    }

    /**
     * 冷启动拉服务端历史填充界面（由界面 `LaunchedEffect(Unit)` 调用；幂等）。
     *
     * 为什么必须补这个端点：此前只有 DELETE 没有 GET，App 界面空白但后端上下文
     * 还在（30 分钟 TTL），AI 会冒出「你刚才提到…」而用户看到的是新对话——
     * 这就是「没隔离」体感的来源。
     *
     * 恢复的只是纯文本气泡（songs/actions 不落服务端历史）。三条守卫：
     * - 拉回时若用户已发过消息（restore 慢于首条 send），不覆盖现有对话；
     * - 失败静默（冷启动网络失败不该在助手页弹错），记 SilentFail 便于诊断；
     * - [clear] 后 [restored] 置位——用户刚清空就不该再把历史拉回来。
     */
    suspend fun restore() {
        if (restored || messages.isNotEmpty()) return
        restored = true
        try {
            val history = restoreRemote()
            if (messages.isEmpty() && history.isNotEmpty()) {
                messages = history.map { ChatMessage(it.role, it.text) }
            }
        } catch (e: Exception) {
            SilentFail.note("AssistantState.restore:${e.javaClass.simpleName}")
        }
    }

    /**
     * 清空对话。界面先清、后端后清：后端失败不该让"点了清空还留着记录"，
     * 但失败要能重试——所以失败时给出提示，用户再点一次即可。
     */
    fun clear(scope: CoroutineScope) {
        val hadMessages = messages.isNotEmpty()
        messages = emptyList()
        restored = true // 刚清空就不该再拉回历史：清空语义优先于恢复
        if (!hadMessages) return
        scope.launch {
            try {
                clearRemote()
            } catch (e: Exception) {
                SilentFail.note("AssistantState.clear:${e.javaClass.simpleName}")
                notice = CLEAR_FAIL_NOTICE
            }
        }
    }

    fun consumeNotice() {
        notice = null
    }

    /** 界面在「正在回答」时按了发送键：给一句提示，而不是让回车毫无反应。 */
    fun notifyBusy() {
        notice = BUSY_NOTICE
    }

    companion object {
        const val BUSY_NOTICE = "我还在回答上一条，稍等一下"
        const val RATE_NOTICE = "问得太快啦，歇一下再问（每分钟最多 10 次）"
        const val FAIL_NOTICE = "AI 助手暂时联系不上，稍后再试"
        const val CLEAR_FAIL_NOTICE = "本地已清空，服务端删除失败，可再点一次"
        const val FALLBACK_REPLY = "我在，不过这次没想出该说什么，再问我一次？"

        /** 后端限流的异常信息形如 `Request HTTP 429 Too Many Requests`（见 VibeApi.rawPostInner）。 */
        fun isRateLimited(e: Exception): Boolean = (e.message ?: "").contains("429")
    }
}
