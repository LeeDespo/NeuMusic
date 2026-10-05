package com.neumusic.player.data.api

import com.neumusic.player.data.LyricLine
import com.neumusic.player.data.LyricWord
import com.neumusic.player.data.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Rust fetches/decodes; the host keeps lyric rendering, alignment and display optimization. */
object LyricApi {
    private const val NO_TRANS_PLACEHOLDER = "//"
    private val cache = HashMap<String, Lyrics>()
    private const val CACHE_MAX = 24
    fun clear() = synchronized(cache) { cache.clear() }

    suspend fun lyricsFor(mid: String): Lyrics? = withContext(Dispatchers.IO) {
        if (mid.isEmpty()) return@withContext null
        synchronized(cache) { cache[mid] }?.let { return@withContext it }
        runCatching { fetch(mid) }.getOrNull()?.also { lyrics -> synchronized(cache) {
            if (cache.size >= CACHE_MAX) cache.clear()
            cache[mid] = lyrics
        } }
    }

    private suspend fun fetch(mid: String): Lyrics? {
        val response = HelperNext.call("fetch_lyric", JSONObject().put("songMid", mid).put("wordTiming", true).put("translation", true))
        return fromComponent(response)
    }

    internal fun fromComponent(response: JSONObject): Lyrics? {
        val lyric = response.getJSONObject("lyric")
        val qrc = timedLines(lyric.optJSONArray("qrcLines"))
        val lines = qrc.ifEmpty { parseLrc(lyric.text("lyric")) }
        if (lines.isEmpty()) return null
        val translation = lyric.text("translation")
        val roman = timedLines(lyric.optJSONArray("romanLines")).ifEmpty { parseLrc(lyric.text("romanization")) }
        return Lyrics(optimize(attachRoman(attachKana(mergeTranslation(lines, parseLrc(translation)),
            parseKanaTokens(translation)), roman)))
    }

    private fun timedLines(lines: JSONArray?): List<LyricLine> = lines.items { line ->
        val words = line.optJSONArray("words").items { word ->
            val start = word.getLong("startMs")
            LyricWord(word.getString("text"), start, start + word.getLong("durationMs"))
        }
        LyricLine(line.getLong("startMs"), words.joinToString("") { it.text }, words = words)
    }

    /** 按时间戳把翻译并到歌词行；`//` 占位行与对不上时间戳的（±300ms）忽略。 */
    private fun mergeTranslation(lines: List<LyricLine>, trans: List<LyricLine>): List<LyricLine> {
        if (trans.isEmpty()) return lines
        val byTime = trans.associateBy { it.timeMs }
        return lines.map { line ->
            val near = byTime[line.timeMs]
                ?: trans.minByOrNull { kotlin.math.abs(it.timeMs - line.timeMs) }
                    ?.takeIf { kotlin.math.abs(it.timeMs - line.timeMs) <= 300L }
            val t = near?.text
            if (near == null || t.isNullOrBlank() || t == NO_TRANS_PLACEHOLDER) line
            else line.copy(translation = t)
        }
    }

    /** 把解出的 roma 行按行起点对齐回主歌词行（±150ms）。 */
    private fun attachRoman(lines: List<LyricLine>, roma: List<LyricLine>): List<LyricLine> {
        if (roma.isEmpty()) return lines
        return lines.map { line ->
            val r = roma.firstOrNull { kotlin.math.abs(it.timeMs - line.timeMs) <= 150L }
                ?: return@map line
            val text = r.text.trim()
            if (text.isEmpty()) line else line.copy(roman = text)
        }
    }

    // ───────────────────────── 注音（kana） ─────────────────────────

    /** `[kana:…]` 里的一个 token：<n 个字符><读音>。 */
    private data class KanaToken(val chars: Int, val reading: String)

    private val KANA_TAG = Regex("""\[kana:(.*?)]""")
    private val KANA_TOKEN = Regex("""(\d+)(\D+)""")

    /** 解析翻译 LRC 里的 `[kana:…]` 元数据；没有该标签（非日文曲）返回空表。 */
    private fun parseKanaTokens(transRaw: String): List<KanaToken> {
        if (transRaw.isBlank()) return emptyList()
        val lrc = transRaw
        val body = KANA_TAG.find(lrc)?.groupValues?.get(1) ?: return emptyList()
        return KANA_TOKEN.findAll(body).map { m ->
            KanaToken(m.groupValues[1].toIntOrNull() ?: 1, m.groupValues[2])
        }.toList()
    }

    /** CJK 统一表意文字（含扩展 A 与叠字符号 々）——注音元数据只给这类字读音。 */
    private fun isKanji(c: Char): Boolean =
        c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF || c.code == 0x3005

