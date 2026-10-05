package com.neumusic.player.data.api

import com.neumusic.player.data.Track

internal data class LikedSnapshot(val ids: Set<Long>, val total: Int?, val consumed: Int)

/** Independent server snapshot for live membership and restoration checks. */
internal suspend fun componentLikedSnapshot(): LikedSnapshot {
    val ids = LinkedHashSet<Long>()
    var offset = 0
    var total: Int? = null
    repeat(100) {
        val page = PlaylistApi.likedPage(offset, 300)
        check(page.songs.all { it.songId > 0 }) { "Snapshot has missing numeric IDs" }
        ids += page.songs.map(Track::songId)
        total = page.total ?: total
        val next = page.advanceFrom(offset)
        if (total != null && next >= total!!) return LikedSnapshot(ids, total, next)
        if (next == offset) {
            check(total == null) { "Incomplete liked snapshot" }
            return LikedSnapshot(ids, total, next)
        }
        offset = next
    }
    error("Liked snapshot exceeded page bound")
}
