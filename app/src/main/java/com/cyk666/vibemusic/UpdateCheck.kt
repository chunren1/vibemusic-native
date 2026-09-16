package com.cyk666.vibemusic

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * Release 列表端点（测试通道用）：GitHub 按新建倒序返回，取首个带 .apk 的条目
 * （含 prerelease）；Gitee 同理。正式通道继续走上面的 .../latest（自动排除 prerelease）。
 */
const val UPDATE_RELEASES_URL =
    "https://api.github.com/repos/chunren1/vibemusic-native/releases?per_page=10"
const val UPDATE_GITEE_RELEASES_URL =
    "https://gitee.com/api/v5/repos/green-leavesQAQ/vibemusic-native/releases?per_page=10"

/** Minimum gap between automatic update checks. */
const val UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

data class GithubRelease(
    val tag: String,
    val name: String,
    val body: String,
    val apkUrl: String,
    /** GitHub/Gitee releases API 的 prerelease 标记；正式版为 false。 */
    val prerelease: Boolean = false
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
        apkUrl = apkUrl,
        prerelease = root.optBoolean("prerelease", false)
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

private fun pickApkUrlFromAsset(o: JSONObject): String? {
    val candidates = listOf(
        o.optString("browser_download_url"),
        o.optString("download_url"),
        o.optString("url")
    )
    return candidates.firstOrNull { it.isNotBlank() && it.lowercase().endsWith(".apk") }
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
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val tag = o.optString("tag_name")
        if (tag.isBlank()) continue
        val assets = o.optJSONArray("assets") ?: continue
        var apkUrl = ""
        for (j in 0 until assets.length()) {
            val a = assets.optJSONObject(j) ?: continue
            val url = pickApkUrlFromAsset(a)
            if (url != null) {
                apkUrl = url
                break
            }
        }
        if (apkUrl.isBlank()) continue
        return GithubRelease(
            tag = tag,
            name = o.optString("name"),
            body = o.optString("body"),
            apkUrl = apkUrl,
            prerelease = o.optBoolean("prerelease", false)
        )
    }
    return null
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
 * Suspend: fetch newest release INCLUDING prereleases (测试通道用),
 * Gitee first, GitHub fallback on ANY Gitee failure. Throws only when both fail.
 * 正式通道继续用 [fetchLatestRelease]（.../latest，天然排除 prerelease）。
 */
suspend fun fetchLatestReleaseIncludingPrerelease(): GithubRelease {
    var giteeError: Exception? = null
    try {
        val body = fetchReleaseListJson(UPDATE_GITEE_RELEASES_URL, null)
        val rel = parseReleaseList(body)
        if (rel != null) {
            android.util.Log.d("UpdateCheck", "update source=gitee tag=${rel.tag} prerelease=${rel.prerelease}")
            return rel
        }
        giteeError = RuntimeException("gitee release list has no .apk entry")
    } catch (e: Exception) {
        giteeError = e
    }
    try {
        val body = fetchReleaseListJson(UPDATE_RELEASES_URL, "application/vnd.github+json")
        val rel = parseReleaseList(body)
        if (rel != null) {
            android.util.Log.d("UpdateCheck", "update source=github tag=${rel.tag} prerelease=${rel.prerelease} (gitee failed: ${giteeError?.message})")
            return rel
        }
        throw RuntimeException("github release list has no .apk entry")
    } catch (e: Exception) {
        throw RuntimeException("Update check failed: gitee (${giteeError?.message}) + github (${e.message})")
    }
}

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
 * Size gate: when [expectedBytes] > 0 (release-metadata size, once the
 * wiring task threads it through) the landed bytes must match exactly;
 * otherwise the response `Content-Length` (when the server sends one) must
 * match. Mismatch / empty / failed `renameTo` all throw and delete the
 * `.part` — [destFile] is only ever replaced by a fully-validated file.
 *
 * Source fallback (mirrors [fetchLatestRelease] Gitee→GitHub): [apkUrl] is
 * tried first, then [fallbackApkUrl] on ANY first-source failure (HTTP /
 * truncated / empty). Pass the other source's release `apkUrl` as
 * [fallbackApkUrl] so Gitee attachment wobble falls back to GitHub.
 * Intended call-site change (wiring task, MainActivity NOT touched here):
 * `MainActivity.startUpdateDownload` keeps calling this with
 * `rel.apkUrl`, adds the other-source `apkUrl` as fallback, and replaces
 * its `!apk.exists() || apk.length() <= 0L` reuse gate with
 * `!isDownloadComplete(apk, expectedBytes)`.
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
 * Pure: reuse gate for a landed apk. True only when the file exists,
 * non-empty, and — when [expectedBytes] > 0 — byte-identical in size.
 * Unknown size (<= 0) keeps the legacy non-empty gate (weak: prefer passing
 * the release-metadata size once available); pre-fix truncated files of
 * unknown size should be deleted once by the wiring task.
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
 * Fail-closed signature check for the Top-5 install gate (wiring task):
 * true only when [apkFile] parses AND its APK-content signers exactly match
 * the installed app's signers (`GET_SIGNING_CERTIFICATES` compare — no
 * reflection, R8-safe). Any failure (missing/truncated file, unparsable
 * package, pre-API-28 no signingInfo, signer mismatch — e.g. the 1.0.40-ai
 * key rotation vs a store build) returns false.
 * Intended call site (wiring task): `MainActivity.tryInstallUpdate` calls
 * this BEFORE the FileProvider launch — false means "show 签名不一致，需卸载重装
 * (会清空离线内容） and do NOT launch the installer"; true proceeds.
 */
fun isSameSignature(context: Context, apkFile: File): Boolean {
    try {
        if (!apkFile.exists() || apkFile.length() <= 0L) return false
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(
            apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES
        ) ?: return false
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(
            context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
        ) ?: return false
        val apkSigners = archive.signingInfo?.apkContentsSigners
        val appSigners = installed.signingInfo?.apkContentsSigners
        if (apkSigners.isNullOrEmpty() || appSigners.isNullOrEmpty()) return false
        return apkSigners.map { it.toCharsString() }.toSet() ==
            appSigners.map { it.toCharsString() }.toSet()
    } catch (_: Exception) {
        return false
    }
}

private fun downloadSingleApk(url: String, destFile: File, expectedBytes: Long): File {
    val req = Request.Builder().url(url).get().build()
    updateHttp.newCall(req).execute().use { res ->
        if (!res.isSuccessful) {
            throw RuntimeException("Download failed: HTTP ${res.code} ${res.message}")
        }
        val body = res.body ?: throw RuntimeException("Download failed: empty file")
        val declared = body.contentLength()
        val expect = if (expectedBytes > 0L) expectedBytes else declared
        val part = partFileFor(destFile)
        try {
            part.parentFile?.mkdirs()
            try {
                if (part.exists()) part.delete()
            } catch (_: Exception) {
            }
            var total = 0L
            body.source().use { src ->
                part.outputStream().buffered().use { out ->
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
            if (total <= 0L) throw RuntimeException("Download failed: empty file")
            if (expect > 0L && total != expect) {
                throw RuntimeException("Download failed: incomplete file ($total/$expect bytes)")
            }
            destFile.parentFile?.mkdirs()
            if (!part.renameTo(destFile)) throw RuntimeException("Download failed: cannot finalize file")
        } catch (e: Exception) {
            try {
                part.delete()
            } catch (_: Exception) {
            }
            throw e
        }
    }
    return destFile
}
