package com.neumusic.player.player

import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 播放条音频可视化的数据源。
 *
 * 做法：一个**透传的 media3 AudioProcessor**，插在 ExoPlayer 的音频渲染管线里
 * （[androidx.media3.exoplayer.audio.DefaultAudioSink.Builder.setAudioProcessors]）。
 * 比 `android.media.audiofx.Visualizer` 好处：**不需要 RECORD_AUDIO 权限**，
 * 且在部分禁用 Visualizer 的设备上也能工作。
 *
 * 每收到一段 PCM 就把样点均分成 [BARS] 段求平均振幅，得到一帧"粗频谱"
 * （严格说是**分段波形能量**，不是 FFT 频谱——免权限方案拿不到频域数据，
 * 但作为播放条的小动画视觉上等效）。发射限频 ~30ms，避免高频重组。
 *
 * PCM 编码兼容 16-bit 与 float（ExoPlayer 开启 float 输出时）。
 */
class VizProcessor : BaseAudioProcessor() {

    companion object {
        const val BARS = 16
        private const val MIN_INTERVAL_MS = 30L
    }

    /** 当前一帧的电平 0..1，长度 [BARS]。UI 画柱状。 */
    private val _levels = MutableStateFlow(FloatArray(BARS))
    val levels: StateFlow<FloatArray> = _levels

    /** 峰值保持：快速上升、缓慢下降，观感更像频谱。 */
    private val peaks = FloatArray(BARS)

    private var channels = 2
    private var pcmEncoding = C.ENCODING_PCM_16BIT
    private var lastEmit = 0L

    override fun onConfigure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): androidx.media3.common.audio.AudioProcessor.AudioFormat {
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        pcmEncoding = inputAudioFormat.encoding
        return inputAudioFormat   // 透传，不改音频
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        // 拷贝一段再读（ByteBuffer 不能随意 rewind 上游缓冲）
        val bytes = ByteArray(remaining)
        inputBuffer.get(bytes)
        val frameBytes = when (pcmEncoding) {
            C.ENCODING_PCM_FLOAT -> 4 * channels
            else -> 2 * channels
        }
        if (frameBytes > 0) {
            val samplesPerBar = bytes.size / frameBytes / BARS
            if (samplesPerBar > 0) {
                val out = FloatArray(BARS)
                for (b in 0 until BARS) {
                    var acc = 0f
                    var count = 0
                    val start = b * samplesPerBar * frameBytes
                    val end = start + samplesPerBar * frameBytes
                    var i = start
                    while (i + frameBytes <= end && i + frameBytes <= bytes.size) {
                        val v = when (pcmEncoding) {
                            C.ENCODING_PCM_FLOAT -> {
                                val f = java.lang.Float.intBitsToFloat(
                                    ((bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                                        or ((bytes[i + 2].toInt() and 0xFF) shl 16) or ((bytes[i + 3].toInt() and 0xFF) shl 24))
                                )
                                kotlin.math.abs(f)
                            }
                            else -> {
                                val s = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort()
                                kotlin.math.abs(s.toInt()) / 32768f
                            }
                        }
                        acc += v
                        count++
                        i += frameBytes
                    }
                    if (count > 0) out[b] = (acc / count).coerceIn(0f, 1f)
                }
                // 峰值保持 + 衰减，观感更"跳"
                for (b in 0 until BARS) {
                    peaks[b] = if (out[b] > peaks[b]) out[b] else peaks[b] * 0.45f + out[b] * 0.55f
                }
                val now = System.currentTimeMillis()
                if (now - lastEmit >= MIN_INTERVAL_MS) {
                    lastEmit = now
                    _levels.value = peaks.copyOf()
                }
            }
        }
        // 透传：把读到的数据交还给下游
        val b = replaceOutputBuffer(remaining)
        b.put(bytes).flip()
    }

    /** 暂停/停止时把柱子归零。 */
    fun resetLevels() {
        for (i in peaks.indices) peaks[i] = 0f
        _levels.value = FloatArray(BARS)
    }
}
