package com.neumusic.player.data.api

import com.neumusic.player.data.AlbumItem
import com.neumusic.player.data.SearchSinger
import com.neumusic.player.data.api.QqCore.Req
import com.neumusic.player.data.api.QqCore.call
import com.neumusic.player.data.api.QqCore.commAuth
import com.neumusic.player.data.api.QqCore.data
import org.json.JSONObject

/**
 * 歌手页数据源（端点经 curl 实测，2026-09-30）：
 * - 歌手解析/头像/歌曲数/专辑数：复用 [SearchApi.singers]（搜索响应自带 songNum/albumNum/pic）。
 * - 歌曲列表：`musichall.song_list_server/GetSingerSongList`，param `{singerMid, order, number, begin}`，
 *   **order=1 热门、order=2 最新**（实测：order=1 首页是晴天/搁浅/夜曲），总数在 `totalNum`，
 *   歌曲在 `songList[].songInfo`（与专辑内歌曲同构，QqMapper 直接吃）。
 * - 专辑列表：`music.musichallAlbum.AlbumListServer/GetAlbumList`，同 param；总数 `total`，
 *   字段 `albumMid/albumName/pmid/publishDate/singerName`。**`totalNum`（专辑内歌曲数）恒为 0**，
 *   要拿每张专辑的歌数得再调 `AlbumSongList/GetAlbumSongList` —— 用 musicu.fcg 的多请求块
 *   批量合并（一次 HTTP 带多个 req_N），不增加请求数。
 */
object SingerApi {

    /** 歌曲排序（实测语义）。 */
    const val ORDER_HOT = 1
    const val ORDER_NEW = 2

    /** 歌手名 → 歌手（精确名优先，找不到取第一条）。 */
    suspend fun resolve(name: String): SearchSinger? {
        val list = SearchApi.singers(name, num = 10)
        return list.firstOrNull { it.name == name } ?: list.firstOrNull()
    }

    /** 歌手歌曲分页。 */
    suspend fun songs(mid: String, order: Int, offset: Int, num: Int = 100): PlaylistApi.Page {
        val root = call(commAuth(), "req_1" to Req(
            "musichall.song_list_server", "GetSingerSongList", JSONObject()
                .put("singerMid", mid)
                .put("order", order)
                .put("number", num)
                .put("begin", offset)))
        val d = data(root)
        val tracks = QqCore.mapItems(d?.optJSONArray("songList")) { item ->
            QqMapper.track(item.optJSONObject("songInfo") ?: item)
        }
        return PlaylistApi.Page(tracks, d?.optInt("totalNum", -1)?.takeIf { it >= 0 })
    }

    data class AlbumPage(val albums: List<AlbumItem>, val total: Int?)

    /** 歌手专辑分页（歌曲数尽量补齐：每 30 张合并成一个批量请求查 totalNum）。 */
    suspend fun albums(mid: String, order: Int, offset: Int, num: Int = 30): AlbumPage {
        val root = call(commAuth(), "req_1" to Req(
            "music.musichallAlbum.AlbumListServer", "GetAlbumList", JSONObject()
                .put("singerMid", mid)
                .put("order", order)
                .put("number", num)
                .put("begin", offset)))
        val d = data(root)
        val arr = d?.optJSONArray("albumList")
        val briefs = QqCore.mapItems(arr) { o ->
            val albumMid = o.optString("albumMid")
            if (albumMid.isEmpty()) null else Triple(
                albumMid,
                o.optString("albumName"),
                o.optString("singerName"),
            )
        }
        // 歌曲数补齐：GetAlbumList 的 totalNum 恒 0，批量查 GetAlbumSongList 的 totalNum。
        val counts = fillSongCounts(briefs.map { it.first })
        val albums = briefs.map { (mid, name, singer) ->
            AlbumItem(
                mid = mid,
                name = name,
                logo = "https://y.gtimg.cn/music/photo_new/T002R300x300M000$mid.jpg",
                songnum = counts[mid] ?: 0,
                singerName = singer,
            )
        }
        return AlbumPage(albums, d?.optInt("total", -1)?.takeIf { it >= 0 })
    }

    /** 每批 30 张专辑合并成一个 musicu.fcg 请求（req_1..req_N）查 totalNum，最多 3 批，超出放弃计数。 */
    private suspend fun fillSongCounts(mids: List<String>): Map<String, Int> {
        val out = HashMap<String, Int>()
        mids.chunked(30).take(3).forEach { batch ->
            runCatching {
                val reqs = batch.mapIndexed { i, mid ->
                    "req_${i + 1}" to Req(
                        "music.musichallAlbum.AlbumSongList", "GetAlbumSongList", JSONObject()
                            .put("albumMid", mid)
                            .put("begin", 0).put("num", 1))
                }
                val root = call(commAuth(), *reqs.toTypedArray())
                batch.forEachIndexed { i, mid ->
                    val total = data(root, "req_${i + 1}")?.optInt("totalNum", -1) ?: -1
                    if (total > 0) out[mid] = total
                }
            }
        }
        return out
    }
}
