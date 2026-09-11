package com.cyk666.vibemusic

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cyk666.vibemusic.MediaCache.toCachedMediaItem

/** Pure auto-skip rule: skip a broken item only while failures are few and a next item exists. */
fun shouldAutoSkip(consecFails: Int, hasNext: Boolean): Boolean = consecFails < 3 && hasNext

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
    }

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var consecFails = 0

    // Signed-URL single-retry state: exactly one same-index retry per item
    // per error episode. Reset on successful play + on index change.
    private var lastStreamRetryKey: String? = null
    private var lastStreamRetryIndex: Int = -1

    override fun onCreate() {
        super.onCreate()
        // Cache-first pipeline: CacheDataSource serves cached bytes first and fills
        // gaps from the HTTP upstream while online, so normal streaming behavior is
        // unchanged. Offline replay works for fully-cached items; a partially-cached
        // item errors on the cache hole (upstream unreachable) → auto-skip/message
        // path handles it (see MainActivity.onPlayerError).
        val upstream = DefaultHttpDataSource.Factory()
        val cache = MediaCache.get(this)
        val cacheSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(cache)
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Scheme routing: file:// download items read straight from disk via
        // FileDataSource; http(s) keeps flowing cache→upstream byte-identical
        // to before. Without this, file:// misses the cache and falls through
        // to the HTTP-only upstream, which errors on the scheme — every local
        // file failed to play and self-heal deleted the good bytes.
        val schemeFactory = DataSource.Factory { SchemeDataSource(cacheSourceFactory) }
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(schemeFactory)
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
                    // Offline: never burn the queue on holes — jump only to the
                    // next offline-playable item (valid download or cached bytes),
                    // wrapping once per repeat-all at most; otherwise stop.
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
                    val target = selectNextOfflineIndex(
                        songs,
                        from,
                        isPlayable = { idx ->
                            val s = songs.getOrNull(idx)
                            if (s == null || s.sourceId.isBlank()) false
                            else try {
                                OfflineStore.isAudioFileIntact(app, s) ||
                                    MediaCache.cachedBytes(app, s.streamUrl()) > 0
                            } catch (_: Exception) {
                                false
                            }
                        },
                        repeatAll = repeatAll
                    )
                    if (target >= 0) {
                        consecFails++
                        try {
                            exo.seekTo(target, 0L)
                            exo.prepare()
                            exo.play()
                        } catch (_: Exception) {
                        }
                    } else {
                        consecFails++
                        try {
                            exo.stop()
                        } catch (_: Exception) {
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
                    exo.seekToNextMediaItem()
                    exo.prepare()
                    exo.play()
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

    fun next() {
        val exo = player ?: return
        if (exo.hasNextMediaItem()) exo.seekToNextMediaItem() else exo.seekTo(0L)
    }

    fun prev() {
        val exo = player ?: return
        if (exo.hasPreviousMediaItem()) exo.seekToPreviousMediaItem() else exo.seekTo(0L)
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
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
