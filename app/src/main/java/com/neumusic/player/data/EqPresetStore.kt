package com.neumusic.player.data

import com.neumusic.player.player.EqualizerHost.EqPreset
import com.neumusic.player.player.eq.Biquad
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** v1 stays in eqStore for rollback; every new write goes to the versioned key. */
object EqPresetStore {
    fun read(): List<EqPreset> = decode(Prefs.eqStoreV2.ifBlank { Prefs.eqStore })
    fun decode(text: String): List<EqPreset> = runCatching {
        val root = JSONObject(text)
        require(root.optInt("v", 1) in 1..2)
        val arr = root.optJSONArray("presets") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> runCatching {
            val o = arr.getJSONObject(i)
            val name = o.getString("name").replace(Regex("[\\u0000-\\u001F]"), "").trim().ifEmpty { "未命名" }
            val gains = o.optJSONArray("gains") ?: JSONArray()
            val fs = o.optJSONArray("frequenciesHz") ?: JSONArray()
            val filters = o.optJSONArray("filters") ?: JSONArray()
            val parsedFilters = (0 until filters.length()).map { j ->
                val f = filters.getJSONObject(j)
                Biquad.Filter(Biquad.Type.valueOf(f.getString("type")), f.getDouble("freqHz"), f.getDouble("gainDb"), f.getDouble("q"))
            }
            require((0 until fs.length()).all { fs.getDouble(it).isFinite() && fs.getDouble(it) in 10.0..24000.0 })
            require((1 until fs.length()).all { fs.getDouble(it) > fs.getDouble(it - 1) })
            require(fs.length() == 0 || fs.length() == gains.length())
            require((0 until gains.length()).all { gains.getInt(it) in -3000..3000 })
            val preamp = o.optDouble("preampDb", 0.0)
            require(preamp.isFinite() && preamp in -60.0..30.0)
            EqPreset(name, IntArray(gains.length()) { gains.getInt(it) }, o.optBoolean("builtin"),
                id = o.optString("id").ifEmpty { UUID.nameUUIDFromBytes("legacy:$i:$name".toByteArray()).toString() },
                frequenciesHz = (0 until fs.length()).map { fs.getDouble(it) },
                preampDb = preamp, filters = parsedFilters,
                source = o.optString("source", if (o.optBoolean("builtin")) "device" else "user"),
                engine = o.optString("engine", "PLATFORM"), graphicResponse = o.optBoolean("graphicResponse", false))
        }.getOrNull() }
    }.getOrDefault(emptyList())
    fun save(presets: List<EqPreset>) {
        val arr = JSONArray()
        presets.forEach { p ->
            val filters = JSONArray()
            p.filters.forEach { f -> filters.put(JSONObject().put("type", f.type.name).put("freqHz", f.freqHz).put("gainDb", f.gainDb).put("q", f.q)) }
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("gains", JSONArray(p.gains.toList())).put("builtin", p.builtin)
                .put("frequenciesHz", JSONArray(p.frequenciesHz)).put("preampDb", p.preampDb).put("filters", filters).put("source", p.source).put("engine", p.engine).put("graphicResponse", p.graphicResponse))
        }
        val text = JSONObject().put("v", 2).put("presets", arr).toString()
        if (Prefs.eqStoreV2 != text) Prefs.eqStoreV2 = text
    }
}
