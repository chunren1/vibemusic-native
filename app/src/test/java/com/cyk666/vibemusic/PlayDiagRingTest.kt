package com.cyk666.vibemusic

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 诊断环形缓冲（2026-10-19 观察层）纯逻辑 + 存储语义回归。
 *
 * 关键约束（改动前必读）：
 * - [PlayDiag.mark] 的主槽语义（last_code/last_ts）**不许变**——设置页首行与保活白名单
 *   提示（shouldShowKilledHint 看 startsWith("cold")）都在读它。
 * - [PlayDiag.observe] 是旁路观察：只进环形缓冲，**不动主槽**。冷启动时 svc-start 紧跟
 *   cold 发生，若覆盖主槽，"进程曾被杀"的证据会被立刻顶掉。
 * - 缓冲必须有界（[PlayDiag.RING_MAX]），不许无限增长。
 */
@RunWith(RobolectricTestRunner::class)
class PlayDiagRingTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private fun entry(seq: Long, code: String = "e$seq") =
        DiagEntry(1_700_000_000_000L + seq, seq, code)

    private fun ringOf(vararg codes: String): String {
        var ring = ""
        codes.forEachIndexed { i, code ->
            ring = appendDiagEntry(ring, entry(i + 1L, code))
        }
        return ring
    }

    // ---- 纯逻辑：追加 / 截断 / 取 n ----

    @Test
    fun append_keepsInsertionOrderUntilFull() {
        val ring = ringOf("a", "b", "c")
        assertEquals(listOf(1L, 2L, 3L), parseDiagRing(ring).map { it.seq })
        assertEquals(listOf("a", "b", "c"), parseDiagRing(ring).map { it.code })
    }

    @Test
    fun append_truncatesHeadPastCapacity() {
        val codes = (1..15).map { "e$it" }.toTypedArray()
        val ring = ringOf(*codes)
        val seqs = parseDiagRing(ring).map { it.seq }
        assertEquals(PlayDiag.RING_MAX, seqs.size)
        // 只留最新 12 条：1..3 被截掉
        assertEquals((4L..15L).toList(), seqs)
    }

    @Test
    fun append_capacityBoundaries() {
        var ring = ""
        (1L..3L).forEach { ring = appendDiagEntry(ring, entry(it), max = 1) }
        assertEquals(listOf(3L), parseDiagRing(ring).map { it.seq })

        // 非正容量 = 不记录（返回空串，不抛）
        assertEquals("", appendDiagEntry("1|1|junk", entry(1), max = 0))
        assertEquals("", appendDiagEntry("1|1|junk", entry(1), max = -5))
    }

    @Test
    fun recent_isNewestFirstWithBoundaries() {
        val ring = ringOf("a", "b", "c", "d", "e")
        assertEquals(listOf(5L, 4L, 3L), recentDiagEntries(ring, 3).map { it.seq })
        assertEquals(listOf(5L), recentDiagEntries(ring, 1).map { it.seq })
        // n 超总量 → 全给（仍是新 → 旧）
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), recentDiagEntries(ring, 99).map { it.seq })
        assertEquals(emptyList<Long>(), recentDiagEntries(ring, 0).map { it.seq })
        assertEquals(emptyList<Long>(), recentDiagEntries(ring, -1).map { it.seq })
        assertEquals(emptyList<Long>(), recentDiagEntries("", 5).map { it.seq })
    }

    @Test
    fun parse_dropsMalformedLinesWithoutThrowing() {
        val ring = "1|x|a\n1|2|good\n\nbroken\nabc|9|alsoBad"
        assertEquals(listOf("good"), parseDiagRing(ring).map { it.code })
        assertNull(decodeDiagEntry("broken"))
        assertNull(decodeDiagEntry("1|2|"))
        assertNull(decodeDiagEntry("abc|2|code"))
        assertNull(decodeDiagEntry("1|2"))
    }

    @Test
    fun code_roundTripsWithColonsAndSuffixes() {
        val ctrl = DiagEntry(123L, 7L, "ctrl:com.android.systemui")
        assertEquals(ctrl, decodeDiagEntry(encodeDiagEntry(ctrl)))
        val svc = DiagEntry(124L, 8L, "svc-start:androidx.media3.session.MediaButtonReceiver")
        assertEquals(svc, decodeDiagEntry(encodeDiagEntry(svc)))
    }

    // ---- 存储语义：mark 主槽不变 / observe 旁路 ----

    @Test
    fun mark_keepsLegacySlotAndAppendsToRing() {
        val ctx = context()
        PlayDiag.mark(ctx, "cold")
        assertEquals("cold", PlayDiag.lastCode(ctx))
        assertTrue(PlayDiag.lastTs(ctx) > 0L)
        assertEquals(listOf("cold"), PlayDiag.recent(ctx, 5).map { it.code })
    }

    @Test
    fun observe_neverTouchesLegacySlot() {
        val ctx = context()
        PlayDiag.mark(ctx, "cold")
        val ts = PlayDiag.lastTs(ctx)
        PlayDiag.observe(ctx, "svc-start:null")
        PlayDiag.observe(ctx, "ctrl:com.android.systemui")
        // 主槽仍是 cold（保活提示 / 设置页首行行为零变化）
        assertEquals("cold", PlayDiag.lastCode(ctx))
        assertEquals(ts, PlayDiag.lastTs(ctx))
        // 但环形缓冲里按时间先后都记下了（新 → 旧）
        assertEquals(
            listOf("ctrl:com.android.systemui", "svc-start:null", "cold"),
            PlayDiag.recent(ctx, 5).map { it.code }
        )
    }

    @Test
    fun ring_staysBoundedAcrossManyMarks() {
        val ctx = context()
        (1..30).forEach { PlayDiag.observe(ctx, "e$it") }
        val recent = PlayDiag.recent(ctx, 99)
        assertEquals(PlayDiag.RING_MAX, recent.size)
        assertEquals("e30", recent.first().code)
        // 新 → 旧：seq 19..30 反向
        assertEquals((30L downTo 19L).toList(), recent.map { it.seq })
    }

    // ---- 展示 ----

    @Test
    fun label_isMultilineNewestFirst() {
        val ctx = context()
        PlayDiag.observe(ctx, "svc-start:null")
        PlayDiag.mark(ctx, "cold")
        val lines = recentDiagLabel(PlayDiag.ringRaw(ctx), 5).split("\n")
        assertEquals(2, lines.size)
        assertTrue(lines[0], lines[0].contains("cold"))
        assertTrue(lines[0].startsWith("#"))
        assertTrue(lines[1], lines[1].contains("svc-start"))
        assertTrue(lines[1], lines[1].contains("服务被拉起"))
        // 无记录 → 空串（设置页据此不显示该行）
        assertEquals("", recentDiagLabel("", 5))
    }

    @Test
    fun newCodes_haveReadableLabels() {
        assertTrue(mapPlayDiagCode("svc-start:null").contains("服务被拉起"))
        assertEquals("服务已销毁", mapPlayDiagCode("svc-destroy"))
        assertTrue(mapPlayDiagCode("task-removed").contains("最近任务"))
        assertTrue(mapPlayDiagCode("ctrl:com.android.systemui").contains("com.android.systemui"))
        assertTrue(mapPlayDiagCode("libroot:com.x").contains("com.x"))
        assertTrue(mapPlayDiagCode("libchildren:com.x").contains("com.x"))
        assertTrue(mapPlayDiagCode("fgs-denied").contains("前台服务启动被系统拒绝"))
        // 老码映射不许被新增分支顶掉
        assertEquals("服务冷启动（进程曾被杀）", mapPlayDiagCode("cold"))
        assertEquals("播放出错", mapPlayDiagCode("error:ERROR_CODE_IO"))
    }
}
