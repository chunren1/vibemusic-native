package com.cyk666.vibemusic

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

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

    /** True once any byte of [url] is on device (drives the 已缓存 badge). */
    fun isCached(context: Context, url: String): Boolean = cachedBytes(context, url) > 0L

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
