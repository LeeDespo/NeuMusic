package com.neumusic.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 一条已下载记录。 */
data class DownloadRecord(
    val mid: String,
    val name: String,
    val singer: String,
    val quality: String,
    val fileName: String,
    val timeMs: Long,
)

/**
 * 已下载歌曲的本地台账（SharedPreferences 持久化）。
 *
 * 用途：列表里把已下载的曲目**置灰且不可再选**（用户要求），
 * 以及设置页/下载区展示。以 mid 为唯一键。
 */
object DownloadStore {
    private const val FILE = "neumusic_downloads"

    private val _records = MutableStateFlow<Map<String, DownloadRecord>>(emptyMap())
    val records: StateFlow<Map<String, DownloadRecord>> = _records

    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val arr = JSONArray(sp.getString("records", "[]"))
            val map = HashMap<String, DownloadRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val r = DownloadRecord(
                    mid = o.optString("mid"),
                    name = o.optString("name"),
                    singer = o.optString("singer"),
                    quality = o.optString("quality"),
                    fileName = o.optString("fileName"),
                    timeMs = o.optLong("timeMs", 0L),
                )
                if (r.mid.isNotEmpty()) map[r.mid] = r
            }
            _records.value = map
        }
    }

    fun isDownloaded(mid: String): Boolean = _records.value.containsKey(mid)

    fun mark(record: DownloadRecord) {
        val next = _records.value.toMutableMap()
        next[record.mid] = record
        _records.value = next
        persist(next)
    }

    private fun persist(map: Map<String, DownloadRecord>) {
        // persist 需要 Context；init 时缓存 application context。
        val ctx = appContext ?: return
        val arr = JSONArray()
        map.values.forEach { r ->
            arr.put(JSONObject()
                .put("mid", r.mid).put("name", r.name).put("singer", r.singer)
                .put("quality", r.quality).put("fileName", r.fileName).put("timeMs", r.timeMs))
        }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("records", arr.toString()).apply()
    }

    @Volatile
    private var appContext: Context? = null

    /** 在 Application/Activity 初始化时调用一次。 */
    fun initContext(context: Context) {
        appContext = context.applicationContext
    }
}
