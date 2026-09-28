package com.neumusic.player.data.api

import com.neumusic.player.data.LyricLine
import com.neumusic.player.data.LyricWord
import com.neumusic.player.data.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 歌词 + 翻译 + QRC 逐字 + 音译(roma) + 注音(kana)。
 *
 * ## 端点与参数
 * `musicu.fcg` 的 `music.musichallSong.PlayLyricInfo/GetPlayLyricInfo`，
 * param 必须显式带布尔开关：
 *
 * ```
 * { songMid, crypt: 0, lrc_t: 0,
 *   qrc: 1, qrc_t: 0,        // ← 逐字（要 1 才给 QRC）
 *   roma: 1, roma_t: 0,      // ← 音译/罗马音（逐字，hex QRC 密文；英文曲为空）
 *   trans: 1, trans_t: 0,    // ← 翻译（只传 trans_t 拿不到，必须传这个布尔）
 *   needSingingAnnotations: false, type: 1 }
 * ```
 *
 * ## 响应里 `lyric` 字段的两种形态（实测判别）
 * - **全 hex 字符** → QRC 逐字**密文**（hex 编码 → 非标准类 DES 三重 → zlib → 带 XML 壳的
 *   QRC 明文，见 [QrcCodec]）。此时行的每个字/词自带时间，渲染为卡拉 OK 扫色。
 * - **base64** → 行级 LRC 明文（该曲没有逐字数据时的普通响应）。
 *
 * ⚠️ 踩过的坑：`qrc=1` 后 `lyric` 变成 hex 密文，若仍按 base64 解会得到乱码，
 * `parseLrc` 找不到时间标签 → 整首歌显示「暂无歌词」。所以必须先判别 hex 再分流。
 *
 * ⚠️ **QRC 字时间是「绝对毫秒」，不是相对行首**：`[5670,5670]Lyrics(5670,378)…`——
 * 首字时间就等于行起点。一律按「相对行首」换算会把除第一行外每行的字时间推后一整个
 * 行起点，扫色恒为 0（「只有第一句有扫色」的根因）。详见 [parseQrcXml]。
 *
 * ## 翻译与注音
 * `trans` 是 base64 明文 LRC，无翻译的行是 `//` 占位，与原文同时间轴。
 * 日文曲的 `trans` 还带 **`[kana:1よね1づ1けん1し…]`** 注音元数据：
 * `<字符数><读音>` 交替，逐字对应主歌词——**只有汉字字符消费 token**（假名/拉丁/标点
 * 不消费，保留原字），token 流按行时间序贯穿整首歌（含 `词：/曲：` 元数据行，实测核过
 * Lemon 全曲对齐）。`[kana:…]` 因此能生成整行假名读音与逐字注音。
 *
 * ## 音译
 * `roma:1` 时 `roma` 字段是 hex QRC 密文（与 `lyric` 同格式），用 [QrcCodec] 解出
 * 逐字罗马音，按行起点对齐回主歌词行。英文曲该字段为空。
 *
 * ## 歌词优化（AMLL 同款策略，自研实现）
 * 1. 空格规范化：连续空格并成一个。
 * 2. 清洗非刻意重叠：与下一行重叠 ≥500ms 视为有意（保留）；<500ms 且（≤100ms 或
 *    ≤下一行时长 10%）→ 把上一行末字截到下一行起点，避免双行同时高亮。
 * 3. 让歌词提前开始：最多提前 600ms；距上一行结束不足 600ms 时提前 400ms；
 *    再不足 400ms 时提前剩余间隔的 70%。只提前行起点（高亮早到），字时间不动
 *    （扫色仍从唱到的那刻开始）——与 AMLL 观感一致。
 * 匿名 comm 即可取（实测），登录 comm 同样可用。
 */
object LyricApi {

    private const val NO_TRANS_PLACEHOLDER = "//"
    private const val FCG = "https://u.y.qq.com/cgi-bin/musicu.fcg"

    /** 同一 mid 的歌词在进程内缓存（切歌往返时避免重复请求）。 */
    private val cache = HashMap<String, Lyrics>()
    private const val CACHE_MAX = 24

    suspend fun lyricsFor(mid: String): Lyrics? = withContext(Dispatchers.IO) {
        if (mid.isEmpty()) return@withContext null
        synchronized(cache) { cache[mid] }?.let { return@withContext it }
        runCatching { fetch(mid) }
            .getOrNull()
            ?.also { lyrics ->
                synchronized(cache) {
                    if (cache.size >= CACHE_MAX) cache.clear()
                    cache[mid] = lyrics
                }
            }
    }

