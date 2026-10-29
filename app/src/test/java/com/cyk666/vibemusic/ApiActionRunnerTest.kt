package com.cyk666.vibemusic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C5 第 2 步：`ApiActionRunner` 替掉了 17 处手抄的三段 catch 梯子。
 * 这里守的是**与旧代码逐条对齐**的三类结局，任何一条走偏都是行为变更：
 * - NetworkAuthException → 保留会话（clearTokens = false）
 * - AuthException → 清会话
 * - 其他 Exception → `diagnosableError(label, e)`，除非调用方给了 onFailure
 * 外加 finally 一定执行（busy/loading 标志不能漏复位）。
 */
class ApiActionRunnerTest {

    private class Recorder {
        var authLostMsg: String? = null
        var authLostClear: Boolean? = null
        var error: String? = null
        var finallyCount = 0
        val order = mutableListOf<String>()

        fun runner() = ApiActionRunner(
            scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
            onAuthLost = { msg, clear ->
                authLostMsg = msg
                authLostClear = clear
                order += "authLost"
            },
            onError = {
                error = it
                order += "error"
            }
        )
    }

    @Test
    fun successRunsOnlyTheBody() {
        val rec = Recorder()
        var ran = false
        rec.runner().launch("加载失败", onFinally = { rec.finallyCount++ }) {
            ran = true
        }
        assertTrue(ran)
        assertNull(rec.authLostMsg)
        assertNull(rec.error)
        assertEquals(1, rec.finallyCount)
    }

    @Test
    fun networkAuthKeepsSession() {
        val rec = Recorder()
        rec.runner().launch("加载失败") { throw NetworkAuthException("refresh timeout") }
        assertEquals("refresh timeout", rec.authLostMsg)
        assertEquals(false, rec.authLostClear)
        assertNull(rec.error)
    }

    @Test
    fun authExceptionClearsSession() {
        val rec = Recorder()
        rec.runner().launch("加载失败") { throw AuthException("token dead") }
        assertEquals("token dead", rec.authLostMsg)
        assertEquals(true, rec.authLostClear)
        assertNull(rec.error)
    }

    @Test
    fun genericFailureUsesDiagnosableLabel() {
        val rec = Recorder()
        rec.runner().launch("新建歌单失败") { throw IllegalStateException("boom") }
        assertNull(rec.authLostMsg)
        assertTrue("实际: ${rec.error}", rec.error!!.startsWith("新建歌单失败："))
        assertTrue("原始信息应保留：${rec.error}", rec.error!!.contains("boom"))
    }

    @Test
    fun onFailureOverridesDefaultToast() {
        val rec = Recorder()
        var captured: Exception? = null
        rec.runner().launch("加载失败", onFailure = { captured = it }) {
            throw IllegalStateException("silent")
        }
        assertEquals("silent", captured?.message)
        assertNull("onFailure 给了就不该再走默认提示", rec.error)
    }

    @Test
    fun finallyRunsAfterAuthLost() {
        val rec = Recorder()
        rec.runner().launch("加载失败", onFinally = { rec.finallyCount++; rec.order += "finally" }) {
            throw AuthException("dead")
        }
        assertEquals(1, rec.finallyCount)
        assertEquals(listOf("authLost", "finally"), rec.order)
    }

    @Test
    fun finallyRunsAfterGenericFailure() {
        val rec = Recorder()
        rec.runner().launch("加载失败", onFinally = { rec.finallyCount++ }) {
            throw IllegalStateException("x")
        }
        assertEquals(1, rec.finallyCount)
        assertTrue(rec.error != null)
    }

    @Test
    fun bodyRunsOnTheGivenScope() = runBlocking {
        var done = false
        val runner = ApiActionRunner(
            scope = this,
            onAuthLost = { _, _ -> },
            onError = { }
        )
        runner.launch("加载失败") { done = true }
        // runBlocking 的事件循环要先让出去，排队的 launch 才会跑（这正是「真调度器」与
        // Unconfined 的差别，也是本用例存在的意义：证明动作确实挂在传入的 scope 上）。
        yield()
        assertTrue("动作应挂在传入的 scope 上执行", done)
    }
}
