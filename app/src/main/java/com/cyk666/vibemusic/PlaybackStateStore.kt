package com.cyk666.vibemusic

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.playbackDataStore by preferencesDataStore(name = "playback")

/** Throttled position-save gate: at most one write per 10s during playback. */
const val POSITION_SAVE_INTERVAL_MS = 10_000L

/** Restore window: skip intros/outros, ignore unknown durations. */
const val POSITION_RESTORE_MIN_MS = 5_000L
const val POSITION_RESTORE_END_MARGIN_MS = 10_000L

/** Pure: restore only a mid-track position (past intro, before outro). */
fun shouldRestorePosition(savedMs: Long, durationMs: Long): Boolean {
    if (durationMs <= 0L) return false
    return savedMs > POSITION_RESTORE_MIN_MS &&
        savedMs < durationMs - POSITION_RESTORE_END_MARGIN_MS
}

/** Pure: throttle gate for periodic saves during playback. */
fun shouldSavePositionTick(nowMs: Long, lastSavedMs: Long): Boolean =
    nowMs - lastSavedMs >= POSITION_SAVE_INTERVAL_MS

fun parsePositions(json: String): Map<String, Long> {
    if (json.isBlank()) return emptyMap()
    return try {
        val o = JSONObject(json)
        val out = LinkedHashMap<String, Long>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = o.optLong(k, 0L).coerceAtLeast(0L)
        }
        out
    } catch (_: Exception) {
        emptyMap()
    }
}

fun renderPositions(positions: Map<String, Long>): String {
    val o = JSONObject()
    for ((k, v) in positions) o.put(k, v)
    return o.toString()
}

/** First-run start destination: LOGIN forces the login gate, SEARCH_GUEST keeps
 * today's guest landing, RESTORE attempts silent token restore via /me. */
enum class StartRoute { LOGIN, SEARCH_GUEST, RESTORE }

/** Pure: blank token + never launched -> LOGIN; blank token + launched -> guest
 * Search; stored token -> RESTORE (validate via /me, guest-null stays guest). */
fun decideStartRoute(tokenBlank: Boolean, launchedBefore: Boolean): StartRoute =
    when {
        !tokenBlank -> StartRoute.RESTORE
        !launchedBefore -> StartRoute.LOGIN
        else -> StartRoute.SEARCH_GUEST
    }

/** Persists last queue so a recreated Activity can restore UI without autoplay. */
object QueueStore {
    private val KEY_QUEUE = stringPreferencesKey("queue_json")
    private val KEY_INDEX = intPreferencesKey("index")
    private val KEY_REPEAT = intPreferencesKey("repeat_mode")
    private val KEY_SHUFFLE = booleanPreferencesKey("shuffle_on")
    private val KEY_PLAY_COUNTS = stringPreferencesKey("play_counts")
    private val KEY_SLEEP_MIN = intPreferencesKey("sleep_timer_min")
    private val KEY_POSITIONS = stringPreferencesKey("positions_json")
    private val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_ms")
    private val KEY_HAS_LAUNCHED = booleanPreferencesKey("has_launched_before")

    suspend fun saveQueue(context: Context, songs: List<Song>, index: Int) {
        val arr = JSONArray()
        for (s in songs) {
            arr.put(
                JSONObject()
                    .put("sourceId", s.sourceId)
                    .put("name", s.name)
                    .put("artist", s.artist)
                    .put("album", s.album)
                    .put("coverUrl", s.coverUrl)
                    .put("durationSec", s.durationSec)
                    .put("platform", s.platform)
            )
        }
        context.playbackDataStore.edit { p ->
            p[KEY_QUEUE] = arr.toString()
            p[KEY_INDEX] = index
        }
    }

