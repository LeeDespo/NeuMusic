package com.neumusic.player.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 主页内容的**磁盘快照**。
 *
 * 解决的问题（用户反馈）：冷启动时内存缓存必空，主页三栏要等 6 个网络请求
 * 全部回来才有内容，期间只有写死的「我喜欢」卡片。
 *
 * 策略是 **stale-while-revalidate**：进主页先把上次的快照立即上屏（若有且未过期），
 * 同时后台发请求刷新，回来后覆盖界面并写回两级缓存（内存 `ApiCache` + 这里）。
 * 快照超过 [MAX_AGE_MS] 视为过期丢弃（避免长期离线后展示太旧的数据）。
 *
 * 存的是**精简字段**的 JSON，不是原始响应 —— 解析与 App 模型解耦，
 * 接口响应形状变了也不用迁移旧快照。
 */
object HomeCache {
    private const val FILE = "neumusic_home"
    private const val MAX_AGE_MS = 7 * 24 * 3600_000L

    @Volatile
    private var sp: SharedPreferences? = null

    fun init(context: Context) {
        if (sp == null) {
            sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    private fun raw(key: String): String? {
        val s = sp ?: return null
        val v = s.getString(key, null) ?: return null
        val ts = s.getLong("$key.ts", 0L)
        if (System.currentTimeMillis() - ts > MAX_AGE_MS) return null
        return v
    }

    private fun put(key: String, json: String) {
        sp?.edit()
            ?.putString(key, json)
            ?.putLong("$key.ts", System.currentTimeMillis())
            ?.apply()
    }

    // ── 收藏的歌单 ──
    fun playlists(): List<PlaylistItem>? = raw("favPlaylists")?.let { json ->
        runCatching {
            val arr = JSONArray(json)
            val out = ArrayList<PlaylistItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val p = PlaylistItem(
                    tid = o.optLong("tid"),
                    name = o.optString("name"),
                    logo = o.optString("logo"),
                    songnum = o.optInt("songnum"),
                )
                if (p.tid > 0) out.add(p)
            }
            out
        }.getOrNull()
    }

    fun savePlaylists(v: List<PlaylistItem>) = put(
        "favPlaylists",
        JSONArray().apply {
            v.forEach { p ->
                put(JSONObject().put("tid", p.tid).put("name", p.name)
                    .put("logo", p.logo).put("songnum", p.songnum))
            }
        }.toString(),
    )

    // ── 收藏的专辑 ──
    fun albums(): List<AlbumItem>? = raw("favAlbums")?.let { json ->
        runCatching {
            val arr = JSONArray(json)
            val out = ArrayList<AlbumItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val a = AlbumItem(
                    mid = o.optString("mid"),
                    name = o.optString("name"),
                    logo = o.optString("logo"),
                    songnum = o.optInt("songnum"),
                )
                if (a.mid.isNotEmpty()) out.add(a)
            }
            out
        }.getOrNull()
    }

    fun saveAlbums(v: List<AlbumItem>) = put(
        "favAlbums",
        JSONArray().apply {
            v.forEach { a ->
                put(JSONObject().put("mid", a.mid).put("name", a.name)
                    .put("logo", a.logo).put("songnum", a.songnum))
            }
        }.toString(),
    )

    // ── 电台分组 ──
    fun radioGroups(): List<RadioGroup>? = raw("radioGroups")?.let { json ->
        runCatching {
            val arr = JSONArray(json)
            val out = ArrayList<RadioGroup>(arr.length())
            for (i in 0 until arr.length()) {
                val g = arr.optJSONObject(i) ?: continue
                val sa = g.optJSONArray("stations") ?: continue
                val stations = ArrayList<RadioStation>(sa.length())
                for (j in 0 until sa.length()) {
                    val o = sa.optJSONObject(j) ?: continue
                    val st = RadioStation(
                        id = o.optInt("id"),
                        title = o.optString("title"),
                        listenDesc = o.optString("listenDesc"),
                        picUrl = o.optString("picUrl"),
                    )
                    if (st.id > 0) stations.add(st)
                }
                if (stations.isNotEmpty()) out.add(RadioGroup(g.optString("title"), stations))
            }
            out
        }.getOrNull()
    }

    fun saveRadioGroups(v: List<RadioGroup>) = put(
        "radioGroups",
        JSONArray().apply {
            v.forEach { g ->
                put(JSONObject().put("title", g.title).put(
                    "stations",
                    JSONArray().apply {
                        g.stations.forEach { s ->
                            put(JSONObject().put("id", s.id).put("title", s.title)
                                .put("listenDesc", s.listenDesc).put("picUrl", s.picUrl))
                        }
                    },
                ))
            }
        }.toString(),
    )

    // ── 我喜欢数量 ──
    fun likedCount(): Int? = raw("likedCount")?.toIntOrNull()?.takeIf { it > 0 }

    fun saveLikedCount(v: Int) = put("likedCount", v.toString())

    /** 退出登录等场景清空。 */
    fun clear() {
        sp?.edit()?.clear()?.apply()
    }
}
