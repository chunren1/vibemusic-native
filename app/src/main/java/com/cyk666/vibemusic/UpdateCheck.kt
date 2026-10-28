package com.cyk666.vibemusic

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

const val UPDATE_LATEST_URL =
    "https://api.github.com/repos/chunren1/vibemusic-native/releases/latest"

/** Gitee mirror (primary update source; GitHub is the fallback). */
const val UPDATE_GITEE_LATEST_URL =
    "https://gitee.com/api/v5/repos/green-leavesQAQ/vibemusic-native/releases/latest"

/**
 * Release 列表端点（测试通道用）：取**版本号最大**的带 .apk 条目（含 prerelease）。
 * ⚠ 不能取「首个」——GitHub 按 created_at 降序返回，Gitee 是升序（最老在前），
 * 依赖顺序会让 Gitee 上的测试版通道永远选中最老的那个 tag。
 * 正式通道继续走上面的 .../latest（自动排除 prerelease）。
 */
const val UPDATE_RELEASES_URL =
    "https://api.github.com/repos/chunren1/vibemusic-native/releases?per_page=10"

/**
 * Gitee 的列表端点必须显式要 `direction=desc`：Gitee 默认**升序**（最老在前），
 * 而且 page=1 就是最老的那一页 —— 2026-10-27 实测 per_page=10 拿到的是
 * v1.0.47-ai-beta.4 … v1.0.54-ai，最新的 v2.0.16-ai 根本不在首页。
 * `per_page=100` 是第二道保险：万一服务端忽略 direction，30 条 release 也能
 * 一次取全，配合 parseReleaseList 的「取版本最大」仍能选对（>100 条时才需分页）。
 */
const val UPDATE_GITEE_RELEASES_URL =
    "https://gitee.com/api/v5/repos/green-leavesQAQ/vibemusic-native/releases?per_page=100&direction=desc"

/**
 * 单 tag 端点（UPD-2 下载回退用）：回退源必须拿**同一个 tag**的资产。
 * 原来回退到 `.../releases/latest`，而正式版 latest 排除 prerelease ——
 * 测试通道回退时会把正式版 APK 装进测试版用户手里（版本不同，签名门拦不住：
 * 同一个开发者签名）。
 */
const val UPDATE_GITHUB_TAG_URL_BASE =
    "https://api.github.com/repos/chunren1/vibemusic-native/releases/tags/"

const val UPDATE_GITEE_TAG_URL_BASE =
    "https://gitee.com/api/v5/repos/green-leavesQAQ/vibemusic-native/releases/tags/"

/**
 * APK 资产允许的下载 host（UPD-4）。release 元数据是**外部输入**：账号被盗时
 * 攻击者可以把 `browser_download_url` 指到任意域名。签名门只保证"同一个开发者"，
 * 不保证"同一个来源"。
 *
 * 子域一并放行（`api.github.com`、`objects.githubusercontent.com` 这类官方 CDN）；
 * 比较用 `host == it || host.endsWith(".$it")`，所以 `github.com.evil.com` 会被拒。
 */
val UPDATE_HOST_ALLOWLIST = setOf("gitee.com", "github.com", "objects.githubusercontent.com")

/** Minimum gap between automatic update checks. */
const val UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

data class GithubRelease(
    val tag: String,
    val name: String,
    val body: String,
    val apkUrl: String,
    /** GitHub/Gitee releases API 的 prerelease 标记；正式版为 false。 */
    val prerelease: Boolean = false,
    /**
     * Release 资产字节数（GitHub assets 自带 `size`；Gitee 附件接口不带则为 -1，
     * 此时下载校验退化为 Content-Length/非空门，续传不受影响）。
     */
    val apkSize: Long = -1L,
    /**
     * Release body 里 CI 写的 `APK sha256: <hex>`（.github/workflows/release.yml
     * 生成的 notes）。下载落盘后按它校验：签名门只证明"同一个开发者"，不证明
     * "同一份字节"，混版/降级要靠这个拦（CODE-REVIEW-2026-10-28 UPD-4）。
     * null = 该 release 没写（老版本），跳过校验 —— 保持可选字段语义。
     */
    val sha256: String? = null
)

private fun numericCore(version: String): List<Int> {
    val stripped = version.trim().removePrefix("v").removePrefix("V")
    val core = stripped.substringBefore("-").substringBefore("+")
    if (core.isBlank()) return emptyList()
    return core.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
}