    /**
     * 把注音 token 流按时间序铺到歌词行上（实测对齐规则：**只有汉字消费 token**）。
     *
     * 同时产出两级数据：
     * - 逐字 [LyricWord.kana]：该字的读音（非汉字保留原字）→ 逐字行渲染成字上注音；
     * - 行级 [LyricLine.kana]：整行的假名读音 → 行级 LRC 渲染成读音行。
     *
     * token 的 `chars>1`（极少见）表示该读音覆盖后续若干字符，跳过它们的独立消费。
     */
    private fun attachKana(lines: List<LyricLine>, tokens: List<KanaToken>): List<LyricLine> {
        if (tokens.isEmpty()) return lines
        var ti = 0
        var skip = 0
        return lines.map { line ->
            if (line.hasWords) {
                var covered = false
                val words = line.words.map { w ->
                    val sb = StringBuilder()
                    for (c in w.text) {
                        when {
                            skip > 0 -> { skip--; sb.append(c) }   // 已被上一读音覆盖
                            ti < tokens.size && isKanji(c) -> {
                                val t = tokens[ti++]
                                sb.append(t.reading)
                                covered = true
                                if (t.chars > 1) skip = t.chars - 1
                            }
                            else -> sb.append(c)
                        }
                    }
                    w.copy(kana = sb.toString())
                }
                if (!covered) line else line.copy(words = words)
            } else {
                val sb = StringBuilder()
                var covered = false
                for (c in line.text) {
                    when {
                        skip > 0 -> { skip--; sb.append(c) }
                        ti < tokens.size && isKanji(c) -> {
                            val t = tokens[ti++]
                            sb.append(t.reading)
                            covered = true
                            if (t.chars > 1) skip = t.chars - 1
                        }
                        else -> sb.append(c)
                    }
                }
                if (!covered) line else line.copy(kana = sb.toString())
            }
        }
    }

    // ───────────────────────── 歌词优化 ─────────────────────────

    /**
     * 歌词优化（AMLL 同款策略）：
     * 1. 空格规范化（连续空格并成一个）；
     * 2. 清洗非刻意重叠（<500ms 且 ≤100ms 或 ≤下一行时长 10% → 截断上一行末字）；
     * 3. 让行提前开始（最多 600ms；间隔不足时提前 400ms / 间隔的 70%）。
     * 只动行起点与末字结束，字的时间轴不动。
     */
    private fun optimize(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines
        // 1) 空格规范化（只动行文本；逐字行的字单元自带空白，渲染不受影响）
        val spaced = lines.map { l ->
            val t = l.text.replace(Regex("""\s+"""), " ")
            if (t != l.text) l.copy(text = t) else l
        }
        // 2) 清洗非刻意重叠（prev = 上一行；它的末字越过本行起点且不算刻意时截断）
        val cleaned = ArrayList<LyricLine>(spaced.size)
        for (l in spaced) {
            val prev = cleaned.lastOrNull()
            if (prev != null && prev.endMs > 0) {
                val overlap = prev.endMs - l.timeMs
                val curDur = (l.endMs - l.timeMs).coerceAtLeast(1L)
                if (overlap > 0 && overlap < 500 && (overlap <= 100 || overlap * 10 <= curDur)) {
                    cleaned[cleaned.lastIndex] = truncateAt(prev, l.timeMs)
                }
            }
            cleaned.add(l)
        }
        // 3) 行起点提前（不越过上一行的结束）
        return cleaned.mapIndexed { i, l ->
            val gap = if (i == 0) Long.MAX_VALUE else l.timeMs - (cleaned[i - 1].endMs.coerceAtLeast(cleaned[i - 1].timeMs))
            val early = when {
                gap >= 600 -> 600L
                gap >= 400 -> 400L
                else -> (gap * 7 / 10)
            }
            if (early <= 0) l else l.copy(timeMs = (l.timeMs - early).coerceAtLeast(0L))
        }
    }

    /** 把一行在 [at] 处截断（末字提前结束），用于清洗非刻意重叠。 */
    private fun truncateAt(line: LyricLine, at: Long): LyricLine {
        if (!line.hasWords) return line
        val words = line.words.toMutableList()
        for (i in words.indices.reversed()) {
            val w = words[i]
            if (w.endMs > at) {
                words[i] = if (w.startMs < at) w.copy(endMs = at) else LyricWord(w.text, w.startMs, at.coerceAtLeast(w.startMs), w.kana)
            } else break
        }
        return line.copy(words = words)
    }

    // ───────────────────────── LRC ─────────────────────────

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /**
     * 解析 LRC。同一行可有多个时间标签（`[00:12.00][01:30.00]副歌`），全部展开；
     * `[ti:]`/`[ar:]`/`[kana:]` 等元信息行与空行丢弃。
     */
    fun parseLrc(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (rawLine in lrc.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val tags = TIME_TAG.findAll(line).toList()
            if (tags.isEmpty()) continue
            val text = line.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (tag in tags) {
                val min = tag.groupValues[1].toLongOrNull() ?: continue
                val sec = tag.groupValues[2].toLongOrNull() ?: continue
                val fracRaw = tag.groupValues[3]
                val ms = when (fracRaw.length) {
                    0 -> 0L
                    1 -> fracRaw.toLong() * 100
                    2 -> fracRaw.toLong() * 10
                    else -> fracRaw.take(3).toLong()
                }
                out.add(LyricLine(min * 60_000 + sec * 1000 + ms, text))
            }
        }
        return out.sortedBy { it.timeMs }
    }

}
