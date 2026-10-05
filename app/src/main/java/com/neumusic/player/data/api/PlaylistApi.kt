package com.neumusic.player.data.api

import com.neumusic.player.data.*
import org.json.JSONObject

object PlaylistApi {
    data class Page(val songs: List<Track>, val total: Int?, val nextOffset: Int? = null) {
        fun advanceFrom(offset: Int): Int = (nextOffset ?: (offset + songs.size)).also {
            check(it >= offset) { "歌曲分页偏移倒退" }
        }
    }
    internal fun page(value: JSONObject) = Page(value.optJSONArray("tracks").items(QqMapper::track), value.total(),
        if (value.isNull("nextOffset")) null else value.optInt("nextOffset").takeIf { it >= 0 })
    suspend fun likedPage(offset: Int = 0, num: Int = 100): Page = page(HelperNext.call(
        "fetch_playlist_tracks_page", JSONObject().put("listId", 0).put("dirId", 201).put("offset", offset).put("limit", num)))
    suspend fun playlistPage(tid: Long, offset: Int = 0, num: Int = 100): Page = page(HelperNext.call(
        "fetch_playlist_tracks_page", JSONObject().put("listId", tid).put("offset", offset).put("limit", num)))
    suspend fun albumPage(albumMid: String, offset: Int = 0, num: Int = 100): Page = page(HelperNext.call(
        "fetch_album_tracks", JSONObject().put("albumMid", albumMid).put("offset", offset).put("limit", num)))
    suspend fun favPlaylists(page: Int = 1, num: Int = 50): List<PlaylistItem> = HelperNext.call(
        "fetch_fav_playlists", JSONObject().put("page", page).put("num", num)).optJSONArray("playlists").items { o ->
        PlaylistItem(o.optLong("id"), o.text("title"), o.text("picurl").toHttps(), o.optInt("songnum"))
    }
    suspend fun favAlbums(page: Int = 1, num: Int = 50): List<AlbumItem> = HelperNext.call(
        "fetch_fav_albums", JSONObject().put("page", page).put("num", num)).optJSONArray("albums").items { o ->
        val mid = o.text("mid").takeIf(String::isNotBlank) ?: return@items null
        val image = o.text("pmid").ifEmpty { mid }
        val cover = if (image.startsWith("http") || image.startsWith("//")) image.toHttps()
            else "https://y.gtimg.cn/music/photo_new/T002R300x300M000$image.jpg"
        AlbumItem(mid, o.text("name").ifEmpty { o.text("title") }, cover, o.optInt("songnum"),
            o.optJSONArray("singers").items { it.text("name") }.joinToString(" / "))
    }
}
