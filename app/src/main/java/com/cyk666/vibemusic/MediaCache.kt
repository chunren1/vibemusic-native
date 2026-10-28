package com.cyk666.vibemusic

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * 离线点播要求的最小"连续可读前缀"（PB-4）。
 *
 * 1MB ≈ 128kbps 下 65 秒：预热只暖 [PlaybackService.PREFETCH_WARM_BYTES]（256KB），
 * 不会再被当成"可离线播"；真播过一遍的歌缓存的是整首（通常数 MB），照样通过。
 *
 * 为什么不是"整首都在缓存里"：SimpleCache 不落 EOF 标记，无法区分"256KB 的完整
 * 小文件"和"5MB 文件的前 256KB"，所以只能取阈值 + 无空洞这个下界。
 * 残留局限：只播了 1-2 分钟的长歌仍可能过门、播到空洞才停 —— 但比 4 秒就炸好，
 * 且文案（未缓存完整）与真实情况一致。
 */
const val OFFLINE_MIN_PREFIX_BYTES = 1024L * 1024L

/** 一个缓存区间的纯描述（把 SimpleCache 的 CacheSpan 抽出来，便于单测前缀算法）。 */
data class CacheSpanRange(val position: Long, val length: Long, val isHole: Boolean = false)

/**
 * Pure: 从 0 开始连续可读的字节数。
 *
 * 区间按 position 排序后累加，遇到空洞 span 或 position 跳到 end 之后立即停 ——
 * 结果就是"能从头一路读到哪里"。空洞之后的字节再多也不算（离线读不过去）。
 */
fun contiguousPrefixBytes(spans: List<CacheSpanRange>): Long {
    var end = 0L
    for (span in spans.sortedBy { it.position }) {
        if (span.isHole) break
        if (span.position > end) break
        end = maxOf(end, span.position + span.length)
    }
    return end.coerceAtLeast(0L)
}

/**
 * Rolling playback cache (smooth replay/seek only — NOT the offline store).
 * Small LRU so replay/seek within recent songs is instant; user-driven
 * downloads live in filesDir/offline (see OfflineStore) and are never evicted.
 *
 * Single SimpleCache instance held here (NOT per-service) to avoid lock
 * contention on the cache index. Always use [get] — never `new SimpleCache`
 * anywhere else.
 */
object MediaCache {
    const val DIR_NAME = "media"

    /** 150MB rolling LRU budget (replay/seek smoothing only — see OfflineStoreTest). */
    const val MAX_BYTES: Long = 150L * 1024L * 1024L

    @Volatile
    private var instance: SimpleCache? = null
    /** Thread-safe singleton accessor. Uses applicationContext (no activity leak). */
    @Synchronized
    fun get(context: Context): SimpleCache {
        val existing = instance
        if (existing != null) return existing
        val app = context.applicationContext
        val created = SimpleCache(
            File(app.cacheDir, DIR_NAME),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            ExoDatabaseProvider(app)
        )
        instance = created
        return created
    }

    /**
     * CacheDataSource factory whose singleton resolution is deferred to
     * [DataSource.Factory.createDataSource] time. ExoPlayer invokes that on
     * its internal loader threads (never Main), so building the player with
     * this factory keeps the [get] disk IO (dir create + DB open) off Main
     * even on first tap / service recreate. Config is byte-identical to the
     * previous eager wiring (write-through [CacheDataSink] +
     * `FLAG_IGNORE_CACHE_ON_ERROR`); eviction semantics unchanged.
     * Pair with an IO warm-up (`serviceIoScope.launch { get(app) }`) so the
     * DB open doesn't stall the first playback load.
     */
    fun cachedDataSourceFactory(
        context: Context,
        upstream: DataSource.Factory
    ): DataSource.Factory = LazyCacheDataSourceFactory(context.applicationContext, upstream)

    private class LazyCacheDataSourceFactory(
        private val appContext: Context,
        private val upstream: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val cache = get(appContext)
            return CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                .setCacheWriteDataSinkFactory(
                    CacheDataSink.Factory()
                        .setCache(cache)
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
        }
    }

    /**
     * Stable cache key for a song: the stream URL. Same song → same key across
     * calls (pinned by CachePolicyTest); different sourceId → different key.
     * Set as MediaItem.customCacheKey so CacheDataSource reads/writes one entry.
     */
    fun cacheKey(song: Song): String = song.streamUrl()

    /** Same metadata as [Song.toMediaItem] but tagged with the stable cache key. */
    fun Song.toCachedMediaItem(): MediaItem = toMediaItem().buildUpon()
        .setCustomCacheKey(cacheKey(this))
        .build()