private fun betaNumber(version: String): Int? {
    val stripped = version.trim().removePrefix("v").removePrefix("V")
        .substringBefore("+").trim()
    val m = Regex("(?i)-beta\\.(\\d+)\\s*$").find(stripped) ?: return null
    return m.groupValues[1].toIntOrNull()
}

/**
 * Pure: true when [latestTag] is strictly newer than [current].
 * Leading v/V stripped; dot-separated numeric core compared with missing
 * parts as 0; non-beta suffixes (-ai etc.) ignored for ordering, so equal
 * cores without a beta marker are NOT newer (avoids reinstall loops).
 * Beta tie-break when numeric cores tie: trailing -beta.N parsed
 * case-insensitively; both absent → false; stable (no beta marker) over
 * beta → true; beta over stable → false; both beta → true iff
 * latestNum > currentNum. NOTE: beta.N suffixes are transitional only —
 * new prereleases always bump the numeric core (e.g. 1.0.44-ai-beta.1),
 * so old clients compare by core and never depend on this tie-break.
 */
fun isNewerVersion(current: String, latestTag: String): Boolean {
    val cur = numericCore(current)
    val lat = numericCore(latestTag)
    val n = maxOf(cur.size, lat.size)
    for (i in 0 until n) {
        val c = cur.getOrElse(i) { 0 }
        val l = lat.getOrElse(i) { 0 }
        if (l != c) return l > c
    }
    val curBeta = betaNumber(current)
    val latBeta = betaNumber(latestTag)
    if (curBeta == null && latBeta == null) return false
    if (curBeta != null && latBeta == null) return true
    if (curBeta == null && latBeta != null) return false
    return latBeta!! > curBeta!!
}

/**
 * Pure: parse a GET .../releases/latest response, picking the first `.apk`
 * asset. Accepts both GitHub and Gitee shapes: the asset download field is
 * `browser_download_url` on both (Gitee mirrors the GitHub field name);
 * `download_url` / plain `url` ending in `.apk` are accepted defensively.
 * Throws descriptive RuntimeException on missing tag / missing assets /
 * no apk asset / malformed.
 */
fun parseLatestRelease(json: String): GithubRelease {
    val root = try {
        JSONObject(json)
    } catch (e: Exception) {
        throw RuntimeException("Parse release failed: malformed JSON (${e.message})")
    }
    val tag = root.optString("tag_name")
    if (tag.isBlank()) throw RuntimeException("Parse release failed: missing tag_name")
    val assets = root.optJSONArray("assets")
        ?: throw RuntimeException("Parse release failed: $tag has no assets array")
    var apk: ApkAsset? = null
    var rejectedHost: String? = null
    for (i in 0 until assets.length()) {
        val o = assets.optJSONObject(i) ?: continue
        // 白名单外的 host 只跳过这个资产、继续找下一个候选；但要把 host 记下来，
        // 否则"上游换了域名"只会表现为一句没头没尾的 no .apk asset，线上没法定位。
        val raw = apkUrlOf(o)
        if (raw != null && !isAllowedUpdateHost(raw)) {
            rejectedHost = hostOf(raw) ?: raw
            continue
        }
        val found = pickApkFromAsset(o)
        if (found != null) {
            apk = found
            break
        }
    }
    if (apk == null) {
        throw RuntimeException(
            if (rejectedHost == null) "Parse release failed: $tag has no .apk asset"
            else "Parse release failed: $tag has no .apk asset (host not allowed: $rejectedHost)"
        )
    }
    val body = root.optString("body")
    return GithubRelease(
        tag = tag,
        name = root.optString("name"),
        body = body,
        apkUrl = apk.url,
        prerelease = root.optBoolean("prerelease", false),
        apkSize = apk.size,
        sha256 = parseSha256FromBody(body)
    )
}

/** Pure throttle gate: never-checked → true; otherwise 24h gap required. */
fun shouldCheckUpdate(nowMs: Long, lastCheckMs: Long): Boolean {
    if (lastCheckMs <= 0L) return true
    return nowMs - lastCheckMs >= UPDATE_CHECK_INTERVAL_MS
}

/**
 * Pure: update-check orchestrator gate. Auto path passes force=false (24h
 * throttle applies); manual "检查更新" tap passes force=true (bypasses the
 * throttle but still records the timestamp so auto stays sane).
 */
fun shouldRunUpdateCheck(nowMs: Long, lastCheckMs: Long, force: Boolean = false): Boolean =
    force || shouldCheckUpdate(nowMs, lastCheckMs)

/** Pure outcome of one update-decision step (shared by auto + manual paths). */
enum class UpdateDecision {
    UPDATE_AVAILABLE,
    UP_TO_DATE,
    CHECK_FAILED
}

