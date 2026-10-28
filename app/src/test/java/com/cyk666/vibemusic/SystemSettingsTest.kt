package com.cyk666.vibemusic

import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * 系统交互组（重构片 C5-lite）回归：入口顺序与兜底默认值是**行为**，
 * 不是实现细节 —— 搬出 `MainActivity` 之前这三条谁都没法测。
 */
@RunWith(RobolectricTestRunner::class)
class SystemSettingsTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    @Test
    fun notificationSettingsIntent_targetsThisApp() {
        val intent = notificationSettingsIntent(context())
        // API 26+ 走直达本应用通知页（Robolectric 默认 SDK 远高于 26）
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context().packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun batteryOptIntents_notExemptTriesRequestThenList() {
        val intents = batteryOptIntents(context(), batteryExempt = false)
        assertEquals(2, intents.size)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intents[0].action)
        assertEquals("package:${context().packageName}", intents[0].data.toString())
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, intents[1].action)
    }

    @Test
    fun batteryOptIntents_exemptHasNothingToOpen() {
        assertTrue(batteryOptIntents(context(), batteryExempt = true).isEmpty())
    }

    @Test
    fun startFirstAvailable_startsTheFirstOneAndStops() {
        // 用 Activity context：生产路径就是 Activity，且应用 context 调 startActivity
        // 需要 NEW_TASK（会在测试里抛，掩盖真正要验的东西）。
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val first = Intent(Settings.ACTION_SETTINGS)
        val second = Intent(Settings.ACTION_WIFI_SETTINGS)
        assertTrue(startFirstAvailable(activity, listOf(first, second)))
        assertEquals(Settings.ACTION_SETTINGS, shadowOf(activity).nextStartedActivity?.action)
        // 第一个成功即停：第二个不许再被启动
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    /**
     * Robolectric 默认**不**校验 Intent 能否被解析（`startActivity` 对不存在的 action
     * 也返回成功），所以"ROM 没有该设置页 → 退到下一个入口"这条真机行为在这里造不出来。
     * 显式打开校验后，再给一个必然失败的显式组件，才能验到 catch 分支。
     */
    @Test
    fun startFirstAvailable_allBadReturnsFalseWithoutThrowing() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        shadowOf(activity.application).checkActivities(true)
        val bad = Intent().setClassName(activity, "com.cyk666.vibemusic.NoSuchSettingsPage")
        assertFalse(startFirstAvailable(activity, listOf(bad)))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun readKeepAliveState_reportsSystemFlags() {
        val state = readKeepAliveState(context())
        // Robolectric 默认：通知开、不在电池白名单 —— 与真机首次安装一致。
        assertTrue(state.notificationsEnabled)
        assertFalse(state.batteryExempt)
    }

    @Test
    fun shareText_buildsChooserWithFixedTitle() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        assertNull(shareText(activity, "https://vibe.cyk666.top/p/1"))
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, started?.action)
        assertEquals("分享歌单", started?.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
        val inner = started?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals(Intent.ACTION_SEND, inner?.action)
        assertEquals("text/plain", inner?.type)
        assertEquals("https://vibe.cyk666.top/p/1", inner?.getStringExtra(Intent.EXTRA_TEXT))
    }
}
