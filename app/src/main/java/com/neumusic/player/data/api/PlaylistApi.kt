package com.neumusic.player.data.api

import com.neumusic.player.data.AlbumItem
import com.neumusic.player.data.CredentialInfo
import com.neumusic.player.data.PlaylistItem
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.Track
import com.neumusic.player.data.api.QqCore.Req
import com.neumusic.player.data.api.QqCore.call
import com.neumusic.player.data.api.QqCore.commAuth
import com.neumusic.player.data.api.QqCore.data
import org.json.JSONObject

/** 歌单 / 专辑 / 我喜欢的读取。 */
object PlaylistApi {

    /**
     * 一页歌曲 + 该歌单/专辑的总数（total 为 null 表示服务端没给）。
     * 列表页据此**一直翻页到拿全**，而不是固定只取前 N 首。
     */
    data class Page(val songs: List<Track>, val total: Int?)

    /** 取凭据并要求 euin 存在；缺它时 `CgiGetDiss` 会静默返回空列表。 */
    private fun requireEuin(): CredentialInfo {
        val cred = Prefs.credential ?: throw IllegalStateException("未登录")
        if (cred.euin.isEmpty()) throw IllegalStateException("缺少 euin，请重新登录一次")
        return cred
    }

    /** 我喜欢（dirid=201）。 */
    suspend fun likedPage(offset: Int = 0, num: Int = 100): Page {
        val cred = requireEuin()
        val root = call(commAuth(), "req_1" to Req(
            "music.srfDissInfo.DissInfo", "CgiGetDiss", JSONObject()
                .put("disstid", 0).put("dirid", 201).put("tag", true)
                .put("song_begin", offset).put("song_num", num)
                .put("userinfo", true).put("orderlist", true)
                .put("enc_host_uin", cred.euin)))
        val d = data(root) ?: throw IllegalStateException("我喜欢数据为空")
        val songs = QqCore.mapItems(d.optJSONArray("songlist")) { QqMapper.track(it) }
        val total = d.optJSONObject("dirinfo")?.optInt("songnum", -1)?.takeIf { it >= 0 }
        return Page(songs, total)
    }


    /** 歌单内歌曲（disstid=歌单 tid，dirid=0），带总数。 */
    suspend fun playlistPage(tid: Long, offset: Int = 0, num: Int = 100): Page {
        val cred = requireEuin()
        val root = call(commAuth(), "req_1" to Req(
            "music.srfDissInfo.DissInfo", "CgiGetDiss", JSONObject()
                .put("disstid", tid).put("dirid", 0).put("tag", true)
                .put("song_begin", offset).put("song_num", num)
                .put("userinfo", true).put("orderlist", true)
                .put("enc_host_uin", cred.euin)))
        val d = data(root)
        val songs = QqCore.mapItems(d?.optJSONArray("songlist")) { QqMapper.track(it) }
        val total = d?.optJSONObject("dirinfo")?.optInt("songnum", -1)?.takeIf { it >= 0 }
        return Page(songs, total)
    }


    /** 收藏的歌单（结果在 `v_list[]`，不是 list/playlists）。 */
    suspend fun favPlaylists(page: Int = 1, num: Int = 50): List<PlaylistItem> {
        val cred = requireEuin()
        val root = call(commAuth(), "req_1" to Req(
            "music.musicasset.PlaylistFavRead", "CgiGetPlaylistFavInfo", JSONObject()
                .put("uin", cred.euin)
                .put("offset", (page - 1) * num).put("size", num)))
        return QqCore.mapItems(data(root)?.optJSONArray("v_list")) { QqMapper.playlistItem(it) }
    }

    /** 收藏的专辑（同样 `v_list[]`）。 */
    suspend fun favAlbums(page: Int = 1, num: Int = 50): List<AlbumItem> {
        val cred = requireEuin()
        val root = call(commAuth(), "req_1" to Req(
            "music.musicasset.AlbumFavRead", "CgiGetAlbumFavInfo", JSONObject()
                .put("euin", cred.euin)
                .put("offset", (page - 1) * num).put("size", num)))
        return QqCore.mapItems(data(root)?.optJSONArray("v_list")) { QqMapper.albumItem(it) }
    }

    /**
     * 专辑内歌曲。参数名是**驼峰 `albumMid`**，响应是 `songList[].songInfo`，
     * 总数在 `totalNum`（播客类可能为 0，属正常）。
     */
    suspend fun albumPage(albumMid: String, offset: Int = 0, num: Int = 100): Page {
        val root = call(commAuth(), "req_1" to Req(
            "music.musichallAlbum.AlbumSongList", "GetAlbumSongList", JSONObject()
                .put("albumMid", albumMid)
                .put("begin", offset).put("num", num)))
        val d = data(root)
        val songs = QqCore.mapItems(d?.optJSONArray("songList")) { item ->
            QqMapper.track(item.optJSONObject("songInfo") ?: item)
        }
        val total = d?.optInt("totalNum", -1)?.takeIf { it >= 0 }
        return Page(songs, total)
    }
}