/**
 * Pure: route both check paths through one decision. fetchOk=false (or a
 * blank/unparseable tag) → CHECK_FAILED; a current version or tag without
 * any digit is malformed → CHECK_FAILED; otherwise strict isNewerVersion
 * ordering decides (equal cores and suffix-only diffs are UP_TO_DATE).
 */
fun compareAndDecide(
    currentVersion: String,
    latestTag: String?,
    fetchOk: Boolean
): UpdateDecision {
    if (!fetchOk) return UpdateDecision.CHECK_FAILED
    if (latestTag.isNullOrBlank()) return UpdateDecision.CHECK_FAILED
    if (!currentVersion.any(Char::isDigit)) return UpdateDecision.CHECK_FAILED
    if (!latestTag.any(Char::isDigit)) return UpdateDecision.CHECK_FAILED
    return if (isNewerVersion(currentVersion, latestTag)) {
        UpdateDecision.UPDATE_AVAILABLE
    } else {
        UpdateDecision.UP_TO_DATE
    }
}

/** Pure: Mine-tab version row label. */
fun formatVersionLabel(versionName: String): String =
    if (versionName.isBlank()) "版本 未知" else "版本 $versionName"

// Clean client: GitHub API needs no auth — never attach the app Bearer interceptor.
private val updateHttp = OkHttpClient.Builder()
    .dispatcher(HttpClientCore.dispatcher)
    .connectionPool(HttpClientCore.connectionPool)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

/** Update source that served the release. FIRST successful source wins. */
enum class UpdateSource {
    GITEE,
    GITHUB
}

/**
 * Pure: which source answered. **注意**：这只是"谁应答了"，不再是选择策略 ——
 * "首个成功者胜"已被 [pickBestRelease]（按版本号取大者）取代，因为 Gitee 滞后
 * 一个版本时会让用户永远看到"已是最新"（2026-10-27 事故同一失效面，UPD-3）。
 * 保留它给只需要"谁应答"的诊断/日志场景。
 */
fun resolveUpdateSource(giteeOk: Boolean, githubOk: Boolean): UpdateSource? =
    if (giteeOk) UpdateSource.GITEE else if (githubOk) UpdateSource.GITHUB else null

/**
 * Pure: 两源结果里取**版本更大**者（UPD-3）。
 *
 * - 一边为 null 就用另一边（单源可用即不失败）；
 * - 两边都在 → [isNewerVersion] 比较，相等时仍优先 Gitee（首选源）；
 * - 两边都 null → null（调用方映射 CHECK_FAILED）。
 */
fun pickBestRelease(
    gitee: GithubRelease?,
    github: GithubRelease?
): Pair<UpdateSource, GithubRelease>? = when {
    gitee == null -> github?.let { UpdateSource.GITHUB to it }
    github == null -> UpdateSource.GITEE to gitee
    isNewerVersion(gitee.tag, github.tag) -> UpdateSource.GITHUB to github
    else -> UpdateSource.GITEE to gitee
}

private suspend fun fetchReleaseJson(url: String, accept: String?): GithubRelease =
    withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).get()
        if (!accept.isNullOrBlank()) builder.header("Accept", accept)
        updateHttp.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) {
                throw RuntimeException("Update check failed: HTTP ${res.code} ${res.message}")
            }
            val body = res.body?.string().orEmpty()
            if (body.isBlank()) throw RuntimeException("Update check failed: empty body")
            parseLatestRelease(body)
        }
    }

/**
 * Suspend: 两源**都取**，按版本号取大者（CODE-REVIEW-2026-10-28 UPD-3）。
 *
 * 旧实现是"首个成功者胜"：Gitee 滞后一个版本时永远提示"已是最新" ——
 * 2026-10-27 的事故就是这个失效面（Gitee 缺 release，用户看到已是最新）。
 * 现在两源并行取（延迟取两者较大值，不是相加），[isNewerVersion] 取大者，
 * 版本相等时仍优先 Gitee。任一源失败不影响另一源；两源都失败才抛。
 */
suspend fun fetchLatestRelease(): GithubRelease = fetchBestOf(
    gitee = { fetchReleaseJson(UPDATE_GITEE_LATEST_URL, null) },
    github = { fetchReleaseJson(UPDATE_LATEST_URL, "application/vnd.github+json") }
)

