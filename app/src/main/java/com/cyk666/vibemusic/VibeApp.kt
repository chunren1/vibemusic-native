package com.cyk666.vibemusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * 应用入口：注册全局 Coil ImageLoader（round6 体验专项）。
 *
 * - 磁盘缓存 256MB（用户明确"不用怕应用大小"）：封面跨会话秒开，配合后端
 *   /api/image-proxy 的 Cache-Control(24h)/ETag(304)，重复浏览同一封面零网络开销
 * - 内存缓存 20%（默认 25%，略收紧，给播放缓冲让路）
 * - crossfade：封面切换淡入，观感更顺
 */
class VibeApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
