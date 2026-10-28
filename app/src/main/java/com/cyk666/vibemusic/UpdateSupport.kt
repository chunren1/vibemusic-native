package com.cyk666.vibemusic

// 更新检查/降级下载支持（从 MainActivity 拆出；round6 T5）

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Pure: manual "检查更新" feedback per decision. UPDATE_AVAILABLE returns
 * null (the update dialog is the feedback); the auto path never calls this
 * and stays silent.
 */
fun manualUpdateCheckMessage(decision: UpdateDecision): String? = when (decision) {
    UpdateDecision.UPDATE_AVAILABLE -> null
    UpdateDecision.UP_TO_DATE -> "已是最新版本"
    UpdateDecision.CHECK_FAILED -> "检查更新失败，请稍后重试"
}

/** Signature-mismatch install gate copy: old-key users must reinstall (clears offline content). */
const val UPDATE_SIGNATURE_MISMATCH_MESSAGE = "签名不一致，需卸载重装（会清空离线内容）"

/**
 * Pure: 另一源的**同 tag** 端点（下载回退用，UPD-2）。
 *
 * 原来是 `.../releases/latest`：正式版 latest 排除 prerelease，于是测试通道
 * 下载失败回退时会把**正式版** APK 装给测试版用户 —— 版本不同，而签名门
 * 只证明"同一个开发者"，拦不住。改成单 tag 端点后回退源与主源必是同版本。
 *
 * [tag] 为空时拼出的 URL 会 404 → [fetchFallbackApkUrl] 返回 null（只用主源），
 * 不会抛异常打断下载。
 */
fun otherUpdateSourceUrl(primaryApkUrl: String, tag: String): String =
    if (primaryApkUrl.contains("gitee", ignoreCase = true)) {
        UPDATE_GITHUB_TAG_URL_BASE + tag.trim()
    } else {
        UPDATE_GITEE_TAG_URL_BASE + tag.trim()
    }

private val fallbackCheckHttp = OkHttpClient.Builder()
    .dispatcher(HttpClientCore.dispatcher)
    .connectionPool(HttpClientCore.connectionPool)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

/**
 * Best-effort fetch of the other source's release apk URL for the download
 * fallback (null on any failure — the primary URL is still tried alone).
 * Runs on IO; never throws.
 */
suspend fun fetchFallbackApkUrl(checkUrl: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val accept = if (checkUrl.contains("github", ignoreCase = true)) {
                "application/vnd.github+json"
            } else {
                null
            }
            val builder = Request.Builder().url(checkUrl).get()
            if (!accept.isNullOrBlank()) builder.header("Accept", accept)
            fallbackCheckHttp.newCall(builder.build()).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val body = res.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                try {
                    parseLatestRelease(body).apkUrl.ifBlank { null }
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
