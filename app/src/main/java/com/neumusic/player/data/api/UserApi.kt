package com.neumusic.player.data.api

import com.neumusic.player.data.api.QqCore.Req
import com.neumusic.player.data.api.QqCore.call
import com.neumusic.player.data.api.QqCore.commAuth
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.api.QqCore.data
import org.json.JSONArray
import org.json.JSONObject

/** 用户信息。 */
object UserApi {

    /**
     * 关注的歌手列表（2026-09-30 接入，端点出自 qqmusic-api 源码）：
     * `music.concern.RelationList/GetFollowSingerList`，param `{HostUin: euin, From: offset, Size: num}`，
     * 需登录 comm；响应 `data.List[*]`，字段 `MID/Name/AvatarUrl/Desc`，总数 `data.Total`。
     */
    suspend fun followSingers(offset: Int = 0, num: Int = 30): Pair<List<FollowSinger>, Int> {
        val euin = Prefs.credential?.euin.orEmpty()
        if (euin.isEmpty()) return emptyList<FollowSinger>() to 0
        val root = call(commAuth(), "req_1" to Req(
            "music.concern.RelationList", "GetFollowSingerList", JSONObject()
                .put("HostUin", euin)
                .put("From", offset)
                .put("Size", num)))
        val d = data(root)
        val arr = d?.optJSONArray("List") ?: JSONArray().also {
            com.neumusic.player.data.AppLog.w("Follow", "followSingers empty: code=${QqCore.code(root)}")
        }
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val mid = o.optString("MID")
            val name = o.optString("Name")
            if (mid.isEmpty() && name.isEmpty()) null else FollowSinger(
                mid = mid,
                name = name,
                pic = o.optString("AvatarUrl").toHttps(),
                desc = o.optString("Desc"),
            )
        }
        return list to (d?.optInt("Total", -1) ?: -1)
    }

    /** 登录用户昵称（主页问候语用）。未登录或失败返回 null。不持久化，见 `NicknameCache`。 */
    suspend fun nickname(): String? = runCatching {
        val root = call(commAuth(), "req_1" to Req(
            "music.UserInfo.userInfoServer", "GetLoginUserInfo", JSONObject()))
        data(root)?.optJSONObject("info")?.optString("nick")?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

/** 关注的歌手（主页「关注的歌手」栏）。 */
data class FollowSinger(
    val mid: String,
    val name: String,
    val pic: String,
    val desc: String,   // 服务端文案（如粉丝数）；歌手卡第二行用它
)
