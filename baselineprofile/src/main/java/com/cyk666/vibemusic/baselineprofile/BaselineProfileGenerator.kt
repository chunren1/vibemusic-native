package com.cyk666.vibemusic.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 生成器（真机/模拟器上跑，跑完把产物拷进 app）。
 *
 * 跑法（USB 连上 Vivo 测试机后，本机执行）：
 *   ./gradlew :baselineprofile:generateBaselineProfile
 * 产物输出目录由任务日志打印；把其中的 baseline-prof.txt
 * （如有 startup-prof.txt 一并）拷到 app/src/main/baselineProfiles/，
 * 此后所有 release 构建自动打包进 APK（AGP 行为，无需改配置）。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = "com.cyk666.vibemusic",
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun navigation() = rule.collect(
        packageName = "com.cyk666.vibemusic",
        outputFilePrefix = "navigation"
    ) {
        pressHome()
        startActivityAndWait()
        device.findObject(By.text("搜索")).click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
        device.findObject(By.text("我的")).click()
        device.waitForIdle()
    }
}
