package com.cyk666.vibemusic

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.util.UnstableApi
import com.cyk666.vibemusic.MediaCache.toCachedMediaItem
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Pure auto-skip rule: skip a broken item only while failures are few and a next item exists. */
fun shouldAutoSkip(consecFails: Int, hasNext: Boolean): Boolean = consecFails < 3 && hasNext

/**
 * 快速失败重试预算（2026-09-17 修"后台拿不到资源长时间静默"）。
 *
 * ExoPlayer 默认策略是 3 次重试 + 1s/2s/4s 指数退避：一首取不到播放链接的歌要 ≈9s 才把
 * 错误抛到服务侧；叠加"同曲换新 URL 重试一次"（签名 URL 过期场景）后，下一首要近 20s
 * 才响——听感就是"一直暂停，过了一会才跳下一首"。
 * 收敛为 1 次重试 + 固定 800ms：单次失败 ≈2s 内落地，仍能扛一次网络抖动。
 */
const val STREAM_LOAD_RETRY_COUNT = 1
const val STREAM_LOAD_RETRY_DELAY_MS = 800L

/** Pure factory：1 次重试 + 固定短延迟（默认是 3 次 + 指数退避）。 */
fun streamLoadErrorHandlingPolicy(): DefaultLoadErrorHandlingPolicy =
    object : DefaultLoadErrorHandlingPolicy(STREAM_LOAD_RETRY_COUNT) {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
            STREAM_LOAD_RETRY_DELAY_MS
    }

// ── 播放批次 (a)：下一首预热 + 快启动缓冲 ──

/** 预热拉取头字节数：首个 ~256KB 覆盖大多数歌曲的解码启动段。 */
const val PREFETCH_WARM_BYTES = 256L * 1024L

/** 切歌耗时预算：预热命中时下首应在该时间内出声（logcat 度量口径）。 */
const val FAST_SWITCH_BUDGET_MS = 500L

/** 缓冲观测门槛：BUFFERING 持续 ≥2s 才写入 PlayDiag（正常起播不记）。 */
const val BUFFERING_DIAG_MIN_MS = 2_000L

/**
 * Pure: 预热目标下标。单曲循环预热本曲；否则取下一首，末尾且列表循环则回绕；
 * 乱序时 Exo 洗牌顺序服务侧不可预知，返回 null（交由 Exo 自己的预缓冲）。
 */
fun selectPrefetchIndex(
    queue: List<Song>,
    currentIndex: Int,
    repeatOne: Boolean,
    repeatAll: Boolean
): Int? {
    if (queue.isEmpty() || currentIndex !in queue.indices) return null
    if (repeatOne) return currentIndex
    val next = currentIndex + 1
    if (next in queue.indices) return next
    if (repeatAll && queue.size > 1) return 0
    return null
}

/** Pure: 切歌耗时是否命中快切预算（logcat "fast-switch" 度量口径）。 */
fun isFastSwitch(elapsedMs: Long): Boolean = elapsedMs in 0L..FAST_SWITCH_BUDGET_MS

/**
 * 播放缓冲：音乐切歌要快，但公网下发一首 320kbps 曲目实测 TTFB 1.3–3.2s、
 * 吞吐在 0.5–1.3MB/s 之间抖动（CF 隧道链路，见 2026-10-22 实测），
 * 缓冲太小会让"首播/切歌"直接听成卡顿。取值权衡：
 * - 最小/最大缓冲抬到 30s/60s（320kbps 约 40KB/s，60s 仅 2.4MB，内存代价可忽略）；
 * - 起播门槛 1.5s：仍比 Exo 默认 2.5s 快，但比原来的 1s 更能扛住握手抖动；
 * - 重缓冲门槛 4s：断流后多攒一点再恢复，避免"刚出声又断"的连环卡顿。
 */
fun playbackLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        30_000,
        60_000,
        1_500,
        4_000
    )
    .build()

// ── 播放批次 (b)：弱网退避重试 ──

/** 弱网同曲退避重试上限（loader 层 1 次之外，服务层最多再补 2 次）。 */
const val WEAK_RETRY_MAX = 2

/** Pure: 弱网第 [attempt] 次重试的等待（800ms 起指数退避，5s 封顶）。 */
fun weakRetryDelayMs(attempt: Int): Long {
    val a = attempt.coerceAtLeast(0)
    val shift = a.coerceAtMost(3)
    return (STREAM_LOAD_RETRY_DELAY_MS shl shift).coerceAtMost(5_000L)
}

/** Pure: 弱网错误码（超时/建连失败——404/鉴权等应用层错误不归此路径）。 */
fun isWeakNetworkError(errorCode: Int): Boolean = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_TIMEOUT -> true
    else -> false
}

/** Pure: 弱网同曲重试预算（loader 层快失败之后，服务层延迟补试）。 */
fun shouldRetryWeakNetwork(attemptDone: Int): Boolean = attemptDone < WEAK_RETRY_MAX

/**
 * Offline-aware next index (pure): from [fromIndex], the first ahead index
 * where [isPlayable] holds; when nothing ahead is playable and [repeatAll],
 * wrap once over 0 until [fromIndex] (the failed current item is excluded so
 * a poisoned single-item queue stops instead of looping forever).
 * Returns -1 when playback must stop. Online callers keep [shouldAutoSkip].
 */
fun selectNextOfflineIndex(
    queue: List<Song>,
    fromIndex: Int,
    isPlayable: (Int) -> Boolean,
    repeatAll: Boolean
): Int {
    if (queue.isEmpty()) return -1
    val from = fromIndex.coerceIn(queue.indices)
    for (i in from + 1 until queue.size) {
        try {
            if (isPlayable(i)) return i
        } catch (_: Exception) {
            SilentFail.note("PlaybackService.selectNextOfflineIndex:142")
        }
    }
    if (!repeatAll) return -1
    for (i in 0 until from) {
        try {
            if (isPlayable(i)) return i
        } catch (_: Exception) {
            SilentFail.note("PlaybackService.selectNextOfflineIndex:150")
        }
    }
    return -1
}

/**
 * 焦点被拒补试阶梯（2026-10-10 二版，修"通知播放键被抢焦点后永久没反应"）：
 * 旧版 2 次 × 5s 且计数只在成功播放时清零——两次耗尽后任何新的点按都被静默拒绝。
 * 首版（75s+30s×2）用"距上次拒绝 >15s = 用户新点按"的时间启发式区分来源，缺陷：
 * 24s 档间隔本身 >15s → 会被误判成用户新点按、计数清零重开 → 实际永远在早期档
 * 循环、走不到用尽。二版改为显式来源标志（isRetryProbe）：
 * - 用户点按被拒（isRetryProbe=false）→ 开启/重开一轮密集补试（从 0 档起）
 * - 补试探测被拒（isRetryProbe=true）→ 沿阶梯推进，用尽即停
 * 阶梯：前 75s 密集 5 次（3/6/12/24/30s），随后每 60s 一次共 9 次，窗口 ≈10 分钟
 * （覆盖看长视频/开会等长打断；用户任意时刻再点按都会立即重置为一轮新的密集补试）。
 * 深省电（Doze）下系统会推迟合批定时消息，长间隔档属尽力而为。
 */
