package com.neumusic.player.data.api

import com.neumusic.player.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

object RadioApi {
    suspend fun groups(): List<RadioGroup> = HelperNext.call("fetch_radio_stations").optJSONArray("radioGroups").items { g ->
        RadioGroup(g.text("title"), g.optJSONArray("stations").items { s ->
            RadioStation(s.optInt("id"), s.text("title"), s.text("listenDesc"), s.text("coverURL").toHttps())
        })
    }
    private suspend fun batch(radioId: Int, firstplay: Boolean): List<Track> = HelperNext.call(
        "fetch_radio_track_batch", JSONObject().put("stationId", radioId).put("firstPlay", firstplay)
            .put("batches", 1).put("excludeMids", org.json.JSONArray())).optJSONArray("tracks").items(QqMapper::track)
    suspend fun tracks(radioId: Int, num: Int = 50): List<Track>? = batch(radioId, true).ifEmpty { null }
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
