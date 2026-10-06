package com.neumusic.player.data.api

import com.example.qqmusic_api_helper_next.AlbumPage as ComponentAlbumPage
import com.example.qqmusic_api_helper_next.HelperError
import com.example.qqmusic_api_helper_next.artistAlbumsPage
import com.example.qqmusic_api_helper_next.artistSongsPage
import com.neumusic.player.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 歌手歌曲/专辑 typed 页消费；sort 取 "hot"/"latest" 与宿主 ORDER_HOT/ORDER_NEW 一一对应。 */
object SingerApi {
    const val ORDER_HOT = 1
    const val ORDER_NEW = 2
    suspend fun resolve(name: String): SearchSinger? = SearchApi.singers(name, 10).let {
        list -> list.firstOrNull { it.name == name } ?: list.firstOrNull()
    }
    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) {
        try { block() } catch (error: HelperError) { throw IllegalStateException(error.userMessage(), error) }
    }
    private fun sort(order: Int) = if (order == ORDER_NEW) "latest" else "hot"

    suspend fun songs(mid: String, order: Int, offset: Int, num: Int = 100): PlaylistApi.Page = call {
        PlaylistApi.page(artistSongsPage(mid, sort(order), offset.toUInt(), num.toUInt()))
    }

    data class AlbumPage(val albums: List<AlbumItem>, val total: Int?, val nextOffset: Int? = null,
                         private val rawRowCount: Int = albums.size) {
        /**
         * 组件 nextOffset 即原始行偏移（无 mid 行已被组件从 albums 省略但仍占上游位置），
         * 现行端点恒回传；万一缺省，防御回退按本页行数推进（typed 模型不再暴露 raw 行数）。
         */
        fun advanceFrom(offset: Int): Int = (nextOffset ?: (offset + rawRowCount)).also {
            check(it >= offset) { "专辑分页偏移倒退" }
        }
    }
    /** typed AlbumPage → 宿主 AlbumPage；total/nextOffset 收窄为非负 Int。 */
    internal fun albumPage(p: ComponentAlbumPage): AlbumPage = AlbumPage(
        p.albums.mapNotNull(QqMapper::album),
        p.total?.takeIf { it >= 0 }?.toInt(),
        p.nextOffset?.takeIf { it >= 0 }?.toInt(),
    )
    suspend fun albums(mid: String, order: Int, offset: Int, num: Int = 30): AlbumPage = call {
        albumPage(artistAlbumsPage(mid, sort(order), offset.toUInt(), num.toUInt()))
    }
}
