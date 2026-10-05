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
    data class AlbumPage(val albums: List<AlbumItem>, val total: Int?, private val rawRowCount: Int) {
        /** fetch_artist_albums_page 不回传 nextOffset，按原始行数推进（无 mid 行同样占上游位置）。 */
        fun advanceFrom(offset: Int): Int = offset + rawRowCount
    }
    suspend fun albums(mid: String, order: Int, offset: Int, num: Int = 30): AlbumPage {
        val page = HelperNext.call("fetch_artist_albums_page", params(mid, order, offset, num))
        val rows = page.optJSONArray("albums")
        return AlbumPage(rows.items(QqMapper::album), page.total(), rows?.length() ?: 0)
    }
}