    suspend fun loadQueue(context: Context): Pair<List<Song>, Int> {
        return try {
            val snap = context.playbackDataStore.data.map { p ->
                Pair(p[KEY_QUEUE].orEmpty(), p[KEY_INDEX] ?: 0)
            }.first()
            if (snap.first.isBlank()) return Pair(emptyList(), 0)
            val arr = JSONArray(snap.first)
            val list = ArrayList<Song>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Song(
                        sourceId = o.optString("sourceId"),
                        name = o.optString("name"),
                        artist = o.optString("artist"),
                        album = o.optString("album"),
                        coverUrl = o.optString("coverUrl"),
                        durationSec = o.optInt("durationSec"),
                        platform = o.optString("platform").ifBlank { "netease" }
                    )
                )
            }
            Pair(list, snap.second)
        } catch (_: Exception) {
            Pair(emptyList(), 0)
        }
    }

    /** Phase 7: persist play mode as (repeatMode, shuffleOn) in the same "playback" file. */
    suspend fun savePlayMode(context: Context, repeatMode: Int, shuffleOn: Boolean) {
        context.playbackDataStore.edit { p ->
            p[KEY_REPEAT] = repeatMode
            p[KEY_SHUFFLE] = shuffleOn
        }
    }

    suspend fun incrementPlayCount(context: Context, key: String): Int {
        return try {
            var next = 1
            context.playbackDataStore.edit { p ->
                val (updated, count) = bumpPlayCount(
                    parsePlayCounts(p[KEY_PLAY_COUNTS].orEmpty()),
                    key
                )
                p[KEY_PLAY_COUNTS] = renderPlayCounts(updated)
                next = count
            }
            next
        } catch (_: Exception) {
            0
        }
    }

    suspend fun getPlayCount(context: Context, key: String): Int {
        return try {
            context.playbackDataStore.data.map { p ->
                parsePlayCounts(p[KEY_PLAY_COUNTS].orEmpty())[key] ?: 0
            }.first()
        } catch (_: Exception) {
            0
        }
    }

    suspend fun saveSleepMinutes(context: Context, minutes: Int) {
        context.playbackDataStore.edit { p ->
            p[KEY_SLEEP_MIN] = minutes.coerceAtLeast(0)
        }
    }

    suspend fun loadSleepMinutes(context: Context): Int {
        return try {
            context.playbackDataStore.data.map { p ->
                p[KEY_SLEEP_MIN] ?: 0
            }.first().coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }

    /** Progress-survives-restart: per-song position keyed by playKey (<platform>:<sourceId>). */
    suspend fun savePosition(context: Context, key: String, positionMs: Long) {
        if (key.isBlank() || positionMs < 0L) return
        try {
            context.playbackDataStore.edit { p ->
                val updated = parsePositions(p[KEY_POSITIONS].orEmpty()).toMutableMap()
                updated[key] = positionMs
                p[KEY_POSITIONS] = renderPositions(updated)
            }
        } catch (_: Exception) {
        }
    }

    suspend fun loadPosition(context: Context, key: String): Long {
        if (key.isBlank()) return 0L
        return try {
            context.playbackDataStore.data.map { p ->
                parsePositions(p[KEY_POSITIONS].orEmpty())[key] ?: 0L
            }.first().coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    /** Phase 7: load persisted play mode; unset → 顺序 (repeat OFF + shuffle OFF). */
    suspend fun loadPlayMode(context: Context): RepeatShuffle {
        return try {
            context.playbackDataStore.data.map { p ->
                RepeatShuffle(
                    repeatMode = p[KEY_REPEAT] ?: Player.REPEAT_MODE_OFF,
                    shuffleOn = p[KEY_SHUFFLE] ?: false
                )
            }.first()
        } catch (_: Exception) {
            RepeatShuffle(Player.REPEAT_MODE_OFF, false)
        }
    }

    suspend fun loadLastUpdateCheck(context: Context): Long {
        return try {
            context.playbackDataStore.data.map { p ->
                p[KEY_LAST_UPDATE_CHECK] ?: 0L
            }.first().coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun saveLastUpdateCheck(context: Context, nowMs: Long) {
        try {
            context.playbackDataStore.edit { p ->
                p[KEY_LAST_UPDATE_CHECK] = nowMs.coerceAtLeast(0L)
            }
        } catch (_: Exception) {
        }
    }

    /** First-run gate flag (lives in "playback" file; "auth" store untouched). */
    suspend fun loadHasLaunchedBefore(context: Context): Boolean {
        return try {
            context.playbackDataStore.data.map { p ->
                p[KEY_HAS_LAUNCHED] ?: false
            }.first()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun saveHasLaunchedBefore(context: Context) {
        try {
            context.playbackDataStore.edit { p ->
                p[KEY_HAS_LAUNCHED] = true
            }
        } catch (_: Exception) {
        }
    }
}
