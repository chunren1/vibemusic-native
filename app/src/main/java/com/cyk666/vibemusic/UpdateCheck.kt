package com.cyk666.vibemusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

const val UPDATE_REPO_OWNER = "chunren1"
const val UPDATE_REPO_NAME = "vibemusic-native"
const val UPDATE_LATEST_URL =
    "https://api.github.com/repos/chunren1/vibemusic-native/releases/latest"

/** Gitee mirror (primary update source; GitHub is the fallback). */
const val UPDATE_GITEE_OWNER = "green-leavesQAQ"
const val UPDATE_GITEE_REPO = "vibemusic-native"
const val UPDATE_GITEE_LATEST_URL =
    "https://gitee.com/api/v5/repos/green-leavesQAQ/vibemusic-native/releases/latest"

/** Minimum gap between automatic update checks. */
const val UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

data class GithubRelease(
    val tag: String,
    val name: String,
    val body: String,
    val apkUrl: String
)

private fun numericCore(version: String): List<Int> {
    val stripped = version.trim().removePrefix("v").removePrefix("V")
    val core = stripped.substringBefore("-").substringBefore("+")
    if (core.isBlank()) return emptyList()
    return core.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
}

/**
 * Pure: true when [latestTag] is strictly newer than [current].
 * Leading v/V stripped; dot-separated numeric core compared with missing
 * parts as 0; suffixes (-ai etc.) ignored for ordering, so equal cores with
 * differing suffixes are NOT newer (avoids reinstall loops).
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
    return false
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
    var apkUrl = ""
    for (i in 0 until assets.length()) {
        val o = assets.optJSONObject(i) ?: continue
        val candidates = listOf(
            o.optString("browser_download_url"),
            o.optString("download_url"),
            o.optString("url")
        )
        val url = candidates.firstOrNull { it.isNotBlank() && it.lowercase().endsWith(".apk") }
        if (url != null) {
            apkUrl = url
            break
        }
    }
    if (apkUrl.isBlank()) throw RuntimeException("Parse release failed: $tag has no .apk asset")
    return GithubRelease(
        tag = tag,
        name = root.optString("name"),
        body = root.optString("body"),
        apkUrl = apkUrl
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
 * Pure: pick the winning update source. Gitee is primary, GitHub is the
 * fallback: the FIRST successful source wins, so a stale-but-reachable
 * Gitee release is used as-is (no cross-source version merge — keeps the
 * check to one round trip and the behavior predictable). Both failed →
 * null (caller maps to CHECK_FAILED).
 */
fun resolveUpdateSource(giteeOk: Boolean, githubOk: Boolean): UpdateSource? =
    if (giteeOk) UpdateSource.GITEE else if (githubOk) UpdateSource.GITHUB else null

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
 * Suspend: fetch latest release, Gitee first, GitHub fallback on ANY
 * Gitee failure (network / non-200 / unparseable — silent). Throws only
 * when both sources fail (caller stays silent / snackbars once).
 */
suspend fun fetchLatestRelease(): GithubRelease {
    var giteeError: Exception? = null
    try {
        val rel = fetchReleaseJson(UPDATE_GITEE_LATEST_URL, null)
        android.util.Log.d("UpdateCheck", "update source=gitee tag=${rel.tag}")
        return rel
    } catch (e: Exception) {
        giteeError = e
    }
    try {
        val rel = fetchReleaseJson(UPDATE_LATEST_URL, "application/vnd.github+json")
        android.util.Log.d("UpdateCheck", "update source=github tag=${rel.tag} (gitee failed: ${giteeError?.message})")
        return rel
    } catch (e: Exception) {
        throw RuntimeException("Update check failed: gitee (${giteeError?.message}) + github (${e.message})")
    }
}

/** Suspend: download the release apk to [destFile] (throws on HTTP/empty; caller snackbars once). */
suspend fun downloadApk(apkUrl: String, destFile: File): File = withContext(Dispatchers.IO) {
    val req = Request.Builder().url(apkUrl).get().build()
    updateHttp.newCall(req).execute().use { res ->
        if (!res.isSuccessful) {
            throw RuntimeException("Download failed: HTTP ${res.code} ${res.message}")
        }
        val bytes = res.body?.bytes()
        if (bytes == null || bytes.isEmpty()) throw RuntimeException("Download failed: empty file")
        destFile.parentFile?.mkdirs()
        destFile.writeBytes(bytes)
    }
    destFile
}
