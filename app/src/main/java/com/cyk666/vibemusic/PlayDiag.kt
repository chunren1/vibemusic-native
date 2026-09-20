package com.cyk666.vibemusic

import android.content.Context
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
 */
object PlayDiag {

    private const val PREFS = "play_diag"
    private const val KEY_CODE = "last_code"
    private const val KEY_TS = "last_ts"

    /** 覆盖写入最近一条诊断记录（任何异常都不抛）。 */
    fun mark(context: Context, code: String) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_CODE, code)
                .putLong(KEY_TS, System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
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
}

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
    code == "focus-retry" -> "焦点被拒后已自动补试一次"
    code == "playing" -> "已开始出声"
    code.startsWith("error") -> "播放出错"
    else -> code
}

/** Pure: 毫秒 → HH:mm:ss（本地时区）。 */
fun formatDiagTime(tsMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(tsMs))
