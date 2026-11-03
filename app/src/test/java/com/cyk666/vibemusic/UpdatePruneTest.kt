package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-11-03 审计 P2：`cacheDir/updates` 只增不删，每发一版留一份 ~4MB APK。
 *
 * 口径：只清本 App 自己的包名（`vibemusic-*.apk` / `*.apk.part`），**保留**本次要装
 * 的那份以及它的 `.part`（拆掉 `.part` 等于把断点续传废了）。
 */
class UpdatePruneTest {

    @Test
    fun keepsCurrentApkAndItsPartDeletesStaleSiblings() {
        val dir = java.nio.file.Files.createTempDirectory("updates-prune").toFile()
        try {
            val keep = File(dir, "vibemusic-2.0.24-ai.apk").apply { writeBytes(ByteArray(4)) }
            val keepPart = File(dir, "vibemusic-2.0.24-ai.apk.part").apply { writeBytes(ByteArray(4)) }
            val staleApk = File(dir, "vibemusic-2.0.23-ai.apk").apply { writeBytes(ByteArray(4)) }
            val stalePart = File(dir, "vibemusic-2.0.22-ai.apk.part").apply { writeBytes(ByteArray(4)) }
            // 非本 App 命名/非 APK：不在清理范围（宁可留垃圾也不误删）。
            val foreign = File(dir, "notes.txt").apply { writeText("keep me") }
            val otherApk = File(dir, "other.apk").apply { writeBytes(ByteArray(4)) }

            assertEquals(2, pruneStaleApks(dir, keep))

            assertTrue(keep.exists())
            assertTrue(keepPart.exists())
            assertFalse(staleApk.exists())
            assertFalse(stalePart.exists())
            assertTrue(foreign.exists())
            assertTrue(otherApk.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun emptyOrMissingDirIsHarmless() {
        val dir = java.nio.file.Files.createTempDirectory("updates-empty").toFile()
        try {
            assertEquals(0, pruneStaleApks(dir, File(dir, "vibemusic-x.apk")))
            assertEquals(0, pruneStaleApks(File(dir, "nonexistent"), File(dir, "vibemusic-x.apk")))
        } finally {
            dir.deleteRecursively()
        }
    }
}
