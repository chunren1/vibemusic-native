package com.cyk666.vibemusic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * AI 助手对话状态：成功、空白输入、连点重入、429 文案、清空的本地/远端语义。
 *
 * 断言口径：`Dispatchers.Unconfined` 让协程立即执行，
 * 需要"卡住第一次请求"时用 [CompletableDeferred]。
 */
@RunWith(RobolectricTestRunner::class)
class AssistantStateTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun song(id: String = "s1") = Song(
        sourceId = id,
        name = "晴天",
        artist = "周杰伦",
        album = "叶惠美",
        coverUrl = "https://c/1.jpg",
        durationSec = 269,
        platform = "netease"
    )

    @Test
    fun send_success_appendsUserThenAssistantWithSongs() {
        val reply = AssistantReply("给你找了这首", listOf(song()), toolCalls = 1)
        val st = AssistantState(send = { reply })

        st.send(scope, "  推荐一首  ")

        assertEquals(2, st.messages.size)
        assertEquals(ChatRole.USER, st.messages[0].role)
        assertEquals("推荐一首", st.messages[0].text)
        assertEquals(ChatRole.ASSISTANT, st.messages[1].role)
        assertEquals("给你找了这首", st.messages[1].text)
        assertEquals(1, st.messages[1].songs.size)
        assertFalse(st.sending)
        assertNull(st.notice)
    }

    @Test
    fun send_blank_isIgnored() {
        var calls = 0
        val st = AssistantState(send = { calls++; AssistantReply("x", emptyList(), 0) })

        st.send(scope, "   ")

        assertEquals(0, calls)
        assertTrue(st.messages.isEmpty())
        assertFalse(st.sending)
    }

    @Test
    fun send_whileSending_isDropped() {
        val gate = CompletableDeferred<AssistantReply>()
        var calls = 0
        val st = AssistantState(send = { calls++; gate.await() })

        st.send(scope, "第一条")
        st.send(scope, "连点重复")

        assertEquals(1, calls)
        assertEquals(1, st.messages.size)
        assertTrue(st.sending)

        gate.complete(AssistantReply("回你", emptyList(), 0))
        assertEquals(2, st.messages.size)
        assertFalse(st.sending)
    }

    @Test
    fun send_rateLimited_showsRateNoticeAndKeepsFailedMessage() {
        val st = AssistantState(send = { throw RuntimeException("Request HTTP 429 Too Many Requests") })

        st.send(scope, "你好")

        assertEquals(2, st.messages.size)
        assertTrue(st.messages[1].failed)
        assertEquals(AssistantState.RATE_NOTICE, st.messages[1].text)
        assertEquals(AssistantState.RATE_NOTICE, st.notice)
    }

    @Test
    fun send_otherFailure_showsFailNotice() {
        val st = AssistantState(send = { throw RuntimeException("Request failed: timeout") })

        st.send(scope, "你好")

        assertTrue(st.messages[1].failed)
        assertEquals(AssistantState.FAIL_NOTICE, st.messages[1].text)
        assertEquals(AssistantState.FAIL_NOTICE, st.notice)
    }

    @Test
    fun send_emptyReply_fallsBackToCannedLine() {
        val st = AssistantState(send = { AssistantReply("   ", emptyList(), 0) })

        st.send(scope, "你好")

        assertEquals(AssistantState.FALLBACK_REPLY, st.messages[1].text)
        assertFalse(st.messages[1].failed)
    }


    // ---- 2026-11-10 审查补测：写操作回执与忙时反馈 ----

    private fun action(tool: String, ok: Boolean) = AssistantAction(
        tool = tool, ok = ok, playlistName = "跑步", playlistId = "pl-1",
        added = 3, failedCount = 0, error = if (ok) "" else "未登录"
    )

    @Test
    fun send_playlistWrite_notifiesHostOnce() {
        var notified = 0
        val st = AssistantState(
            send = { AssistantReply("建好了", emptyList(), 1, listOf(action("create_playlist", true))) },
            onWriteAction = { notified++ }
        )

        st.send(scope, "建个歌单")

        assertEquals(1, notified)
        assertEquals(1, st.messages[1].actions.size)
    }

    @Test
    fun send_failedAction_doesNotNotifyHost() {
        var notified = 0
        val st = AssistantState(
            send = { AssistantReply("建好了", emptyList(), 1, listOf(action("create_playlist", false))) },
            onWriteAction = { notified++ }
        )

        st.send(scope, "建个歌单")

        // 模型说"建好了"也不算：只有工具回执 ok=true 才刷新歌单缓存
        assertEquals(0, notified)
    }

    @Test
    fun send_readOnlyTool_doesNotNotifyHost() {
        var notified = 0
        val st = AssistantState(
            send = { AssistantReply("找到了", listOf(song()), 1, listOf(action("search_songs", true))) },
            onWriteAction = { notified++ }
        )

        st.send(scope, "找首歌")

        assertEquals(0, notified)
    }

    @Test
    fun send_whileBusy_setsBusyNoticeAndKeepsSingleRequest() {
        val gate = CompletableDeferred<AssistantReply>()
        var calls = 0
        val st = AssistantState(send = { calls++; gate.await() })

        st.send(scope, "第一条")
        st.send(scope, "第二条")

        assertEquals(1, calls)
        assertEquals(AssistantState.BUSY_NOTICE, st.notice)
        gate.complete(AssistantReply("回你", emptyList(), 0))
    }

    @Test
    fun consumeNotice_clearsOnce() {
        val st = AssistantState(send = { throw RuntimeException("boom") })
        st.send(scope, "你好")

        st.consumeNotice()

        assertNull(st.notice)
    }

    @Test
    fun clear_emptiesLocallyAndCallsRemote() {
        var cleared = 0
        val st = AssistantState(
            send = { AssistantReply("x", emptyList(), 0) },
            clearRemote = { cleared++ }
        )
        st.send(scope, "你好")

        st.clear(scope)

        assertTrue(st.messages.isEmpty())
        assertEquals(1, cleared)
        assertNull(st.notice)
    }

    @Test
    fun clear_whenAlreadyEmpty_skipsRemote() {
        var cleared = 0
        val st = AssistantState(clearRemote = { cleared++ })

        st.clear(scope)

        assertEquals(0, cleared)
        assertTrue(st.messages.isEmpty())
    }

    @Test
    fun clear_remoteFailure_keepsLocalClearAndWarnsUser() {
        val st = AssistantState(
            send = { AssistantReply("x", emptyList(), 0) },
            clearRemote = { throw RuntimeException("Request failed: offline") }
        )
        st.send(scope, "你好")

        st.clear(scope)

        assertTrue(st.messages.isEmpty())
        assertEquals(AssistantState.CLEAR_FAIL_NOTICE, st.notice)
    }

    // ---------- 冷启动恢复（GET /api/assistant/history） ----------

    @Test
    fun restore_fillsMessagesFromServerHistory() {
        val st = AssistantState(
            deviceId = "d1",
            send = { AssistantReply("x", emptyList(), 0) },
            restoreRemote = {
                listOf(
                    AssistantHistoryMessage(ChatRole.USER, "推荐一首周杰伦"),
                    AssistantHistoryMessage(ChatRole.ASSISTANT, "给你推荐《晴天》")
                )
            }
        )
        scope.launch { st.restore() }

        assertEquals(2, st.messages.size)
        assertEquals(ChatRole.USER, st.messages[0].role)
        assertEquals("推荐一首周杰伦", st.messages[0].text)
        assertEquals(ChatRole.ASSISTANT, st.messages[1].role)
        // 历史不落 songs/actions：恢复出来的是纯文本气泡
        assertTrue(st.messages[1].songs.isEmpty())
    }

    @Test
    fun restore_isIdempotentAndDoesNotOverwriteNewMessages() {
        val st = AssistantState(
            deviceId = "d1",
            send = { AssistantReply("x", emptyList(), 0) },
            restoreRemote = { listOf(AssistantHistoryMessage(ChatRole.USER, "旧对话")) }
        )
        scope.launch { st.restore() }
        // 恢复与首条 send 竞争时（restore 慢于 send），已有对话不能被旧历史覆盖
        st.send(scope, "新的问题")
        scope.launch { st.restore() }

        assertEquals(3, st.messages.size)
        assertEquals("旧对话", st.messages[0].text)
        assertEquals("新的问题", st.messages[1].text)
    }

    @Test
    fun clear_setsRestoredSoHistoryIsNotPulledBack() {
        var pulled = 0
        val st = AssistantState(
            deviceId = "d1",
            send = { AssistantReply("x", emptyList(), 0) },
            restoreRemote = { pulled++; emptyList() }
        )
        scope.launch { st.restore() }
        st.clear(scope)
        scope.launch { st.restore() } // 清空后再进页面：清空语义优先，不再拉历史

        assertEquals(1, pulled)
        assertTrue(st.messages.isEmpty())
    }

    @Test
    fun restore_remoteFailure_isSilent() {
        val st = AssistantState(
            deviceId = "d1",
            send = { AssistantReply("x", emptyList(), 0) },
            restoreRemote = { throw RuntimeException("offline") }
        )
        scope.launch { st.restore() } // 不抛：冷启动网络失败不该在助手页弹错

        assertTrue(st.messages.isEmpty())
        assertNull(st.notice)
    }
}
