package com.cyk666.vibemusic

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Records which delegate the wrapper consults; serves canned bytes. */
private class SpyDataSource(private val bytes: ByteArray = "spy".toByteArray()) : DataSource {
    var opened: DataSpec? = null
    var openCount = 0
    var closed = false
    private var pos = 0

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        opened = dataSpec
        openCount++
        pos = dataSpec.position.toInt().coerceIn(0, bytes.size)
        return (bytes.size - pos).toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (pos >= bytes.size) return -1
        val n = minOf(length, bytes.size - pos)
        System.arraycopy(bytes, pos, buffer, offset, n)
        pos += n
        return n
    }

    override fun getUri(): Uri? = opened?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        mapOf("X-Spy" to listOf("yes"))

    override fun close() {
        closed = true
    }
}

private class SpyFactory(val spy: SpyDataSource) : DataSource.Factory {
    override fun createDataSource(): DataSource = spy
}

private fun readAll(ds: DataSource): ByteArray {
    val out = mutableListOf<Byte>()
    val buf = ByteArray(256)
    while (true) {
        val n = ds.read(buf, 0, buf.size)
        if (n == -1) break
        for (i in 0 until n) out.add(buf[i])
    }
    return out.toByteArray()
}

@RunWith(RobolectricTestRunner::class)
class SchemeDataSourceTest {

    @Test
    fun routeScheme_fileIsFileRegardlessOfCase() {
        assertEquals(Route.FILE, routeScheme("file"))
        assertEquals(Route.FILE, routeScheme("FILE"))
        assertEquals(Route.FILE, routeScheme("File"))
    }

    @Test
    fun routeScheme_everythingElseIsStream() {
        assertEquals(Route.STREAM, routeScheme("http"))
        assertEquals(Route.STREAM, routeScheme("https"))
        assertEquals(Route.STREAM, routeScheme("HTTP"))
        assertEquals(Route.STREAM, routeScheme("content"))
        assertEquals(Route.STREAM, routeScheme("ftp"))
        assertEquals(Route.STREAM, routeScheme(null))
        assertEquals(Route.STREAM, routeScheme(""))
        assertEquals(Route.STREAM, routeScheme("   "))
    }

    @Test
    fun open_fileUriReadsRealTempFileBytes() {
        val tmp = File.createTempFile("scheme-test", ".mp3")
        try {
            val payload = byteArrayOf(0x49, 0x44, 0x33, 1, 2, 3, 4, 5)
            tmp.writeBytes(payload)
            val cache = SpyDataSource()
            val wrapper = SchemeDataSource(SpyFactory(cache), FileDataSource.Factory())
            val spec = DataSpec(Uri.fromFile(tmp))
            wrapper.open(spec)
            assertArrayEquals(payload, readAll(wrapper))
            wrapper.close()
            // Cache delegate must never be touched for file:// URIs.
            assertEquals(0, cache.openCount)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun open_httpUriConsultsCacheDelegateOnly() {
        val cache = SpyDataSource("cached-bytes".toByteArray())
        val file = SpyDataSource()
        val wrapper = SchemeDataSource(SpyFactory(cache), SpyFactory(file))
        val uri = Uri.parse("https://vibe.cyk666.top/api/songs/stream?sourceId=1")
        wrapper.open(DataSpec(uri))
        assertEquals(1, cache.openCount)
        assertEquals(uri, cache.opened?.uri)
        assertEquals(0, file.openCount)
        assertArrayEquals("cached-bytes".toByteArray(), readAll(wrapper))
        assertEquals(mapOf("X-Spy" to listOf("yes")), wrapper.responseHeaders)
        assertEquals(uri, wrapper.uri)
        wrapper.close()
        assertTrue(cache.closed)
    }

    @Test
    fun open_nullSchemeFallsBackToCache() {
        val cache = SpyDataSource()
        val file = SpyDataSource()
        val wrapper = SchemeDataSource(SpyFactory(cache), SpyFactory(file))
        wrapper.open(DataSpec(Uri.parse("vibe.cyk666.top/stream")))
        assertEquals(1, cache.openCount)
        assertEquals(0, file.openCount)
        wrapper.close()
    }

    @Test
    fun factory_createsIndependentWrappers() {
        val cacheSpy = SpyDataSource()
        val fileSpy = SpyDataSource()
        val factory = SchemeDataSource.Factory(SpyFactory(cacheSpy), SpyFactory(fileSpy))
        assertNotSame(factory.createDataSource(), factory.createDataSource())
    }

    @Test
    fun close_resetsActiveDelegate() {
        val cache = SpyDataSource("abc".toByteArray())
        val wrapper = SchemeDataSource(SpyFactory(cache), SpyFactory(SpyDataSource()))
        wrapper.open(DataSpec(Uri.parse("https://example.com/a.mp3")))
        wrapper.close()
        assertTrue(cache.closed)
        // Re-open after close routes fresh (re-entrancy per open call).
        wrapper.open(DataSpec(Uri.parse("https://example.com/b.mp3")))
        assertEquals(2, cache.openCount)
        wrapper.close()
    }
}
