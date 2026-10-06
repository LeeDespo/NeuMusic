package com.neumusic.player.data.api

import com.example.qqmusic_api_helper_next.Album
import com.example.qqmusic_api_helper_next.Artist
import com.example.qqmusic_api_helper_next.Playlist
import com.example.qqmusic_api_helper_next.Track as ComponentTrack
import com.neumusic.player.data.*
import org.json.JSONObject

/** Component values to UI models; upstream response parsing lives in Rust. */
internal object QqMapper {
    // ── raw JSON 映射：其余域（电台/歌单/歌手专辑页）typed 迁移前的存量路径 ──
    fun track(o: JSONObject): Track? {
        val mid = o.text("songMid").takeIf { it.isNotBlank() } ?: return null
        val sizes = o.optJSONArray("fileSizes").items { f -> f.text("name") to f.optLong("bytes") }.toMap()
        val singers = o.optJSONArray("singers").items { it.text("name").takeIf(String::isNotBlank) }
        return Track(mid, o.text("title").cleanHighlight(), o.text("mediaMid").ifEmpty { mid },
            singers.joinToString(" / ").ifEmpty { o.text("artist") }, o.text("album"), o.text("albumMid"),
            o.optInt("duration"), o.optInt("payPlay") == 1, o.optLong("songId"), sizes, o.optInt("genre"))
    }
    fun album(o: JSONObject): AlbumItem? {
        val mid = o.text("albumMid").takeIf(String::isNotBlank) ?: return null
        return AlbumItem(mid, o.text("title").cleanHighlight(), o.text("coverURL").toHttps(),
            o.optInt("songCount"), o.text("artist"))
    }

    // ── typed 搜索映射：组件对歌手/专辑/歌单路线已去 <em>，歌曲路线不去，
    //    且组件不解 HTML 实体，所以宿主统一保留 cleanHighlight（幂等）。
    //    歌手串用 singers 重组「 / 」，不用 typed artist（", " 分隔与宿主切分规则冲突）。──
    fun track(t: ComponentTrack): Track? {
        val mid = t.songMid.takeIf(String::isNotBlank) ?: return null
        val sizes = t.fileSizes.associate { it.name to it.bytes }
        val singers = t.singers.orEmpty().mapNotNull { it.name?.takeIf(String::isNotBlank) }
        return Track(mid, t.title.cleanHighlight(), t.mediaMid?.takeIf(String::isNotEmpty) ?: mid,
            singers.joinToString(" / ").ifEmpty { t.artist }, t.album ?: "", t.albumMid ?: "",
            (t.duration ?: 0L).toInt(), t.payPlay == 1L, t.songId ?: 0L, sizes, (t.genre ?: 0L).toInt())
    }
    fun artist(a: Artist) = SearchSinger(a.singerMid, a.name.cleanHighlight(),
        (a.coverUrl ?: "").toHttps(), (a.songCount ?: 0L).toInt(), (a.albumCount ?: 0L).toInt())
    fun album(a: Album): AlbumItem? {
        val mid = a.albumMid?.takeIf(String::isNotBlank) ?: return null
        return AlbumItem(mid, a.title.cleanHighlight(), (a.coverUrl ?: "").toHttps(),
            (a.songCount ?: 0L).toInt(), a.artist ?: "")
    }
    fun playlist(p: Playlist) = PlaylistItem(p.id, p.title.cleanHighlight(),
        (p.coverUrl ?: "").toHttps(), (p.songCount ?: 0L).toInt())
}
