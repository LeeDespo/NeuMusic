package com.neumusic.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 主页推荐歌曲的**预取缓冲**（2026-09-30 规格）：常备 5 首（曲目 + 介绍），落盘持久化——
 * 打开应用推荐即显、点刷新即时换下一首，都不用等网络。
 *
 * 工作方式：`init` 读盘；HomeScreen 找到「猜你喜欢」电台后设 [stationId]；
 * 队列不足 [CAP] 首时 [refill] 按需补（`RadioApi.nextTracks` 避开已展示的 mid，
 * `SongApi.intro` 取介绍），全部由 HelperNext 的请求保护控制。`advance` 弹出当前首、
 * 展示下一首并触发补货。已展示的 mid 记在 [excluded]（随盘持久，防重复推荐）。
 */
object RecommendStore {

    const val CAP = 5

    data class Entry(val track: Track, val intro: String)

    private const val FILE = "recommend_buffer.json"
    private const val EXCLUDED_CAP = 60

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _queue = MutableStateFlow<List<Entry>>(emptyList())
    val queue: StateFlow<List<Entry>> = _queue

    @Volatile var stationId: Int? = null

    private var bufferFile: File? = null
    private var refillBusy = false
    private val excluded = LinkedHashSet<String>()

    fun init(context: Context) {
        bufferFile = File(context.filesDir, FILE)
        runCatching {
            val text = bufferFile?.takeIf { it.exists() }?.readText().orEmpty()
            if (text.isEmpty()) return
            val arr = JSONArray(text)
            val list = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val t = o.optJSONObject("track") ?: return@mapNotNull null
                Entry(
                    track = Track(
                        mid = t.optString("mid"),
                        name = t.optString("name"),
                        mediaMid = t.optString("mediaMid"),
                        singer = t.optString("singer"),
                        albumName = t.optString("albumName"),
                        albumMid = t.optString("albumMid"),
                        intervalSec = t.optInt("intervalSec"),
                        isVip = t.optBoolean("isVip"),
                        songId = t.optLong("songId"),
                    ),
                    intro = o.optString("intro"),
                )
            }
            _queue.value = list
            list.forEach { if (it.track.mid.isNotEmpty()) excluded.add(it.track.mid) }
        }
    }

    /** 队列不足就补货；返回是否补进了新歌。 */
    suspend fun refill(): Boolean {
        val sid = stationId ?: return false
        if (refillBusy) return false
        refillBusy = true
        var added = false
        try {
            var guard = 0
            while (_queue.value.size < CAP && guard++ < 8) {
                val batch = runCatching {
                    com.neumusic.player.data.api.RadioApi.nextTracks(
                        sid, firstplay = false, exclude = excluded.toSet(), batches = 2,
                    )
                }.onFailure { com.neumusic.player.data.AppLog.w("Recommend", "refill nextTracks failed", it) }
                    .getOrNull().orEmpty()
                val fresh = batch.filter { it.mid.isNotEmpty() && it.mid !in excluded }
                if (fresh.isEmpty()) {
                    if (batch.isNotEmpty()) excluded.addAll(batch.map { it.mid })
                    kotlinx.coroutines.delay(1200)   // 全是旧的/被限流：歇一会再试
                    continue
                }
                for (t in fresh) {
                    if (_queue.value.size >= CAP) break
                    excluded.add(t.mid)
                    val intro = runCatching { com.neumusic.player.data.api.SongApi.intro(t.mid) }
                        .onFailure { com.neumusic.player.data.AppLog.w("Recommend", "intro failed: ${t.mid}", it) }
                        .getOrNull().orEmpty()
                    _queue.value = _queue.value + Entry(t, intro)
                    added = true
                    persist()
                }
            }
            persistExcluded()
        } finally {
            refillBusy = false
        }
        return added
    }

    /** 刷新：弹出当前首（展示下一首），返回被弹出的那首。 */
    fun advance(): Entry? {
        val cur = _queue.value.firstOrNull() ?: return null
        _queue.value = _queue.value.drop(1)
        scope.launch {
            if (_queue.value.size < CAP) refill()
            persistExcluded()
        }
        return cur
    }

    private fun persist() {
        val f = bufferFile ?: return
        runCatching {
            val arr = JSONArray()
            _queue.value.forEach { e ->
                arr.put(
                    JSONObject()
                        .put("intro", e.intro)
                        .put("track", JSONObject()
                            .put("mid", e.track.mid)
                            .put("name", e.track.name)
                            .put("mediaMid", e.track.mediaMid)
                            .put("singer", e.track.singer)
                            .put("albumName", e.track.albumName)
                            .put("albumMid", e.track.albumMid)
                            .put("intervalSec", e.track.intervalSec)
                            .put("isVip", e.track.isVip)
                            .put("songId", e.track.songId))
                )
            }
            f.writeText(arr.toString())
        }
    }

    private fun persistExcluded() {
        val bf = bufferFile ?: return
        val f = File(bf.parentFile, "recommend_excluded.json")
        runCatching {
            while (excluded.size > EXCLUDED_CAP) excluded.remove(excluded.first())
            f.writeText(JSONArray(excluded.toList()).toString())
        }
    }

    /** 读回已展示的 mid（避免重启后重复推荐）。 */
    fun loadExcluded(context: Context) {
        runCatching {
            val f = File(context.filesDir, "recommend_excluded.json")
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) excluded.add(arr.optString(i))
        }
    }
}
