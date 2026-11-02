package com.cyk666.vibemusic

import java.io.File

/**
 * 本地下载的「扫目录 → 解析 → 去重 → 清理重复记录」（原来整块在
 * `OfflineStore.listDownloads` 里；那个文件靠棘轮卡 314 纯 LOC，所以把逻辑
 * 搬到这里，`OfflineStore` 只留一行委托。IO 全在这一层，界面层不碰 File。
 *
 * 为什么会有重复：`.meta` 文件名是 `sanitize(platform)_sanitize(sourceId)`，
 * 但文件**内容**里的 platform/sourceId 可能与文件名对不上（旧命名规则残留、
 * 手工改过、异常写入），于是同一个 `playKey` 能落在两个不同名字的 `.meta` 上。
 * 这类重复在列表里表现为「同名两条」，多选时点其中一条还会因为同一个 key
 * 被点掉而互相干扰。
 *
 * 口径（用户已拍板）：重复记录只留 `downloadedAt` 最新的一条，其余 `.meta`
 * **文件一并删掉**（只从列表隐藏的话，下次进页面又会「复活」）。音频文件按
 * `offlineBaseName` 由 key 决定，重复记录本来就指向同一份音频，所以只删多余
 * 的 `.meta`，不动音频。
 */

/** 一条磁盘上的下载记录：meta 内容 + 它自己的 `.meta` 文件名（清理时要按名字删）。 */
data class OfflineDownloadEntry(val meta: OfflineMeta, val metaFileName: String)

/**
 * 纯函数：每个 `playKey` 只留 `downloadedAt` 最新的一条。
 * 时间并列时保留先出现的（`listFiles` 顺序稳定），保证结果可复现。
 */
fun pickNewestDownloads(entries: List<OfflineDownloadEntry>): List<OfflineDownloadEntry> {
    val kept = LinkedHashMap<String, OfflineDownloadEntry>()
    for (e in entries) {
        val key = playKey(e.meta.platform, e.meta.sourceId)
        val cur = kept[key]
        if (cur == null || e.meta.downloadedAt > cur.meta.downloadedAt) kept[key] = e
    }
    return kept.values.toList()
}

/** 纯函数：被 [pickNewestDownloads] 淘汰的那些 `.meta` 文件名（调用方据此删文件）。 */
fun duplicateMetaFileNames(entries: List<OfflineDownloadEntry>): List<String> {
    val keep = pickNewestDownloads(entries).map { it.metaFileName }.toSet()
    return entries.map { it.metaFileName }.filter { it !in keep }
}

/**
 * IO：扫目录 → 解析 → 删掉重复记录的 `.meta` → 去重后按下载时间倒序。
 * 任何一步失败都退化成「少几条」而不是崩，与旧 `listDownloads` 的容错一致。
 */
fun readDownloads(dir: File): List<OfflineMeta> {
    val files = try {
        if (!dir.isDirectory) return emptyList()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".meta") }.orEmpty()
    } catch (_: Exception) {
        return emptyList()
    }
    val entries = files.mapNotNull { f ->
        try {
            OfflineMeta.fromJson(f.readText())?.let { OfflineDownloadEntry(it, f.name) }
        } catch (_: Exception) {
            null
        }
    }.filter { it.meta.sourceId.isNotBlank() }
    for (name in duplicateMetaFileNames(entries)) {
        try {
            File(dir, name).delete()
        } catch (_: Exception) {
            SilentFail.note("OfflineDownloads.readDownloads:66")
        }
    }
    return pickNewestDownloads(entries)
        .map { it.meta }
        .sortedByDescending { it.downloadedAt }
}
