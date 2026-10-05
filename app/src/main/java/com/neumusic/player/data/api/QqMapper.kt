package com.neumusic.player.data.api

import com.neumusic.player.data.*
import org.json.JSONObject

/** Component values to UI models; upstream response parsing lives in Rust. */
internal object QqMapper {
    fun track(o: JSONObject): Track? {
        val mid = o.text("songMid").takeIf { it.isNotBlank() } ?: return null
        val sizes = o.optJSONArray("fileSizes").items { f -> f.text("name") to f.optLong("bytes") }.toMap()
        val singers = o.optJSONArray("singers").items { it.text("name").takeIf(String::isNotBlank) }
        return Track(mid, o.text("title").cleanHighlight(), o.text("mediaMid").ifEmpty { mid },
            singers.joinToString(" / ").ifEmpty { o.text("artist") }, o.text("album"), o.text("albumMid"),
            o.optInt("duration"), o.optInt("payPlay") == 1, o.optLong("songId"), sizes, o.optInt("genre"))
    }
    fun artist(o: JSONObject) = SearchSinger(o.text("singerMid"), o.optLong("id"),
        o.text("name").cleanHighlight(), o.text("coverURL").toHttps(), o.optInt("songCount"), o.optInt("albumCount"))
    fun album(o: JSONObject): AlbumItem? {
        val mid = o.text("albumMid").takeIf(String::isNotBlank) ?: return null
        return AlbumItem(mid, o.text("title").cleanHighlight(), o.text("coverURL").toHttps(),
            o.optInt("songCount"), o.text("artist"))
    }
    fun playlist(o: JSONObject) = PlaylistItem(o.optLong("id"), o.text("title").cleanHighlight(),
        o.text("coverURL").toHttps(), o.optInt("songCount"))
}
