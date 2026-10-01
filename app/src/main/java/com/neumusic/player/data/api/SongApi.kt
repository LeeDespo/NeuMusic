package com.neumusic.player.data.api

import com.neumusic.player.data.AppLog
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.Quality
import com.neumusic.player.data.Track
import com.neumusic.player.data.api.QqCore.Req
import com.neumusic.player.data.api.QqCore.call
import com.neumusic.player.data.api.QqCore.commAuth
import com.neumusic.player.data.api.QqCore.commWeb
import com.neumusic.player.data.api.QqCore.data
import org.json.JSONArray
import org.json.JSONObject

/** 曲目相关：搜索、播放直链、喜欢（写）。 */
/**
 * 「我喜欢」写操作的结果。
 *
 * 之所以不用 Boolean：服务端失败时**业务码有不同含义**，必须区分开给用户看。
 * 实测 `AddSonglist/DelSonglist` 在短时间多次调用后会返回业务码 **1000**
 * （读接口同时完全正常），隔一段时间才恢复 —— 属于**风控限流**，不是参数错、也不是没权限。
 * 早前 `80105` 是网页 comm 下的结果，`1000` 是这里的限流码。
 */
sealed interface LikeResult {
    data object Success : LikeResult

    /** 被服务端拒绝（多半是短时风控限流）。 */
    data class Rejected(val code: Int) : LikeResult

    /** 本地条件不满足，压根没发请求。 */
    data class Unavailable(val reason: String) : LikeResult

    val ok: Boolean get() = this is Success
}

object SongApi {

    /**
     * 取播放直链。[quality] 指定首选音质档位（filename 前缀与扩展名必须成对）。
     *
     * 实测要点（2026-09-25）：
     * - **104003 是按"档位"给的，不是按"歌"给的** —— 免费歌的无损/高品质档同样返回
     *   104003，但它的标准档（M500）能正常拿 purl。所以**绝不能见到 104003 就中断
     *   降级链**（早前版本犯过这个错：播放音质设为无损时，所有歌都误报"没有会员"）。
     * - 有些歌没有某些档位（`file.size_*` 为 0），请求不存在的档位只会拿到误导性
     *   错误码 —— 先用 [Track.fileSizes] 过滤，只请求真实存在的档位。
     * - `result=101404` 为短时限流，降级重试即可。
     *
     * 失败抛 [PlayUrlException]，message 可直接展示。
     */
    suspend fun playUrl(track: Track, quality: Quality = Quality.STANDARD): String {
        val wanted = buildList {
            add(quality)
            Quality.entries.filter { it != quality }.sortedBy { it.ordinal }.forEach { add(it) }
        }
        // 这首歌实际拥有的档位（size > 0）；元数据缺失（fileSizes 为空）时全链都试。
        val chain = if (track.fileSizes.isEmpty()) wanted
        else wanted.filter { hasFile(track, it) }.ifEmpty { listOf(Quality.STANDARD) }

        var sawVipOnly = false
        var sawThrottle = false
        var lastResult = -1
        for (q in chain) {
            val r = fetchPurl(track, q)
            r.getOrNull()?.let { return it }
            val res = (r.exceptionOrNull() as? PlayUrlException)?.result ?: -1
            lastResult = res
            when (res) {
                104003 -> sawVipOnly = true      // 这个档位要会员 —— 降级继续试
                101404 -> sawThrottle = true
            }
        }
        AppLog.w("Play", "playUrl failed: ${track.mid} lastResult=$lastResult")
        throw PlayUrlException(
            when {
                // 连最便宜的档位都要会员，才下"无播放权限"的结论
                sawVipOnly && lastResult == 104003 ->
                    "该曲目/音质需要会员播放权限（错误 104003），请确认会员状态或重新登录"
                sawThrottle -> "取链接被限流，请稍后重试"
                else -> "拿不到播放链接（错误 $lastResult）"
            },
            lastResult,
        )
    }

