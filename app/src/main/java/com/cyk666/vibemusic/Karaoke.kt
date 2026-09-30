package com.cyk666.vibemusic

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import org.json.JSONArray

// Karaoke renderer (dual-mode, zero backend dependency).
//
// Mode A (true word timing): LyricLine.words carries per-word [startMs, endMs).
//   The active line sweep-fills bright left-to-right, interpolated inside the
//   active word; completed share stays bright, upcoming stays muted.
// Mode B (fallback, available today): backend sends line timing only, so the
//   line duration is split evenly across Unicode code points. This is an
//   APPROXIMATION — it tracks line progress, not the true vocal beat. It
//   activates automatically per line whenever words is null/empty; Mode A
//   takes over the moment LyricLine carries words (backend yrc work is a
//   separate later task and needs no UI change).

private val KaraokeLit = Color(0xFFF5E6C8)
private val KaraokeDim = Color(0xFF9CA3AF)

/** A single word with timing. endMs < 0 = unknown (runs until the next word). */
data class WordTimed(val startMs: Long, val endMs: Long, val text: String)

/** Sentinel for "word end unknown" (last word of an inline-tagged line). */
const val WORD_END_UNKNOWN = -1L

private val LINE_TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
private val WORD_TAG = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")

/** Pure: [mm:ss.xx] / <mm:ss.xx> timestamp → ms. Frac is centis or millis. */
fun lrcTimestampToMs(min: String, sec: String, frac: String?): Long {
    val m = min.toLongOrNull() ?: return -1L
    val s = sec.toLongOrNull() ?: return -1L
    if (m < 0 || s < 0 || s >= 60) return -1L
    val ms = when (frac?.length) {
        null, 0 -> 0L
        1 -> (frac.toLongOrNull() ?: return -1L) * 100L
        2 -> (frac.toLongOrNull() ?: return -1L) * 10L
        else -> (frac.take(3).toLongOrNull() ?: return -1L)
    }
    return m * 60_000L + s * 1_000L + ms
}

/**
 * Pure: parse one enhanced-LRC line into word timings.
 * Format: `[mm:ss.xx]<mm:ss.xx>word1<mm:ss.xx>word2…` — leading [...] tags
 * give the line start; each <...> tag starts a word running to the next tag.
 * Plain lines (no valid tags) → empty list (caller falls back to Mode B).
 * Malformed tags (bad numbers, missing brackets) are kept as literal text,
 * never dropped; blank segments between adjacent tags are skipped.
 */
fun parseEnhancedLrcLine(line: String): List<WordTimed> {
    var rest = line
    var lineStartMs = 0L
    var sawLineTag = false
    while (true) {
        val m = LINE_TAG.find(rest) ?: break
        if (m.range.first != 0) break
        val ms = lrcTimestampToMs(m.groupValues[1], m.groupValues[2], m.groupValues[3].ifEmpty { null })
        if (ms < 0) break
        if (!sawLineTag) {
            lineStartMs = ms
            sawLineTag = true
        }
        rest = rest.substring(m.range.last + 1)
    }
    val tags = WORD_TAG.findAll(rest).toList()
    if (tags.isEmpty()) return emptyList()
    val out = ArrayList<WordTimed>(tags.size + 1)
    val head = rest.substring(0, tags.first().range.first)
    if (head.isNotBlank()) out.add(WordTimed(lineStartMs, tags.first().let {
        lrcTimestampToMs(it.groupValues[1], it.groupValues[2], it.groupValues[3].ifEmpty { null })
    }, head))
    tags.forEachIndexed { i, t ->
        val start = lrcTimestampToMs(t.groupValues[1], t.groupValues[2], t.groupValues[3].ifEmpty { null })
        if (start < 0) return@forEachIndexed
        val segStart = t.range.last + 1
        val segEnd = if (i + 1 < tags.size) tags[i + 1].range.first else rest.length
        val seg = rest.substring(segStart.coerceAtMost(rest.length), segEnd.coerceAtMost(rest.length))
        if (seg.isNotBlank()) {
            val end = if (i + 1 < tags.size) {
                lrcTimestampToMs(
                    tags[i + 1].groupValues[1],
                    tags[i + 1].groupValues[2],
                    tags[i + 1].groupValues[3].ifEmpty { null }
                ).takeIf { it >= 0 }
            } else {
                null
            }
            out.add(WordTimed(start, end ?: WORD_END_UNKNOWN, seg))
        }
    }
    return out.sortedBy { it.startMs }
}

