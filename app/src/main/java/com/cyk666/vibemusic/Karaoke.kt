package com.cyk666.vibemusic

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONArray

// Karaoke renderer (dual-mode, zero backend dependency).
//
// Mode A (true word timing): LyricLine.words carries per-word [startMs, endMs).
//   Active word = champagne bold, completed words = champagne, upcoming = muted.
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

/**
 * Dual-mode karaoke line. Inactive lines render fully muted (matches the old
 * lyric list look); the active line lights words/chars champagne up to
 * [positionMs], with the current word bold.
 */
@Composable
fun KaraokeLine(
    line: LyricLine,
    positionMs: Long,
    lineEndMs: Long,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val words = remember(line, lineEndMs) {
        val w = line.words?.takeIf { it.isNotEmpty() }
        w ?: proportionalSplit(line.text, (line.timeSec * 1000).toLong(), lineEndMs)
    }
    if (!isActive) {
        Text(
            text = if (words.isNotEmpty()) words.joinToString("") { it.text } else line.text.ifBlank { " " },
            style = MaterialTheme.typography.bodyMedium,
            color = KaraokeDim,
            modifier = modifier
        )
        return
    }
    val activeIdx = wordAtTime(words, positionMs)
    val annotated = buildAnnotatedString {
        if (words.isEmpty()) {
            pushStyle(SpanStyle(color = KaraokeDim))
            append(line.text.ifBlank { " " })
            pop()
        } else {
            words.forEachIndexed { i, w ->
                val lit = positionMs >= w.startMs
                pushStyle(
                    SpanStyle(
                        color = if (lit) KaraokeLit else KaraokeDim,
                        fontWeight = if (i == activeIdx) FontWeight.Bold else FontWeight.Normal
                    )
                )
                append(w.text)
                pop()
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
    )
}
