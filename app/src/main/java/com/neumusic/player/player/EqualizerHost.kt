package com.neumusic.player.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.neumusic.player.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 均衡器宿主：把 Android 平台的 [Equalizer] / [BassBoost] 挂到 ExoPlayer 的音频会话上。
 *
 * 选择平台 API 而非第三方库的原因：主流带 EQ 的开源播放器（Retro Music / Auxio /
 * Vinyl / OuterTune / Music-Player-GO）全是 **GPL-3.0**，代码不能搬进计划开源的
 * 本项目；而平台 audiofx 属于 AOSP（Apache-2.0），零协议负担。
 *
 * ## 预设模型（2026-09-28 重构）
 * 预设不再直接用设备的 `usePreset` 下标，而是**一份自己的存储**（`Prefs.eqStore` JSON）：
 * - 首次挂载时把设备全部内置预设的增益**快照**进来（`builtin=true`），此后运行时
 *   只读写自己的存储；
 * - 所有预设都可以改：选中某预设后拖频段滑杆，增益**直接写回该预设**（用户的修改权）；
 * - 可以**新增预设**（以当前频段值入库、用户命名）、删除非内置预设；
 * - 智能调音的「曲风 → 预设」映射也存这里（`Prefs.eqGenreMap`），UI 可改。
 *
 * 注意：Equalizer 的 band/level 参数都是 **Int**（level 单位毫贝，范围通常 -1500..1500）。
 */
object EqualizerHost {

    /** 一个均衡器预设：名字 + 各频段毫贝值 + 是否来自设备内置（内置不可删）。 */
    data class EqPreset(val name: String, val gains: IntArray, val builtin: Boolean)

    /** 设备是否真的支持（模拟器/部分 ROM 可能没有 audiofx 实现）。 */
    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available

    /** 均衡器启用状态（响应式；界面直接 collect，否则切开关界面不动）。 */
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    /** 频段中心频率（Hz），用于界面标签；长度随设备（通常 5）。 */
    val bandFreqs: IntArray
        get() {
            val eq = equalizer ?: return IntArray(0)
            return IntArray(eq.numberOfBands.toInt()) { i -> eq.getCenterFreq(i.toShort()) / 1000 }
        }

    /** 频段级别范围（毫贝），界面滑杆据此换算。 */
    val bandLevelRange: IntArray
        get() {
            val eq = equalizer ?: return intArrayOf(-1500, 1500)
            val r = eq.bandLevelRange          // short[]
            return intArrayOf(r[0].toInt(), r[1].toInt())
        }

    val numberOfBands: Int
        get() = equalizer?.numberOfBands?.toInt() ?: 0

    private val _presets = MutableStateFlow<List<EqPreset>>(emptyList())
    val presets: StateFlow<List<EqPreset>> = _presets

    /** 当前选中的预设名；null = 手动（不挂预设，滑杆即全部）。 */
    private val _selected = MutableStateFlow<String?>(null)
    val selectedPreset: StateFlow<String?> = _selected

    private val _bands = MutableStateFlow<IntArray>(IntArray(0))
    val bands: StateFlow<IntArray> = _bands

    private val _bass = MutableStateFlow(0)
    val bass: StateFlow<Int> = _bass

    /**
     * 在播放器就绪后调用一次。[sessionId] 是 ExoPlayer 的音频会话 id。
     * 已就绪则重复调用是空操作；设备不支持时置 [available] 为 false。
     */
    fun attach(sessionId: Int) {
        if (equalizer != null) return
        runCatching {
            val eq = Equalizer(0, sessionId)
            equalizer = eq
            runCatching { bassBoost = BassBoost(0, sessionId) }
            _available.value = true

            seedStore(eq)
            _presets.value = loadStore()

            val (lo, hi) = bandLevelRange.let { it[0] to it[1] }
            val saved = Prefs.eqBands
            _bands.value = if (saved.size == eq.numberOfBands.toInt()) saved
            else IntArray(eq.numberOfBands.toInt()) { (lo + hi) / 2 }   // 默认 0dB 居中
            _selected.value = Prefs.eqSelected
                ?.takeIf { name -> _presets.value.any { it.name == name } }
            _bass.value = Prefs.eqBass

            _enabled.value = Prefs.eqEnabled
            applyAll(enabled = Prefs.eqEnabled)
        }.onFailure {
            _available.value = false
            android.util.Log.w("EqualizerHost", "audiofx unavailable: $it")
        }
    }

    // ───────────────────── 预设存储 ─────────────────────

    /** 预设名清理：去掉控制字符（实测设备 preset 名可能带 U+0，会把 uiautomator dump 搞崩）。 */
    private fun sanitizeName(raw: String): String =
        raw.replace(Regex("""[\u0000-\u001F]"""), "").trim().ifEmpty { "未命名" }

    /**
     * 首次挂载：把设备内置预设的增益逐个读出，快照进自己的存储。
     * 只做一次（`Prefs.eqSeeded`），之后设备预设怎么变都与我们无关。
     */
    private fun seedStore(eq: Equalizer) {
        if (Prefs.eqSeeded) return
        val presets = JSONArray()
        for (i in 0 until eq.numberOfPresets.toInt()) {
            runCatching {
                eq.usePreset(i.toShort())
                val gains = IntArray(eq.numberOfBands.toInt()) { b -> eq.getBandLevel(b.toShort()).toInt() }
                presets.put(JSONObject()
                    .put("name", sanitizeName(eq.getPresetName(i.toShort())))
                    .put("gains", JSONArray(gains.toList()))
                    .put("builtin", true))
            }
        }
        val store = JSONObject().put("presets", presets)
        Prefs.eqStore = store.toString()
        if (Prefs.eqGenreMap.isEmpty()) Prefs.eqGenreMap = SmartEq.defaultGenreMapJson()
        Prefs.eqSeeded = true
    }

