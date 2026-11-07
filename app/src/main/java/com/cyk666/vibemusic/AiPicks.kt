package com.cyk666.vibemusic

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// AI 推荐板块（2026-11-07）：状态持有者 + 一个 Composable。
//
// 为什么独立成文件：DiscoverUi.kt 已是纯 LOC 硬上限 400 的存量文件（余量 0），
// 而这块要同时带「状态 + 冷启动缓存 + 配额失败提示 + 渲染」，塞回去必然超限。
//
// 交互口径：
// - `loadIfNeeded` = 免费路径（refresh=false），进程内只自动跑一次；
// - `refresh`     = 花配额的路径（refresh=true），只在用户点「换一批」时走；
//   429（3 次/天用完）保留旧列表 + 友好提示，绝不把已经看到的歌清掉。

/** AI 板块的磁盘缓存键，与 [HotspotStore.ALLOWED_KEYS] 里的 "ai" 必须同名。 */
const val AI_CACHE_KEY = "ai"

/**
 * AI 推荐的状态 + 取数。构造参数注入 [fetch] / 缓存读写，便于单测脱离网络与磁盘。
 *
 * 不持有 Activity / Context：两个入口都要求调用方传 scope 与 context，
 * 避免把长生命周期对象塞进状态里（C4 遗留风险的同一条纪律）。
 */
class AiRecommendState(
    private val fetch: suspend (Boolean) -> PersonalizedResult = { refresh ->
        VibeApi.aiRecommend(refresh)
    },
    private val readCache: suspend (Context, String) -> Pair<Long, String>? = HotspotStore::read,
    private val writeCache: suspend (Context, String, String) -> Unit = HotspotStore::write
) {
    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var reason by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set

    /** 给 Snackbar 的一次性提示；消费后必须归 null（见 [consumeNotice]）。 */
    var notice by mutableStateOf<String?>(null)
        private set

    /** 已为哪个登录态加载过（null = 还没加载过；"" = 游客）。 */
    private var loadedFor: String? = null

    /**
     * HOME 口径：无封面的歌不渲染（与每日推荐/新歌速递同一条规则）。
     * 列表与索引都由这一份派生值给出，保证「显示的第 N 首」=「播放的第 N 首」。
     */
    val visibleSongs: List<Song> get() = homeVisibleSongs(songs)

    /**
     * 首次进入发现页调用：先还原磁盘缓存（冷启动秒出），再静默拉一次。
     * 同一登录态只跑一次；空结果/异常都不动已有内容，也不弹提示 —— 这个板块只是
     * 发现页的一部分，不允许它把别人的错误条顶出来。
     *
     * [userKey] 是登录态标识（userId；游客为 null/空）：
     * - 游客不请求也不展示（服务端只会返回空，白跑一趟还会闪一次骨架）；
     * - 游客→登录、或换账号时要重新加载，否则「先游客浏览再登录」的用户
     *   得等下次冷启动才看到板块。
     */
    fun loadIfNeeded(scope: CoroutineScope, context: Context, userKey: String? = null) {
        if (loadedFor == userKey) return
        loadedFor = userKey
        if (userKey.isNullOrBlank()) {
            songs = emptyList()
            reason = ""
            loading = false
            return
        }
        scope.launch {
            seedFromCache(context)
            if (songs.isEmpty()) loading = true
            try {
                apply(fetch(false), context)
            } catch (e: Exception) {
                SilentFail.note("AiRecommendState.load:${e.javaClass.simpleName}")
            } finally {
                loading = false
            }
        }
    }

    /**
     * 用户显式「换一批」（花一次服务端配额）。
     * 429 → 配额文案；其它失败 → 通用文案；两者都保留旧列表（列表闪空比报错更难忍）。
     */
    fun refresh(scope: CoroutineScope, context: Context) {
        // 必须无条件置 loading：它同时是「换一批」按钮的 busy 态与重入闸门。
        // 只在列表为空时置位的话，有列表时连点会把当天 3 次配额一键点光。
        if (loading) return
        loading = true
        scope.launch {
            try {
                apply(fetch(true), context)
            } catch (e: Exception) {
                SilentFail.note("AiRecommendState.refresh:${e.javaClass.simpleName}")
                notice = if (isQuotaExhausted(e)) QUOTA_NOTICE else FAIL_NOTICE
            } finally {
                loading = false
            }
        }
    }

    fun consumeNotice() {
        notice = null
    }

    private suspend fun apply(result: PersonalizedResult, context: Context) {
        // 空 songs 是正常契约（未登录 / 生成失败 / 命中不足），不是错误：
        // 保持现状、不写缓存、不提示。
        if (result.songs.isEmpty()) return
        songs = result.songs
        reason = result.reason
        try {
            writeCache(context, AI_CACHE_KEY, encodeDaily(result.reason, result.songs))
        } catch (_: Exception) {
            SilentFail.note("AiRecommendState.writeCache")
        }
    }

    private suspend fun seedFromCache(context: Context) {
        val payload = try {
            readCache(context, AI_CACHE_KEY)?.second
        } catch (_: Exception) {
            SilentFail.note("AiRecommendState.readCache")
            null
        } ?: return
        val decoded = decodeDaily(payload) ?: return
        if (songs.isNotEmpty() || decoded.second.isEmpty()) return
        songs = decoded.second
        reason = decoded.first
    }

    /** 429 既可能来自 HTTP 状态（rawGet 抛 "Request HTTP 429 …"），也可能是信封 code=429。 */
    private fun isQuotaExhausted(e: Exception): Boolean =
        (e.message ?: "").contains("429")

    companion object {
        const val QUOTA_NOTICE = "今天的 AI 推荐次数用完了，明天再来"
        const val FAIL_NOTICE = "AI 推荐刷新失败，稍后再试"
    }
}

