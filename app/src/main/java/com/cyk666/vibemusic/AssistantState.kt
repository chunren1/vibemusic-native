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
    val failed: Boolean = false
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
    private val clearRemote: suspend () -> Unit = { VibeApi.clearAssistantHistory(deviceId) }
) {
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    var sending by mutableStateOf(false)
        private set

    /** 给 Snackbar 的一次性提示；消费后必须归 null（见 [consumeNotice]）。 */
    var notice by mutableStateOf<String?>(null)
        private set

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
                    songs = reply.songs
                )
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
     * 清空对话。界面先清、后端后清：后端失败不该让"点了清空还留着记录"，
     * 但失败要能重试——所以失败时给出提示，用户再点一次即可。
     */
    fun clear(scope: CoroutineScope) {
        val hadMessages = messages.isNotEmpty()
        messages = emptyList()
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
