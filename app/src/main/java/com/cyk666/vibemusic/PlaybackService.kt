package com.cyk666.vibemusic

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cyk666.vibemusic.MediaCache.toCachedMediaItem
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
        }
    }
    if (!repeatAll) return -1
    for (i in 0 until from) {
        try {
            if (isPlayable(i)) return i
        } catch (_: Exception) {
        }
    }
    return -1
}

/**
 * Notification-path command with audible intent (verified against the
 * media3-session 1.5.1 API jar: MediaSession.Callback.onPlayerCommandRequest
 * receives each controller command for approval as
 * `int onPlayerCommandRequest(MediaSession, ControllerInfo, int)` — echo the
 * command to allow it). NARROW (post-1.0.11-ai rollback): ONLY
 * [Player.COMMAND_PLAY_PAUSE] may materialize the timeline. The 1.0.11-ai set
 * intercepted 9 command types and once surfaced a stale song, so every other
 * command — seeks and transport especially — passes through untouched.
 */
val SERVICE_MATERIALIZE_COMMANDS: Set<Int> = setOf(
    Player.COMMAND_PLAY_PAUSE
)

/** Pure: a session player command needs service-side timeline materialization. */
fun isServiceMaterializeCommand(playerCommand: Int): Boolean =
    playerCommand in SERVICE_MATERIALIZE_COMMANDS

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
 * - permanent loss (other music/video app, phone call) → pause, stays paused;
 * - transient loss → pause, stays paused (no auto-resume, see listener below);
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

/** Player action for an Android audio-focus change. Only can-duck ducks. */
enum class FocusLossAction { PAUSE, DUCK }

/**
 * Pure policy mirror of the ExoPlayer wiring above: every focus change except
 * [AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK] pauses (and regain never
 * auto-resumes — user taps play); can-duck natively ducks the volume.
 */
fun focusLossAction(focusChange: Int): FocusLossAction = when (focusChange) {
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusLossAction.DUCK
    else -> FocusLossAction.PAUSE
}

class PlaybackService : MediaSessionService() {

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
    }

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var consecFails = 0

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

    override fun onCreate() {
        super.onCreate()
        // Cache singleton warms on IO: dir create + DB open must never run
        // on Main (first tap / service recreate). The pipeline below
        // resolves the same singleton on ExoPlayer loader threads instead.
        serviceIoScope.launch {
            try {
                MediaCache.get(this@PlaybackService)
            } catch (_: Exception) {
            }
        }
        // Cache-first pipeline: CacheDataSource serves cached bytes first and fills
        // gaps from the HTTP upstream while online, so normal streaming behavior is
        // unchanged. Offline replay works for fully-cached items; a partially-cached
        // item errors on the cache hole (upstream unreachable) → auto-skip/message
        // path handles it (see MainActivity.onPlayerError).
        val upstream = DefaultHttpDataSource.Factory()
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
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(audioFocusConfig().usage)
                    .setContentType(audioFocusConfig().contentType)
                    .build(),
                audioFocusConfig().handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(audioFocusConfig().handleAudioBecomingNoisy)
            .build()
        player = exo
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (playing) {
                    consecFails = 0
                    lastStreamRetryKey = null
                    lastStreamRetryIndex = -1
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
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // ExoPlayer auto-resumes (playWhenReady=true, reason AUDIO_FOCUS_LOSS)
                // on focus regain after a transient loss; music-app policy is no
                // auto-resume — user taps play. Re-pause exactly those resumes.
                // Loss/noisy pauses arrive with playWhenReady=false (untouched);
                // user taps arrive as USER_REQUEST (untouched); ducking changes no
                // playWhenReady (untouched).
                if (playWhenReady &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
                ) {
                    try {
                        exo.pause()
                    } catch (_: Exception) {
                    }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                lastAudioSessionId = audioSessionId
            }

            override fun onPlayerError(error: PlaybackException) {
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
                        exo.replaceMediaItem(
                            errIndex,
                            songFromMediaItem(errItem).toCachedMediaItem()
                        )
                        exo.prepare()
                        exo.play()
                    } catch (_: Exception) {
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
        // NOTE (post-1.0.11-ai): the rolled-back materializer intercepted 9
        // command types and once surfaced a stale song. The narrow successor
        // above ([sessionCallback]) handles ONLY COMMAND_PLAY_PAUSE on an
        // empty timeline; Activity-side ensureTimeline stays the recovery path
        // for every other transport action. See lessons.
        // NOTE (1.0.28-ai): session-callback materializer REMOVED again after
        // 1.0.27-ai total-playback-death report (2nd incident; same suspect as
        // 1.0.10-ai). Pure helpers + tests stay pinned; Activity ensureTimeline
        // remains the single recovery path. See lessons.
        // Tap-to-open: without a session activity the notification tap does
        // nothing (users read it as "notification dead"). This only sets the
        // launch target — no callback / player-command logic touched.
        val launchIntent = try {
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, MainActivity::class.java)
        } catch (_: Exception) {
            Intent(this, MainActivity::class.java)
        }
        val sessionBuilder = MediaSession.Builder(this, exo)
        try {
            sessionBuilder.setSessionActivity(
                PendingIntent.getActivity(
                    this, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } catch (_: Exception) {
        }
        mediaSession = sessionBuilder.build()
    }

    fun playQueue(songs: List<Song>, index: Int) {
        val exo = player ?: return
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    // Swipe-away from recents: while audible the service stays foreground so
    // the session + notification survive; when already paused/idle, stop
    // quietly instead of lingering with a dead notification. Process death
    // itself (MIUI battery saver) is outside app control — recovery is the
    // Activity QueueStore-snapshot + ensureTimeline path, see MainActivity.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val playing = try {
            player?.isPlaying == true
        } catch (_: Exception) {
            false
        }
        if (!playing) {
            try {
                stopSelf()
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        try {
            serviceIoScope.cancel()
        } catch (_: Exception) {
        }
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
