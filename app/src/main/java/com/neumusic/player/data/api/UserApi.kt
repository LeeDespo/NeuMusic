package com.neumusic.player.data.api

import org.json.JSONObject

object UserApi {
    suspend fun followSingers(offset: Int = 0, num: Int = 30): Pair<List<FollowSinger>, Int> {
        val value = HelperNext.call("fetch_follow_singers", JSONObject().put("offset", offset).put("num", num))
        return value.optJSONArray("users").items { o ->
            FollowSinger(o.text("mid"), o.text("name"), o.text("avatarUrl").toHttps(), o.text("desc"))
        } to (value.total() ?: -1)
    }
    suspend fun nickname(): String? = runCatching { HelperNext.call("get_login_status")
        .optJSONObject("login")?.text("nickname")?.takeIf(String::isNotBlank) }.getOrNull()
}

data class FollowSinger(val mid: String, val name: String, val pic: String, val desc: String)
