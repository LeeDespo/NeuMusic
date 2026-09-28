package com.neumusic.player.data.api

import com.neumusic.player.data.RadioGroup
import com.neumusic.player.data.Track
import com.neumusic.player.data.api.QqCore.Req
import com.neumusic.player.data.api.QqCore.call
import com.neumusic.player.data.api.QqCore.commAuth
import com.neumusic.player.data.api.QqCore.commRadio
import com.neumusic.player.data.api.QqCore.data
import org.json.JSONObject

/** 电台：分组列表与分组内电台曲目。 */
object RadioApi {

    /** 电台分组（热门/心情/主题…）。分组在 `radio_list[]`，电台在其 `list[]`。 */
    suspend fun groups(): List<RadioGroup> {
        val root = call(commRadio, "radiolist" to Req(
            "pf.radiosvr", "GetRadiolist", JSONObject().put("ct", "24")))
        return QqMapper.radioGroups(data(root, "radiolist")?.optJSONArray("radio_list"))
    }

    /**
     * 电台曲目。[radioId] 必须是**真实电台 id**（不是分组 id）。
     * 网页裸调会被风控，必须带登录 comm。
     */
    suspend fun tracks(radioId: Int, num: Int = 50): List<Track>? {
        val root = call(commAuth(), "songlist" to Req(
            "mb_track_radio_svr", "get_radio_track",
            JSONObject().put("id", radioId).put("firstplay", 1).put("num", num)))
        val arr = data(root, "songlist")?.optJSONArray("tracks") ?: return null
        val result = QqCore.mapItems(arr) { QqMapper.track(it) }
        return result.ifEmpty { null }
    }

    /**
     * 电台**下一批**曲目（无限加载用）。
     *
     * 实测（2026-09-29）：`get_radio_track` 每次调用只回 **5 首**（`num` 被忽略），
     * 但**每次调用都返回不同的歌**（连续三批 0 重叠）——所以"加载更多"= 反复调用，
     * 按 mid 去重累积。第一批用 `firstplay=1`，后续传 0。
     *
     * [exclude] 是已加载的 mid 集合；某批全部重复视为**电台已循环到底**，返回空表结束。
     * [batches] 控制一次取几批（默认 4 批 ≈ 20 首，够滚一屏）。
     */
    suspend fun nextTracks(radioId: Int, firstplay: Boolean, exclude: Set<String>, batches: Int = 4): List<Track> {
        val acc = mutableListOf<Track>()
        var ok = 0
        repeat(batches) { i ->
            val root = runCatching { call(commAuth(), "songlist" to Req(
                "mb_track_radio_svr", "get_radio_track",
                JSONObject().put("id", radioId).put("firstplay", if (firstplay && i == 0) 1 else 0))) }
                .getOrNull() ?: return@repeat
            val arr = data(root, "songlist")?.optJSONArray("tracks")
            val batch = if (arr != null) QqCore.mapItems(arr) { QqMapper.track(it) } else emptyList()
            ok++
            acc += batch.filter { it.mid !in exclude && acc.none { a -> a.mid == it.mid } }
        }
        // 全部批次失败（风控/网络）必须按**失败**处理（上层可重试），
        // 不能当成空表——否则首屏就被标成"没有更多了"死路（实测踩过）。
        if (ok == 0) throw IllegalStateException("电台取歌失败，请稍后重试")
        return acc
    }
}
