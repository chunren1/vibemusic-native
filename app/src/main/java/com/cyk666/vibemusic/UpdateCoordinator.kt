package com.cyk666.vibemusic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Self-update flow, lifted out of `MainActivity.onCreate` (refactor slice C4).
 *
 * This is a **pure code move**: state names, operation order, 24h throttle,
 * error wording, download fallback and signature check are unchanged. Only the
 * owner changed — the Activity used to hold these as local `remember`s inside a
 * 3.7k-line composable; now they live in a state holder that Compose still
 * observes (snapshot state), which is the shape described in
 * `docs/REFACTOR-mainactivity.md`.
 *
 * @param onError UI error channel (the Activity's Snackbar).
 */
class UpdateCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit
) {

    /** A newer release to show the dialog for; null when nothing to do. */
    var release by mutableStateOf<GithubRelease?>(null)
        private set

    /** Download/install in flight — drives the dialog's busy state. */
    var busy by mutableStateOf(false)
        private set

    /** Manual check in flight — drives the Settings row spinner. */
    var manualChecking by mutableStateOf(false)
        private set

    var channel by mutableStateOf(UpdateChannel.STABLE)
        private set

    /** Load the persisted channel once per Activity (was `LaunchedEffect("update-channel")`). */
    fun loadChannel() {
        scope.launch {
            channel = try {
                QueueStore.loadUpdateChannel(context)
            } catch (_: Exception) {
                SilentFail.note("UpdateCoordinator.loadChannel:55")
                UpdateChannel.STABLE
            }
        }
    }

    fun selectChannel(value: UpdateChannel) {
        channel = value
        scope.launch {
            try {
                QueueStore.saveUpdateChannel(context, value)
            } catch (_: Exception) {
                SilentFail.note("UpdateCoordinator.selectChannel:67")
            }
        }
    }

    /** Dialog "later": ignore while installing so the sheet cannot vanish mid-flow. */
    fun dismiss() {
        if (!busy) release = null
    }

    /**
     * Cold-start check: once per process + 24h throttle, silent unless a newer
     * release exists (same predicate and ordering as the inline version).
     */
    fun checkOnLaunch() {
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val last = try {
                    QueueStore.loadLastUpdateCheck(context)
                } catch (_: Exception) {
                    0L
                }
                if (!shouldRunUpdateCheck(now, last, force = false)) return@launch
                val storedChannel = try {
                    QueueStore.loadUpdateChannel(context)
                } catch (_: Exception) {
                    UpdateChannel.STABLE
                }
                channel = storedChannel
                val rel = try {
                    fetchLatestForChannel(storedChannel)
                } catch (_: Exception) {
                    // 拉取失败**不**记时间戳：否则断网启动一次就把 24h 窗口烧掉，
                    // 用户接下来一天都收不到更新（手动检查不受影响，但没人会去点）。
                    return@launch
                }
                // 只在真的拿到 release 之后才推进节流窗口（2026-10-28 修正）。
                try {
                    QueueStore.saveLastUpdateCheck(context, now)
                } catch (_: Exception) {
                    SilentFail.note("UpdateCoordinator.checkOnLaunch:94")
                }
                val current = installedVersionName()
                when (compareAndDecide(current, rel.tag, fetchOk = true)) {
                    UpdateDecision.UPDATE_AVAILABLE -> release = rel
                    else -> Unit
                }
            } catch (_: Exception) {
                SilentFail.note("UpdateCoordinator.checkOnLaunch:113")
            }
        }
    }

    /**
     * Manual check from Settings. [appVersionName] is the Activity's already
     * resolved value; blank falls back to PackageManager exactly as before.
     */
    fun checkManual(appVersionName: String) {
        if (manualChecking) return
        manualChecking = true
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val rel = try {
                    fetchLatestForChannel(channel)
                } catch (e: Exception) {
                    onError(friendlyNetworkMessage(e))
                    return@launch
                }
                // 同 checkOnLaunch：手动检查失败也不该重置自动检查的 24h 窗口。
                try {
                    QueueStore.saveLastUpdateCheck(context, now)
                } catch (_: Exception) {
                    SilentFail.note("UpdateCoordinator.checkManual:131")
                }
                val current = appVersionName.ifBlank { installedVersionName() }
                val decision = compareAndDecide(current, rel.tag, fetchOk = true)
                when (decision) {
                    UpdateDecision.UPDATE_AVAILABLE -> release = rel
                    else -> manualUpdateCheckMessage(decision)?.let { onError(it) }
                }
            } finally {
                manualChecking = false
            }
        }
    }

    /** Download (with cross-source fallback) then hand the APK to the installer. */
    fun download(rel: GithubRelease) {
        if (busy) return
        busy = true
        scope.launch(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val safeTag = rel.tag.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val apk = File(dir, "vibemusic-$safeTag.apk")
                if (!isDownloadComplete(apk, rel.apkSize)) {
                    val fallback = fetchFallbackApkUrl(otherUpdateSourceUrl(rel.apkUrl))
                    downloadApk(
                        rel.apkUrl,
                        apk,
                        fallbackApkUrl = fallback,
                        expectedBytes = rel.apkSize
                    )
                }
                withContext(Dispatchers.Main) {
                    busy = false
                    tryInstall(apk)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    busy = false
                    onError("更新失败: ${friendlyNetworkMessage(e)}")
                }
            }
        }
    }

    private fun tryInstall(apk: File) {
        try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                onError("请允许安装未知应用后，再点立即更新重试")
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                } catch (e: Exception) {
                    onError("无法打开安装权限设置: ${friendlyNetworkMessage(e)}")
                }
                return
            }
            if (!isSameSignature(context, apk)) {
                onError(UPDATE_SIGNATURE_MISMATCH_MESSAGE)
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            release = null
        } catch (e: Exception) {
            onError("安装失败: ${friendlyNetworkMessage(e)}")
        }
    }

    private fun installedVersionName(): String = try {
        @Suppress("DEPRECATION")
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName.orEmpty()
    } catch (_: Exception) {
        ""
    }
}
