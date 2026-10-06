package com.neumusic.player.data.api

import com.neumusic.player.data.Quality
import com.neumusic.player.data.Track
import org.json.JSONArray
import org.json.JSONObject

sealed interface LikeResult {
    data object Success : LikeResult

    /** 被服务端拒绝（多半是短时风控限流）。 */
    data class Rejected(val code: Int) : LikeResult

    /** 本地条件不满足，压根没发请求。 */
    data class Unavailable(val reason: String) : LikeResult

    val ok: Boolean get() = this is Success
}


object SongApi {
    class PlayUrlException(message: String, val result: Int) : IllegalStateException(message)

    private fun format(quality: Quality): String = when (quality) {
        Quality.STANDARD -> "MP3_128"
        Quality.HQ -> "MP3_320"
        Quality.AAC96 -> "ACC_96"
        Quality.OGG192 -> "OGG_192"
        Quality.OGG320 -> "OGG_320"
        Quality.FLAC -> "FLAC"
    }
    private fun sizeKey(quality: Quality): String = when (quality) {
        Quality.STANDARD -> "128mp3"
        Quality.HQ -> "320mp3"
        Quality.AAC96 -> "96aac"
        Quality.OGG192 -> "192ogg"
        Quality.OGG320 -> "320ogg"
        Quality.FLAC -> "flac"
    }

    suspend fun playUrl(track: Track, quality: Quality = Quality.STANDARD): String {
        val wanted = listOf(quality) + Quality.entries.filter { it != quality }
        val chain = if (track.fileSizes.isEmpty()) wanted else wanted.filter {
            (track.fileSizes[sizeKey(it)] ?: 0L) > 0L
        }.ifEmpty { listOf(Quality.STANDARD) }
        var sawVipOnly = false
        var sawThrottle = false
        var lastResult = -1
        for (tier in chain) {
            val response = HelperNext.call("resolve_song_urls", JSONObject()
                .put("fileType", format(tier)).put("fileInfo", JSONArray().put(JSONObject()
                    .put("mid", track.mid).put("mediaMid", track.mediaMid).put("songType", 0))))
            val info = response.optJSONArray("data")?.optJSONObject(0)
                ?: throw PlayUrlException("播放地址响应不完整", -1)
            lastResult = if (info.isNull("result")) -1 else info.optInt("result", -1)
            val purl = info.text("purl")
            if (lastResult == 0 && purl.isNotBlank()) return if (purl.startsWith("http") || purl.startsWith("//"))
                purl.toHttps() else "https://isure.stream.qqmusic.qq.com/${purl.trimStart('/')}"
            if (lastResult == 104003) sawVipOnly = true
            if (lastResult == 101404) sawThrottle = true
        }
        throw PlayUrlException(when {
            sawVipOnly && lastResult == 104003 -> "该曲目/音质需要会员播放权限（错误 104003），请确认会员状态或重新登录"
            sawThrottle -> "取链接被限流，请稍后重试"
            lastResult == 22 -> "播放凭据已过期，请重新登录"
            else -> "拿不到播放链接（错误 $lastResult）"
        }, lastResult)
    }

    suspend fun intro(mid: String): String? = runCatching { HelperNext.call("fetch_song_detail",
        JSONObject().put("songMid", mid)).optJSONObject("detail")?.text("description")?.takeIf(String::isNotBlank) }.getOrNull()

    suspend fun setLiked(track: Track, liked: Boolean): LikeResult {
        if (HelperNext.login.value == null) return LikeResult.Unavailable("未登录")
        if (track.songId <= 0L) return LikeResult.Unavailable("这首没有可用的歌曲 id")
        val receipt = HelperNext.call("set_liked_by_id", JSONObject().put("songId", track.songId).put("liked", liked))
        return if (receipt.optBoolean("success")) LikeResult.Success else LikeResult.Rejected(receipt.optInt("code", -1))
    }
}
