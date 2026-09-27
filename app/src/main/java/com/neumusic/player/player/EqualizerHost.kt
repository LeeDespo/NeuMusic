package com.neumusic.player.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.neumusic.player.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 均衡器宿主：把 Android 平台的 [Equalizer] / [BassBoost] 挂到 ExoPlayer 的音频会话上。
 *
 * 选择平台 API 而非第三方库的原因：主流带 EQ 的开源播放器（Retro Music / Auxio /
 * Vinyl / OuterTune / Music-Player-GO）全是 **GPL-3.0**，代码不能搬进计划开源的
 * 本项目；而平台 audiofx 属于 AOSP（Apache-2.0），零协议负担。GPL 项目仅作
 * 交互参考（开关 + 预设 + 频段滑杆是通用设计模式）。
 *
 * 状态统一放 [Prefs]（磁盘）+ 这里的一组 StateFlow（界面响应式），与
 * `themeFlow`/`reliefFlow` 同一套模式：界面改值 → 写 Prefs → 推 flow →
 * 即时重绘，同时同步到底层音效。
 *
 * 注意：Equalizer 的 band/level 参数都是 **Int**（level 单位毫贝，范围通常 -1500..1500）。
 */
object EqualizerHost {

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

    val presetNames: List<String>
        get() {
            val eq = equalizer ?: return emptyList()
            return (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()) }
        }

    private val _bands = MutableStateFlow<IntArray>(IntArray(0))
    val bands: StateFlow<IntArray> = _bands

    private val _preset = MutableStateFlow(-1)   // -1 = 自定义
    val preset: StateFlow<Int> = _preset

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

            // 恢复持久化状态，再推给界面。
            val saved = Prefs.eqBands
            val (lo, hi) = bandLevelRange.let { it[0] to it[1] }
            val restored = if (saved.size == eq.numberOfBands.toInt()) saved
            else IntArray(eq.numberOfBands.toInt()) { (lo + hi) / 2 }   // 默认 0dB 居中
            _bands.value = restored
            _preset.value = Prefs.eqPreset
            _bass.value = Prefs.eqBass

            _enabled.value = Prefs.eqEnabled
            applyAll(enabled = Prefs.eqEnabled)
        }.onFailure {
            _available.value = false
            android.util.Log.w("EqualizerHost", "audiofx unavailable: $it")
        }
    }

    /** 把当前状态同步到底层音效（开关、频段、预设、低音一起）。 */
    private fun applyAll(enabled: Boolean) {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = enabled
            if (enabled) {
                val p = _preset.value
                if (p >= 0 && p < eq.numberOfPresets.toInt() && Prefs.eqPreset != -1) {
                    eq.usePreset(p.toShort())
                } else {
                    _bands.value.forEachIndexed { i, lvl -> eq.setBandLevel(i.toShort(), lvl.toShort()) }
                }
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

    /** 选预设：底层 usePreset 后把各频段实际电平读回快照，界面滑杆跟着动。 */
    fun setPreset(index: Int) {
        Prefs.eqPreset = index
        _preset.value = index
        runCatching {
            equalizer?.let { eq ->
                if (index >= 0 && index < eq.numberOfPresets.toInt()) {
                    eq.usePreset(index.toShort())
                    val levels = IntArray(eq.numberOfBands.toInt()) { i -> eq.getBandLevel(i.toShort()).toInt() }
                    _bands.value = levels
                    Prefs.eqBands = levels
                }
            }
        }
        applyAll(Prefs.eqEnabled)
    }

    /** 拖动某频段。progress 0..1 换算成毫贝；一旦手调即进入「自定义」。 */
    fun setBandProgress(index: Int, progress: Float) {
        val (lo, hi) = bandLevelRange.let { it[0] to it[1] }
        val level = (lo + (hi - lo) * progress.coerceIn(0f, 1f)).toInt()
        val next = _bands.value.copyOf()
        if (index < next.size) {
            next[index] = level
            _bands.value = next
            Prefs.eqBands = next
            if (Prefs.eqPreset != -1) {
                Prefs.eqPreset = -1
                _preset.value = -1
            }
            equalizer?.let { it.setBandLevel(index.toShort(), level.toShort()) }
        }
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
