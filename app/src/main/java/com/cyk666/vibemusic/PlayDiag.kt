package com.cyk666.vibemusic

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 播放诊断（同步 SharedPreferences，**跨进程死亡保留**）。
 *
 * 用途：定位"被清后台后通知播放键没反应"这类只能真机复现的问题——在每个关键节点
 * 覆盖写入一条「事件码 + 时间戳」，设置页只读展示最近一条。复现一次后就能判断卡在哪环：
 *
 * - 完全没有新记录 → 按键事件根本没到 App（系统/厂商层，通知是僵尸卡片）
 * - cold → 进程曾被系统清理（服务冷启动）
 * - resume-ok / resume-fail → 官方恢复钩子的交回结果（队列快照）
 * - focus-blocked → 播放被音频焦点拒绝（别的应用占着声音，属"点了没反应"最常见成因）
 * - playing → 真的开始出声（成功终态）
 *
 * 2026-10-19 扩充（**纯观察，零行为变更**）：除"最近一条主槽"外，另加一个有界环形缓冲
 * （[RING_MAX] 条），把原先看不见的三件事记下来——谁连上来的（控制器包名）、服务是否被
 * 拉起过、启动前台服务是否被系统静默拒绝。主槽语义（last_code/last_ts）原样保留，
 * 设置页与保活白名单提示照旧读它。
 */
object PlayDiag {

    private const val PREFS = "play_diag"
    private const val KEY_CODE = "last_code"
    private const val KEY_TS = "last_ts"
    /** 环形缓冲的单调序号计数（只增不回卷）。 */
    private const val KEY_SEQ = "ring_seq"
    /** 环形缓冲本体（一整个字符串，见 [appendDiagEntry] 的编码规则）。 */
    private const val KEY_RING = "ring_entries"

    /** 环形缓冲容量上限（只保留最近 N 条；有界，不会无限增长）。 */
    const val RING_MAX = 12

    /** 覆盖写入最近一条诊断记录（任何异常都不抛）。语义与历史版本完全一致。 */
    fun mark(context: Context, code: String) = record(context, code, primary = true)

    /**
     * 纯观察型记录：**只进环形缓冲，不动 last_code/last_ts**。
     *
     * 用于"服务被拉起/控制器连上来"这类信息型事件：它们在冷启动时**紧跟** [mark]("cold")
     * 发生，若覆盖主槽会立刻把"进程曾被杀"的证据顶掉（保活白名单提示 `shouldShowKilledHint`
     * 与设置页首行都在读主槽）——那本身就是一次行为变更。观察点只上环形缓冲，设置页
     * "最近 5 条"里照常看得到，且带序号+时间可排先后。
     */
    fun observe(context: Context, code: String) = record(context, code, primary = false)

    private fun record(context: Context, code: String, primary: Boolean) {
        try {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val seq = sp.getLong(KEY_SEQ, 0L) + 1L
            val now = System.currentTimeMillis()
            val ring = appendDiagEntry(
                sp.getString(KEY_RING, "").orEmpty(),
                DiagEntry(now, seq, code),
                RING_MAX
            )
            sp.edit {
                putLong(KEY_SEQ, seq)
                putString(KEY_RING, ring)
                if (primary) {
                    putString(KEY_CODE, code)
                    putLong(KEY_TS, now)
                }
            }
        } catch (_: Exception) {
        }
        // Bugly 已初始化时同步一份"面包屑"，崩溃报告里直接可见死前状态；
        // 未初始化（单测/未填 AppID）时零开销、零行为变化。
        if (BuglyReporter.ready) {
            try {
                com.tencent.bugly.crashreport.CrashReport.putUserData(context, "playDiag", code)
            } catch (_: Exception) {
            }
        }
    }

    fun lastCode(context: Context): String = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CODE, "").orEmpty()
    } catch (_: Exception) {
        ""
    }

    fun lastTs(context: Context): Long = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_TS, 0L)
    } catch (_: Exception) {
        0L
    }

    /** 环形缓冲原始串（未解析；异常/无记录返回空串）。 */
    fun ringRaw(context: Context): String = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RING, "").orEmpty()
    } catch (_: Exception) {
        ""
    }

    /** 最近 [n] 条记录，**新 → 旧**排序；无记录或 [n] <= 0 返回空表。 */
    fun recent(context: Context, n: Int): List<DiagEntry> = recentDiagEntries(ringRaw(context), n)
}

// ── 环形缓冲的纯逻辑（无 Android 依赖，可单测）──

/** 缓冲里的一条记录：时间戳 + 单调序号（排先后用）+ 事件码。 */
data class DiagEntry(val tsMs: Long, val seq: Long, val code: String)