/**
 * 镜像源（非主源）的宽限：主源已经答完后，最多再等这么久拿镜像的版本号。
 *
 * 为什么需要：`api.github.com` 在国内常年不可达，connect 超时 15s。并行取两源后
 * 如果无条件 `await` 两边，"检查更新"会从 1s 变成 15s —— 用户点一下要转圈 15 秒。
 * 主源（Gitee）照常等满，镜像只给一小段；超时按"没应答"处理（用主源的结果）。
 */
const val UPDATE_MIRROR_GRACE_MS = 2_500L

/** 单源取回的结果：release 与错误都留着，用于"两源都失败"的诊断文案。 */
private class ReleaseAttempt(val release: GithubRelease?, val error: Exception?)

private suspend fun attemptRelease(block: suspend () -> GithubRelease): ReleaseAttempt =
    try {
        ReleaseAttempt(block(), null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ReleaseAttempt(null, e)
    }

/**
 * Suspend: 并行问两个源，返回版本更大的那个（相等 → Gitee）。
 * 两个都失败才抛，消息里带两边原始原因 —— 单源抖动不该让用户看不到更新。
 */
internal suspend fun fetchBestOf(
    gitee: suspend () -> GithubRelease,
    github: suspend () -> GithubRelease
): GithubRelease = coroutineScope {
    val g = async { attemptRelease(gitee) }
    val h = async { attemptRelease(github) }
    val giteeAttempt = g.await()
    val githubAttempt = withTimeoutOrNull(UPDATE_MIRROR_GRACE_MS) { h.await() }
        ?: ReleaseAttempt(null, null)
    // 宽限内没回来的镜像请求要显式取消：coroutineScope 会等所有子任务，
    // 不取消就还是会被 15s 的 connect 超时拖住。
    h.cancel()
    val picked = pickBestRelease(giteeAttempt.release, githubAttempt.release)
        ?: throw RuntimeException(
            "Update check failed: gitee (${giteeAttempt.error?.message})" +
                " + github (${githubAttempt.error?.message})"
        )
    android.util.Log.d(
        "UpdateCheck",
        "update source=${picked.first} tag=${picked.second.tag}" +
            " gitee=${giteeAttempt.release?.tag ?: "err"}" +
            " github=${githubAttempt.release?.tag ?: "err"}"
    )
    picked.second
}

/** 更新通道：STABLE 只看正式版（.../latest，自动排除 prerelease）；BETA 看含预发布的最新版。 */
enum class UpdateChannel {
    STABLE,
    BETA
}

/**
 * Pure: settings 更新通道 row label. STABLE → 正式版, BETA → 测试版.
 */
fun updateChannelLabel(channel: UpdateChannel): String =
    if (channel == UpdateChannel.BETA) "测试版" else "正式版"

/**
 * Pure: settings 更新通道 row subtitle describing what each channel receives.
 */
fun updateChannelSubtitle(channel: UpdateChannel): String =
    if (channel == UpdateChannel.BETA) "测试版 · 含预发布版本，可能不稳定"
    else "正式版 · 只接收稳定更新"

/** 屏幕方向偏好：默认锁定竖屏（旋转会重建 Activity 丢界面态），跟随系统则交还传感器。 */
enum class OrientationMode {
    LOCK_PORTRAIT,
    FOLLOW_SYSTEM
}

/** Pure: resolve a persisted orientation-mode string. Blank/unknown → LOCK_PORTRAIT (default). */
fun parseOrientationMode(raw: String?): OrientationMode =
    if (raw?.trim().equals("FOLLOW_SYSTEM", ignoreCase = true)) OrientationMode.FOLLOW_SYSTEM
    else OrientationMode.LOCK_PORTRAIT

/** Pure: settings 屏幕方向 row subtitle describing what each mode does. */
fun orientationModeSubtitle(mode: OrientationMode): String =
    if (mode == OrientationMode.FOLLOW_SYSTEM) "跟随系统 · 旋转会重建界面并回到首页"
    else "锁定竖屏 · 旋转不再丢界面"

/**
 * Pure: one-line note shown in the update dialog when the release is a
 * prerelease; null for stable releases (dialog unchanged).
 */
fun betaReleaseNote(prerelease: Boolean): String? =
    if (prerelease) "测试版：可能不稳定，升级前请知悉。" else null

/**
 * Suspend: branch the release fetch on the update channel. STABLE keeps
 * today's [fetchLatestRelease] (.../latest, prereleases excluded by
 * design); BETA uses [fetchLatestReleaseIncludingPrerelease] (newest entry
 * wins, stable or beta). Fetcher params exist only as a test seam —
 * call sites pass the channel and use the defaults.
 */
suspend fun fetchLatestForChannel(
    channel: UpdateChannel,
    stableFetch: suspend () -> GithubRelease = ::fetchLatestRelease,
    betaFetch: suspend () -> GithubRelease = ::fetchLatestReleaseIncludingPrerelease
): GithubRelease =
    if (channel == UpdateChannel.BETA) betaFetch() else stableFetch()

private data class ApkAsset(val url: String, val size: Long)

/** 资产里的下载 URL（按 GitHub/Gitee 字段优先级取第一个 `.apk`），不看 host。 */
private fun apkUrlOf(o: JSONObject): String? {
    val candidates = listOf(
        o.optString("browser_download_url"),
        o.optString("download_url"),
        o.optString("url")
    )
    return candidates.firstOrNull { it.isNotBlank() && it.lowercase().endsWith(".apk") }
}

/** 解析 URL 的 host（小写）；畸形 URL 返回 null（调用方按"不在白名单"处理 = fail closed）。 */
private fun hostOf(url: String): String? =
    try {
        java.net.URI(url.trim()).host?.lowercase()
    } catch (_: Exception) {
        null
    }

/**
 * Pure: 该下载 URL 的 host 是否在白名单内（UPD-4）。
 * 子域放行（官方 CDN 常是 `xxx.githubusercontent.com`），`github.com.evil.com`
 * 这种后缀伪装不放行 —— 匹配的是 `.github.com` 这个点分后缀，不是裸 endsWith。
 */
fun isAllowedUpdateHost(url: String): Boolean {
    val host = hostOf(url) ?: return false
    return UPDATE_HOST_ALLOWLIST.any { host == it || host.endsWith(".$it") }
}

private fun pickApkFromAsset(o: JSONObject): ApkAsset? {
    val url = apkUrlOf(o) ?: return null
    if (!isAllowedUpdateHost(url)) {
        // 可观察信号：被拒的 host 必须能在 logcat 里看到（静默丢弃会让
        // "上游换了资产域名"表现成"没有 APK"）。
        android.util.Log.w("UpdateCheck", "rejected apk asset host: ${hostOf(url)} url=$url")
        return null
    }
    val size = o.optLong("size", -1L).takeIf { it > 0L } ?: -1L
    return ApkAsset(url, size)
}

/**
 * Pure: parse a GET .../releases?per_page=N response (JSON array, newest first),
 * returning the first entry carrying a `.apk` asset (prerelease included).
 * Returns null when the array is empty / no entry has an apk (caller maps to CHECK_FAILED).
 */
fun parseReleaseList(json: String): GithubRelease? {
    val arr = try {
        org.json.JSONArray(json)
    } catch (e: Exception) {
        throw RuntimeException("Parse release list failed: malformed JSON (${e.message})")
    }
    // ⚠ 不要取「首个带 .apk 的条目」：列表顺序不是版本顺序。
    // GitHub 按 created_at 降序（最新在前），Gitee 却是**升序**（最老在前）——
    // 2026-10-27 事故：Gitee 上首个带 APK 的是 v1.0.47-ai-beta.4，
    // isNewerVersion(2.0.15-ai, 那个) = false → 测试版通道永远提示「已是最新」。
    // 故这里按版本号取最大值（isNewerVersion 同时处理 -beta.N 排序）。
    var best: GithubRelease? = null
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val tag = o.optString("tag_name")
        if (tag.isBlank()) continue
        val assets = o.optJSONArray("assets") ?: continue
        var apk: ApkAsset? = null
        for (j in 0 until assets.length()) {
            val a = assets.optJSONObject(j) ?: continue
            val found = pickApkFromAsset(a)
            if (found != null) {
                apk = found
                break
            }
        }
        if (apk == null) continue
        val body = o.optString("body")
        val candidate = GithubRelease(
            tag = tag,
            name = o.optString("name"),
            body = body,
            apkUrl = apk.url,
            prerelease = o.optBoolean("prerelease", false),
            apkSize = apk.size,
            sha256 = parseSha256FromBody(body)
        )
        val incumbent = best
        if (incumbent == null || isNewerVersion(incumbent.tag, candidate.tag)) {
            best = candidate
        }
    }
    return best
}