    /** Bytes already on device for [url] (0 when nothing cached / on any error). */
    fun cachedBytes(context: Context, url: String): Long {
        if (url.isBlank()) return 0L
        return try {
            get(context).getCachedBytes(url, 0L, Long.MAX_VALUE).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Prefetch warm-up: pull head bytes of [song]'s stream through the cache
     * pipeline so the next-track switch starts from warm bytes. BLOCKING
     * network+disk I/O — call ONLY on Dispatchers.IO. Never throws.
     */
    fun warmUp(
        context: Context,
        song: Song,
        upstream: DataSource.Factory,
        maxBytes: Long = 256L * 1024L
    ): Long {
        if (song.sourceId.isBlank() || song.sourceId.startsWith("local:")) return 0L
        if (maxBytes <= 0L) return 0L
        return try {
            val app = context.applicationContext ?: context
            val url = song.streamUrl()
            if (get(app).getCachedBytes(url, 0L, maxBytes).coerceAtLeast(0L) >= maxBytes) return 0L
            val source = cachedDataSourceFactory(app, upstream).createDataSource()
            val spec = DataSpec(android.net.Uri.parse(url))
            var total = 0L
            val buf = ByteArray(32 * 1024)
            try {
                if (source.open(spec) < 0) return 0L
                while (total < maxBytes) {
                    val n = try {
                        source.read(buf, 0, buf.size)
                    } catch (_: Exception) {
                        break
                    }
                    if (n <= 0) break
                    total += n
                }
            } catch (_: Exception) {
                SilentFail.note("MediaCache.warmUp:134")
            } finally {
                try {
                    source.close()
                } catch (_: Exception) {
                    SilentFail.note("MediaCache.warmUp:139")
                }
            }
            total.coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    /** True once any byte of [url] is on device (drives the 已缓存 badge). */
    fun isCached(context: Context, url: String): Boolean = cachedBytes(context, url) > 0L

    /**
     * 从 0 开始**连续可读**的字节数（离线点播门用，PB-4）。
     *
     * 为什么不能用 [cachedBytes]：那是区间内所有 span 的字节**总和**，中间有空洞
     * 也算数；而 [warmUp] 预热的 256KB 头会让"只暖了个头"的歌通过离线门，离线
     * 播到 4-6 秒撞空洞报错 —— 用户感知是"离线总在歌中间断"，不是"未缓存"。
     *
     * 这里按 span 顺序累加，遇到空洞或空洞 span 立即停：得到"能从头连续读到哪"。
     * 注意这是**下界**而不是文件总长（SimpleCache 不记录 EOF 标记），所以调用方
     * 还要再比一个最小阈值，见 [OFFLINE_MIN_PREFIX_BYTES]。
     */
    fun cachedPrefixBytes(context: Context, url: String): Long {
        if (url.isBlank()) return 0L
        return try {
            contiguousPrefixBytes(
                get(context).getCachedSpans(url).map {
                    CacheSpanRange(it.position, it.length, it.isHoleSpan())
                }
            )
        } catch (_: Exception) {
            0L
        }
    }

    /** Total cache dir usage in bytes (0 on any error). */
    fun sizeBytes(context: Context): Long {
        return try {
            get(context).cacheSpace.coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Wipe the whole media cache (清理缓存 with confirm). Never releases the
     * live singleton: PlaybackService's CacheDataSource holds this instance,
     * and releasing it mid-playback kills subsequent songs until the service
     * rebuilds. Instead entries are evicted (removeResource per key) so the
     * pipeline stays valid and playback survives; the dir itself is deleted
     * only when no live instance exists (nothing can be using it).
     * @return true when the cache is actually empty afterwards.
     */
    @Synchronized
    fun clear(context: Context): Boolean {
        val live = instance
        if (live == null) {
            return try {
                File(context.applicationContext.cacheDir, DIR_NAME).deleteRecursively()
                !File(context.applicationContext.cacheDir, DIR_NAME).exists()
            } catch (_: Exception) {
                false
            }
        }
        return try {
            for (key in live.keys.toList()) {
                try {
                    live.removeResource(key)
                } catch (_: Exception) {
                    return false
                }
            }
            // Eviction loop completed: all entries known at snapshot time are
            // gone (concurrent streaming may add new bytes right after — the
            // badge/size row refreshes via cacheTick regardless).
            true
        } catch (_: Exception) {
            false
        }
    }
}
