package com.neumusic.player.player

import android.media.audiofx.DynamicsProcessing
import com.neumusic.player.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 动态处理（DynamicsProcessing，API 28+）宿主：
 * - **DVC 动态范围压缩**：单段多带压缩压低响度差（夜间/通勤听歌不被副歌炸耳）；
 * - **声道平衡**：逐通道输入增益衰减一侧（±6dB 封顶），实现 L↔R 平移。
 *
 * 与 [EqualizerHost]（Equalizer/BassBoost）是两个独立音效实例，挂同一会话可共存。
 * API 签名按本机 SDK（android-36）实测：`Config.Builder(variant, channelCount,
 * preEqInUse, preEqBandCount, mbcInUse, mbcBandCount, postEqInUse, postEqBandCount, limiterInUse)`，
 * 无顶层 Builder；`setInputGainAllChannelsTo(float)` 是单值，逐通道用 `setInputGainbyChannel`。
 * 设备不支持时 [available]=false，界面隐藏对应功能。
 */
object DynamicsFxHost {

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available

    private val _dvc = MutableStateFlow(false)
    val dvc: StateFlow<Boolean> = _dvc

    /** -100（全左）..100（全右）。 */
    private val _balance = MutableStateFlow(0)
    val balance: StateFlow<Int> = _balance

    private var dp: DynamicsProcessing? = null

    fun attach(sessionId: Int) {
        if (dp != null) return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return
        runCatching {
            dp = buildDp(sessionId)
            _available.value = true
            _dvc.value = Prefs.eqDvc
            _balance.value = Prefs.eqBalance
            apply()
        }.onFailure {
            _available.value = false
            android.util.Log.w("DynamicsFxHost", "DynamicsProcessing unavailable: $it")
        }
    }

    /** 温和压动态档：阈值 -30dB、4:1、起 20ms/放 200ms，出增益 +3dB 补偿响度。 */
    private fun buildDp(sessionId: Int): DynamicsProcessing {
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,      // channelCount：立体声
            false, 1,   // preEq 不启用（段数仍需有效值）
            true, 1,    // MBC 启用，1 段（全频带压缩）
            false, 1,   // postEq 不启用
            true,       // limiter 启用（防爆音）
        ).build()
        return DynamicsProcessing(0, sessionId, cfg)
    }

    private fun mbcBand(enabled: Boolean): DynamicsProcessing.MbcBand =
        DynamicsProcessing.MbcBand(
            enabled,
            200f,    // cutoffFrequency Hz（单段下限，覆盖全频带）
            20f,     // attackTime ms
            200f,    // releaseTime ms
            4f,      // ratio
            -30f,    // threshold dB
            6f,      // kneeWidth dB
            -90f,    // noiseGateThreshold dB
            1f,      // expanderRatio
            0f,      // preGain dB
            3f,      // postGain dB
        )

    /** DVC 开关。 */
    fun setDvc(v: Boolean) {
        Prefs.eqDvc = v
        _dvc.value = v
        apply()
    }

    /** 声道平衡 [-100,100]：正=右移（衰减左声道）。 */
    fun setBalance(v: Int) {
        val clamped = v.coerceIn(-100, 100)
        Prefs.eqBalance = clamped
        _balance.value = clamped
        apply()
    }

    private fun apply() {
        val d = dp ?: return
        runCatching {
            d.enabled = Prefs.eqDvc || Prefs.eqBalance != 0
            if (!d.enabled) return@runCatching
            // 声道平衡：逐通道输入增益（±6dB 封顶）
            val b = Prefs.eqBalance / 100f
            d.setInputGainbyChannel(0, -maxOf(0f, b) * 6f)
            d.setInputGainbyChannel(1, -maxOf(0f, -b) * 6f)
            // DVC：MBC 段启停
            d.setMbcBandAllChannelsTo(0, mbcBand(Prefs.eqDvc))
        }.onFailure {
            android.util.Log.w("DynamicsFxHost", "apply failed: $it")
        }
    }
}
