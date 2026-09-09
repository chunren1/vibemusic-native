package com.cyk666.vibemusic

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener

/**
 * Scheme-routing DataSource: local download files (file:// URIs from
 * [OfflineStore.toLocalMediaItem]) are read straight from disk, everything
 * else flows through the rolling [CacheDataSource] pipeline unchanged.
 *
 * Why: [CacheDataSource]'s upstream is HTTP-only (DefaultHttpDataSource), so
 * a file:// URI misses the cache, falls through to the HTTP upstream, and
 * errors on the non-http(s) scheme — every downloaded file failed to play and
 * self-heal then deleted the good bytes ("download then gone"). Network bytes
 * were never at fault (direct GET of the stream URL returns valid audio/mpeg).
 *
 * Thread-safety: both delegates are constructed inside this wrapper's
 * constructor and never shared — one wrapper per [Factory.createDataSource]
 * call, because CacheDataSource instances are not safe to share across
 * wrappers/threads.
 */
enum class Route {
    FILE,
    STREAM,
}

/**
 * Pure routing rule: [Route.FILE] only for the "file" scheme
 * (case-insensitive); [Route.STREAM] for everything else, including
 * null/blank/http/https/content/ftp.
 */
fun routeScheme(scheme: String?): Route =
    if (scheme.equals("file", ignoreCase = true)) Route.FILE else Route.STREAM

class SchemeDataSource(
    cacheFactory: DataSource.Factory,
    fileFactory: DataSource.Factory = FileDataSource.Factory(),
) : DataSource {

    private val fileDelegate: DataSource = fileFactory.createDataSource()
    private val cacheDelegate: DataSource = cacheFactory.createDataSource()

    /** Delegate chosen by the most recent [open] call (null before first open). */
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        fileDelegate.addTransferListener(transferListener)
        cacheDelegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val target =
            if (routeScheme(dataSpec.uri.scheme) == Route.FILE) fileDelegate
            else cacheDelegate
        active = target
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(active) { "SchemeDataSource.read before open" }
            .read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }

    /** Factory: every [createDataSource] returns a fresh wrapper with fresh delegates. */
    class Factory(
        private val cacheFactory: DataSource.Factory,
        private val fileFactory: DataSource.Factory = FileDataSource.Factory(),
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            SchemeDataSource(cacheFactory, fileFactory)
    }
}