val FOCUS_RETRY_DELAYS_MS = longArrayOf(
    3_000, 6_000, 12_000, 24_000, 30_000,
    60_000, 60_000, 60_000, 60_000, 60_000, 60_000, 60_000, 60_000, 60_000
)

/** 媒体库浏览根的稳定标识（见 PlaybackService.onGetLibraryRoot，仅为启用系统恢复通道）。 */
private const val LIBRARY_ROOT_ID = "vibemusic.library.root"

/**
 * 纯函数：返回本轮应使用的阶梯下标（-1 = 不再补试）。
 * - playedBefore=true（播放中被打断）：不自动补试，等用户显式点按
 * - isRetryProbe=false（用户点按被拒）：新的一轮，从 0 档开始
 * - isRetryProbe=true（补试探测被拒）：沿阶梯推进，用尽即 -1
 */
fun nextFocusRetryIndex(playedBefore: Boolean, retriesDone: Int, isRetryProbe: Boolean): Int {
    if (playedBefore) return -1
    val used = if (isRetryProbe) retriesDone else 0
    return if (used in FOCUS_RETRY_DELAYS_MS.indices) used else -1
}

/**
 * 纯函数：补试探测开火前的"用户意愿门"（2026-10-11 未点按自动续播 bug）。
 * - userPlayMs == 0（本进程用户从未点过播放）→ 禁止开火：当前这首是"被打断停掉"
 *   的，不是"点了被拒"的，自动续播违背用户意图。
 * - 用户按过暂停且晚于上次点按 → 禁止开火（暂停即"我不想听"，pending 探测作废）。
 * 其余情况放行（点了被拒 → 等对端放手是合法补试）。
 */
fun shouldFireRetryProbe(
    userPlayMs: Long,
    userPauseMs: Long,
    isPlaying: Boolean,
    playWhenReady: Boolean
): Boolean {
    if (isPlaying || playWhenReady) return false
    if (userPlayMs == 0L) return false
    if (userPauseMs > userPlayMs) return false
    return true
}

/** 播放救援检查间隔 / 单轮上限（"点了播放但一直卡着"→ 重置通道重播）。 */
const val PLAY_RESCUE_CHECK_MS = 2_500L
const val PLAY_RESCUE_MAX_ATTEMPTS = 2

/**
 * 救援看门狗总开关——默认关闭（2026-10-11 审查结论：真机未验证成功，
 * 且触发条件与"进程已死/焦点被拒"两大致因互斥，留着只会误伤正常播放；
 * 真机 logcat 定位根因前保持关闭，诊断时手动置 true）。
 */
const val PLAY_RESCUE_ENABLED = false

/**
 * 纯函数：是否应执行"播放救援"（对播放器执行一轮 stop→prepare→play，
 * 借 IDLE→重请求的路径把音频焦点通道从"等待卡死"状态里拔出来）。
 *
 * 背景（2026-10-10 实机）：通知中心播放键点击后"图标第一次闪、之后不闪"、
 * 划掉对端也无声明、App 内/网易云正常——排除"对端占焦"，指向焦点等待态的
 * 内部卡死（Media3 的焦点等待没有超时）。救援作用于**所有播放路径**（汇聚在
 * service 的 player 上），App 内点击遇到同场景同样自愈。
 *
 * 只在"想播（playWhenReady）却不出声（!isPlaying）"时考虑，并严格排除合法等待：
 * - BUFFERING（网络缓冲）/ IDLE 或空队列（进程被杀后的 resumption 流程正在走，
 *   player 是空的 IDLE，绝不能打断官方恢复）/ ENDED（自然播完）都不动
 * - 最多 [PLAY_RESCUE_MAX_ATTEMPTS] 次：对"对端真的还占着声音"的情况无害收手
 * 瞬断（来电/语音）时 playWhenReady 保持 true **不发生变化**，本检查只挂在
 * false→true 的切换之后，所以通话结束的自动续播不受影响。
 */
fun shouldRescuePlayback(
    playWhenReady: Boolean,
    isPlaying: Boolean,
    playbackState: Int,
    mediaItemCount: Int,
    attemptsDone: Int
): Boolean =
    playWhenReady && !isPlaying &&
        playbackState == Player.STATE_READY &&
        mediaItemCount > 0 &&
        attemptsDone < PLAY_RESCUE_MAX_ATTEMPTS

/**
 * 进程被杀后从通知栏/媒体按钮"继续播放"的恢复点（纯函数，便于钉死）。
 *
 * 背景（Media3 1.5.1 源码 MediaSessionImpl.handleMediaControllerPlayRequest）：
 * 当控制器请求播放、而播放器**没有当前曲目**时，Media3 会调用
 * [MediaSession.Callback.onPlaybackResumption] 让 App 交回队列快照，由 Media3
 * 自己铺轨并开始播放。清单里声明了 MediaButtonReceiver 就必须实现它（官方要求），
 * 否则回调失败 → 在空播放器上执行播放 = 点了没反应（"通知播放键没反应"的真因）。
 *
 * 返回 -1 表示无队列快照，不可恢复（Media3 会退回空操作）。
 */
fun resumptionStartIndex(songs: List<Song>, savedIndex: Int): Int =
    if (songs.isEmpty()) -1 else savedIndex.coerceIn(songs.indices)

/** 恢复点位置：负值/异常一律从 0 开始（避免 seek 到非法位置）。 */
fun resumptionStartPosition(savedMs: Long): Long = savedMs.coerceAtLeast(0L)

// ── 开发禁区（勿重犯）：通知/媒体按钮路径上的"带声意图"由 Media3 自己处理 ──
// 1.0.10-ai 曾在 Service 侧做时间轴物化（拦截命令），1.0.11-ai 因拦截 9 类命令
// 导致通知栏串歌而整体回滚；1.0.27-ai 又试了一次窄口径（仅 COMMAND_PLAY_PAUSE），
// 1.0.28-ai 因"播放全死"报告第二次回滚。结论：这类拦截式实现在本项目两次实装
// 两次失败，第三次不做——进程被杀后的恢复只走官方
// [MediaSession.Callback.onPlaybackResumption]（见 onCreate 里的回调），
// Activity 侧 ensureTimeline 是唯一的兜底物化路径。
// 这里曾留有一组常量与单测做"语义钉桩"，于 round6（2026-09-23）清理为纯注释，
// 因为零生产调用点的常量+测试本身就是会漂移的墓碑。

