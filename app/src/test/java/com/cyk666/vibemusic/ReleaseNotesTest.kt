package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 发布说明的人话化：用户只该看到「改了什么」，不该看到哈希和构建样板。
 */
class ReleaseNotesTest {

    @Test
    fun stripsHiddenSha256Comment() {
        val body = """
            ## 更新内容
            - 修复登录入口崩溃

            <!-- APK sha256: 7fa460dd727f6cf7941dd55f800273846a3b25952121f54a79d5d408837685ce -->
        """.trimIndent()

        val out = releaseNotesText(body)

        assertFalse("哈希不能出现在正文里", out.contains("7fa460dd"))
        assertFalse(out.contains("sha256", ignoreCase = true))
        assertTrue(out.contains("修复登录入口崩溃"))
    }

    @Test
    fun dropsBuildBoilerplateAndDuplicateVersionLine() {
        val body = "VibeMusic 原生版 v2.0.34-ai（CI 自动构建签名）\n\nAPK sha256: abc"

        assertEquals("", releaseNotesText(body))
    }

    @Test
    fun convertsMarkdownToPlainList() {
        val body = "## 更新内容\n- 第一项\n- 第二项"

        assertEquals("更新内容\n• 第一项\n• 第二项", releaseNotesText(body))
    }

    @Test
    fun keepsBodyWhenAlreadyClean() {
        assertEquals("修复了若干问题", releaseNotesText("修复了若干问题"))
    }

    @Test
    fun blankBodyYieldsEmpty() {
        assertEquals("", releaseNotesText(""))
        assertEquals("", releaseNotesText("   \n  "))
    }

    @Test
    fun displayVersionDropsGitPrefix() {
        assertEquals("2.0.34-ai", displayVersion("v2.0.34-ai"))
        assertEquals("2.0.35-ai-beta.1", displayVersion("v2.0.35-ai-beta.1"))
        assertEquals("2.0.34-ai", displayVersion("2.0.34-ai"))
    }
}
