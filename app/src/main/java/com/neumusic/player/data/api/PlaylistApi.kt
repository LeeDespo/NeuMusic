package com.neumusic.player.data.api

import com.example.qqmusic_api_helper_next.HelperError
import com.example.qqmusic_api_helper_next.TrackPage as ComponentTrackPage
import com.example.qqmusic_api_helper_next.albumTracks
import com.example.qqmusic_api_helper_next.fetchFavAlbums
import com.example.qqmusic_api_helper_next.fetchFavPlaylists
import com.example.qqmusic_api_helper_next.playlistTracksPage
import com.neumusic.player.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 歌单/我喜欢/专辑曲目的 typed 消费与 UI 映射；上游解析归组件，宿主只做产品映射。 */
object PlaylistApi {
    data class Page(
        val songs: List<Track>, val total: Int?, val nextOffset: Int? = null,
        private val rawRowCount: Int = songs.size,
    ) {
        /**
         * 组件 nextOffset 即原始行偏移（无 MID 的畸形行已被组件从 tracks 省略但仍占位），
         * 现行三类分页端点恒回传；万一缺省，防御回退按本页行数推进（typed 模型不再暴露 raw 行数）。
         */
        fun advanceFrom(offset: Int): Int = (nextOffset ?: (offset + rawRowCount)).also {
            check(it >= offset) { "歌曲分页偏移倒退" }
        }
    }

    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) {
        try { block() } catch (error: HelperError) { throw IllegalStateException(error.userMessage(), error) }
    }

    /** typed TrackPage → 宿主 Page；total/nextOffset 收窄为非负 Int。 */
    internal fun page(p: ComponentTrackPage): Page = Page(
        p.tracks.mapNotNull(QqMapper::track),
        p.total?.takeIf { it >= 0 }?.toInt(),
        p.nextOffset?.takeIf { it >= 0 }?.toInt(),
    )

    /** 我喜欢 = 保留目录（listId 0 + dirId 201），与旧 raw 同参。 */
    suspend fun likedPage(offset: Int = 0, num: Int = 100): Page = call {
        page(playlistTracksPage(0, 201, offset.toUInt(), num.toUInt()))
    }
    suspend fun playlistPage(tid: Long, offset: Int = 0, num: Int = 100): Page = call {
        page(playlistTracksPage(tid, null, offset.toUInt(), num.toUInt()))
    }
    suspend fun albumPage(albumMid: String, offset: Int = 0, num: Int = 100): Page = call {
        page(albumTracks(albumMid, null, offset.toLong(), num.toLong()))
    }

    /** 收藏歌单/专辑是 page 制分页（曲目才是 offset 制），勿混；euin 传 null 由组件按当前登录账号解析。 */
    suspend fun favPlaylists(page: Int = 1, num: Int = 50): List<PlaylistItem> = call {
        fetchFavPlaylists(null, page.toLong(), num.toLong()).playlists.orEmpty().mapNotNull(QqMapper::favPlaylist)
    }
    suspend fun favAlbums(page: Int = 1, num: Int = 50): List<AlbumItem> = call {
        fetchFavAlbums(null, page.toLong(), num.toLong()).albums.orEmpty().mapNotNull(QqMapper::favAlbum)
    }
}
