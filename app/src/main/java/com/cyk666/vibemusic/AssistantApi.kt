package com.cyk666.vibemusic

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

/**
 * AI 助手后端契约（`POST /api/assistant/chat`、`DELETE /api/assistant/history`）。
 *
 * 为什么用 `/chat` 而不是 `/stream`：后端流式分支的请求体里**没有** `tools`
 * （见 `AssistantStreamOrchestrator#buildStreamRequestBody`），也就是流式下模型
 * 调不了 `search_songs`，只能凭记忆"嘴推"；而 `/chat` 带 5 个工具，返回
 * `reply + songs[]`（真搜出来的歌）。本页要的是"能推真歌"，所以先用同步接口，
 * 逐字动效在 UI 层补（思考态 → 收到后按句揭示）。
 */
data class AssistantReply(
    val reply: String,
    val songs: List<Song>,
    val toolCalls: Int
)

/**
 * 匿名设备标识：后端用它区分游客的对话记忆与限流桶。
 *
 * 不传的话后端会落到公共兜底 `anon`（[com.vibemusic.common.utils.AnonymousIdentityUtils#FALLBACK]），
 * 所有游客共享同一份对话历史与限流额度——既会串味也会互相挤爆配额。
 * 只走 `?deviceId=` 查询参数：`VibeApi.rawPost` 不支持自定义请求头，后端参数优先，
 * 效果与 `X-Device-Id` 一致。
 */
object AssistantDevice {
    private const val PREFS = "assistant"
    private const val KEY = "deviceId"

    /** 首次调用生成并持久化，之后稳定复用（清 App 数据才会换）。 */
    fun ensure(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY, null)
        if (!saved.isNullOrBlank()) return saved
        val id = "a" + UUID.randomUUID().toString().replace("-", "").take(23)
        prefs.edit().putString(KEY, id).apply()
        return id
    }
}

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

/** `deviceId` 为空时退化成不带参数（后端按 `anon` 处理），便于测试与容错。 */
fun buildAssistantChatPath(deviceId: String): String =
    if (deviceId.isBlank()) "api/assistant/chat" else "api/assistant/chat?deviceId=" + enc(deviceId)

fun buildAssistantHistoryPath(deviceId: String): String =
    if (deviceId.isBlank()) "api/assistant/history" else "api/assistant/history?deviceId=" + enc(deviceId)

/**
 * 请求体。`message` 上限 2000 字（后端硬校验），`context` 是当前播放歌曲的上下文，
 * 没有就不传——后端用 `getOrDefault` 取默认值，显式 null 会绕过默认值。
 */
fun buildAssistantChatBody(message: String, context: String = ""): String {
    val body = JSONObject().put("message", message)
    if (context.isNotBlank()) body.put("context", context)
    return body.toString()
}

/**
 * 解析 `/chat` 信封。非 200 一律抛异常（与 [VibeApi] 其余接口一致：失败走异常，不用 Result），
 * 429 的异常信息里含 "429"，供上层判"问得太快"。
 */
fun parseAssistantReply(json: String): AssistantReply {
    val root = JSONObject(json)
    val code = root.optInt("code", -1)
    if (code != 200) {
        throw RuntimeException("AI 助手失败: code=$code ${root.optString("message", "unknown error")}")
    }
    val data = root.optJSONObject("data") ?: throw RuntimeException("AI 助手返回缺少 data")
    val arr = data.optJSONArray("songs")
    val songs = ArrayList<Song>(arr?.length() ?: 0)
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            songs.add(VibeApi.parsePlaylistSong(item))
        }
    }
    return AssistantReply(
        reply = data.optString("reply", ""),
        songs = songs,
        toolCalls = data.optInt("toolCalls", 0)
    )
}

suspend fun VibeApi.assistantChat(
    message: String,
    deviceId: String,
    context: String = ""
): AssistantReply = parseAssistantReply(
    rawPost(buildAssistantChatPath(deviceId), buildAssistantChatBody(message, context))
)

/** 清服务端对话记忆（App 侧"清空"必须同时清后端，否则下一轮又会被灌回上下文）。 */
suspend fun VibeApi.clearAssistantHistory(deviceId: String) {
    rawDelete(buildAssistantHistoryPath(deviceId))
}
