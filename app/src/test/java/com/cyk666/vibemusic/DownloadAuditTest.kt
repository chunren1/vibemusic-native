package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadAuditTest {

    private fun meta(id: String) = OfflineMeta(
        sourceId = id,
        name = "n$id",
        artist = "a",
        album = "",
        coverUrl = "",
        durationSec = 100,
        platform = "netease",
        downloadedAt = 1L
    )

    private fun probe(id: String, exists: Boolean, size: Long, head: ByteArray) =
        MetaLike(meta(id), exists, size, head)

    private val goodHead = byteArrayOf(0x49, 0x44, 0x33, 0x04)
    private val jsonHead = """{"co""".toByteArray()

    @Test
    fun keepsValidEntry() {
        val kept = auditDownloads(listOf(probe("1", true, MIN_AUDIO_BYTES.toLong(), goodHead)))
        assertEquals(listOf("1"), kept.map { it.meta.sourceId })
    }

    @Test
    fun dropsMissingFile() {
        val kept = auditDownloads(listOf(probe("1", false, MIN_AUDIO_BYTES.toLong(), goodHead)))
        assertTrue(kept.isEmpty())
    }

    @Test
    fun dropsUndersizedFile() {
        val kept = auditDownloads(
            listOf(probe("1", true, (MIN_AUDIO_BYTES - 1).toLong(), goodHead))
        )
        assertTrue(kept.isEmpty())
    }

    @Test
    fun dropsBadHead() {
        val kept = auditDownloads(
            listOf(probe("1", true, (MIN_AUDIO_BYTES + 500).toLong(), jsonHead))
        )
        assertTrue(kept.isEmpty())
    }

    @Test
    fun dropsShortHead() {
        val kept = auditDownloads(
            listOf(probe("1", true, MIN_AUDIO_BYTES.toLong(), byteArrayOf(0x49, 0x44)))
        )
        assertTrue(kept.isEmpty())
    }

    @Test
    fun emptyInEmptyOut() {
        assertTrue(auditDownloads(emptyList()).isEmpty())
    }

    @Test
    fun sizeFloorBoundary() {
        val entries = listOf(
            probe("below", true, (MIN_AUDIO_BYTES - 1).toLong(), goodHead),
            probe("exact", true, MIN_AUDIO_BYTES.toLong(), goodHead)
        )
        assertEquals(listOf("exact"), auditDownloads(entries).map { it.meta.sourceId })
    }

    @Test
    fun mixedKeepsOnlyValid() {
        val entries = listOf(
            probe("good", true, MIN_AUDIO_BYTES.toLong(), goodHead),
            probe("gone", false, MIN_AUDIO_BYTES.toLong(), goodHead),
            probe("small", true, 1024L, goodHead),
            probe("poison", true, (MIN_AUDIO_BYTES + 9).toLong(), jsonHead)
        )
        assertEquals(listOf("good"), auditDownloads(entries).map { it.meta.sourceId })
    }
}