    /** 这首歌是否真的提供该档位（`file.size_*` > 0 才存在）。 */
    private fun hasFile(track: Track, q: Quality): Boolean = when (q) {
        Quality.STANDARD -> (track.fileSizes["128mp3"] ?: 0L) > 0
        Quality.HQ -> (track.fileSizes["320mp3"] ?: 0L) > 0
        Quality.AAC96 -> (track.fileSizes["96aac"] ?: 0L) > 0
        Quality.OGG192 -> (track.fileSizes["192ogg"] ?: 0L) > 0
        Quality.OGG320 -> (track.fileSizes["320ogg"] ?: 0L) > 0
        Quality.FLAC -> (track.fileSizes["flac"] ?: 0L) > 0
    }

    class PlayUrlException(message: String, val result: Int) : IllegalStateException(message)

    private suspend fun fetchPurl(track: Track, q: Quality): Result<String> {
        val cred = Prefs.credential
        val param = JSONObject()
            .put("uin", cred?.uin ?: "0")
            .put("filename", JSONArray().put("${q.prefix}${track.mediaMid}${q.ext}"))
            .put("guid", Prefs.guid)
            .put("songmid", JSONArray().put(track.mid))
            .put("songtype", JSONArray().put(0))
            .put("ctx", 0)
        val root = call(commAuth(), "req_1" to Req("music.vkey.GetVkey", "UrlGetVkey", param))
        val info = data(root)?.optJSONArray("midurlinfo")?.optJSONObject(0)
        val purl = info?.optString("purl").orEmpty()
        return if (purl.isNotEmpty()) Result.success("https://isure.stream.qqmusic.qq.com/$purl")
        else Result.failure(PlayUrlException("purl empty", info?.optInt("result", -1) ?: -1))
    }

    /**
     * 歌曲介绍（详情页/推荐卡的介绍框）。端点 2026-09-30 curl 实测：
     * `music.pf_song_detail_svr/get_song_detail_yqq`（匿名网页 comm 即可），param `{song_mid}`，
     * 文案在 `data.info.intro.content[*].value`。没有介绍返回 null（调用方显示占位文案）。
     */
    suspend fun intro(mid: String): String? = runCatching {
        val root = call(commWeb, "req_1" to Req(
            "music.pf_song_detail_svr", "get_song_detail_yqq", JSONObject().put("song_mid", mid)))
        val arr = data(root)?.optJSONObject("info")?.optJSONObject("intro")
            ?.optJSONArray("content") ?: return@runCatching null
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i)?.optString("value").orEmpty()
            if (v.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(v)
            }
        }
        sb.toString().takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * 加/取消「我喜欢」（dirId 固定 201）。
     *
     * 两个必须守住的点：
     * 1. 参数是**数字 songId** 而非 mid（`songId<=0` 时无法操作）。
     * 2. 必须用 **Android 登录 comm**。公开文档里此接口返回 `80105` 被判为「不可写」，
     *    那是**网页 comm（ct:24）**下的结果；换 Android 档（ct:11）实测可用。
     */
    suspend fun setLiked(track: Track, liked: Boolean): LikeResult {
        if (Prefs.credential == null) return LikeResult.Unavailable("未登录")
        if (track.songId <= 0L) return LikeResult.Unavailable("这首没有可用的歌曲 id")
        val param = JSONObject()
            .put("dirId", 201)
            .put("tid", 0)
            .put("bFmtUtf8", true)
            .put("v_songInfo", JSONArray().put(
                JSONObject().put("songId", track.songId).put("songType", 0)))
        val root = call(commAuth(), "req_1" to Req(
            "music.musicasset.PlaylistDetailWrite",
            if (liked) "AddSonglist" else "DelSonglist", param))
        // 成功要同时看外层 code 与内层 retCode，只看外层会误报。
        val retCode = data(root)?.optInt("retCode", -1) ?: -1
        val biz = QqCore.code(root)
        if (biz != 0 || retCode != 0) AppLog.w("Like", "setLiked($liked) rejected: biz=$biz retCode=$retCode")
        return if (biz == 0 && retCode == 0) LikeResult.Success else LikeResult.Rejected(biz)
    }
}