/**
 * Retry key for the signed-URL single-retry: mediaId + player error code, so
 * the same dead item with a new failure mode gets its one retry, while a
 * repeat of the identical failure falls through to auto-skip.
 */
fun streamRetryKey(mediaId: String, errorCode: Int): String = "$mediaId|$errorCode"

/**
 * Pure: retry the SAME index once with a rebuilt MediaItem before auto-skip.
 * NetEase/Migu upstream URLs are time-signed; resuming a hours-old paused
 * item replays a dead URL → error. Exactly one retry per item per error
 * episode: local (downloaded-file) items never retry here — they are owned by
 * the Activity self-heal path — and an identical repeat (same key, same
 * index) means the fresh URL failed too, so fall through to auto-skip.
 */
fun shouldRetrySameItem(
    mediaId: String,
    errorCode: Int,
    currentIndex: Int,
    lastRetriedKey: String?,
    lastRetriedIndex: Int
): Boolean {
    if (mediaId.isBlank() || mediaId.startsWith("local:")) return false
    if (currentIndex < 0) return false
    return lastRetriedKey != streamRetryKey(mediaId, errorCode) ||
        lastRetriedIndex != currentIndex
}

/**
 * Service-side error-handling outcome (reporting only — the retry / skip /
 * stop decisions above are untouched). Mirrored to [PlaybackService.Companion.lastSkipOutcome]
 * so the Activity toast reports what actually happened instead of a blanket
 * "已跳过": a same-track retry may still succeed, and queue-end / melt-down
 * may do nothing at all.
 */
enum class SkipOutcome {
    /** Same index retried once with a rebuilt MediaItem (may still succeed). */
    RETRIED_SAME_ITEM,

    /** Advanced to the next (offline-playable) item. */
    SKIPPED_TO_NEXT,

    /** Nothing to advance to: the queue genuinely hit the end. */
    STOPPED_AT_END,

    /**
     * Melt-down guard tripped: consecutive failures hit the cap while items
     * remain (NOT the queue end — toast must not claim 队列已到末尾).
     * See [meltDownOutcome].
     */
    STOPPED_CONSEC_FAILURES
}

/**
 * Pure: resolve the toast outcome from the service-reported [reported]
 * value, falling back to the controller's [hasNext] when the service never
 * recorded one for this error (controller-only failure ordering).
 */
fun inferSkipOutcome(reported: SkipOutcome?, hasNext: Boolean): SkipOutcome =
    reported ?: if (hasNext) SkipOutcome.SKIPPED_TO_NEXT else SkipOutcome.STOPPED_AT_END

/**
 * Pure: melt-down signal split. The `shouldAutoSkip` guard stops for two
 * different reasons — neither may share one toast: items remain but the
 * consecutive-failure cap tripped → [SkipOutcome.STOPPED_CONSEC_FAILURES];
 * genuinely nothing ahead → [SkipOutcome.STOPPED_AT_END].
 * [hasMoreItems] is the controller's `hasNextMediaItem()` snapshot.
 */
fun meltDownOutcome(hasMoreItems: Boolean): SkipOutcome =
    if (hasMoreItems) SkipOutcome.STOPPED_CONSEC_FAILURES else SkipOutcome.STOPPED_AT_END

/**
 * Pure: truthful skip-failure toast (Chinese UX, keeps the error code for
 * diagnosis). SKIPPED keeps the historic "已跳过" wording; STOPPED_AT_END
 * says the queue hit the end; STOPPED_CONSEC_FAILURES says repeated
 * failures stopped playback while songs remain (never claims 末尾);
 * RETRIED says a same-track retry is in flight (a follow-up toast reports
 * that retry's own result).
 */
fun skipErrorToast(title: String?, errorCodeName: String, outcome: SkipOutcome): String {
    val label = "《${title ?: "unknown"}》"
    return when (outcome) {
        SkipOutcome.SKIPPED_TO_NEXT -> "${label}播不了，已跳过 ($errorCodeName)"
        SkipOutcome.STOPPED_AT_END -> "${label}播不了，队列已到末尾，播放停止 ($errorCodeName)"
        SkipOutcome.STOPPED_CONSEC_FAILURES -> "${label}播不了，连续多次失败，播放停止 ($errorCodeName)"
        SkipOutcome.RETRIED_SAME_ITEM -> "${label}播不了，正在重试 ($errorCodeName)"
    }
}

/**
 * Audio-focus / becoming-noisy config applied to the service ExoPlayer
 * (pure, unit-tested). Verified against the media3 1.5.1 jars via javap:
 * - `ExoPlayer$Builder.setAudioAttributes(AudioAttributes, boolean)` (two-arg)
 * - `ExoPlayer$Builder.setHandleAudioBecomingNoisy(boolean)` (exists, idiomatic —
 *   no manual ACTION_AUDIO_BECOMING_NOISY receiver needed)
 * Resulting behavior (all handled inside ExoPlayer's AudioFocusManager /
 * AudioBecomingNoisyManager, no extra permissions — phone-call pause comes free
 * via transient/permanent focus loss, do NOT add READ_PHONE_STATE):
 * - permanent loss (other music/video app) → pause, stays paused（框架不回发 GAIN，
 *   只能用户手动恢复；另见下方 2026-09-18 的权威结论）；
 * - transient loss（来电等）→ 压制（静音）但 playWhenReady 保持 true，焦点恢复后
 *   由框架**自动续播**（此前这里写成"不自动续播"，与 AudioFocusManager 实读结论相反）；
 * - transient-can-duck → native volume duck (VOLUME_MULTIPLIER_DUCK);
 * - Bluetooth disconnect / wired-headset unplug → pause;
 * - MediaSession + notification follow player state automatically (no manual
 *   setPlaybackState anywhere in the codebase — verified by grep, truthful icon).
 */
data class AudioFocusConfig(
    val usage: Int,
    val contentType: Int,
    val handleAudioFocus: Boolean,
    val handleAudioBecomingNoisy: Boolean
)

fun audioFocusConfig(): AudioFocusConfig = AudioFocusConfig(
    usage = C.USAGE_MEDIA,
    // AUDIO_CONTENT_TYPE_MUSIC == CONTENT_TYPE_MUSIC == 2; the CONTENT_TYPE_*
    // alias is deprecated in 1.5.1, so use the canonical AUDIO_ name.
    contentType = C.AUDIO_CONTENT_TYPE_MUSIC,
    handleAudioFocus = true,
    handleAudioBecomingNoisy = true
)

