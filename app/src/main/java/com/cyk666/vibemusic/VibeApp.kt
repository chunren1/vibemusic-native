package com.cyk666.vibemusic

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.tencent.bugly.crashreport.CrashReport

/**
 * Bugly AppID（bugly.qq.com 注册应用后填入；未填时不上报，只记本地 PlayDiag）。
 */
const val BUGLY_APP_ID = "5b95d7249e"

/**
 * 应用入口：注册全局 Coil ImageLoader（round6 体验专项）。
 *
 * - 磁盘缓存 256MB（用户明确"不用怕应用大小"）：封面跨会话秒开，配合后端
 *   /api/image-proxy 的 Cache-Control(24h)/ETag(304)，重复浏览同一封面零网络开销
 * - 内存缓存 20%（默认 25%，略收紧，给播放缓冲让路）
 * - crossfade：封面切换淡入，观感更顺
 */
class VibeApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        initBugly()
    }

    /**
     * Bugly 崩溃/ANR 上报（只在主进程初始化，子进程不上报，省资源）。
     * 未填 AppID 时直接跳过——本地 PlayDiag 照常工作，单测也不受影响。
     */
    private fun initBugly() {
        if (BUGLY_APP_ID.startsWith("REPLACE")) return
        try {
            val strategy = CrashReport.UserStrategy(this)
            strategy.setUploadProcess(packageName == currentProcessName())
            // 崩溃时把"死之前在干什么"（PlayDiag 面包屑）一起带上
            strategy.setCrashHandleCallback(object : CrashReport.CrashHandleCallback() {
                override fun onCrashHandleStart(
                    crashType: Int, errorType: String?,
                    errorMessage: String?, errorStack: String?
                ): Map<String, String> = buildMap {
                    put(
                        "playDiag",
                        "${PlayDiag.lastCode(this@VibeApp)} @ ${PlayDiag.lastTs(this@VibeApp)}"
                    )
                    // Swallowed-exception tally: how many best-effort paths had
                    // already been failing before the crash. Omitted when empty
                    // so the Bugly payload stays clean on healthy runs.
                    SilentFail.summary().takeIf { it.isNotEmpty() }?.let { put("silentFail", it) }
                }
            })
            CrashReport.initCrashReport(this, BUGLY_APP_ID, BuildConfig.DEBUG, strategy)
            BuglyReporter.ready = true
        } catch (_: Exception) {
            SilentFail.note("VibeApp.onCrashHandleStart:61")
        }
    }

    private fun currentProcessName(): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
        }
    } catch (_: Exception) {
        null
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}

/** Bugly 是否已初始化成功：PlayDiag 只在 true 时才往 Bugly 写自定义数据。 */
object BuglyReporter {
    @Volatile
    var ready: Boolean = false
}
