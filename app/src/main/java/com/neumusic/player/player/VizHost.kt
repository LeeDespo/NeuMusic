package com.neumusic.player.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import com.neumusic.player.data.AppLog
import kotlinx.coroutines.flow.MutableStateFlow

/** FFT needs a granted runtime RECORD_AUDIO permission; PCM never needs it. */
object VizHost {
    const val BARS = 16
    val levels = MutableStateFlow(FloatArray(BARS))
    val usingFft = MutableStateFlow(false)
    val status = MutableStateFlow("波形电平")
    private var context: Context? = null
    private var visualizer: Visualizer? = null
    private val peaks = FloatArray(BARS)
    private var session = 0
    @Volatile private var enabled = false
    @Volatile private var playing = false
    fun init(context: Context) { this.context = context.applicationContext }
    fun attach(sessionId: Int) {
        if (sessionId <= 0) { detach(); return }
        if (session == sessionId && visualizer != null) return
        detach()
        session = sessionId
        if (enabled) retryFft(sessionId)
    }
    fun retryFft(sessionId: Int = session): Boolean {
        releaseVisualizer()
        session = sessionId
        if (!enabled || sessionId <= 0) return false
        if (context?.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.value = "录音权限未授予，使用波形电平（不采集麦克风）"
            AppLog.i("VizHost", "viz.usingFft=false permission denied")
            return false
        }
        var candidate: Visualizer? = null
        runCatching {
            val v = Visualizer(sessionId)
            candidate = v
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[1]
            val result = v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, wave: ByteArray?, rate: Int) = Unit
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                    if (v !== visualizer || !enabled || !playing || fft == null || fft.size < 4) return
                    val bins = fft.size / 2
                    val per = (bins - 1) / BARS
                    if (per <= 0) return
                    val out = FloatArray(BARS)
                    for (bar in 0 until BARS) {
                        var acc = 0f
                        // Packed FFT indices 0/1 contain DC/Nyquist, not a complex pair.
                        for (k in 0 until per) {
                            val i = (1 + bar * per + k) * 2
                            val re = fft[i].toInt(); val im = fft[i + 1].toInt()
                            acc += kotlin.math.sqrt((re * re + im * im).toFloat())
                        }
                        out[bar] = (acc / per / 128f).coerceIn(0f, 1f)
                    }
                    synchronized(peaks) {
                        if (!enabled || !playing || v !== visualizer) return
                        for (i in peaks.indices) peaks[i] = maxOf(out[i], peaks[i] * .72f + out[i] * .28f)
                        levels.value = peaks.copyOf()
                    }
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            check(result == Visualizer.SUCCESS) { "capture listener error=$result" }
            visualizer = v
            v.enabled = playing
            usingFft.value = true
            status.value = "系统 FFT"
        }.onFailure {
            runCatching { candidate?.release() }
            visualizer = null
            usingFft.value = false
            status.value = "本机 FFT 不可用，已回退波形电平"
            AppLog.w("VizHost", "FFT unavailable session=$sessionId", it)
        }
        AppLog.i("VizHost", "viz.usingFft=${usingFft.value} status=${status.value}")
        return usingFft.value
    }
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) { releaseVisualizer(); zero(); status.value = "已关闭" }
        else if (visualizer == null) retryFft()
    }
    fun setPlaying(value: Boolean) {
        playing = value
        runCatching { visualizer?.enabled = enabled && value }
            .onFailure { releaseVisualizer(); status.value = "FFT 停止，已回退波形电平" }
        if (!value) zero()
    }
    fun zero() = synchronized(peaks) { peaks.fill(0f); levels.value = FloatArray(BARS) }
    fun decay() = zero()
    private fun releaseVisualizer() {
        val old = visualizer
        visualizer = null
        runCatching { old?.enabled = false }
        runCatching { old?.release() }
        usingFft.value = false
        zero()
    }
    fun detach() { releaseVisualizer(); session = 0 }
    fun release() = detach()
}
