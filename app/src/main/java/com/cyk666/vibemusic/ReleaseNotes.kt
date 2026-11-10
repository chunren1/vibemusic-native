package com.cyk666.vibemusic

/**
 * 发布说明的人话化处理。
 *
 * 为什么需要：CI 生成的 release body 是给机器读的——首行写「VibeMusic 原生版 vX
 * （CI 自动构建签名）」，后面挂着 `APK sha256: <hex>` 供更新器做内容校验。
 * 直接摊到对话框里，用户看到的是重复的版本号加一长串哈希。
 *
 * 清洗完全走纯文本，不引入 Markdown 渲染依赖。
 */
fun releaseNotesText(body: String): String {
    if (body.isBlank()) return ""
    return body
        .replace(Regex("(?s)<!--.*?-->"), "")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        // 机器用的校验字段与构建样板，不进用户视野
        .filterNot { it.contains("APK sha256", ignoreCase = true) }
        .filterNot { it.startsWith("VibeMusic 原生版") }
        .filterNot { it.contains("CI 自动构建签名") }
        .map { line ->
            when {
                line.startsWith("### ") -> line.removePrefix("### ")
                line.startsWith("## ") -> line.removePrefix("## ")
                line.startsWith("# ") -> line.removePrefix("# ")
                line.startsWith("- ") -> "• " + line.removePrefix("- ")
                line.startsWith("* ") -> "• " + line.removePrefix("* ")
                else -> line
            }
        }
        .joinToString("\n")
}

/** `v2.0.34-ai` → `2.0.34-ai`：对话框标题没必要带 git tag 的 v 前缀。 */
fun displayVersion(tag: String): String = tag.removePrefix("v")