private suspend fun fetchReleaseListJson(url: String, accept: String?): String =
    withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).get()
        if (!accept.isNullOrBlank()) builder.header("Accept", accept)
        updateHttp.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) {
                throw RuntimeException("Update check failed: HTTP ${res.code} ${res.message}")
            }
            val body = res.body?.string().orEmpty()
            if (body.isBlank()) throw RuntimeException("Update check failed: empty body")
            body
        }
    }

/**
 * Suspend: 测试通道 —— 含 prerelease 的最新版，同样两源取大者（UPD-3）。
 * 正式通道继续用 [fetchLatestRelease]（`.../latest`，天然排除 prerelease）；
 * 通道语义不变，改的只是"两源怎么合并"。
 */
suspend fun fetchLatestReleaseIncludingPrerelease(): GithubRelease = fetchBestOf(
    gitee = { fetchListRelease(UPDATE_GITEE_RELEASES_URL, null) },
    github = { fetchListRelease(UPDATE_RELEASES_URL, "application/vnd.github+json") }
)

/** 列表端点取单条：没有带 .apk 的条目 → 抛（与单条端点同一错误口径）。 */
private suspend fun fetchListRelease(url: String, accept: String?): GithubRelease =
    parseReleaseList(fetchReleaseListJson(url, accept))
        ?: throw RuntimeException("release list has no .apk entry")