/**
 * Pure: backend credit/promo line detector. Matches ONLY at line start,
 * followed by whitespace, ':'/'：', or end-of-line — so a real lyric like
 * "编曲的故事" (prefix glued to lyric text) never matches. Credits never
 * appear mid-song in practice, hence filtered ANYWHERE, not just leading
 * lines. Case-insensitive for the OP/SP/ED Latin tags.
 */
private val CREDIT_PREFIX = Regex(
    "^(作词|作曲|编曲|制作人|出品|主题曲|片头|片尾|OP|SP|ED)(?=\\s|:|：|$)",
    RegexOption.IGNORE_CASE
)

fun isCreditLine(text: String): Boolean = CREDIT_PREFIX.containsMatchIn(text.trimStart())

/** Pure: lyric line with no readable text (tags stripped, blank). */
fun isBlankLyricLine(text: String): Boolean = stripInlineTags(text).trim().isEmpty()

private fun isLyricMusicSymbolCp(cp: Int): Boolean = cp == '♪'.code ||
    cp == '♫'.code || cp == '♩'.code || cp == '♬'.code ||
    cp == '♭'.code || cp == '♮'.code || cp == '♯'.code ||
    cp in 0x1D100..0x1D1FF ||
    cp in 0x2600..0x27BF ||
    cp in 0x1F300..0x1FAFF ||
    cp == 0xFE0F || cp == 0x200D

/**
 * Pure: line carries no readable lyric — only music symbols/emoji/punctuation
 * around at least one music symbol (e.g. "♪", "♪～♪"). Any letter/digit
 * (incl. CJK) conserves the line; blank lines report false here (see
 * [isBlankLyricLine]). Callers drop lines where either is true.
 */
fun isMusicSymbolLine(text: String): Boolean {
    val plain = stripInlineTags(text).trim()
    if (plain.isEmpty()) return false
    var sawMusic = false
    var i = 0
    while (i < plain.length) {
        val cp = plain.codePointAt(i)
        if (!Character.isWhitespace(cp)) {
            if (isLyricMusicSymbolCp(cp)) sawMusic = true
            else if (Character.isLetterOrDigit(cp)) return false
        }
        i += Character.charCount(cp)
    }
    return sawMusic
}

/**
 * Pure: strip transport noise from a backend lyric line — carriage
 * returns, XML-escaped entities, surrounding whitespace. Tag syntax
 * ([...]/<...>) is preserved for the word-timing parsers downstream.
 */
fun stripLyricNoise(text: String): String = text
    .replace("\r", "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .trim()

/** Pure: strip valid [...] line tags and <...> word tags; malformed kept. */
fun stripInlineTags(text: String): String {
    var rest = text
    while (true) {
        val m = LINE_TAG.find(rest) ?: break
        if (m.range.first != 0) break
        rest = rest.substring(m.range.last + 1)
    }
    return WORD_TAG.replace(rest, "")
}

/**
 * Pure: tolerant parse of a backend "words" JSONArray.
 * Entry keys: startMs/start, endMs/end, text/word. Numbers are ms (Number or
 * numeric String); unparseable entries are skipped, blank texts dropped,
 * missing end → WORD_END_UNKNOWN. Absent/empty array → null (Mode B).
 */
fun parseWordsJson(arr: JSONArray?): List<WordTimed>? {
    if (arr == null || arr.length() == 0) return null
    val out = ArrayList<WordTimed>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val start = o.opt("startMs").asMs() ?: o.opt("start").asMs() ?: continue
        val end = o.opt("endMs").asMs() ?: o.opt("end").asMs() ?: WORD_END_UNKNOWN
        val text = o.optString("text").ifBlank { o.optString("word") }
        if (text.isBlank()) continue
        out.add(WordTimed(start, if (end >= 0 && end < start) WORD_END_UNKNOWN else end, text))
    }
    if (out.isEmpty()) return null
    return out.sortedBy { it.startMs }
}

