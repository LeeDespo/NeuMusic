package com.neumusic.player.data

import com.neumusic.player.data.api.PlaylistApi
import com.neumusic.player.data.api.SongApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 「我喜欢」的全局状态：一轮拉取后常驻内存，供任意列表行显示红心状态。
 *
 * 用 songId 集合做判定（写操作也用 songId，见 [SongApi.setLiked]）。
 * 按实际返回数量推进偏移，直到已知总数或空页；失败的部分结果不标记为已加载。
 */
object LikedStore {
    private val _liked = MutableStateFlow<Set<Long>>(emptySet())
    val liked: StateFlow<Set<Long>> = _liked

    private val _loaded = MutableStateFlow(false)

    private val lock = Any()
    private val pendingMarks = mutableMapOf<Long, Boolean>()
    @Volatile
    private var loading = false
    @Volatile private var generation = 0L

    /** 是否已成功拉取过一次。 */
    val loaded: StateFlow<Boolean> = _loaded

    /**
     * 拉取一次我喜欢列表并缓存。已加载过则直接返回（除非 [force]）。
     * 未登录时静默跳过。
     */
    suspend fun refresh(force: Boolean = false) {
        val credential = Prefs.credential ?: return
        val requestGeneration = synchronized(lock) {
            if (loading || (_loaded.value && !force)) return
            loading = true
            pendingMarks.clear()
            generation
        }
        try {
            val accumulated = LikedPageAccumulator()
            repeat(50) {
                val page = runCatching { PlaylistApi.likedPage(accumulated.offset, 300) }.getOrNull() ?: return
                if (requestGeneration != generation || Prefs.credential != credential) return
                val complete = runCatching { accumulated.consume(page) }.getOrElse { return }
                val ids = accumulated.ids
                if (complete) {
                    synchronized(lock) {
                        if (requestGeneration != generation || Prefs.credential != credential) return
                        pendingMarks.forEach { (id, liked) -> if (liked) ids.add(id) else ids.remove(id) }
                        _liked.value = ids
                        _loaded.value = true
                    }
                    return
                }
            }
        } finally { synchronized(lock) { if (requestGeneration == generation) { loading = false; pendingMarks.clear() } } }
    }

    fun isLiked(songId: Long): Boolean = songId > 0L && _liked.value.contains(songId)

    /** 本地翻转（写接口成功后调用），避免为了刷新而再拉一次整表。 */
    fun mark(songId: Long, liked: Boolean) {
        if (songId <= 0L) return
        synchronized(lock) {
            if (loading) pendingMarks[songId] = liked
            _liked.value = if (liked) _liked.value + songId else _liked.value - songId
        }
    }

    /** 退出登录时清空。 */
    fun clear() {
        synchronized(lock) {
            generation++
            loading = false
            pendingMarks.clear()
            _liked.value = emptySet()
            _loaded.value = false
        }
    }
}

/** Tracks that cannot be normalized still consume positions in the upstream list. */
internal class LikedPageAccumulator {
    val ids = HashSet<Long>()
    var offset = 0
        private set

    fun consume(page: PlaylistApi.Page): Boolean {
        val next = page.advanceFrom(offset)
        val complete = page.total?.let { next >= it } ?: (next == offset)
        check(next > offset || complete) { "喜欢列表在总数边界前停止推进" }
        ids += page.songs.mapNotNull { it.songId.takeIf { id -> id > 0 } }
        offset = next
        return complete
    }
}
