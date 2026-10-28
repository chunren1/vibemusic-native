package com.cyk666.vibemusic

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Scheme-routing DataSource: schemes only a local DataSource can read
 * (`file://` downloads from [OfflineStore.toLocalMediaItem], `content://`
 * audio handed over by another app) are read straight from the resolver/disk,
 * everything else flows through the rolling [CacheDataSource] pipeline.
 *
 * Why: [CacheDataSource]'s upstream is HTTP-only (DefaultHttpDataSource), so
 * a `file://` URI misses the cache, falls through to the HTTP upstream, and
 * errors on the non-http(s) scheme — every downloaded file failed to play and
 * self-heal then deleted the good bytes ("download then gone"). Network bytes
 * were never at fault (direct GET of the stream URL returns valid audio/mpeg).
 *
 * 2026-10-28: `content://` had the same defect. WeChat's「用其他应用打开」hands
 * us a `content://com.tencent.mm.external.fileprovider/...` URI; it was routed
 * to [Route.STREAM], hit the HTTP-only upstream, and failed 100% of the time
 * with「这个音频文件播放失败（可能已移动或没有读取权限）」— the file was fine,
 * the scheme was wrong.
 *
 * Thread-safety: both delegates are constructed inside this wrapper's
 * constructor and never shared — one wrapper per [Factory.createDataSource]
 * call, because CacheDataSource instances are not safe to share across
 * wrappers/threads.
 */
enum class Route {
    /** file:// or content:// — must be read by a local/resolver DataSource. */
    LOCAL,
    STREAM,
}

/**
 * Pure routing rule: [Route.LOCAL] for `file` and `content` (case-insensitive);
 * [Route.STREAM] for everything else, including null/blank/http/https/ftp.
 */
fun routeScheme(scheme: String?): Route = when {
    scheme.equals("file", ignoreCase = true) -> Route.LOCAL
    scheme.equals("content", ignoreCase = true) -> Route.LOCAL
    else -> Route.STREAM
}

class SchemeDataSource(
    cacheFactory: DataSource.Factory,
    /**
     * Handles both local schemes. Production passes
     * `DefaultDataSource.Factory(context)` (file → FileDataSource,
     * content → ContentDataSource); tests pass a spy.
     *
     * 刻意不给默认值：默认 FileDataSource 会让 content:// 再次静默失败，
     * 而失败表现是「用户的文件坏了」，查起来极贵。
     */
    localFactory: DataSource.Factory,
) : DataSource {

    private val localDelegate: DataSource = localFactory.createDataSource()
    private val cacheDelegate: DataSource = cacheFactory.createDataSource()

    /** Delegate chosen by the most recent [open] call (null before first open). */
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        localDelegate.addTransferListener(transferListener)
        cacheDelegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val target =
            if (routeScheme(dataSpec.uri.scheme) == Route.LOCAL) localDelegate
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
        private val localFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            SchemeDataSource(cacheFactory, localFactory)
    }
}
