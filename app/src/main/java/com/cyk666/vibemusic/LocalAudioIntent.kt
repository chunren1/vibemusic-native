package com.cyk666.vibemusic

// 外部音频 intent（微信「用其他应用打开」/「分享到」）→ MediaItem。
// 2026-10-27 新增：让系统把本应用识别为音频播放器（此前 Manifest 只有 MAIN/LAUNCHER）。

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 设计边界（第一版刻意只做这些，越界即回归风险）：
 *
 * - 只做「打开即播」：不进搜索/推荐/历史/下载，不改 PlaybackService 的播放状态机，
 *   也不碰通知/媒体按钮路径（README「血泪教训 4」：拦截式 session 回调两次回滚，第三次不做）。
 * - 不做「最近打开」：content:// 的读权限随 intent 一次性授予，微信 FileProvider 不给
 *   persistable 授权，进程重启后再播必抛 SecurityException；要持久化只能先复制进私有目录，
 *   成本远超收益。
 * - mediaId 沿用离线下载的 `local:` 前缀约定：PlaybackService 见到 `local:` 就不做换源重试，
 *   CachePolicy 也不会自动跳过 —— 否则会拿文件 URI 去拼流地址，把队列带偏。
 */
object LocalAudioIntent {

    /** `local:file:<uri>`：`file` 是 platform 段，`songFromMediaItem` 会原样解析出来。 */
    const val MEDIA_ID_PREFIX = "local:file:"

    /** 外部音频没有可回退的流地址，Activity 的错误提示要单独一条。 */
    fun isExternal(mediaId: String?): Boolean =
        mediaId.orEmpty().startsWith(MEDIA_ID_PREFIX)

    /**
     * 一次性交接：Activity 把解析出的 URI 放这里，Compose 拿到 controller 后取走。
     * 用 StateFlow 而不是回调，是因为冷启动时 intent 早于 controller 就绪。
     */
    private val _pending = MutableStateFlow<List<Uri>?>(null)
    val pending: StateFlow<List<Uri>?> = _pending

    fun submit(uris: List<Uri>) {
        if (uris.isNotEmpty()) _pending.value = uris
    }

    fun consume() {
        _pending.value = null
    }

    fun mediaIdFor(uri: Uri): String = MEDIA_ID_PREFIX + uri.toString()

    /**
     * intent 里的音频 URI（VIEW / SEND / SEND_MULTIPLE，含 ClipData 兜底）。
     * 只接受 content:/file: —— http(s) 归流媒体链路，不从这里进。
     */
    @Suppress("DEPRECATION")
    fun urisFrom(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val out = LinkedHashSet<Uri>()
        intent.data?.let { out.add(it) }
        when (intent.action) {
            Intent.ACTION_SEND ->
                (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { out.add(it) }
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach { out.add(it) }
            else -> Unit
        }
        // 有些分享方只填 ClipData，不填 EXTRA_STREAM
        val clip = intent.clipData
        if (clip != null) for (i in 0 until clip.itemCount) clip.getItemAt(i)?.uri?.let { out.add(it) }
        return out.filter { isPlayable(it) }
    }

    fun isPlayable(uri: Uri): Boolean = when (uri.scheme?.lowercase()) {
        "content", "file" -> true
        else -> false
    }

    /** 解析 + 取元信息。**阻塞 I/O**（读文件头 / 查 MediaStore），调用方负责 Dispatchers.IO。 */
    fun buildMediaItems(context: Context, uris: List<Uri>): List<MediaItem> =
        uris.mapNotNull { buildMediaItem(context, it) }

    fun buildMediaItem(context: Context, uri: Uri): MediaItem? = try {
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(mediaIdFor(uri))
            .setMediaMetadata(readMetadata(context, uri))
            .build()
    } catch (e: Exception) {
        SilentFail.note("LocalAudioIntent.buildMediaItem: ${e.javaClass.simpleName}")
        null
    }

    private fun readMetadata(context: Context, uri: Uri): MediaMetadata {
        val retriever = MediaMetadataRetriever()
        var title = fallbackTitle(context, uri)
        var artist = ""
        var album = ""
        var artwork: ByteArray? = null
        try {
            retriever.setDataSource(context, uri)
            retriever.meta(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { title = it }
            retriever.meta(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { artist = it }
            retriever.meta(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { album = it }
            artwork = retriever.embeddedPicture
        } catch (e: Exception) {
            // 元信息读不出不影响播放：标题回退文件名，其余留空
            SilentFail.note("LocalAudioIntent.readMetadata: ${e.javaClass.simpleName}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                SilentFail.note("LocalAudioIntent.release")
            }
        }
        val builder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist.ifBlank { null })
            .setAlbumTitle(album.ifBlank { null })
        if (artwork != null && artwork.isNotEmpty()) {
            builder.setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return builder.build()
    }

    private fun MediaMetadataRetriever.meta(key: Int): String? =
        extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }

    /** 无内嵌标题时用文件名（微信下载的音频，文件名通常就是歌名）。 */
    fun fallbackTitle(context: Context, uri: Uri): String {
        val fromProvider = try {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            SilentFail.note("LocalAudioIntent.fallbackTitle: ${e.javaClass.simpleName}")
            null
        }
        val name = fromProvider?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty()
        return name.substringBeforeLast('.').ifBlank { "本地音频" }
    }
}