/** Suspend: download the release apk to [destFile] (throws on HTTP/empty; caller snackbars once). */
suspend fun downloadApk(apkUrl: String, destFile: File): File =
    downloadApk(apkUrl, destFile, fallbackApkUrl = null, expectedBytes = -1L)

/**
 * Suspend: stream the release apk to [destFile] without ever holding the
 * whole file in RAM (OkHttp [okhttp3.ResponseBody.source] streaming, fixed
 * 32KB buffer — never `bytes()`), landing via `<name>.apk.part` +
 * same-dir [File.renameTo] (same convention as [OfflineStore.saveBytes]:
 * a killed process / full disk leaves only the `.part`, never a truncated
 * final file, so an interrupted download is never reused).
 *
 * Size gate: when [expectedBytes] > 0 (release-asset size, [GithubRelease.apkSize])
 * the landed bytes must match exactly; otherwise the response `Content-Length`
 * (when the server sends one) must match the resumed offset plus received
 * bytes. Empty / failed `renameTo` throw and drop the `.part`; an
 * *incomplete* landing keeps the `.part` so the next attempt resumes via
 * `Range` instead of restarting (stale/oversized parts restart once on 416,
 * or immediately when the server answers 200 to a ranged request).
 *
 * Source fallback (mirrors [fetchLatestRelease] Gitee→GitHub): [apkUrl] is
 * tried first, then [fallbackApkUrl] on ANY first-source failure (HTTP /
 * truncated / empty). Callers pass the other source's release `apkUrl` as
 * [fallbackApkUrl] so Gitee attachment wobble falls back to GitHub; both
 * sources serve the identical release file so a kept `.part` stays valid
 * across the switch, and the size gate still rejects mixed garbage.
 */
suspend fun downloadApk(
    apkUrl: String,
    destFile: File,
    fallbackApkUrl: String? = null,
    expectedBytes: Long = -1L
): File = withContext(Dispatchers.IO) {
    val sources = resolveDownloadSources(apkUrl, fallbackApkUrl)
    if (sources.isEmpty()) throw RuntimeException("Download failed: empty url")
    var lastError: Exception? = null
    for ((index, url) in sources.withIndex()) {
        try {
            return@withContext downloadSingleApk(url, destFile, expectedBytes)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            lastError = e
            android.util.Log.d("UpdateCheck", "apk source #$index failed: ${e.message}")
        }
    }
    throw RuntimeException("Download failed: ${sources.size} source(s) failed (last: ${lastError?.message})")
}

/**
 * Pure: ordered distinct non-blank download URLs — primary first, fallback
 * second. Empty primary falls through to the fallback; identical URLs are
 * tried once; both blank → empty (caller throws "empty url").
 */
fun resolveDownloadSources(primaryApkUrl: String, fallbackApkUrl: String? = null): List<String> {
    val out = ArrayList<String>(2)
    val primary = primaryApkUrl.trim()
    if (primary.isNotBlank()) out.add(primary)
    val fallback = fallbackApkUrl?.trim().orEmpty()
    if (fallback.isNotBlank() && fallback !in out) out.add(fallback)
    return out
}

/** Pure: staging file for an apk download (`<name>.apk.part`, same dir so [File.renameTo] stays on one fs). */
fun partFileFor(destFile: File): File {
    val parent = destFile.parentFile ?: destFile.absoluteFile.parentFile ?: File(".")
    return File(parent, destFile.name + ".part")
}

