package com.neumusic.player.data.api

import com.neumusic.player.data.LyricLine
import com.neumusic.player.data.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

/**
 * 歌词 + 翻译。
 *
 * ## 做法（学自 qqmusic-api-python 的 `LyricApi.get_lyric`）
 * 早期误判「翻译拿不到」：那时走 `fcg_query_lyric_new.fcg`，它的 `trans` 字段恒为空。
 * 实际上翻译要用 `musicu.fcg` 的 `music.musichallSong.PlayLyricInfo/GetPlayLyricInfo`，
 * 且必须带 **`trans: 1` 请求参数**（布尔开关，不是只读响应字段）：
 *
 * ```
 * param = { songMid, crypt: 0, lrc_t: 0, qrc: 0, qrc_t: 0,
 *           roma: 0, roma_t: 0, trans: 1, trans_t: 0,
 *           needSingingAnnotations: false, type: 1 }
 * ```
 *
 * `crypt: 0` 时 `lyric` 与 `trans` 都是**明文 LRC 的 base64**（`crypt: 1` 是密文，不采用）。
 * 翻译 LRC 与原文同时间轴；某行没有翻译时内容是 `//` 占位。
 * 匿名 comm 即可取到（实测），登录 comm 同样可用。
 */
object LyricApi {

    private const val NO_TRANS_PLACEHOLDER = "//"

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
        val param = JSONObject()
            .put("songMid", mid)
            .put("crypt", 0)
            .put("lrc_t", 0)
            .put("qrc", 1)
            .put("qrc_t", 0)
            .put("roma", 0)
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
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            .post(okhttp3.RequestBody.create(
                "application/json; charset=utf-8".toMediaType(), body.toString()))
            .build()
        QqCore.http.newCall(request).execute().use { resp ->
            val root = JSONObject(resp.body?.string() ?: "{}")
            val data = root.optJSONObject("req_1")?.optJSONObject("data") ?: return null

            val lrc = decodeLrc(data.optString("lyric", ""))
            if (lrc.isBlank()) return null
            val lines = parseLrc(lrc)
            if (lines.isEmpty()) return null

            val trans = decodeLrc(data.optString("trans", ""))
            val merged = if (trans.isBlank()) lines else mergeTranslation(lines, parseLrc(trans))
            return Lyrics(merged)
        }
    }

    /** 该端点返回 base64；已经明文的（异常情况）原样返回。 */
    private fun decodeLrc(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            val decoded = String(android.util.Base64.decode(raw, android.util.Base64.DEFAULT))
            // 明文 LRC 以 [ 开头；base64 解码失败会抛异常，不会走到这。
            decoded
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