private fun Any?.asMs(): Long? = when (this) {
    is Number -> toLong().takeIf { it >= 0 }
    is String -> toLongOrNull()?.takeIf { it >= 0 }
    else -> null
}

/** Pure: index of the word containing [positionMs] (start ≤ pos < end); -1 if none. */
fun wordAtTime(words: List<WordTimed>, positionMs: Long): Int {
    words.forEachIndexed { i, w ->
        if (positionMs >= w.startMs && (w.endMs < 0 || positionMs < w.endMs)) return i
    }
    return -1
}

/**
 * Pure APPROXIMATION (Mode B): split [text] evenly across code points over
 * [lineStartMs, lineEndMs). Tracks line progress, NOT the vocal beat.
 * Empty text or non-positive duration → empty list (renders dim).
 */
fun proportionalSplit(text: String, lineStartMs: Long, lineEndMs: Long): List<WordTimed> {
    if (text.isEmpty()) return emptyList()
    if (lineEndMs <= lineStartMs) return emptyList()
    val chars = ArrayList<String>(text.length)
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        chars.add(String(Character.toChars(cp)))
        i += Character.charCount(cp)
    }
    if (chars.isEmpty()) return emptyList()
    val dur = lineEndMs - lineStartMs
    return chars.mapIndexed { idx, c ->
        val s = lineStartMs + dur * idx / chars.size
        val e = if (idx == chars.size - 1) lineEndMs else lineStartMs + dur * (idx + 1) / chars.size
        WordTimed(s, e, c)
    }
}

/** Active-line smoothing: animateFloatAsState tween toward the target fraction. */
const val KARAOKE_SMOOTH_MS = 120

/** Fast lyric ticker interval (active line only, lyrics-visible only). */
const val LYRIC_FAST_TICK_MS = 100L

/**
 * Pure: fraction of [lineStartMs, lineEndMs) covered at [positionMs],
 * clamped to 0..1. Degenerate windows (end <= start) snap to 0/1 by side.
 */
fun karaokeLineFraction(positionMs: Long, lineStartMs: Long, lineEndMs: Long): Float {
    if (lineEndMs <= lineStartMs) return if (positionMs >= lineStartMs) 1f else 0f
    return ((positionMs - lineStartMs).toFloat() / (lineEndMs - lineStartMs).toFloat())
        .coerceIn(0f, 1f)
}

/**
 * Pure: advance the local 100ms lyric ticker by [elapsedMs] while playing;
 * paused (or non-positive elapsed) leaves the value untouched, never negative.
 */
fun advanceLyricTicker(currentMs: Long, elapsedMs: Long, playing: Boolean): Long {
    if (!playing || elapsedMs <= 0L) return currentMs.coerceAtLeast(0L)
    return (currentMs + elapsedMs).coerceAtLeast(0L)
}

/** Pure: effective lyric position from a smoothed fraction (inverse of fraction). */
fun smoothLyricPosition(lineStartMs: Long, lineEndMs: Long, fraction: Float): Long {
    val f = fraction.coerceIn(0f, 1f)
    return lineStartMs + ((lineEndMs - lineStartMs).toDouble() * f).toLong()
}

/**
 * Pure: continuous left-to-right sweep fraction for the active lyric line.
 *
 * Word timings drive the sweep: each word owns a horizontal share
 * proportional to its code-point length, and inside the active word the
 * fraction interpolates linearly between word start and word end — so the
 * bright edge glides smoothly instead of popping word by word. A word with
 * unknown end runs until the next word start (or [lineEndMs] for the last
 * word); zero-length windows light instantly once started. Empty word lists
 * fall back to plain line-level progress ([lineStartMs] → [lineEndMs]).
 * Result is always clamped to 0..1 with no allocations beyond the loop.
 */
