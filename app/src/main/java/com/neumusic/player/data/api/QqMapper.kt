package com.neumusic.player.data.api

import com.neumusic.player.data.AlbumItem
import com.neumusic.player.data.PlaylistItem
import com.neumusic.player.data.RadioGroup
import com.neumusic.player.data.RadioStation
import com.neumusic.player.data.Track
import com.neumusic.player.data.api.QqCore.mapItems
import org.json.JSONArray
import org.json.JSONObject

/**
 * QQ 音乐响应 → 应用模型 的映射。集中在这里，端点模块只管发请求。
 *
 * QQ 音乐的坑：同一个概念在不同接口里字段名和嵌套层级都不一样
 * （专辑曲目是 `songList[].songInfo`、电台曲目是顶层扁平、我喜欢是 `songlist[]`…），
 * 所以解析器要能容忍多种形状，而不是每个接口抄一份。
 */
internal object QqMapper {

    /**
     * 解析一首歌。对以下形状都有效：
     * - 搜索/歌单/我喜欢的 `{mid,name,singer[],album{},file{},interval,pay,id}`
     * - 专辑接口的 `songInfo`（同上）
     * - 电台 `tracks[]` 的扁平结构（`singer` 可能是数组也可能是字符串）
     */
    internal fun track(o: JSONObject?): Track? {
        if (o == null) return null

        // mid 有的在顶层，有的藏在 `songInfo`（调用方已拆包），再兜一层 file.media_mid。
        val mid = o.optString("mid").ifEmpty { o.optString("songmid") }
        if (mid.isEmpty()) return null

        val file = o.optJSONObject("file")
        val mediaMid = file?.optString("media_mid").takeUnless { it.isNullOrEmpty() } ?: mid

        val album = o.optJSONObject("album")

        // 收集可用格式大小（size_128mp3 / size_320mp3 / size_96aac / size_192ogg / size_320ogg / size_flac ...）
        val sizes = LinkedHashMap<String, Long>()
        file?.let { f ->
            for (key in f.keys()) {
                if (key.startsWith("size_")) {
                    val v = f.optLong(key, 0L)
                    if (v > 0L) sizes[key.removePrefix("size_")] = v
                }
            }
        }

        return Track(
            mid = mid,
            name = o.optString("name").ifEmpty { o.optString("songname") },
            mediaMid = mediaMid,
            singer = singerNames(o),
            albumName = album?.optString("name").orEmpty(),
            albumMid = album?.optString("mid").orEmpty(),
            intervalSec = o.optInt("interval", 0),
            isVip = o.optJSONObject("pay")?.optInt("pay_play", 0) == 1,
            songId = o.optLong("id", o.optLong("songId", 0L)),
            fileSizes = sizes,
            genre = o.optInt("genre", 0),
        )
    }

    /**
     * 歌手名。三种形状都要吃：
     * - `singer: [{name:..}]`（搜索/歌单/专辑）
     * - `singer: "名字"`（部分电台/推荐接口）
     * - `singername: "A/B"`（老接口，用 `/` 分隔）
     */
    private fun singerNames(o: JSONObject): String {
        val arr = o.optJSONArray("singer")
        if (arr != null) {
            val names = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
            if (names.isNotEmpty()) return names.joinToString(" / ")
        }
        // optString 对非字符串类型会返回 ""，所以真字符串要靠 is String 判断。
        (o.opt("singer") as? String)?.takeIf { it.isNotEmpty() }?.let { return it }
        o.optString("singername").takeIf { it.isNotEmpty() }?.let {
            return it.split('/').joinToString(" / ") { s -> s.trim() }
        }
        return "未知歌手"
    }

    /** 收藏的歌单（`v_list[]`，字段 tid/name/logo/songnum）。 */
    internal fun playlistItem(o: JSONObject): PlaylistItem? {
        val tid = o.optLong("tid", o.optLong("dissid", 0L))
        if (tid <= 0L) return null
        return PlaylistItem(
            tid = tid,
            name = o.optString("name", o.optString("dissname")),
            logo = o.optString("logo").toHttps(),
            songnum = o.optInt("songnum", 0),
        )
    }

    /** 收藏的专辑（`v_list[]`，字段 mid/name/logo/songnum）。 */
    internal fun albumItem(o: JSONObject): AlbumItem? {
        val mid = o.optString("mid")
        if (mid.isEmpty()) return null
        return AlbumItem(
            mid = mid,
            name = o.optString("name"),
            logo = o.optString("logo").toHttps(),
            songnum = o.optInt("songnum", 0),
        )
    }

    /**
     * 电台分组。注意外层 `radio_list[]` 是**分组**（热门/心情…），
     * 内层 `list[]` 才是电台；分组 id 不能当电台 id 用（会被服务端拒）。
     */
    internal fun radioGroups(arr: JSONArray?): List<RadioGroup> = mapItems(arr) { g ->
        val stations = mapItems(g.optJSONArray("list")) { s ->
            val id = s.optInt("id", -1)
            if (id <= 0) null else RadioStation(
                id = id,
                title = s.optString("title"),
                listenDesc = s.optString("listenDesc"),
                picUrl = s.optString("pic_url").toHttps(),
            )
        }
        if (stations.isEmpty()) null else RadioGroup(g.optString("title"), stations)
    }
}
