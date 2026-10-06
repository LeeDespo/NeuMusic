package com.neumusic.player.data.api

import com.example.qqmusic_api_helper_next.HelperError
import com.example.qqmusic_api_helper_next.radioStations
import com.example.qqmusic_api_helper_next.radioTrackBatch
import com.neumusic.player.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 电台 typed 消费：分组展示与无限轮换批次。组件 radioTrackBatch 每次给一批全新轮换
 * （旧 raw 显式带的 batches/excludeMids 组件本就不读）；跨批去重、失败容忍是宿主产品策略。
 */
object RadioApi {
    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) {
        try { block() } catch (error: HelperError) { throw IllegalStateException(error.userMessage(), error) }
    }
    suspend fun groups(): List<RadioGroup> = call { radioStations().map(QqMapper::radioGroup) }
    private suspend fun batch(radioId: Int, firstplay: Boolean): List<Track> = call {
        radioTrackBatch(radioId.toLong(), firstplay).tracks.mapNotNull(QqMapper::track)
    }
    suspend fun nextTracks(radioId: Int, firstplay: Boolean, exclude: Set<String>, batches: Int = 4): List<Track> {
        val acc = LinkedHashMap<String, Track>()
        var ok = 0
        repeat(batches) { i ->
            if (i > 0) delay(300)
            try {
                val tracks = batch(radioId, firstplay && i == 0)
                ok++
                tracks.filter { it.mid !in exclude }.forEach { acc.putIfAbsent(it.mid, it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IllegalStateException) { /* A later batch may succeed; never count a failed batch as empty. */ }
        }
        if (ok == 0) throw IllegalStateException("电台取歌失败，请稍后重试")
        return acc.values.toList()
    }
}
