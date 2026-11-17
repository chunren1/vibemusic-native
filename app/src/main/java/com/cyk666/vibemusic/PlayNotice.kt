package com.cyk666.vibemusic

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import java.net.URLDecoder

/**
 * 服务端换源提示。
 *
 * 背景：换源发生在服务端流代理里，客户端拿到的 MediaItem 元数据还是原曲的，
 * 于是「耳朵听到 A 版，封面歌名还写着原曲」。服务端现在会在音频流响应里补
 * X-Play-Substitute / X-Play-Title / X-Play-Artist / X-Play-Cover / X-Play-Reason
 * （UTF-8 百分号编码）。这里在数据源层把响应头捞出来，转成一条提示给界面。
 *
 * 为什么放在数据源层：ExoPlayer 不把响应头暴露给 UI；包一层 DataSource 是唯一
 * 不改播放链路、不改缓存键的接入点。缓存命中时不会走网络，也就不会触发提示——
 * 那种情况本来就没换源。
 */
data class PlaySubstituteNotice(
    val platform: String,
    val title: String?,
    val artist: String?,
    val coverUrl: String?,
    val quality: String?,
    val reason: String?
)

object PlayNoticeBus {
    /** extraBufferCapacity：没有订阅者时也不阻塞播放线程。 */
    val notices = MutableSharedFlow<PlaySubstituteNotice>(extraBufferCapacity = 8)
}

/**
 * 收集换源提示并交给宿主展示。
 *
 * 放在这里而不是 MainActivity：根组件的行数有守卫（check-file-size.sh），
 * 新增逻辑不该靠调高债务记录来过关。
 */
@Composable
fun PlayNoticeEffect(announce: (String) -> Unit) {
    LaunchedEffect("play-substitute") {
        PlayNoticeBus.notices.collect { n ->
            val who = listOfNotNull(
                n.title?.takeIf { it.isNotBlank() },
                n.artist?.takeIf { it.isNotBlank() }
            ).joinToString(" - ")
            val quality = if (n.quality in setOf("128", "PQ", "AAC")) " · 标准音质" else ""
            announce(
                if (who.isBlank()) "已切换为${platformLabel(n.platform)}$quality"
                else "已切换为${platformLabel(n.platform)}：$who$quality（可能为不同版本）"
            )
        }
    }
}

/** 平台代码 → 界面文案。 */
fun platformLabel(platform: String?): String = when (platform?.lowercase()) {
    "qq" -> "QQ 音乐"
    "migu" -> "咪咕音乐"
    "kugou" -> "酷狗音乐"
    "bilibili", "bili" -> "哔哩哔哩"
    "netease" -> "网易云音乐"
    else -> platform ?: "其他平台"
}

/** 包装 HTTP 数据源：open() 成功后读响应头，命中替代版本就发一条提示。 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayNoticeDataSourceFactory(
    private val upstream: DataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource = PlayNoticeDataSource(upstream.createDataSource())
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class PlayNoticeDataSource(private val delegate: DataSource) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val length = delegate.open(dataSpec)
        emitIfSubstituted()
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate.read(buffer, offset, length)

    override fun getUri(): Uri? = delegate.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun close() = delegate.close()

    private fun emitIfSubstituted() {
        try {
            val http = delegate as? HttpDataSource ?: return
            val headers = http.responseHeaders
            if (headers["X-Play-Substitute"]?.firstOrNull() != "1") return
            PlayNoticeBus.notices.tryEmit(
                PlaySubstituteNotice(
                    platform = headers["X-Play-Source"]?.firstOrNull().orEmpty(),
                    title = headers["X-Play-Title"]?.firstOrNull()?.decode(),
                    artist = headers["X-Play-Artist"]?.firstOrNull()?.decode(),
                    coverUrl = headers["X-Play-Cover"]?.firstOrNull()?.decode(),
                    quality = headers["X-Quality"]?.firstOrNull(),
                    reason = headers["X-Play-Reason"]?.firstOrNull()?.decode()
                )
            )
        } catch (_: Exception) {
            SilentFail.note("PlayNoticeDataSource.emitIfSubstituted")
        }
    }

    private fun String.decode(): String? = try {
        URLDecoder.decode(this, "UTF-8")
    } catch (_: Exception) {
        null
    }
}
