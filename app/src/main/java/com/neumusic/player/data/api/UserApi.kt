package com.neumusic.player.data.api

import com.neumusic.player.data.api.QqCore.Req
import com.neumusic.player.data.api.QqCore.call
import com.neumusic.player.data.api.QqCore.commAuth
import com.neumusic.player.data.api.QqCore.data
import org.json.JSONObject

/** 用户信息。 */
object UserApi {

    /** 登录用户昵称（主页问候语用）。未登录或失败返回 null。不持久化，见 `NicknameCache`。 */
    suspend fun nickname(): String? = runCatching {
        val root = call(commAuth(), "req_1" to Req(
            "music.UserInfo.userInfoServer", "GetLoginUserInfo", JSONObject()))
        data(root)?.optJSONObject("info")?.optString("nick")?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