/**
 * Pure: resume offset into an interrupted download. A `.part` shorter than
 * the known total resumes where it stopped; an empty/missing part, or one
 * at/past the known total (stale), restarts from zero. Unknown total (<= 0,
 * the Gitee case) resumes whatever exists — the server answers 416 if the
 * offset is past EOF and the caller restarts fresh.
 */
fun resumeOffsetFor(partLen: Long, expectedBytes: Long): Long {
    if (partLen <= 0L) return 0L
    if (expectedBytes > 0L && partLen >= expectedBytes) return 0L
    return partLen
}

/**
 * Pure: reuse gate for a landed apk. True only when the file exists,
 * non-empty, and — when [expectedBytes] > 0 — byte-identical in size.
 * Unknown size (<= 0) keeps the legacy non-empty gate (weak: the release
 * sources that publish `size` — GitHub — now feed [GithubRelease.apkSize]
 * through, so prefer exact); a leftover `.part` from an interrupted attempt
 * is resumed by [downloadSingleApk], never silently reused as complete.
 * Intended call site (wiring task): `MainActivity.startUpdateDownload`
 * replaces `!apk.exists() || apk.length() <= 0L` with `!isDownloadComplete(apk, expectedBytes)`.
 */
fun isDownloadComplete(file: File, expectedBytes: Long): Boolean {
    try {
        if (!file.exists() || !file.isFile) return false
        val len = file.length()
        if (len <= 0L) return false
        if (expectedBytes <= 0L) return true
        return len == expectedBytes
    } catch (_: Exception) {
        return false
    }
}

/**
 * Pure: 从 release body 里抽 CI 写的 sha256。
 *
 * release.yml 生成的 notes 形如 ``APK sha256: `<64 hex>` ``（正式版与测试版
 * 都是这个格式）。没有这个字段 → null，调用方跳过校验：老 release 不写，
 * 不能把可选字段变成硬要求。
 */
fun parseSha256FromBody(body: String): String? =
    Regex("(?i)sha256[\\s:：`=]*([0-9a-f]{64})").find(body)
        ?.groupValues?.get(1)?.lowercase()

/** 流式算文件 SHA-256（32KB 缓冲，不把 APK 读进内存）；读失败返回 null。 */
fun sha256OfFile(file: File): String? =
    try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    } catch (_: Exception) {
        null
    }

/**
 * Pure-ish gate: 落盘文件是否与 release 声明的 sha256 一致。
 * [expected] 为空（release 没写）→ true（可选字段，保持现状）；
 * 非空但算不出来 → false（fail closed：宁可让用户重下一次）。
 */
fun verifyApkSha256(file: File, expected: String?): Boolean {
    val want = expected?.trim()?.lowercase().orEmpty()
    if (want.isEmpty()) return true
    val got = sha256OfFile(file) ?: return false
    return got == want
}

/**
 * APK 的 versionCode；解析失败返回 null（调用方 fail-closed，绝不安装）。
 *
 * API 28+ 用 `longVersionCode`，26/27 用已废弃的 `versionCode` —— 与
 * [packageSigners] 同一处分叉（直接摸 28+ 的字段在 26/27 上是 NoSuchFieldError）。
 */
