package com.cyk666.vibemusic

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 「后台保活 / 通知设置 / 分享」这一组系统交互（重构片 C5-lite）。
 *
 * 为什么单独成文件：这三件事在 `MainActivity.onCreate` 里是纯系统调用 + 纯 Intent 拼装，
 * **不碰任何播放状态**，却因为住在 3.5k 行的 composable 里而完全无法单测。
 * 搬出来之后 `batteryOptIntents` / `notificationSettingsIntent` 的入口顺序、
 * 以及 `readKeepAliveState` 的"读失败按什么默认值"都能被钉住。
 *
 * 行为与原实现逐字一致（含所有 catch 的兜底值），属**纯搬迁**：
 * - 通知设置：API 26+ 走 `ACTION_APP_NOTIFICATION_SETTINGS` + `EXTRA_APP_PACKAGE`，
 *   否则退到应用详情页；两者都拼不出来时再退一次应用详情页。
 * - 电池优化：按 [batteryOptActions] 的顺序逐个 `startActivity`，第一个能打开的就停。
 * - 保活状态：读不到电池白名单按 `false`（"没开"更安全，会提示用户去开），
 *   读不到通知开关按 `true`（不让"读失败"表现成"通知被关了"）。
 */
data class KeepAliveState(
    /** 已在系统电池优化白名单里（`isIgnoringBatteryOptimizations`）。 */
    val batteryExempt: Boolean,
    /** 系统通知总开关（`areNotificationsEnabled`）。 */
    val notificationsEnabled: Boolean
)

/** 读系统侧保活状态；任何异常都退到安全默认值（见 KDoc）。 */
fun readKeepAliveState(context: Context): KeepAliveState {
    val exempt = try {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    } catch (_: Exception) {
        false
    }
    val notifications = try {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } catch (_: Exception) {
        true
    }
    return KeepAliveState(exempt, notifications)
}

/**
 * 通知设置页的 Intent（直达本应用的通知页）。
 *
 * minSdk 26 保证 `ACTION_APP_NOTIFICATION_SETTINGS` 一定可用，所以原实现里的
 * `SDK_INT >= O` 分支是**死分支**（lint `ObsoleteSdkInt` 也这么判），删掉；
 * 真出异常时仍退到应用详情页。
 */
fun notificationSettingsIntent(context: Context): Intent {
    val pkg = context.packageName
    return try {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        }
    } catch (_: Exception) {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            try {
                data = Uri.parse("package:$pkg")
            } catch (_: Exception) {
                SilentFail.note("SystemSettings.notificationSettingsIntent:70")
            }
        }
    }
}

/**
 * 电池优化入口候选，按 [batteryOptActions] 的顺序。
 * 已豁免时返回空表（没有可引导的动作）。
 *
 * `@SuppressLint("BatteryLife")`：这是**用户主动点「后台保活」**才发出的 Intent，
 * 不是启动即弹的豁免申请；音乐播放被 OEM 清理是 vivo/OriginOS 实测的真实故障
 * （暂停 2-3 分钟后进程被杀 → 通知栏播放键点了没反应）。策略风险已知，属产品决策。
 */
@SuppressLint("BatteryLife")
fun batteryOptIntents(context: Context, batteryExempt: Boolean): List<Intent> =
    batteryOptActions(batteryExempt).map { action ->
        when (action) {
            BatteryOptAction.REQUEST_EXEMPTION -> Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
            BatteryOptAction.OPEN_SETTINGS_LIST ->
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

/**
 * 依次尝试打开，第一个成功的就返回 true。全部失败返回 false（不抛）。
 * 某些 ROM 没有 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 的目标页，
 * 这正是要按顺序退到"设置列表"的原因。
 */
fun startFirstAvailable(context: Context, intents: List<Intent>): Boolean {
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
            // 该 ROM 没有这个设置页 → 按顺序退到下一个入口
        }
    }
    return false
}

/** 打开通知设置；没有可用目标页时返回 false（调用方决定是否提示）。 */
fun launchNotificationSettings(context: Context): Boolean =
    try {
        context.startActivity(notificationSettingsIntent(context))
        true
    } catch (_: Exception) {
        SilentFail.note("SystemSettings.launchNotificationSettings:113")
        false
    }

/**
 * 系统分享面板。返回 null 表示成功，否则返回失败原因（调用方用
 * [diagnosableError] 拼提示 —— 原来就是带原始报错的，别降级成固定文案）。
 * 标题固定"分享歌单"—— 原实现如此，不改文案。
 */
fun shareText(context: Context, text: String, chooserTitle: String = "分享歌单"): Exception? =
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
        null
    } catch (e: Exception) {
        e
    }
