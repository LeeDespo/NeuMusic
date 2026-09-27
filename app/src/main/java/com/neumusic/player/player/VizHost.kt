package com.neumusic.player.player

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 播放条可视化数据源（频谱）。
 *
 * 优先用系统 [Visualizer]（真 FFT，柱子按频段跳动）；
 * Visualizer 不可用（部分 ROM 禁用/权限缺失）时自动回退到
 * [VizProcessor] 的 PCM 分段电平（时域波形能量，观感平缓）。
 * 两者都输出 [BARS] 段 0..1 电平。
 *
 * Visualizer 需在 manifest 声明 RECORD_AUDIO（普通权限，安装即授；
 * 这是系统把该 API 挂在录音权限组下的模型要求，并不真的采集麦克风）。
 */
object VizHost {
    const val BARS = 16

    private val _levels = MutableStateFlow(FloatArray(BARS))
    val levels: StateFlow<FloatArray> = _levels

    /** 用的是真频谱（FFT）还是回退的波形电平。 */
    private val _usingFft = MutableStateFlow(false)
    val usingFft: StateFlow<Boolean> = _usingFft

    private var visualizer: Visualizer? = null
    private val peaks = FloatArray(BARS)

    /** 在拿到音频会话 id 后调用。 */
    fun attach(sessionId: Int) {
        if (visualizer != null) return
        runCatching {
            val v = Visualizer(sessionId)
            v.enabled = false
            val size = Visualizer.getCaptureSizeRange()?.let { if (it.size >= 2) it[1] else 1024 } ?: 1024
            v.captureSize = size
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        // 用 FFT 时不需要波形
                    }

                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null || fft.size < 4) return
                        val out = FloatArray(BARS)
                        // fft 是 packed：re[0], im[0], re[1], im[1]...（8bit signed）
                        val bins = fft.size / 2
                        val per = bins / BARS
                        if (per <= 0) return
                        for (b in 0 until BARS) {
                            var acc = 0f
                            for (k in 0 until per) {
                                val idx = (b * per + k) * 2
                                if (idx + 1 < fft.size) {
                                    val re = fft[idx].toInt()
                                    val im = fft[idx + 1].toInt()
                                    acc += kotlin.math.sqrt((re * re + im * im).toFloat())
                                }
                            }
                            // 归一：单 bin 最大 ≈ sqrt(128²+128²)≈181，per 个取均值后缩放
                            out[b] = ((acc / per) / 128f).coerceIn(0f, 1f)
                        }
                        publish(out)
                    }
                },
                Visualizer.getMaxCaptureRate(),
                true,   // waveform
                true,   // fft
            )
            v.enabled = true
            visualizer = v
            _usingFft.value = true
        }.onFailure {
            // 回退：VizProcessor 的分段电平已在管线里跑着
            _usingFft.value = false
            android.util.Log.i("VizHost", "Visualizer unavailable, fallback to PCM levels: $it")
        }
    }

    private fun publish(frame: FloatArray) {
        for (b in 0 until BARS) {
            // 快升慢降的峰值保持，观感跳跃又不过分抖
            peaks[b] = if (frame[b] > peaks[b]) frame[b] else peaks[b] * 0.72f + frame[b] * 0.28f
        }
        _levels.value = peaks.copyOf()
    }

    /** 停止时归零（暂停）。 */
    fun decay() {
        for (b in 0 until BARS) peaks[b] *= 0.5f
        _levels.value = peaks.copyOf()
    }
}
