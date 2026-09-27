package com.neumusic.player.data.api

import com.neumusic.player.data.AlbumItem
import com.neumusic.player.data.PlaylistItem
import com.neumusic.player.data.SearchSinger
import com.neumusic.player.data.Track
import com.neumusic.player.data.api.QqCore.Req
import com.neumusic.player.data.api.QqCore.call
import com.neumusic.player.data.api.QqCore.commWeb
import com.neumusic.player.data.api.QqCore.data
import org.json.JSONObject

/** 搜索（歌曲 / 歌手 / 专辑 / 歌单四类，同一模块的 `search_type` 区分）。 */
object SearchApi {

    private const val MODULE = "music.search.SearchCgiService"
    private const val METHOD = "DoSearchForQQMusicMobile"

    private suspend fun searchBody(query: String, type: Int, num: Int, page: Int): JSONObject? {
        val param = JSONObject()
            .put("searchid", System.currentTimeMillis().toString().takeLast(10))
            .put("query", query)
            .put("search_type", type)
            .put("num_per_page", num)
            .put("page_num", page)
            // 非"歌曲"类不放大标记：返回字段里的 <em> 标签还要手动剥，干脆关掉。
            .put("highlight", type == 0)
            .put("grp", true)
            .put("selectors", JSONObject())
            .put("vec_selectors", org.json.JSONArray())
        val root = call(commWeb, "req_1" to Req(MODULE, METHOD, param))
        return data(root)?.optJSONObject("body")
    }

    /** 歌曲（综合模式解析 `body.item_song`；`body.song.list` 是另一模式）。 */
    suspend fun songs(query: String, num: Int = 30, page: Int = 1): List<Track> {
        val body = searchBody(query, 0, num, page) ?: return emptyList()
        val items = body.optJSONArray("item_song")
            ?: body.optJSONObject("song")?.optJSONArray("list")
            ?: return emptyList()
        return QqCore.mapItems(items) { QqMapper.track(it) }
    }

    /** 歌手（`body.singer[]`）。 */
    suspend fun singers(query: String, num: Int = 30, page: Int = 1): List<SearchSinger> {
        val body = searchBody(query, 1, num, page) ?: return emptyList()
        val arr = body.optJSONArray("singer") ?: return emptyList()
        return QqCore.mapItems(arr) { o ->
            val mid = o.optString("singerMID")
            val name = o.optString("singerName")
            if (mid.isEmpty() && name.isEmpty()) null else SearchSinger(
                mid = mid,
                id = o.optLong("singerID", 0L),
                name = name.cleanHighlight(),
                pic = o.optString("singerPic").toHttps(),
                songNum = o.optInt("songNum", 0),
                albumNum = o.optInt("albumNum", 0),
            )
        }
    }

    /** 专辑（`body.item_album[]`）。 */
    suspend fun albums(query: String, num: Int = 30, page: Int = 1): List<AlbumItem> {
        val body = searchBody(query, 2, num, page) ?: return emptyList()
        val arr = body.optJSONArray("item_album") ?: return emptyList()
        return QqCore.mapItems(arr) { o ->
            val mid = o.optString("albummid")
            if (mid.isEmpty()) null else AlbumItem(
                mid = mid,
                name = o.optString("name").cleanHighlight(),
                logo = o.optString("pic").toHttps(),
                songnum = o.optInt("song_num", 0),
                singerName = o.optString("singer"),
            )
        }
    }

    /** 歌单（`body.item_songlist[]`）。 */
    suspend fun playlists(query: String, num: Int = 30, page: Int = 1): List<PlaylistItem> {
        val body = searchBody(query, 3, num, page) ?: return emptyList()
        val arr = body.optJSONArray("item_songlist") ?: return emptyList()
        return QqCore.mapItems(arr) { o ->
            val tid = o.optLong("dissid", 0L)
            if (tid <= 0L) null else PlaylistItem(
                tid = tid,
                name = o.optString("dissname").cleanHighlight(),
                logo = o.optString("logo").toHttps(),
                songnum = o.optInt("songnum", 0),
            )
        }
    }
}
