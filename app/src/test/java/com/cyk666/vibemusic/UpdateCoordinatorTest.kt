package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * `UpdateCoordinator` 的接线回归（CODE-REVIEW-2026-10-28 CODE-9）。
 *
 * 这些断言锁的是**事故现场**，不是实现细节：
 * - 拉取失败绝不能推进 24h 节流窗口（否则断网启动一次 = 一整天收不到更新，
 *   用户看到的就是「已是最新」）；
 * - 窗口内不能再发请求（启动每次都打 API 会被限流，也会把慢网拖成启动卡顿）；
 * - 测试通道必须走 beta 端点（2026-10-27 事故：测试通道永远拿正式版 latest）。
 *
 * 网络是注入的：`fetchLatest` 是生产构造函数的默认参数，这里换成 stub。
 * 协调器把工作丢进 `scope.launch`，所以断言前必须**等**它跑完 —— 用轮询而不是
 * 固定 sleep，避免在慢机器上假红。
 */
@RunWith(RobolectricTestRunner::class)
class UpdateCoordinatorTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private fun release(tag: String) = GithubRelease(
        tag = tag,
        name = tag,
        body = "",
        apkUrl = "https://gitee.com/a.apk"
    )

    private fun coordinator(
        scope: CoroutineScope,
        onError: (String) -> Unit = {},
        fetch: suspend (UpdateChannel) -> GithubRelease
    ) = UpdateCoordinator(context(), scope, fetchLatest = fetch, onError = onError)

    /** 清掉上一个用例留下的 DataStore 时间戳（同一个 Robolectric 进程会复用文件）。 */
    private fun resetThrottle(): Unit = runBlocking {
        QueueStore.saveLastUpdateCheck(context(), 0L)
    }

    private suspend fun awaitTrue(timeoutMs: Long = 3000, cond: suspend () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return true
            delay(10)
        }
        return cond()
    }

    /** 给"应该什么都不做"的用例一个执行窗口（不能等条件，因为条件永不为真）。 */
    private suspend fun settle(ms: Long = 300) = delay(ms)

    @Test
    fun checkOnLaunch_fetchFailureDoesNotBurnThrottleWindow(): Unit = runBlocking {
        resetThrottle()
        var attempted = false
        val c = coordinator(this, fetch = { attempted = true; throw RuntimeException("offline") })
        c.checkOnLaunch()
        assertTrue(awaitTrue { attempted })
        settle()
        // 关键：失败不写时间戳 —— 下一次启动必须还能再试。
        assertEquals(0L, QueueStore.loadLastUpdateCheck(context()))
        assertNull(c.release)
    }

    @Test
    fun checkOnLaunch_successSavesTimestamp(): Unit = runBlocking {
        resetThrottle()
        val before = System.currentTimeMillis()
        val c = coordinator(this, fetch = { release("v99.0.0-ai") })
        c.checkOnLaunch()
        assertTrue(awaitTrue { QueueStore.loadLastUpdateCheck(context()) >= before })
    }

    @Test
    fun checkOnLaunch_withinWindowDoesNotFetch(): Unit = runBlocking {
        // 刚检查过（now 就是窗口起点）→ 直接返回，一次网络都不许发。
        QueueStore.saveLastUpdateCheck(context(), System.currentTimeMillis())
        var calls = 0
        val c = coordinator(this, fetch = { calls += 1; release("v99.0.0-ai") })
        c.checkOnLaunch()
        settle()
        assertEquals(0, calls)
        assertNull(c.release)
    }

    @Test
    fun checkOnLaunch_newerReleaseSurfacesDialog(): Unit = runBlocking {
        resetThrottle()
        var fetched = false
        val c = coordinator(this, fetch = { fetched = true; release("v99.0.0-ai") })
        c.checkOnLaunch()
        assertTrue(awaitTrue { fetched })
        assertTrue(awaitTrue { c.release?.tag == "v99.0.0-ai" })
    }

    @Test
    fun checkOnLaunch_olderReleaseIsIgnored(): Unit = runBlocking {
        resetThrottle()
        // 当前装的是 debug 包（versionName 形如 1.0 / 未设置），v0.0.1 必然不更新；
        // 断言的是"不弹窗"，不是"不写时间戳"（成功拉取仍推进窗口）。
        var fetched = false
        val c = coordinator(this, fetch = { fetched = true; release("v0.0.1-ai") })
        c.checkOnLaunch()
        assertTrue(awaitTrue { fetched })
        settle()
        assertNull(c.release)
    }

    @Test
    fun checkOnLaunch_usesPersistedChannel(): Unit = runBlocking {
        resetThrottle()
        QueueStore.saveUpdateChannel(context(), UpdateChannel.BETA)
        var seen: UpdateChannel? = null
        val c = coordinator(this, fetch = { ch -> seen = ch; release("v99.0.0-ai") })
        c.checkOnLaunch()
        assertTrue(awaitTrue { seen != null })
        assertEquals(UpdateChannel.BETA, seen)
        assertEquals(UpdateChannel.BETA, c.channel)
    }

    @Test
    fun checkManual_failureReportsAndKeepsWindow(): Unit = runBlocking {
        resetThrottle()
        val errors = ArrayList<String>()
        val c = coordinator(this, onError = { errors += it }, fetch = { throw RuntimeException("offline") })
        c.checkManual("1.0")
        assertTrue(awaitTrue { errors.isNotEmpty() })
        assertEquals(0L, QueueStore.loadLastUpdateCheck(context()))
        assertFalse(c.manualChecking)
    }

    @Test
    fun checkManual_successSavesTimestampAndClearsSpinner(): Unit = runBlocking {
        resetThrottle()
        val c = coordinator(this, fetch = { release("v99.0.0-ai") })
        c.checkManual("1.0")
        assertTrue(awaitTrue { c.release?.tag == "v99.0.0-ai" })
        assertTrue(QueueStore.loadLastUpdateCheck(context()) > 0L)
        assertFalse(c.manualChecking)
    }

    @Test
    fun dismiss_clearsReleaseWhenNotBusy(): Unit = runBlocking {
        resetThrottle()
        val c = coordinator(this, fetch = { release("v99.0.0-ai") })
        c.checkOnLaunch()
        assertTrue(awaitTrue { c.release?.tag == "v99.0.0-ai" })
        c.dismiss()
        assertNull(c.release)
    }

    @Test
    fun selectChannel_persists(): Unit = runBlocking {
        val c = coordinator(this, fetch = { release("v99.0.0-ai") })
        c.selectChannel(UpdateChannel.BETA)
        assertEquals(UpdateChannel.BETA, c.channel)
        assertTrue(awaitTrue { QueueStore.loadUpdateChannel(context()) == UpdateChannel.BETA })
    }
}