/**
 * 音频焦点策略（2026-09-18 依据 Media3 1.5.1 源码更正，此前注释与死代码描述有误）。
 *
 * 事实（AudioFocusManager.java / ExoPlayerImpl.java 实读，非猜测）：
 * - 瞬时丢失 LOSS_TRANSIENT → PLAYER_COMMAND_WAIT_FOR_CALLBACK：playWhenReady **保持 true**，
 *   仅加"压制"状态（静音）；焦点恢复 GAIN → PLAYER_COMMAND_PLAY_WHEN_READY → **原生自动续播**。
 * - CAN_DUCK → 原生压低音量（本 App content type = MUSIC，走 duck 分支）。
 * - 永久丢失 LOSS → PLAYER_COMMAND_DO_NOT_PLAY → playWhenReady=false（原因码 AUDIO_FOCUS_LOSS）；
 *   对端释放焦点时框架**不会回发 GAIN**，因此"永久被抢"场景无法自动恢复（只能用户手动点）。
 * - 原因码映射：只有 DO_NOT_PLAY 会给 AUDIO_FOCUS_LOSS，而它必然把 playWhenReady 置 false，
 *   故不存在 (playWhenReady=true, AUDIO_FOCUS_LOSS) 组合——曾经据此写的"抑制自动恢复"
 *   分支是死代码，已删除。
 *
 * ⇒ 结论：瞬时打断已是"静音等待+自动续播"；永久被抢只能靠白名单（见设置页"后台保活"）
 *   与用户点按恢复。无需（也无法）自行接管焦点。
 */