@Suppress("DEPRECATION")
fun readApkVersionCode(context: Context, apkFile: File): Long? =
    try {
        val info = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        if (info == null) {
            null
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    } catch (_: Throwable) {
        // Throwable：与签名门同理，26/27 上摸 API 28 字段抛的是 Error 不是 Exception。
        null
    }

/**
 * Pure: 安装门 —— 只有 `apk >= 当前` 才允许安装（UPD-2）。
 * 回退源原来指向正式版 latest，测试通道可能拿到**另一个版本**的 APK；
 * 签名门拦不住（同一个开发者签名），所以必须比 versionCode。
 * [currentVersionCode] <= 0（读不到当前版本）时不拦，交给签名门兜底。
 */
fun isInstallableUpdate(apkVersionCode: Long, currentVersionCode: Long): Boolean =
    currentVersionCode <= 0L || apkVersionCode >= currentVersionCode

/**
 * Fail-closed signature check for the Top-5 install gate (wiring task):
 * true only when [apkFile] parses AND its APK-content signers exactly match
 * the installed app's signers (`GET_SIGNING_CERTIFICATES` on API 28+,
 * `GET_SIGNATURES` on 26/27 — no reflection, R8-safe). Any failure (missing/
 * truncated file, unparsable package, no signers, signer mismatch — e.g. the
 * 1.0.40-ai key rotation vs a store build) returns false.
 * Intended call site (wiring task): `MainActivity.tryInstallUpdate` calls
 * this BEFORE the FileProvider launch — false means "show 签名不一致，需卸载重装
 * (会清空离线内容） and do NOT launch the installer"; true proceeds.
 */
fun isSameSignature(context: Context, apkFile: File): Boolean {
    try {
        if (!apkFile.exists() || apkFile.length() <= 0L) return false
        val pm = context.packageManager
        val apkSigners = packageSigners(pm, apkFile.absolutePath, archive = true)
        val appSigners = packageSigners(pm, context.packageName, archive = false)
        if (apkSigners.isNullOrEmpty() || appSigners.isNullOrEmpty()) return false
        return apkSigners.map { it.toCharsString() }.toSet() ==
            appSigners.map { it.toCharsString() }.toSet()
    } catch (_: Throwable) {
        // Throwable, not Exception: on API 26/27 a field added in API 28 throws
        // NoSuchFieldError (an Error), which used to escape this gate and crash
        // the caller's coroutine. The API branch below is the real fix; this
        // catch is the backstop so no install path can ever take the app down.
        return false
    }
}

/**
 * Signers of an APK archive ([archive] = true) or of the installed app.
 *
 * API split is mandatory: `PackageInfo.signingInfo` only exists on API 28+.
 * On 26/27 the field access throws `NoSuchFieldError` — not catchable as
 * `Exception` — so 8.0/8.1 users could never install an update (and crashed
 * on the way there). `GET_SIGNATURES` is the only door on those versions.
 */
@Suppress("DEPRECATION")
private fun packageSigners(
    pm: PackageManager,
    path: String,
    archive: Boolean
): Array<android.content.pm.Signature>? {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        val info = if (archive) {
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
        } ?: return null
        return info.signingInfo?.apkContentsSigners
    }
    val info = if (archive) {
        pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
    } else {
        pm.getPackageInfo(path, PackageManager.GET_SIGNATURES)
    } ?: return null
    return info.signatures
}

private fun downloadSingleApk(url: String, destFile: File, expectedBytes: Long): File {
    val part = partFileFor(destFile)
    try {
        part.parentFile?.mkdirs()
    } catch (_: Exception) {
        SilentFail.note("UpdateCheck.downloadSingleApk:530")
    }
    val existing = try {
        if (part.exists()) part.length() else 0L
    } catch (_: Exception) {
        0L
    }
    var offset = resumeOffsetFor(existing, expectedBytes)
    if (offset == 0L) {
        try {
            if (part.exists()) part.delete()
        } catch (_: Exception) {
            SilentFail.note("UpdateCheck.downloadSingleApk:542")
        }
    }
    var restarted = false
    while (true) {
        val builder = Request.Builder().url(url).get()
        if (offset > 0L) builder.header("Range", "bytes=$offset-")
        var retryFresh = false
        updateHttp.newCall(builder.build()).execute().use { res ->
            if (res.code == 416 && offset > 0L && !restarted) {
                try {
                    part.delete()
                } catch (_: Exception) {
                    SilentFail.note("UpdateCheck.downloadSingleApk:555")
                }
                offset = 0L
                restarted = true
                retryFresh = true
                return@use
            }
            if (!res.isSuccessful) {
                throw RuntimeException("Download failed: HTTP ${res.code} ${res.message}")
            }
            val body = res.body ?: throw RuntimeException("Download failed: empty file")
            if (offset > 0L && res.code == 200) {
                try {
                    part.delete()
                } catch (_: Exception) {
                    SilentFail.note("UpdateCheck.downloadSingleApk:570")
                }
                offset = 0L
            }
            val declared = body.contentLength()
            val expect = if (expectedBytes > 0L) expectedBytes
            else if (declared >= 0L) offset + declared else -1L
            var total = offset
            body.source().use { src ->
                java.io.FileOutputStream(part, offset > 0L).buffered().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = src.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        total += n
                    }
                    out.flush()
                }
            }
            if (total <= 0L) {
                try {
                    part.delete()
                } catch (_: Exception) {
                    SilentFail.note("UpdateCheck.downloadSingleApk:594")
                }
                throw RuntimeException("Download failed: empty file")
            }
            if (expect > 0L && total != expect) {
                throw RuntimeException("Download failed: incomplete file ($total/$expect bytes)")
            }
            destFile.parentFile?.mkdirs()
            if (!part.renameTo(destFile)) throw RuntimeException("Download failed: cannot finalize file")
        }
        if (!retryFresh) return destFile
    }
}
