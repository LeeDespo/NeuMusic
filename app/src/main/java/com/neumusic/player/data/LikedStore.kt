package com.neumusic.player.data

import com.neumusic.player.data.api.PlaylistApi
import com.neumusic.player.data.api.SongApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 「我喜欢」的全局状态：一轮拉取后常驻内存，供任意列表行显示红心状态。
 *
 * 用 songId 集合做判定（写操作也用 songId，见 [SongApi.setLiked]）。
 * 收藏列表很长（几百首），一次拉 300 首足够覆盖列表里出现的曲目；
 * 超出范围的曲子会显示为未喜欢，这是刻意的取舍 —— 只在用户点红心时才真正写。
 */
object LikedStore {
    private val _liked = MutableStateFlow<Set<Long>>(emptySet())
    val liked: StateFlow<Set<Long>> = _liked

    private val _loaded = MutableStateFlow(false)

    @Volatile
    private var loading = false

    /** 是否已成功拉取过一次。 */
    val loaded: StateFlow<Boolean> = _loaded

    /**
     * 拉取一次我喜欢列表并缓存。已加载过则直接返回（除非 [force]）。
     * 未登录时静默跳过。
     */
    suspend fun refresh(force: Boolean = false) {
        if (Prefs.credential?.euin.isNullOrEmpty()) return
        if (loading) return
        if (_loaded.value && !force) return
        loading = true
        // 翻页取全：喜欢状态必须准确，漏页会导致列表红心显示错误。
        val ids = HashSet<Long>()
        var offset = 0
        var guard = 0
        while (guard++ < 50) {
            val page = runCatching { PlaylistApi.likedPage(offset, 300) }.getOrNull() ?: break
            ids += page.songs.mapNotNull { it.songId.takeIf { id -> id > 0L } }
            offset += 300
            val t = page.total
            if (page.songs.isEmpty()) break
            if (t != null && ids.size >= t) break
            if (page.songs.size < 300) break
        }
        if (ids.isNotEmpty() || offset > 0) {
            _liked.value = ids
            _loaded.value = true
        }
        loading = false
    }

    fun isLiked(songId: Long): Boolean = songId > 0L && _liked.value.contains(songId)

    /** 本地翻转（写接口成功后调用），避免为了刷新而再拉一次整表。 */
    fun mark(songId: Long, liked: Boolean) {
        if (songId <= 0L) return
        _liked.value = if (liked) _liked.value + songId else _liked.value - songId
    }

    /** 退出登录时清空。 */
    fun clear() {
        _liked.value = emptySet()
        _loaded.value = false
    }
}
