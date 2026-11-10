package com.cyk666.vibemusic

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * AI 助手接口契约（纯字符串）：路径带 deviceId、请求体省略空 context、信封解析。
 *
 * 不测 HTTP：`VibeApi` 的 base 是常量域名，业务请求无法改指向（见 AuthRefreshOutcomeTest
 * 注释），所以网络层用注入 lambda 在 [AssistantStateTest] 里覆盖。
 */
// org.json 在 JVM 单测里是 stub（方法直接抛 not mocked），必须走 Robolectric 提供的真实实现。
@RunWith(RobolectricTestRunner::class)
class AssistantApiTest {

    @Test
    fun chatPath_carriesDeviceId() {
        assertEquals("api/assistant/chat?deviceId=abc123", buildAssistantChatPath("abc123"))
        assertEquals("api/assistant/chat", buildAssistantChatPath(""))
    }

    @Test
    fun historyPath_carriesDeviceId() {
        assertEquals("api/assistant/history?deviceId=abc123", buildAssistantHistoryPath("abc123"))
        assertEquals("api/assistant/history", buildAssistantHistoryPath("  "))
    }

    @Test
    fun chatBody_omitsBlankContext() {
        val body = JSONObject(buildAssistantChatBody("你好"))
        assertEquals("你好", body.getString("message"))
        assertFalse(body.has("context"))
    }

    @Test
    fun chatBody_keepsContextAndEscapesQuotes() {
        val body = JSONObject(buildAssistantChatBody("这首\"晴天\"怎么样", "正在播放：晴天"))
        assertEquals("这首\"晴天\"怎么样", body.getString("message"))
        assertEquals("正在播放：晴天", body.getString("context"))
    }

    @Test
    fun parse_success_withSongs() {
        val json = """
            {"code":200,"message":"success","data":{"reply":"给你找了这首","model":"deepseek-v4-flash",
            "songs":[{"sourceId":"66842","name":"十年","artist":"陈奕迅","album":"黑白灰","coverUrl":"https://c/1.jpg","duration":205,"platform":"netease"}],
            "toolCalls":1}}
        """.trimIndent()

        val reply = parseAssistantReply(json)

        assertEquals("给你找了这首", reply.reply)
        assertEquals(1, reply.toolCalls)
        assertEquals(1, reply.songs.size)
        assertEquals("十年", reply.songs[0].name)
        assertEquals("陈奕迅", reply.songs[0].artist)
        assertEquals("netease", reply.songs[0].platform)
    }

    @Test
    fun parse_success_withoutSongs() {
        val json = """{"code":200,"data":{"reply":"我在","songs":[],"toolCalls":0}}"""

        val reply = parseAssistantReply(json)

        assertEquals("我在", reply.reply)
        assertTrue(reply.songs.isEmpty())
    }

    @Test
    fun parse_nonOkEnvelope_throws() {
        val json = """{"code":429,"message":"请求太频繁，请稍后再试","data":null}"""

        val e = runCatching { parseAssistantReply(json) }.exceptionOrNull()

        assertTrue(e is RuntimeException)
        assertTrue(e!!.message!!.contains("429"))
    }

    @Test
    fun parse_missingData_throws() {
        val json = """{"code":200,"message":"success"}"""

        val e = runCatching { parseAssistantReply(json) }.exceptionOrNull()

        assertTrue(e is RuntimeException)
        assertTrue(e!!.message!!.contains("data"))
    }

    @Test
    fun parseActions_readsToolResultFields() {
        val arr = JSONArray(
            """[{"tool":"create_playlist","ok":true,"playlistName":"跑步","playlistId":"pl-1","added":0},
                {"tool":"add_to_playlist","ok":false,"error":"未登录"}]"""
        )

        val actions = parseActions(arr)

        assertEquals(2, actions.size)
        assertEquals("create_playlist", actions[0].tool)
        assertTrue(actions[0].ok)
        assertEquals("跑步", actions[0].playlistName)
        assertTrue(actions[0].touchesPlaylists())
        assertFalse(actions[1].ok)
        assertEquals("未登录", actions[1].error)
        assertTrue(actions[1].touchesPlaylists())
    }

    @Test
    fun parseActions_missingFieldsDegradeToDefaults() {
        // 后端加字段/删字段都不该把整轮对话判失败：缺什么就取默认值。
        val actions = parseActions(JSONArray("""[{"tool":"search_songs"}]"""))

        assertEquals(1, actions.size)
        assertEquals("", actions[0].playlistName)
        assertEquals(0, actions[0].added)
        assertFalse(actions[0].ok)
        assertFalse(actions[0].touchesPlaylists())
    }

    @Test
    fun parseActions_emptyOrNull_isEmpty() {
        assertTrue(parseActions(null).isEmpty())
        assertTrue(parseActions(JSONArray("[]")).isEmpty())
    }

    @Test
    fun parse_replyCarriesActions() {
        val json = """{"code":200,"data":{"reply":"建好了","songs":[],"toolCalls":1,
            "actions":[{"tool":"create_playlist","ok":true,"playlistName":"跑步"}]}}"""

        val reply = parseAssistantReply(json)

        assertEquals(1, reply.actions.size)
        assertEquals("跑步", reply.actions[0].playlistName)
    }
}