/**
 * 用 [MediaLibraryService]（[MediaSessionService] 的超集）而非裸 MediaSessionService：
 * Media3 文档明确 "System UI resumption notification is only available if you
 * implement a MediaLibraryService to serve a content library"——App 被系统回收后，
 * 下拉媒体控件要能作为"恢复入口"存在（并点击拉起服务恢复播放），这是官方通道；
 * 裸 MediaSessionService 只有蓝牙/系统控制直连，没有这层恢复展示。库本身只暴露
 * 一个轻量空根（[onGetLibraryRoot]），不提供真实内容树。
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    companion object {
        /**
         * Latest ExoPlayer audio session id (> 0 once the output is set).
         * Read by the Activity to attach the spectrum Visualizer (needs only
         * MODIFY_AUDIO_SETTINGS). Plain volatile — no playback logic depends
         * on it; 0 means "no session yet, hide the visualizer".
         */
        @Volatile
        var lastAudioSessionId: Int = 0

        /**
         * Latest service-side skip outcome (reporting only). Written by
         * onPlayerError alongside each retry/skip/stop decision; read by the
         * Activity to drive the error toast. Plain volatile — never feeds
         * back into any playback decision.
         */
        @Volatile
        var lastSkipOutcome: SkipOutcome? = null

        /**
         * 是否有一轮"焦点被拒自动补试"正在进行（reporting only，供 UI 提示；
         * 绝不参与任何播放决策——决策只看 nextFocusRetryIndex 的返回值）。
         */
        @Volatile
        var focusRetryActive: Boolean = false

        /**
         * Fast-switch metrics (reporting only, never feeds a playback decision).
         * [lastSwitchElapsedMs] = 上次切歌到出声的耗时（onMediaItemTransition →
         * 下一次 onIsPlaying=true 的间隔；-1 = 尚未度量）；[lastPrefetchBytes] =
         * 上次预热拉到的字节数（0 = 跳过/失败）。
         */
        @Volatile
        var lastSwitchElapsedMs: Long = -1L

        @Volatile
        var lastPrefetchBytes: Long = 0L
    }

    private var mediaSession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private var consecFails = 0

    // 焦点被拒补试（见 nextFocusRetryIndex）：wasPlayingBeforeFocusChange 区分
    // "被打断"（发生在播放中，不自动恢复）与"点了播放被拒"（用户意图未满足，阶梯补试）。
    // inFocusRetryProbe：此刻这次 play() 是否来自我们的补试探测（区分被拒来源；
    // 只在主线程读写，串行安全）。
    private var wasPlayingBeforeFocusChange = false
    private var focusRetries = 0
    private var inFocusRetryProbe = false
    private val focusRetryHandler = Handler(Looper.getMainLooper())
    // 用户显式暂停 vs 补试探测开火的时间戳（elapsedRealtime）：探测在用户暂停之后
    // 开火 = 违背用户意图，必须拦截（2026-10-11 审查：stillSilent 区分不出"被拒的静"
    // 和"用户按暂停的静"，pending 探测会在用户暂停后自动续播）。
    private var lastUserPauseMs = 0L
    private var lastProbeFireMs = 0L
    // 用户最近一次"想播"点按（elapsedRealtime；0=本进程从未点过）：补试探测开火的
    // 意愿门——只有用户点过播放，自动补试才有意义；被对端打断停掉的歌，用户没点
    // 就永远不自动响（2026-10-11：未点按自动续播 bug）。
    private var lastUserPlayMs = 0L

    // 播放救援（见 shouldRescuePlayback）：挂在 playWhenReady=false→true 之后，
    // 每 PLAY_RESCUE_CHECK_MS 检查一次；卡住则 stop→prepare→play 重置焦点通道。
    private var playRescueAttempts = 0
    private val playRescueHandler = Handler(Looper.getMainLooper())

    // Review item 5: the offline probe below fans out to per-song disk/cache
    // I/O, so it runs on IO; ExoPlayer transport (seek/prepare/play/stop)
    // posts back to the main thread that built the player. This service is
    // the SINGLE owner of offline-next transport — the Activity only reports
    // (it must not seek/stop here, or the queue burns twice).
    private val serviceIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serviceMainHandler = Handler(Looper.getMainLooper())

    // Signed-URL single-retry state: exactly one same-index retry per item
    // per error episode. Reset on successful play + on index change.
    private var lastStreamRetryKey: String? = null
    private var lastStreamRetryIndex: Int = -1

    // 音频 HTTP 数据源：显式超时，别用 Media3 默认的 8s。
    // 服务端 /api/songs/stream 的取链预算是 12s（StreamController.STREAM_TOTAL_BUDGET_MS），
    // 客户端 8s 读超时会先于服务端放弃 → 首播被误判成网络故障，再叠加 800ms 重试更慢。
    // connect 15s / read 20s 给"冷解析 + CF 隧道抖动"留余量；跨协议重定向放行以防上游换协议。
    private val httpUpstream: DataSource.Factory = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)
        .setAllowCrossProtocolRedirects(true)

    // 弱网退避重试计数（同曲同错误 episode 内累计；出声/切轨清零）。
    private var weakRetryAttempt = 0

    // 快切度量起点：每次切轨（transition）记录，首次出声时结算。
    private var switchStartMs = -1L

    /** 本次 BUFFERING 起点（-1=当前不在缓冲）；用于统计卡顿真实时长。 */
    private var bufferingSinceMs = -1L

    // 预热去重：同一下标只暖一次（切轨/出声都会触发，episode 内去重）。
    private var lastPrefetchIndex: Int = -1

    // 同轨重试抑制一次切歌计时（replaceMediaItem 不算窗口切换）。
    private var suppressSwitchStart = false

    override fun onCreate() {
        super.onCreate()
        // 冷启动落一条诊断（进程曾被杀时，这是复现"通知播放键没反应"的第一手证据）
        PlayDiag.mark(this, "cold")
        // 观察点（2026-10-19，纯记录）：官方"启动前台服务被系统拒绝"钩子。
        // 国产 ROM 后台启动 FGS 被拒是**静默**的——通知卡片/媒体键拉起服务时前台服务没起来，
        // 听感就是"点了没反应"，此前完全不可见。回调体只写诊断，不改变任何启动/通知行为。
        try {
            setListener(object : MediaSessionService.Listener {
                override fun onForegroundServiceStartNotAllowedException() {
                    PlayDiag.mark(this@PlaybackService, "fgs-denied")
                }
            })
        } catch (_: Exception) {
            SilentFail.note("PlaybackService.onForegroundServiceStartNotAllowedException:531")
        }
        // Cache singleton warms on IO: dir create + DB open must never run
        // on Main (first tap / service recreate). The pipeline below
        // resolves the same singleton on ExoPlayer loader threads instead.
        serviceIoScope.launch {
            try {
                MediaCache.get(this@PlaybackService)
            } catch (_: Exception) {
                SilentFail.note("PlaybackService.onForegroundServiceStartNotAllowedException:540")
            }
        }
        // Cache-first pipeline: CacheDataSource serves cached bytes first and fills
        // gaps from the HTTP upstream while online, so normal streaming behavior is
        // unchanged. Offline replay works for fully-cached items; a partially-cached
        // item errors on the cache hole (upstream unreachable) → auto-skip/message
        // path handles it (see MainActivity.onPlayerError).
        val upstream = httpUpstream
        val cacheSourceFactory = MediaCache.cachedDataSourceFactory(this, upstream)
        // Scheme routing: file:// download items read straight from disk via
        // FileDataSource; http(s) keeps flowing cache→upstream byte-identical
        // to before. Without this, file:// misses the cache and falls through
        // to the HTTP-only upstream, which errors on the scheme — every local
        // file failed to play and self-heal deleted the good bytes.
        val schemeFactory = DataSource.Factory { SchemeDataSource(cacheSourceFactory) }
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(schemeFactory)
            // 快失败：坏歌/取不到链接时尽快把错误交给 onPlayerError 跳下一首（见上方常量注释）。
            // Media3 里重试预算挂在 MediaSourceFactory 上（ExoPlayer.Builder 无此方法）。
            .setLoadErrorHandlingPolicy(streamLoadErrorHandlingPolicy())
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(playbackLoadControl())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(audioFocusConfig().usage)
                    .setContentType(audioFocusConfig().contentType)
                    .build(),
                audioFocusConfig().handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(audioFocusConfig().handleAudioBecomingNoisy)
            // 锁屏/熄屏后保持 CPU 唤醒（WAKE_MODE_LOCAL 只持 partial wake lock，不含 WifiLock），
            // 避免缓冲中的音频被系统挂起（通知栏播放键在息屏后失效的一类诱因）。需配合 WAKE_LOCK 权限。
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player = exo
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                // 播放中被打断（playing true→false）：上一轮"点了被拒"排的探测若还
                // pending，必须一起清掉——否则打断停歌后探测照样开火自动续播
                // （2026-10-11 未点按自动续播 bug 的另一半）。
                if (!playing && wasPlayingBeforeFocusChange) {
                    focusRetryHandler.removeCallbacksAndMessages(null)
                    focusRetryActive = false
                }
                wasPlayingBeforeFocusChange = playing
                if (playing) {
                    consecFails = 0
                    lastStreamRetryKey = null
                    lastStreamRetryIndex = -1
                    weakRetryAttempt = 0
                    focusRetries = 0
                    inFocusRetryProbe = false
                    focusRetryActive = false
                    lastUserPauseMs = 0L
                    lastProbeFireMs = 0L
                    PlayDiag.mark(this@PlaybackService, "playing")
                    if (switchStartMs > 0L) {
                        val elapsed = android.os.SystemClock.elapsedRealtime() - switchStartMs
                        lastSwitchElapsedMs = elapsed
                        switchStartMs = -1L
                        try {
                            android.util.Log.d(
                                "VibePrefetch",
                                "fast-switch elapsed=${elapsed}ms hit=${isFastSwitch(elapsed)}"
                            )
                        } catch (_: Exception) {
                            SilentFail.note("PlaybackService.onIsPlayingChanged:605")
                        }
                    }
                    prefetchNext()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // 用户显式暂停：取消所有未执行的补试探测（用户意图优先，绝不自动续播）；
                // 若暂停时已有探测在飞行（probe 已开火、拒绝回调还在路上），记下时间戳，
                // 由 maybeRetryDeniedPlay 按"暂停晚于开火"拦截下一轮。
                if (!playWhenReady &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                ) {
                    lastUserPauseMs = android.os.SystemClock.elapsedRealtime()
                    focusRetryHandler.removeCallbacksAndMessages(null)
                    focusRetryActive = false
                }
                // 焦点不可用（含"用户点了播放但焦点申请被拒"）：Media3 会把 playWhenReady
                // 直接置回 false（AudioFocusManager.requestAudioFocus 被拒 → DO_NOT_PLAY），
                // 界面毫无变化 = 用户看到的"点了没反应"。
                // fromProbe 区分来源：true = 我们自己的补试探测被拒（沿阶梯推进）；
                // false = 用户点按被拒（重开一轮密集补试，也是对端释放后的主恢复路径）。
                if (!playWhenReady &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
                ) {
                    val fromProbe = inFocusRetryProbe
                    inFocusRetryProbe = false
                    PlayDiag.mark(this@PlaybackService, "focus-blocked")
                    maybeRetryDeniedPlay(fromProbe)
                }
                // "想播"变为 true 后启动救援检查（见 shouldRescuePlayback）：
                // 若 2.5s 后仍 READY+无声（焦点等待卡死型），重置通道重播。
                if (playWhenReady) {
                    lastUserPlayMs = android.os.SystemClock.elapsedRealtime()
                    schedulePlayRescue()
                }
            }

            // 缓冲观测（2026-10-22）：首播/切歌卡顿的现场取证。
            // 只在 BUFFERING 持续 ≥2s 时记一条（正常起播门槛 1.5s 不刷屏）；
            // 与 ring 里的 error:/playing 配合，可区分「慢但没报错」与「网络错误重试」。
            override fun onPlaybackStateChanged(playbackState: Int) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (playbackState == Player.STATE_BUFFERING) {
                    if (bufferingSinceMs < 0) bufferingSinceMs = now
                    return
                }
                val since = bufferingSinceMs
                bufferingSinceMs = -1
                if (since >= 0) {
                    val stalled = now - since
                    if (stalled >= BUFFERING_DIAG_MIN_MS) {
                        PlayDiag.observe(this@PlaybackService, "buff-ms:$stalled")
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // A new window starts a new error episode; the same-index
                // retry replace keeps its record so a second identical
                // failure falls through to auto-skip instead of looping.
                val idx = try {
                    exo.currentMediaItemIndex
                } catch (_: Exception) {
                    -1
                }
                if (idx != lastStreamRetryIndex) {
                    lastStreamRetryKey = null
                    lastStreamRetryIndex = -1
                }
                // 快切度量起点：同轨重试的 replace 不算切歌（调用方置位抑制一次）。
                if (suppressSwitchStart) {
                    suppressSwitchStart = false
                } else {
                    switchStartMs = android.os.SystemClock.elapsedRealtime()
                }
                weakRetryAttempt = 0
                lastPrefetchIndex = -1
                // 切轨重置缓冲观测起点，避免把上一首的缓冲时长算到新曲
                bufferingSinceMs = -1
                // 切歌视为新一轮：焦点被拒的阶梯计数清零（切歌后按播放应立即生效）
                focusRetries = 0
                inFocusRetryProbe = false
                prefetchNext()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                lastAudioSessionId = audioSessionId
            }

            override fun onPlayerError(error: PlaybackException) {
                PlayDiag.mark(this@PlaybackService, "error:${error.errorCodeName}")
                // Local (downloaded-file) items are owned by MainActivity's
                // self-heal path (delete + fall back to stream, or message
                // when offline). Skipping here would race the heal and burn
                // the queue, so hands off local: items entirely.
                val isLocal = try {
                    exo.currentMediaItem?.mediaId?.startsWith("local:") == true
                } catch (_: Exception) {
                    false
                }
                if (isLocal) return
                if (!isNetworkAvailable(this@PlaybackService)) {
                    // SINGLE owner of offline-next transport (Activity only
                    // reports): snapshot + target compute on IO, transport
                    // back on main. Same triage bar, zero main-thread I/O.
                    val app = this@PlaybackService
                    val count = try {
                        exo.mediaItemCount
                    } catch (_: Exception) {
                        0
                    }
                    val from = try {
                        exo.currentMediaItemIndex
                    } catch (_: Exception) {
                        0
                    }
                    val repeatAll = try {
                        exo.repeatMode == Player.REPEAT_MODE_ALL
                    } catch (_: Exception) {
                        false
                    }
                    val songs = (0 until count).map { i ->
                        try {
                            songFromMediaItem(exo.getMediaItemAt(i))
                        } catch (_: Exception) {
                            Song("", "", "", "", "", 0, "")
                        }
                    }
                    serviceIoScope.launch {
                        val avail = try {
                            buildOfflineAvailability(app, songs)
                        } catch (_: Exception) {
                            OfflineAvailability()
                        }
                        val target = selectNextOfflineIndex(
                            songs,
                            from,
                            isPlayable = { idx ->
                                songs.getOrNull(idx)?.let { avail.isOfflinePlayable(it) }
                                    ?: false
                            },
                            repeatAll = repeatAll
                        )
                        serviceMainHandler.post {
                            try {
                                if (target >= 0) {
                                    consecFails++
                                    lastSkipOutcome = SkipOutcome.SKIPPED_TO_NEXT
                                    exo.seekTo(target, 0L)
                                    exo.prepare()
                                    exo.play()
                                } else {
                                    consecFails++
                                    lastSkipOutcome = SkipOutcome.STOPPED_AT_END
                                    exo.stop()
                                }
                            } catch (_: Exception) {
                                SilentFail.note("PlaybackService.onPlayerError:743")
                            }
                        }
                    }
                    return
                }
                // Signed stream URL expiry: netease/migu upstreams are
                // time-signed, so resuming a hours-old paused item replays a
                // dead URL. Retry the SAME index ONCE with a rebuilt MediaItem
                // (fresh backend URL resolution on prepare) before the
                // auto-skip below. Local items stay untouched (Activity heal).
                val errIndex = try {
                    exo.currentMediaItemIndex
                } catch (_: Exception) {
                    -1
                }
                val errItem = try {
                    exo.currentMediaItem
                } catch (_: Exception) {
                    null
                }
                val errMediaId = errItem?.mediaId.orEmpty()
                // 弱网退避：超时/建连失败类错误先延迟同曲补试（指数退避），
                // 不消耗签名 URL 的单次重试预算；预算耗尽才落到下面的换 URL/跳歌。
                if (errIndex >= 0 && errItem != null &&
                    !errMediaId.startsWith("local:") &&
                    isWeakNetworkError(error.errorCode) &&
                    shouldRetryWeakNetwork(weakRetryAttempt)
                ) {
                    val attempt = weakRetryAttempt++
                    val delay = weakRetryDelayMs(attempt)
                    lastSkipOutcome = SkipOutcome.RETRIED_SAME_ITEM
                    try {
                        android.util.Log.d(
                            "VibeWeakRetry",
                            "weak retry idx=$errIndex attempt=$attempt delay=${delay}ms"
                        )
                    } catch (_: Exception) {
                        SilentFail.note("PlaybackService.onPlayerError:781")
                    }
                    serviceMainHandler.postDelayed({
                        val cur = player
                        if (cur == null || cur !== exo) return@postDelayed
                        val stillSame = try {
                            cur.currentMediaItemIndex == errIndex &&
                                cur.currentMediaItem?.mediaId == errMediaId
                        } catch (_: Exception) {
                            false
                        }
                        if (!stillSame) return@postDelayed
                        try {
                            suppressSwitchStart = true
                            cur.replaceMediaItem(
                                errIndex,
                                songFromMediaItem(errItem).toCachedMediaItem()
                            )
                            cur.prepare()
                            cur.play()
                        } catch (_: Exception) {
                            SilentFail.note("PlaybackService.onPlayerError:802")
                        }
                    }, delay)
                    return
                }
                if (errIndex >= 0 && errItem != null &&
                    shouldRetrySameItem(
                        errMediaId,
                        error.errorCode,
                        errIndex,
                        lastStreamRetryKey,
                        lastStreamRetryIndex
                    )
                ) {
                    lastStreamRetryKey = streamRetryKey(errMediaId, error.errorCode)
                    lastStreamRetryIndex = errIndex
                    lastSkipOutcome = SkipOutcome.RETRIED_SAME_ITEM
                    try {
                        suppressSwitchStart = true
                        exo.replaceMediaItem(
                            errIndex,
                            songFromMediaItem(errItem).toCachedMediaItem()
                        )
                        exo.prepare()
                        exo.play()
                    } catch (_: Exception) {
                        SilentFail.note("PlaybackService.onPlayerError:828")
                    }
                    return
                }
                // 单首源坏了自动跳下一首；连续坏 3 次就停手（大概率没网，别把队列一口气烧光）
                if (shouldAutoSkip(consecFails, exo.hasNextMediaItem())) {
                    consecFails++
                    lastSkipOutcome = SkipOutcome.SKIPPED_TO_NEXT
                    exo.seekToNextMediaItem()
                    exo.prepare()
                    exo.play()
                } else {
                    // Guard exit with items remaining is the melt-down cap,
                    // not the queue end — report it truthfully (never 末尾).
                    lastSkipOutcome = try {
                        meltDownOutcome(exo.hasNextMediaItem())
                    } catch (_: Exception) {
                        SkipOutcome.STOPPED_AT_END
                    }
                }
            }
        })
        // 禁区（详见文件头 KDoc 旁的"开发禁区"注释）：通知路径的"带声意图"不要用
        // 拦截式 session 回调实现——1.0.10/1.0.11 与 1.0.27/1.0.28 两次实装两次回滚
        // （串歌 / 播放全死）。恢复只走官方 onPlaybackResumption（下方 setCallback），
        // Activity 侧 ensureTimeline 是唯一兜底物化路径。
        // Tap-to-open: without a session activity the notification tap does
        // nothing (users read it as "notification dead"). This only sets the
        // launch target — no callback / player-command logic touched.
        val launchIntent = try {
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, MainActivity::class.java)
        } catch (_: Exception) {
            Intent(this, MainActivity::class.java)
        }
        // 官方恢复钩子（清单里声明了 MediaButtonReceiver 就必须实现，见 Media3 文档）：
        // 进程被杀后点通知播放键/媒体按钮 → 播放器无当前曲目 → Media3 调这里要队列快照，
        // 交回后由 Media3 自己铺轨并开始播放（我们只返回数据，不接管任何 transport 命令，
        // 与 1.0.10/1.0.11 被回滚的"命令拦截式物化"是不同机制）。
        val sessionCallback = object : MediaLibrarySession.Callback {
            // 观察点（2026-10-19，纯记录）：谁连上来的。直接判定"连接方是不是
            // com.android.systemui"——被 ROM 杀进程后，若系统媒体卡片连不上来，
            // 这里就看不到 ctrl:com.android.systemui，问题范围瞬间收窄。
            // 严格只记录：super 的 ConnectionResult 原样返回，不碰命令协商/授权。
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                PlayDiag.observe(this@PlaybackService, "ctrl:${controllerLabel(controller)}")
                return super.onConnect(session, controller)
            }

            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                serviceIoScope.launch {
                    try {
                        val (songs, index) = QueueStore.loadQueue(this@PlaybackService)
                        val start = resumptionStartIndex(songs, index)
                        if (start < 0) {
                            PlayDiag.mark(this@PlaybackService, "resume-fail:no-snapshot")
                            future.setException(IllegalStateException("无队列快照，无法恢复播放"))
                            return@launch
                        }
                        val position = try {
                            QueueStore.loadPosition(this@PlaybackService, playKey(songs[start]))
                        } catch (_: Exception) {
                            0L
                        }
                        PlayDiag.mark(this@PlaybackService, "resume-ok:${songs.size}@$start")
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                songs.map { it.toCachedMediaItem() },
                                start,
                                resumptionStartPosition(position)
                            )
                        )
                    } catch (e: Exception) {
                        PlayDiag.mark(this@PlaybackService, "resume-fail:${e.javaClass.simpleName}")
                        future.setException(e)
                    }
                }
                return future
            }

            // 轻量浏览根：只为"声明内容库"以启用系统 UI 媒体恢复通道（见类注释），
            // 不对外暴露真实内容；子级查询固定返回空列表。
            // 观察点（2026-10-19，纯记录）：这两条**只在对端不被识别为系统 UI**（需要真实
            // 浏览内容树）时才会被调用，是"连上来的到底是谁"的关键信号。返回值逻辑一字未动
            // （固定空根/空表故意不委托 super——调 super 会改成"不支持"而改变行为）。
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                PlayDiag.observe(this@PlaybackService, "libroot:${controllerLabel(browser)}")
                val root = MediaItem.Builder()
                    .setMediaId(LIBRARY_ROOT_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(root, null))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                PlayDiag.observe(this@PlaybackService, "libchildren:${controllerLabel(browser)}")
                return Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), null))
            }
        }
        val sessionBuilder = MediaLibrarySession.Builder(this, exo, sessionCallback)
        try {
            sessionBuilder.setSessionActivity(
                PendingIntent.getActivity(
                    this, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } catch (_: Exception) {
            SilentFail.note("PlaybackService.onGetChildren:959")
        }
        mediaSession = sessionBuilder.build()
    }

    private fun prefetchNext() {
        val exo = player ?: return
        val count = try {
            exo.mediaItemCount
        } catch (_: Exception) {
            return
        }
        if (count <= 1) return
        val current = try {
            exo.currentMediaItemIndex
        } catch (_: Exception) {
            return
        }
        val repeatOne = try {
            exo.repeatMode == Player.REPEAT_MODE_ONE
        } catch (_: Exception) {
            false
        }
        val repeatAll = try {
            exo.repeatMode == Player.REPEAT_MODE_ALL
        } catch (_: Exception) {
            false
        }
        val shuffleOn = try {
            exo.shuffleModeEnabled
        } catch (_: Exception) {
            false
        }
        if (shuffleOn) return
        val songs = (0 until count).map { i ->
            try {
                songFromMediaItem(exo.getMediaItemAt(i))
            } catch (_: Exception) {
                Song("", "", "", "", "", 0, "")
            }
        }
        val target = selectPrefetchIndex(songs, current, repeatOne, repeatAll) ?: return
        if (target == lastPrefetchIndex) return
        val song = songs.getOrNull(target) ?: return
        if (song.sourceId.isBlank()) return
        lastPrefetchIndex = target
        val app = this
        serviceIoScope.launch {
            val bytes = MediaCache.warmUp(app, song, httpUpstream, PREFETCH_WARM_BYTES)
            lastPrefetchBytes = bytes
            try {
                android.util.Log.d("VibePrefetch", "prefetch idx=$target bytes=$bytes")
            } catch (_: Exception) {
                SilentFail.note("PlaybackService.prefetchNext:1012")
            }
        }
    }

    /**
     * 见 [nextFocusRetryIndex]：按阶梯延迟补试——只有在"仍处于暂停待播"时才重发 play()
     * （此时 playWhenReady 的 false→true 是一次真实状态切换，会重新申请音频焦点）。
     * [isRetryProbe] = 本次"被拒"是否来自我们自己的补试探测（区分被拒来源）。
     */
    private fun maybeRetryDeniedPlay(isRetryProbe: Boolean) {
        // 飞行中的探测被拒、但用户在它开火之后按了暂停 → 用户意图优先，不再排下一轮。
        if (isRetryProbe && lastUserPauseMs > lastProbeFireMs) {
            inFocusRetryProbe = false
            focusRetryActive = false
            return
        }
        val idx = nextFocusRetryIndex(wasPlayingBeforeFocusChange, focusRetries, isRetryProbe)
        if (idx < 0) {
            focusRetryActive = false
            return
        }
        // 先清掉未执行的旧探测再排新的：用户快速连点时，每轮点按都会清旧排新，
        // 避免多个探测并发堆积导致阶梯被翻倍执行。
        focusRetryHandler.removeCallbacksAndMessages(null)
        focusRetries = idx + 1
        focusRetryActive = true
        focusRetryHandler.postDelayed({
            val exo = player ?: return@postDelayed
            val userPlay = lastUserPlayMs
            val userPause = lastUserPauseMs
            val pwr = try {
                exo.playWhenReady
            } catch (_: Exception) {
                true
            }
            val playing = try {
                exo.isPlaying
            } catch (_: Exception) {
                true
            }
            if (!shouldFireRetryProbe(userPlay, userPause, playing, pwr)) {
                focusRetryActive = false
                return@postDelayed
            }
            val stillSilent = !playing && !pwr
            if (stillSilent) {
                // 意愿门：用户本进程从未点过播放 → 这轮是"被打断停掉"的歌，
                // 不是"点了被拒"的歌，绝不自动响（2026-10-11 未点按自动续播 bug）。
                if (lastUserPlayMs == 0L) {
                    focusRetryActive = false
                    return@postDelayed
                }
                PlayDiag.mark(this, "focus-retry-${idx + 1}")
                inFocusRetryProbe = true
                lastProbeFireMs = android.os.SystemClock.elapsedRealtime()
                try {
                    exo.play()
                } catch (_: Exception) {
                    // play() 异常 → 不会有 LOSS 回调 → 本轮自然结束，收掉提示状态
                    inFocusRetryProbe = false
                    focusRetryActive = false
                }
            }
        }, FOCUS_RETRY_DELAYS_MS[idx])
    }

    /**
     * 见 [shouldRescuePlayback]：每次"想播"变为 true 后启动一轮救援检查
     * （先清旧检查，重置计数——新一轮播放意图覆盖上一轮）。
     */
    private fun schedulePlayRescue() {
        if (!PLAY_RESCUE_ENABLED) return
        playRescueAttempts = 0
        playRescueHandler.removeCallbacksAndMessages(null)
        playRescueHandler.postDelayed({ runPlayRescueCheck() }, PLAY_RESCUE_CHECK_MS)
    }

    /**
     * 救援检查：命中 [shouldRescuePlayback] 时对播放器执行 stop→prepare→play
     * ——stop 进 IDLE 会让 Media3 撤回当前焦点请求，prepare/play 再走一次
     * 全新申请，从而脱离"焦点等待卡死"（没有超时机制的内部状态）。
     */
    private fun runPlayRescueCheck() {
        if (!PLAY_RESCUE_ENABLED) return
        val exo = player ?: return
        val playWhenReady = try {
            exo.playWhenReady
        } catch (_: Exception) {
            return
        }
        val isPlaying = try {
            exo.isPlaying
        } catch (_: Exception) {
            false
        }
        val state = try {
            exo.playbackState
        } catch (_: Exception) {
            return
        }
        val count = try {
            exo.mediaItemCount
        } catch (_: Exception) {
            0
        }
        if (!shouldRescuePlayback(playWhenReady, isPlaying, state, count, playRescueAttempts)) {
            // 到上限后仍"想播却无声"→ 留一条脚印（供复现时读诊断）；其他情况静默。
            if (playWhenReady && !isPlaying && playRescueAttempts >= PLAY_RESCUE_MAX_ATTEMPTS) {
                PlayDiag.mark(this, "rescue-exhausted")
            }
            return
        }
        playRescueAttempts++
        PlayDiag.mark(this, "rescue-$playRescueAttempts")
        try {
            exo.stop()
            exo.prepare()
            exo.play()
        } catch (_: Exception) {
            return
        }
        playRescueHandler.postDelayed({ runPlayRescueCheck() }, PLAY_RESCUE_CHECK_MS)
    }

    fun playQueue(songs: List<Song>, index: Int) {
        val exo = player ?: return
        lastUserPlayMs = android.os.SystemClock.elapsedRealtime()
        exo.setMediaItems(
            songs.map { it.toCachedMediaItem() },
            index.coerceIn(songs.indices),
            0L
        )
        exo.prepare()
        exo.play()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
    }

    /** 仅诊断用：控制器包名（取不到 → "unknown"）。不参与任何播放/连接决策。 */
    private fun controllerLabel(controller: MediaSession.ControllerInfo): String =
        try {
            controller.packageName.orEmpty().ifBlank { "unknown" }
        } catch (_: Exception) {
            "unknown"
        }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    // Swipe-away from recents（2.0.8 改）：不再"暂停即 stopSelf"（父类默认）。
    // 国产 ROM 用户习惯性清后台，划卡 ≠ 想停止播放——让服务/会话保留，系统媒体控件
    // （含下拉面板卡片）才能持续可用；不播放时服务处于低优先级，内存吃紧时系统仍会回收。
    // 被杀后的恢复链路：系统 UI 媒体恢复（MediaLibraryService，见类注释）+
    // Activity QueueStore-snapshot + ensureTimeline（见 MainActivity）。
    override fun onTaskRemoved(rootIntent: Intent?) {
        // 观察点（2026-10-19，纯记录）：划卡只记录，**body 仍不调 super、不 stopSelf**
        // （2.0.8 有意为之的行为，本次观察不改它）。
        PlayDiag.observe(this, "task-removed")
    }

    // 观察点（2026-10-19，纯记录）：服务被谁、用什么 action 拉起过。
    // 通知卡片点按会走 MediaButtonReceiver/系统 FGS 启动（action 各不相同），
    // "服务到底有没有被拉起"此前只能靠猜。严格只记录：super 的返回值原样返回。
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = try {
            intent?.action.orEmpty().ifBlank { "null" }
        } catch (_: Exception) {
            "unknown"
        }
        PlayDiag.observe(this, "svc-start:$action")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        // 观察点（2026-10-19，纯记录）：服务销毁（ROM 回收/正常退出都会走到）。
        PlayDiag.observe(this, "svc-destroy")
        try {
            serviceIoScope.cancel()
        } catch (_: Exception) {
            SilentFail.note("PlaybackService.onDestroy:1194")
        }
        // 补试回调持有本 Service：不清掉会把实例多留一个补试间隔才被回收。
        focusRetryHandler.removeCallbacksAndMessages(null)
        playRescueHandler.removeCallbacksAndMessages(null)
        focusRetryActive = false
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