    private fun fetch(mid: String): Lyrics? {
        val data = call(mid, qrc = true) ?: return null
        val rawLyric = data.optString("lyric", "")
        if (rawLyric.isBlank()) return null

        // ① hex → QRC 逐字；② 其余 → base64 明文 LRC 兜底。
        var lines = if (looksLikeHex(rawLyric)) {
            runCatching { parseQrcXml(QrcCodec.decryptHex(rawLyric)) }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
        if (lines.isEmpty()) {
            lines = parseLrc(decodeLrc(rawLyric))
            if (lines.isEmpty()) return null
        }

        // 翻译：qrc=1 时通常为空，补一次行级请求（同一时间轴）合并。
        var transRaw = data.optString("trans", "")
        if (transRaw.isBlank() && lines.any { it.hasWords }) {
            transRaw = runCatching { call(mid, qrc = false)?.optString("trans", "") ?: "" }
                .getOrDefault("")
        }
        val transLines = if (transRaw.isBlank()) emptyList() else parseLrc(decodeLrc(transRaw))
        val merged = mergeTranslation(lines, transLines)

        // 音译（roma）：hex QRC 密文，同一解码器；为空（多数英文曲）就跳过。
        val romaLines = data.optString("roma", "").takeIf { looksLikeHex(it) }
            ?.let { runCatching { parseQrcXml(QrcCodec.decryptHex(it)) }.getOrElse { emptyList() } }
            ?: emptyList()

        // 注音（kana）：翻译 LRC 里的 [kana:…] 元数据 → 逐字假名。
        val kanaTokens = parseKanaTokens(transRaw)

        return Lyrics(optimize(attachRoman(attachKana(merged, kanaTokens), romaLines)))
    }

    /** 一次 GetPlayLyricInfo 请求，返回 `req_1.data`。 */
    private fun call(mid: String, qrc: Boolean): JSONObject? {
        val param = JSONObject()
            .put("songMid", mid)
            .put("crypt", 0)
            .put("lrc_t", 0)
            .put("qrc", if (qrc) 1 else 0)
            .put("qrc_t", 0)
            .put("roma", 1)
            .put("roma_t", 0)
            .put("trans", 1)
            .put("trans_t", 0)
            .put("needSingingAnnotations", false)
            .put("type", 1)
        val body = JSONObject()
            .put("comm", JSONObject().put("ct", 19).put("cv", 1873))
            .put("req_1", JSONObject()
                .put("module", "music.musichallSong.PlayLyricInfo")
                .put("method", "GetPlayLyricInfo")
                .put("param", param))
        val request = Request.Builder()
            .url(FCG)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return QqCore.http.newCall(request).execute().use { resp ->
            JSONObject(resp.body?.string() ?: "{}")
                .optJSONObject("req_1")?.optJSONObject("data")
        }
    }

    /** 是否纯 hex 文本（QRC 密文的特征）。加长度与等偶校验，避免误判普通 LRC。 */
    private fun looksLikeHex(s: String): Boolean {
        if (s.length < 32 || s.length % 2 != 0) return false
        for (c in s) {
            val hex = (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')
            if (!hex) return false
        }
        return true
    }

    /** 该端点返回 base64；已经明文的（异常情况）原样返回。 */
    private fun decodeLrc(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            String(android.util.Base64.decode(raw, android.util.Base64.DEFAULT))
        }.getOrElse { raw }
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
        val lrc = decodeLrc(transRaw)
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

    // ───────────────────────── QRC ─────────────────────────

    private val CDATA_RE = Regex("""<!\[CDATA\[([\s\S]*?)\]\]>""")
    private val ATTR_RE = Regex("""LyricContent="([^"]*)"""")
    private val LINE_RE = Regex("""\[(\d+),(\d+)]([^\n]*)""")
    private val WORD_RE = Regex("""\((\d+),(\d+)\)""")

    /**
     * 解析 QRC XML：取出 `LyricContent`（CDATA 或属性形式），逐行
     * `[行起始,行时长]字(起,时长)字(起,时长)…` → 带逐字时间的 [LyricLine]。
     *
     * 元信息行（`[ti:]` 等，不含 `[数字,数字]`）自然被 LINE_RE 过滤掉。
     *
     * ⚠️ **字时间是绝对毫秒，不是相对行首**。实测（Five Hundred Miles）：
     * 第 2 行是 `[5670,5670]Lyrics(5670,378) (6048,378)by(6426,378)…`——首字时间就是行起点
     * 而非 0。早前一律按 `lineStart + 字偏移` 换算，于是**除第一行（lineStart=0）外**
     * 每行的字时间都被推后一整个行起点，扫色计算判定成「还没唱」而恒为 0，
     * 表现为「只有第一句歌词有扫色」。这里先判别基准（兼容相对时间的变体）再换算。
     */
    private fun parseQrcXml(xml: String): List<LyricLine> {
        val content = CDATA_RE.find(xml)?.groupValues?.get(1)
            ?: ATTR_RE.find(xml)?.groupValues?.get(1)
            ?: return emptyList()
        // 属性形式会做 XML 实体转义，反解回原文
        val body = content
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

        val out = ArrayList<LyricLine>()
        for (m in LINE_RE.findAll(body)) {
            val lineStart = m.groupValues[1].toLongOrNull() ?: continue
            val lineDur = m.groupValues[2].toLongOrNull() ?: 0L
            val payload = m.groupValues[3]

            // 先原样收，再定基准。
            val raw = ArrayList<QrcWord>()   // 时间含义待定的字（见下）
            var idx = 0
            while (idx < payload.length) {
                val wm = WORD_RE.find(payload, idx) ?: break
                val wordText = payload.substring(idx, wm.range.first)
                val ws = wm.groupValues[1].toLongOrNull() ?: 0L
                val wd = wm.groupValues[2].toLongOrNull() ?: 0L
                if (wordText.isNotEmpty()) raw.add(QrcWord(wordText, ws, wd))
                idx = wm.range.last + 1
            }
            if (raw.isEmpty()) continue
            val text = raw.joinToString("") { it.text }.trim()
            if (text.isEmpty()) continue

            // 相对时间的行，字时间不会超出该行时长；超出即说明本来就是绝对时间。
            val absolute = raw.maxOf { it.at + it.dur } > lineDur + 500L
            val words = raw.map { w ->
                val start = if (absolute) w.at else lineStart + w.at
                LyricWord(w.text, start, start + w.dur)
            }
            out.add(LyricLine(timeMs = lineStart, text = text, words = words))
        }
        return out.sortedBy { it.timeMs }
    }

    /** QRC 原始字：`at`/`dur` 的含义（绝对或相对行首）由整行判别后确定。 */
    private data class QrcWord(val text: String, val at: Long, val dur: Long)
}
