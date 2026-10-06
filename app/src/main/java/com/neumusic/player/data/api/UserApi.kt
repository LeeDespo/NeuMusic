package com.neumusic.player.data.api

import com.example.qqmusic_api_helper_next.HelperError
import com.example.qqmusic_api_helper_next.fetchFollowSingersAtOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 非敏感登录状态（宿主模型）：由组件 typed loginStatus 映射而来，
 * 只含账号 id 与昵称，不含任何会话秘密；快照为 null 表示未登录（或尚未解析）。
 */
data class LoginStatus(val musicId: Long?, val nickname: String?)

object UserApi {
    /**
     * 关注的歌手一页；euin 传 null 由组件按当前登录账号解析。
     * typed fetchFollowSingersAtOffset 与旧 raw fetch_follow_singers 同一上游方法，
     * offset 按已加载行数直接作 From。
     */
    suspend fun followSingers(offset: Int = 0, num: Int = 30): Pair<List<FollowSinger>, Int> = withContext(Dispatchers.IO) {
        val page = try { fetchFollowSingersAtOffset(null, offset.toLong(), num.toLong()) }
        catch (error: HelperError) { throw IllegalStateException(error.userMessage(), error) }
        (page.users.orEmpty().map { user ->
            FollowSinger(user.mid.orEmpty(), user.name.orEmpty(), user.avatarUrl.orEmpty().toHttps(), user.desc.orEmpty())
        }) to (page.total?.takeIf { it >= 0 }?.toInt() ?: -1)
    }
}

data class FollowSinger(val mid: String, val name: String, val pic: String, val desc: String)