fun karaokeSweepFraction(
    words: List<WordTimed>,
    positionMs: Long,
    lineStartMs: Long,
    lineEndMs: Long
): Float {
    if (words.isEmpty()) return karaokeLineFraction(positionMs, lineStartMs, lineEndMs)
    var total = 0
    for (w in words) total += w.text.codePointCount(0, w.text.length)
    if (total <= 0) return karaokeLineFraction(positionMs, lineStartMs, lineEndMs)
    var done = 0
    for (i in words.indices) {
        val w = words[i]
        val weight = w.text.codePointCount(0, w.text.length)
        val start = w.startMs
        val end = if (w.endMs >= 0) w.endMs
        else words.getOrNull(i + 1)?.startMs?.takeIf { it > start } ?: lineEndMs
        if (positionMs < start) return done.toFloat() / total
        if (end <= start) {
            done += weight
            continue
        }
        if (positionMs >= end) {
            done += weight
            continue
        }
        val local = (positionMs - start).toFloat() / (end - start).toFloat()
        return (done + weight * local.coerceIn(0f, 1f)) / total
    }
    return 1f
}

/**
 * Pure: pixel clip edge for the sweep overlay. The overlay always lays out at
 * full row width (identical wrapping to the dim base); only this draw-phase
 * right edge moves with the animated fraction. Clamped to 0..width so a dead
 * fraction never overdraws and float drift never clips outside the row.
 */
fun karaokeSweepClipRight(widthPx: Float, fraction: Float): Float {
    val w = widthPx.coerceAtLeast(0f)
    return (w * fraction.coerceIn(0f, 1f)).coerceIn(0f, w)
}

// ---- Lyrics batch: manual karaoke offset (per-song, persisted) ----

/** Manual karaoke offset range: ±2s covers slow-LRC skew; step 0.1s is tappable. */
const val LYRIC_OFFSET_MAX_MS = 2_000L
const val LYRIC_OFFSET_STEP_MS = 100L

/**
 * Pure: clamp a manual karaoke offset into ±[LYRIC_OFFSET_MAX_MS].
 * NaN-proof by construction (Long domain, no float math).
 */
fun clampLyricOffset(offsetMs: Long): Long =
    offsetMs.coerceIn(-LYRIC_OFFSET_MAX_MS, LYRIC_OFFSET_MAX_MS)

/**
 * Pure: step the offset by [steps] × [LYRIC_OFFSET_STEP_MS], clamped.
 * Positive steps push the highlight earlier (lyrics lead the vocal).
 */
fun stepLyricOffset(currentMs: Long, steps: Int): Long =
    clampLyricOffset(currentMs + steps * LYRIC_OFFSET_STEP_MS)

/**
 * Pure: effective lyric clock = playback position shifted by the manual
 * offset. Positive offset advances the highlight (for LRC that lags the
 * vocal); negative delays it. Never negative — line lookup uses <= against
 * line starts, so a negative clock simply precedes the first line.
 */
fun applyLyricOffset(positionMs: Long, offsetMs: Long): Long =
    (positionMs + clampLyricOffset(offsetMs)).coerceAtLeast(0L)

/**
 * Pure: Chinese offset label for the calibration row.
 * Zero → "±0秒" (truthful neutral, doubles as the reset affordance hint).
 */
fun formatLyricOffset(offsetMs: Long): String {
    val v = clampLyricOffset(offsetMs)
    if (v == 0L) return "±0秒"
    val sign = if (v > 0) "+" else "−"
    val abs = kotlin.math.abs(v)
    return "$sign${abs / 1000}.${(abs % 1000) / 100}秒"
}

// ---- Lyrics batch: no-lyric fallback reason ----

/**
 * Why the lyrics view is empty. Every branch carries user-facing Chinese
 * copy — empty states must explain why, never a bare 暂无歌词.
 */
