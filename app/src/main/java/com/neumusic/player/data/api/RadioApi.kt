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
}