/**
 * 把 [AiRecommendState.notice] 转交给宿主 Snackbar（只转一次，随后清空）。
 * 普通提示样式：走宿主统一的 showError 通道即可，文案本身已是友好口径。
 */
@Composable
fun AiNoticeEffect(state: AiRecommendState, onNotice: (String) -> Unit) {
    LaunchedEffect(state.notice) {
        val msg = state.notice ?: return@LaunchedEffect
        state.consumeNotice()
        onNotice(msg)
    }
}

/**
 * 自动加载（发现页可见时按登录态触发一次）。放在 AiPicks.kt 而不是 MainActivity：
 * Activity 已到体量上限，且「何时该拉、拉几次」属于本板块自己的口径。
 */
@Composable
fun AiPicksAutoLoad(state: AiRecommendState, userKey: String?, active: Boolean) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(userKey, active) {
        if (active) state.loadIfNeeded(scope, context, userKey)
    }
}

/**
 * AI 为你选：空且不在加载时整块不渲染（连 header 都不出），避免首屏噪音。
 * 颜色取 UiChampagne（现有调色板里唯一没被别的板块占用的那支）。
 */
@Composable
fun AiPicksSection(
    state: AiRecommendState,
    onPlay: (Int) -> Unit,
    onOverflow: (Song) -> Unit
) {
    val songs = state.visibleSongs
    if (songs.isEmpty() && !state.loading) return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    HomeSectionHeader(
        title = "AI 为你选",
        enSubtitle = "AI PICKS",
        enColor = UiChampagne,
        actionLabel = "换一批",
        actionBusy = state.loading && songs.isNotEmpty(),
        onAction = { state.refresh(scope, context) }
    )
    if (state.reason.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    Spacer(Modifier.height(8.dp))
    if (songs.isEmpty()) {
        SearchSkeleton()
        return
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(songs, key = { index, s -> songRowKey(index, s, "ai") }) { index, song ->
            HomeSongCard(
                song = song,
                backCoverUrl = songs.getOrNull(index + 1)?.coverUrl.orEmpty(),
                enLabel = "AI PICK",
                enColor = UiChampagne,
                onPlay = { onPlay(index) },
                onOverflow = { onOverflow(song) }
            )
        }
    }
}