    private fun loadStore(): List<EqPreset> = runCatching {
        val arr = JSONObject(Prefs.eqStore).optJSONArray("presets") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val gains = o.optJSONArray("gains") ?: return@mapNotNull null
            EqPreset(
                sanitizeName(o.optString("name")),
                IntArray(gains.length()) { gains.optInt(it) },
                o.optBoolean("builtin", false),
            )
        }
    }.getOrDefault(emptyList())

    private fun saveStore(presets: List<EqPreset>) {
        val arr = JSONArray()
        presets.forEach { p ->
            arr.put(JSONObject()
                .put("name", p.name)
                .put("gains", JSONArray(p.gains.toList()))
                .put("builtin", p.builtin))
        }
        Prefs.eqStore = JSONObject().put("presets", arr).toString()
        _presets.value = presets
    }

    /** 选预设（null = 手动）：把该预设增益套到设备上，滑杆跟着动。 */
    fun selectPreset(name: String?) {
        Prefs.eqSelected = name
        _selected.value = name
        val p = name?.let { n -> _presets.value.firstOrNull { it.name == n } }
        if (p != null && p.gains.size == numberOfBands) {
            _bands.value = p.gains.copyOf()
            Prefs.eqBands = p.gains
        }
        applyAll(Prefs.eqEnabled)
    }

    /** 新增预设：以当前频段值入库并选中。名字重复时自动加后缀。 */
    fun addPreset(name: String) {
        val trimmed = name.trim().ifEmpty { "新预设" }
        var final = trimmed
        var n = 2
        while (_presets.value.any { it.name == final }) final = "$trimmed ${n++}"
        val p = EqPreset(final, _bands.value.copyOf(), builtin = false)
        saveStore(_presets.value + p)
        selectPreset(p.name)
    }

    /** 删除预设（内置预设不可删）。删的是当前选中的则回到手动。 */
    fun deletePreset(name: String) {
        val target = _presets.value.firstOrNull { it.name == name } ?: return
        if (target.builtin) return
        saveStore(_presets.value.filterNot { it.name == name })
        if (_selected.value == name) selectPreset(null)
    }

    // ───────────────────── 曲风映射 ─────────────────────

    /** 曲风码 → 预设名（智能调音映射）。 */
    fun genreMap(): Map<Int, String> = runCatching {
        val o = JSONObject(Prefs.eqGenreMap)
        o.keys().asSequence().mapNotNull { k ->
            val code = k.toIntOrNull() ?: return@mapNotNull null
            val v = o.optString(k)
            if (v.isEmpty()) null else code to v
        }.toMap()
    }.getOrDefault(emptyMap())

    /** 改某曲风的映射预设。 */
    fun setGenrePreset(genre: Int, presetName: String?) {
        val map = genreMap().toMutableMap()
        if (presetName == null) map.remove(genre) else map[genre] = presetName
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k.toString(), v) }
        Prefs.eqGenreMap = o.toString()
    }

    // ───────────────────── 设备同步 ─────────────────────

    /** 把当前状态同步到底层音效（开关、频段、低音一起）。 */
    private fun applyAll(enabled: Boolean) {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = enabled
            if (enabled) {
                _bands.value.forEachIndexed { i, lvl -> eq.setBandLevel(i.toShort(), lvl.toShort()) }
            }
        }
        runCatching {
            val bb = bassBoost ?: return@runCatching
            bb.enabled = enabled && _bass.value > 0
            bb.setStrength(_bass.value.toShort())
        }
    }

    // ── 供均衡器界面调用：改状态 → 持久化 → 推 flow → 应用 ──

    fun setEnabled(v: Boolean) {
        Prefs.eqEnabled = v
        _enabled.value = v
        applyAll(v)
    }

    /** 拖动某频段：更新设备 + 持久化；选中预设时**直接写回该预设**（修改权在用户）。 */
    fun setBandProgress(index: Int, progress: Float) {
        val (lo, hi) = bandLevelRange.let { it[0] to it[1] }
        val level = (lo + (hi - lo) * progress.coerceIn(0f, 1f)).toInt()
        val next = _bands.value.copyOf()
        if (index >= next.size) return
        next[index] = level
        _bands.value = next
        Prefs.eqBands = next
        val selected = _selected.value
        if (selected != null) {
            val updated = _presets.value.map {
                if (it.name == selected) it.copy(gains = next.copyOf()) else it
            }
            saveStore(updated)
        }
        equalizer?.let { it.setBandLevel(index.toShort(), level.toShort()) }
    }

    /** 某频段当前进度（0..1），供滑杆初始值。 */
    fun bandProgress(index: Int): Float {
        val (lo, hi) = bandLevelRange.let { it[0] to it[1] }
        val bands = _bands.value
        if (index >= bands.size) return 0.5f
        return ((bands[index] - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
    }

    fun setBass(progress: Float) {
        val strength = (progress.coerceIn(0f, 1f) * 1000).toInt()
        _bass.value = strength
        Prefs.eqBass = strength
        runCatching {
            val bb = bassBoost ?: return@runCatching
            bb.enabled = Prefs.eqEnabled && strength > 0
            bb.setStrength(strength.toShort())
        }
    }
}
