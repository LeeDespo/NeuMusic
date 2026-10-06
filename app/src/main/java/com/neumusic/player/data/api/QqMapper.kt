package com.neumusic.player.data.api

import com.example.qqmusic_api_helper_next.Album
import com.example.qqmusic_api_helper_next.Artist
import com.example.qqmusic_api_helper_next.Playlist
import com.example.qqmusic_api_helper_next.RadioGroup as ComponentRadioGroup
import com.example.qqmusic_api_helper_next.Track as ComponentTrack
import com.example.qqmusic_api_helper_next.UserFavAlbumItem
import com.example.qqmusic_api_helper_next.UserFavSonglistItem
import com.neumusic.player.data.*

/** Component values to UI models; upstream response parsing lives in Rust. */
internal object QqMapper {
    // ── typed 组件值 → UI 模型。组件对歌手/专辑/歌单/收藏路线已去 <em>，歌曲路线不去，
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

    // ── 歌单域：收藏歌单/专辑（page 制列表）。收藏专辑的封面沿用 pmid 拼接规则——
    //    typed UserFavAlbumItem 只给 pmid，成品 URL 由宿主拼。──
    fun favPlaylist(p: UserFavSonglistItem) = PlaylistItem(p.id ?: 0L, p.title.orEmpty(),
        p.picurl.orEmpty().toHttps(), (p.songnum ?: 0L).toInt())
    fun favAlbum(a: UserFavAlbumItem): AlbumItem? {
        val mid = a.mid?.takeIf(String::isNotBlank) ?: return null
        val image = a.pmid.orEmpty().ifEmpty { mid }
        val cover = if (image.startsWith("http") || image.startsWith("//")) image.toHttps()
            else "https://y.gtimg.cn/music/photo_new/T002R300x300M000$image.jpg"
        return AlbumItem(mid, a.name.orEmpty().ifEmpty { a.title.orEmpty() }, cover,
            (a.songnum ?: 0L).toInt(),
            a.singers.orEmpty().map { it.name.orEmpty() }.joinToString(" / "))
    }

    // ── 电台域：typed RadioStation 只有 id/title/coverUrl（listenDesc 已随迁移弃用——UI 从未展示）。──
    fun radioGroup(g: ComponentRadioGroup) = RadioGroup(g.title, g.stations.map { s ->
        RadioStation(s.id.toInt(), s.title, s.coverUrl.orEmpty().toHttps())
    })
}
