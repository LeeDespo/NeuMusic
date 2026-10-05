package com.neumusic.player.data.api

import com.neumusic.player.data.*
import org.json.JSONObject

/** Paging and UI mapping for the component's four search categories. */
object SearchApi {
    private suspend fun search(method: String, query: String, num: Int, page: Int) =
        HelperNext.call(method, JSONObject().put("keyword", query).put("limit", num).put("page", page))
    suspend fun songs(query: String, num: Int = 30, page: Int = 1): List<Track> =
        search("search_songs", query, num, page).optJSONArray("tracks").items(QqMapper::track)
    suspend fun singers(query: String, num: Int = 30, page: Int = 1): List<SearchSinger> =
        search("search_artists", query, num, page).optJSONArray("artists").items(QqMapper::artist)
    suspend fun albums(query: String, num: Int = 30, page: Int = 1): List<AlbumItem> =
        search("search_albums", query, num, page).optJSONArray("albums").items(QqMapper::album)
    suspend fun playlists(query: String, num: Int = 30, page: Int = 1): List<PlaylistItem> =
        search("search_playlists", query, num, page).optJSONArray("playlists").items(QqMapper::playlist)
}
