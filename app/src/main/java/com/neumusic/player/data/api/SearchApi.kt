package com.neumusic.player.data.api

import com.example.qqmusic_api_helper_next.HelperError
import com.example.qqmusic_api_helper_next.searchAlbums
import com.example.qqmusic_api_helper_next.searchArtists
import com.example.qqmusic_api_helper_next.searchPlaylists
import com.example.qqmusic_api_helper_next.searchSongs
import com.neumusic.player.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 组件 typed 四类搜索的消费与 UI 映射；分页按页号续拉（appendMore 行为不变）。 */
object SearchApi {
    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) {
        try { block() } catch (error: HelperError) { throw IllegalStateException(error.userMessage(), error) }
    }
    suspend fun songs(query: String, num: Int = 30, page: Int = 1): List<Track> =
        call { searchSongs(query, page.toLong(), num.toLong()).tracks.mapNotNull(QqMapper::track) }
    suspend fun singers(query: String, num: Int = 30, page: Int = 1): List<SearchSinger> =
        call { searchArtists(query, page.toLong(), num.toLong()).artists.mapNotNull(QqMapper::artist) }
    suspend fun albums(query: String, num: Int = 30, page: Int = 1): List<AlbumItem> =
        call { searchAlbums(query, page.toLong(), num.toLong()).albums.mapNotNull(QqMapper::album) }
    suspend fun playlists(query: String, num: Int = 30, page: Int = 1): List<PlaylistItem> =
        call { searchPlaylists(query, page.toLong(), num.toLong()).playlists.mapNotNull(QqMapper::playlist) }
}
