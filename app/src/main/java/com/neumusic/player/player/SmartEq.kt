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
 */
object SmartEq {

    /** 风格码 → 设备预设名（与 Android 内置 Equalizer 预设对齐）。 */
    private val GENRE_TO_PRESET = mapOf(
        1 to "Pop",
        2 to "Classical",
        20 to "Dance",
        22 to "Rock",
        23 to "Folk",
        27 to "Jazz",
        28 to "Heavy Metal",
        33 to "Dance",
        34 to "Hip Hop",
        50 to "Rock",
    )

    private val _lastApplied = MutableStateFlow<String?>(null)

    /** 音效页展示的最近一次智能动作说明。 */
    val lastApplied: StateFlow<String?> = _lastApplied

    /** 该风格码对应的预设下标；无映射返回 null。 */
    fun presetIndexFor(genre: Int): Int? {
        val name = GENRE_TO_PRESET[genre] ?: return null
        return EqualizerHost.presetNames.indexOfFirst { it.equals(name, ignoreCase = true) }
            .takeIf { it >= 0 }
    }

    /** 换歌时调用：应用映射预设并更新提示。 */
    fun applyFor(track: Track) {
        if (!EqualizerHost.available.value) return
        if (track.genre == 0) {
            _lastApplied.value = "本曲无风格数据，保持当前调音"
            return
        }
        val idx = presetIndexFor(track.genre)
        if (idx == null) {
            _lastApplied.value = "风格 ${track.genre} 暂无映射，保持当前调音"
            return
        }
        if (idx != EqualizerHost.preset.value) {
            EqualizerHost.setPreset(idx)
        }
        _lastApplied.value = "智能调音：${track.name.take(12)} → ${EqualizerHost.presetNames.getOrNull(idx) ?: "?"}"
    }
}
