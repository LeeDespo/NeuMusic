package com.neumusic.player.player

import com.neumusic.player.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 智能调音：按曲目自带的风格码（`Track.genre`）自动套用均衡器预设。
 *
 * 码表是 **实测样本校准**（每种风格用代表性歌曲搜索后统计 genre 分布），
 * 不是官方枚举，遇到未知码一律保持现状 —— 不猜。
 *
 * 实测样本：
 * 1=流行(晴天) 2=古典(变奏曲) 20=电子(All Falls Down) 22=摇滚(Linkin Park)
 * 23=民谣(宋冬野 3/3) 27=爵士 28=金属(Metallica) 33=电子(Daft Punk)
 * 34=说唱(Eminem 3/3) 50=摇滚(Creep/New Divide)
 * 未校准：3/15/19/31/37/39 等，保持当前预设。
 *
 * 「曲风 → 预设」映射存 `Prefs.eqGenreMap`（默认值见 [defaultGenreMapJson]），
 * 音效页里用户可以为每个曲风挑任意预设（含自建预设），改完即生效。
 */
object SmartEq {

    /** 已校准的风格码与中文名（界面展示用；顺序即音效页里的展示顺序）。 */
    val GENRES: List<Pair<Int, String>> = listOf(
        1 to "流行", 2 to "古典", 22 to "摇滚", 50 to "摇滚",
        23 to "民谣", 27 to "爵士", 28 to "金属", 34 to "说唱",
        20 to "电子", 33 to "电子",
    )

    private val DEFAULT_MAP = mapOf(
        1 to "Pop", 2 to "Classical", 20 to "Dance", 22 to "Rock",
        23 to "Folk", 27 to "Jazz", 28 to "Heavy Metal", 33 to "Dance",
        34 to "Hip Hop", 50 to "Rock",
    )

    fun defaultGenreMap(): Map<Int, String> = DEFAULT_MAP

    /** 默认映射 JSON（首次播种用）。 */
    fun defaultGenreMapJson(): String {
        val o = org.json.JSONObject()
        DEFAULT_MAP.forEach { (k, v) -> o.put(k.toString(), v) }
        return o.toString()
    }

    private val _lastApplied = MutableStateFlow<String?>(null)

    /** 音效页展示的最近一次智能动作说明。 */
    val lastApplied: StateFlow<String?> = _lastApplied

    /** 该风格码当前映射的预设名；无映射返回 null。 */
    fun presetNameFor(genre: Int): String? = EqualizerHost.genreMap()[genre]

    /** 换歌时调用：应用映射预设并更新提示。 */
    fun applyFor(track: Track) {
        if (!EqualizerHost.mounted.value) {
            _lastApplied.value = "音效暂未挂载，保持当前调音"
            return
        }
        if (track.genre == 0) {
            _lastApplied.value = "本曲无风格数据，保持当前调音"
            return
        }
        val name = presetNameFor(track.genre)
        if (name.isNullOrEmpty()) {
            _lastApplied.value = "风格 ${track.genre} 暂无映射，保持当前调音"
            return
        }
        val preset = EqualizerHost.presets.value.firstOrNull { it.name == name }
        if (preset == null) {
            _lastApplied.value = "预设不存在：$name"
            return
        }
        if (!EqualizerHost.canSelectPreset(preset) || !EqualizerHost.selectPreset(name)) {
            _lastApplied.value = "预设不适用于当前引擎：$name"
            return
        }
        _lastApplied.value = "智能调音：${track.name.take(12)} → $name"
    }
}
