package com.neumusic.player.data.api

import com.neumusic.player.data.*
import org.json.JSONObject

object SingerApi {
    const val ORDER_HOT = 1
    const val ORDER_NEW = 2
    suspend fun resolve(name: String): SearchSinger? = SearchApi.singers(name, 10).let {
        list -> list.firstOrNull { it.name == name } ?: list.firstOrNull()
    }
    private fun params(mid: String, order: Int, offset: Int, num: Int) = JSONObject()
        .put("singerMid", mid).put("sort", if (order == ORDER_NEW) "latest" else "hot").put("offset", offset).put("limit", num)
    suspend fun songs(mid: String, order: Int, offset: Int, num: Int = 100) =
        PlaylistApi.page(HelperNext.call("fetch_artist_songs_page", params(mid, order, offset, num)))
    data class AlbumPage(val albums: List<AlbumItem>, val total: Int?, val nextOffset: Int? = null,
                         private val rawRowCount: Int = albums.size) {
        /** 优先用组件回传的 nextOffset（原始行偏移）；缺省回退原始行数，无 mid 行同样占上游位置。 */
        fun advanceFrom(offset: Int): Int = (nextOffset ?: (offset + rawRowCount)).also {
            check(it >= offset) { "专辑分页偏移倒退" }
        }
    }
    internal fun albumPage(value: JSONObject): AlbumPage {
        // 优先组件回传的 nextOffset；缺省回退原始行数（无 mid 行同样占上游位置），
        // 不按过滤后的 albums.size 推进，否则有不可用行时会重复拉同一页。
        val rows = value.optJSONArray("albums")
        return AlbumPage(rows.items(QqMapper::album), value.total(),
            if (value.isNull("nextOffset")) null else value.optInt("nextOffset").takeIf { it >= 0 },
            rows?.length() ?: 0)
    }
    suspend fun albums(mid: String, order: Int, offset: Int, num: Int = 30): AlbumPage =
        albumPage(HelperNext.call("fetch_artist_albums_page", params(mid, order, offset, num)))
}