enum class LyricEmptyReason(val copy: String) {
    /** Transport failure (LyricUiState.Failed): retry may help. */
    LOAD_FAILED("歌词加载失败，请检查网络后重试"),
    /** Bilibili has no guest lyric source (backend returns [] by design). */
    NO_BILI_SOURCE("B站歌曲暂无歌词源，可换其他版本试试"),
    /** Any other platform returned zero usable lines. */
    NO_LYRIC("这首歌暂无歌词，可换其他版本试试");
}

/**
 * Pure: resolve the empty-state reason.
 *
 * @param state current lyric fetch state (null/Loading = not empty yet).
 * @param song current song (platform decides the bili branch).
 * @param visibleLines lines surviving the blank/symbol filter — the filter
 *   can empty a non-empty payload (e.g. a "♪"-only LRC), which is NO_LYRIC,
 *   not a load failure.
 * @return null when there is something to show (or still loading).
 */
fun describeLyricEmpty(
    state: LyricUiState?,
    song: Song?,
    visibleLines: Int
): LyricEmptyReason? {
    if (state == null || state is LyricUiState.Loading) return null
    if (state is LyricUiState.Failed) return LyricEmptyReason.LOAD_FAILED
    if (visibleLines > 0) return null
    if (song != null && isBilibiliSong(song)) return LyricEmptyReason.NO_BILI_SOURCE
    return LyricEmptyReason.NO_LYRIC
}

// ---- Lyrics batch: one-tap switch to same song on other source ----

/**
 * Pure: pick the best alternate-source candidate for the same song.
 * Skips the current item itself (same sourceId); prefers a DIFFERENT
 * platform (netease↔qq↔kugou cross-source lyric odds), else the first
 * different sourceId. Blank-sourceId candidates are never playable.
 * Null = no other version found (caller shows 没找到… instead of switching).
 */
fun pickAlternateSource(candidates: List<Song>, current: Song): Song? {
    val others = candidates.filter {
        it.sourceId.isNotBlank() && it.sourceId != current.sourceId
    }
    if (others.isEmpty()) return null
    return others.firstOrNull { it.platform != current.platform } ?: others.first()
}

/**
 * Sweep-fill karaoke line: a dim base text plus a bright overlay clipped to
 * the [karaokeSweepFraction] width, so the current line fills smoothly
 * left-to-right synced to word timings. Inactive lines render fully muted.
 * The overlay is laid out at the same full width as the base (identical
 * wrapping); only a GPU clipRect moves per frame — no Box width constraint
 * is ever set from the animated fraction (fillMaxWidth().width(lit) would
 * clamp lit back to the full row via enforceIncoming and pin the highlight
 * full-on from the first frame).
 */
@Composable
fun KaraokeLine(
    line: LyricLine,
    positionMs: Long,
    lineEndMs: Long,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val lineStartMs = (line.timeSec * 1000).toLong()
    val words = remember(line, lineEndMs) {
        val w = line.words?.takeIf { it.isNotEmpty() }
        w ?: proportionalSplit(line.text, lineStartMs, lineEndMs)
    }
    val plain = if (words.isNotEmpty()) {
        buildString { words.forEach { append(it.text) } }
    } else {
        line.text.ifBlank { " " }
    }
    if (!isActive) {
        Text(
            text = plain,
            style = MaterialTheme.typography.bodyMedium,
            color = KaraokeDim,
            modifier = modifier
        )
        return
    }
    val target = karaokeSweepFraction(words, positionMs, lineStartMs, lineEndMs)
    val sweep by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(KARAOKE_SMOOTH_MS),
        label = "karaokeSweep"
    )
    val style = MaterialTheme.typography.titleMedium
    Box(modifier = modifier.fillMaxWidth()) {
        Text(text = plain, style = style, color = KaraokeDim)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    clipRect(right = karaokeSweepClipRight(size.width, sweep)) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            Text(
                text = plain,
                style = style,
                color = KaraokeLit,
                maxLines = Int.MAX_VALUE
            )
        }
    }
}