/** 单条记录内的字段分隔符（事件码自身不含 '|'）。 */
private const val FIELD_SEP = "|"

/** 记录之间的分隔符（事件码不含换行，故按行分条安全）。 */
private const val ENTRY_SEP = "\n"

/** Pure: 单条 → 存储串（`时间戳|序号|事件码`）。 */
fun encodeDiagEntry(entry: DiagEntry): String =
    "${entry.tsMs}$FIELD_SEP${entry.seq}$FIELD_SEP${entry.code}"

/** Pure: 存储串 → 单条；格式不对（字段数/数字/空码）返回 null 由调用方丢弃，绝不抛。 */
fun decodeDiagEntry(raw: String): DiagEntry? {
    val parts = raw.split(FIELD_SEP, limit = 3)
    if (parts.size != 3) return null
    val ts = parts[0].toLongOrNull() ?: return null
    val seq = parts[1].toLongOrNull() ?: return null
    if (parts[2].isBlank()) return null
    return DiagEntry(ts, seq, parts[2])
}

/** Pure: 解析整个缓冲串，**旧 → 新**（存储顺序）；坏行直接丢弃。 */
fun parseDiagRing(ring: String): List<DiagEntry> =
    if (ring.isEmpty()) emptyList() else ring.split(ENTRY_SEP).mapNotNull { decodeDiagEntry(it) }

/**
 * Pure: 追加一条并**截断到最多 [max] 条**（保留最新的 [max] 条）。
 * [max] <= 0 → 返回空串（视为不记录）。返回值为新的整个缓冲串。
 */
fun appendDiagEntry(ring: String, entry: DiagEntry, max: Int = PlayDiag.RING_MAX): String {
    if (max <= 0) return ""
    val kept = parseDiagRing(ring).takeLast(max - 1)
    return (kept + entry).joinToString(ENTRY_SEP) { encodeDiagEntry(it) }
}

/** Pure: 最近 [n] 条，**新 → 旧**排序；[n] <= 0 返回空表，[n] 超出总量则全给。 */
fun recentDiagEntries(ring: String, n: Int): List<DiagEntry> =
    if (n <= 0) emptyList() else parseDiagRing(ring).takeLast(n).reversed()

/** Pure: 单条 → 展示行（`#序号 HH:mm:ss 事件码 · 人话`）。 */
fun formatDiagEntryLine(entry: DiagEntry): String {
    val human = mapPlayDiagCode(entry.code)
    val suffix = if (human == entry.code) "" else " · $human"
    return "#${entry.seq} ${formatDiagTime(entry.tsMs)} ${entry.code}$suffix"
}

/** Pure: 最近 [n] 条的多行展示文案（**新 → 旧**，一行一条）；空 → ""。 */
fun recentDiagLabel(ring: String, n: Int): String =
    recentDiagEntries(ring, n).joinToString("\n") { formatDiagEntryLine(it) }

/** Pure: 设置页诊断行文案（无记录 → 占位）。 */
fun playDiagLabel(code: String, tsMs: Long): String =
    if (code.isBlank() || tsMs <= 0L) "暂无记录（复现后回来看这行）"
    else "${mapPlayDiagCode(code)} · ${formatDiagTime(tsMs)}"

/** Pure: 事件码 → 人话（便于用户复述给我们）。 */
fun mapPlayDiagCode(code: String): String = when {
    code == "cold" -> "服务冷启动（进程曾被杀）"
    code.startsWith("resume-ok") -> "已交回队列快照，等待播放"
    code.startsWith("resume-fail") -> "队列快照交回失败"
    code == "focus-blocked" -> "播放被音频焦点拒绝（别的应用占着声音）"
    code.startsWith("focus-retry") -> "焦点被拒后已自动补试"
    code == "playing" -> "已开始出声"
    // ── 2026-10-19 观察点（纯记录，不影响任何播放/连接行为）──
    code.startsWith("svc-start") -> "服务被拉起（action=${code.substringAfter(":", "无")}）"
    code == "svc-destroy" -> "服务已销毁"
    code == "task-removed" -> "最近任务被划掉（服务未停，2.0.8 有意为之）"
    code.startsWith("ctrl:") -> "控制器连上来：${code.substringAfter(":")}"
    code.startsWith("libroot:") -> "库根被查询：${code.substringAfter(":")}"
    code.startsWith("libchildren:") -> "库子级被查询：${code.substringAfter(":")}"
    code == "fgs-denied" -> "前台服务启动被系统拒绝（静默失败）"
    code.startsWith("error") -> "播放出错"
    else -> code
}

/** Pure: 毫秒 → HH:mm:ss（本地时区）。 */
fun formatDiagTime(tsMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(tsMs))
