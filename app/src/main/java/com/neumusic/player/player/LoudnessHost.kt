package com.neumusic.player.player

import android.media.audiofx.LoudnessEnhancer
import com.neumusic.player.data.AppLog
import com.neumusic.player.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow

/** LoudnessEnhancer includes platform compression; target gain is in millibels. */
object LoudnessHost {
    val available = MutableStateFlow(false)
    val mounted = MutableStateFlow(false)
    val hasControl = MutableStateFlow(false)
    val actualEnabled = MutableStateFlow(false)
    val gain = MutableStateFlow(0)
    private var pending = 0
    private var session = 0
    private var effect: LoudnessEnhancer? = null
    private var settingsLoaded = false
    private var dirty = false
    fun initSettings() {
        if (settingsLoaded) return
        pending = Prefs.eqLoudness.coerceIn(0, 900)
        gain.value = pending
        settingsLoaded = true
    }
    fun attach(sessionId: Int) {
        if (sessionId <= 0 || (session == sessionId && mounted.value)) return
        detach()
        initSettings()
        runCatching {
            val e = LoudnessEnhancer(sessionId)
            effect = e
            e.setControlStatusListener { _, control ->
                hasControl.value = control
                AppLog.i("LoudnessFx", "control=$control")
                if (control) apply(updateEnabled = false)
            }
            e.setEnableStatusListener { _, enabled -> actualEnabled.value = enabled }
            hasControl.value = e.hasControl()
            mounted.value = true
            available.value = true
            session = sessionId
            apply()
        }.onFailure { detach(); available.value = false; AppLog.w("LoudnessFx", "attach failed", it) }
    }
    fun previewGain(value: Int) { initSettings(); pending = value.coerceIn(0, 900); dirty = pending != gain.value; apply() }
    fun commitGain() {
        if (!settingsLoaded || !dirty) return
        dirty = false
        if (Prefs.eqLoudness != pending) Prefs.eqLoudness = pending
        gain.value = pending
        AppLog.i("LoudnessFx", "loudness.commit value=$pending")
    }
    fun commitGain(value: Int) { previewGain(value); commitGain() }
    private fun apply(updateEnabled: Boolean = true) {
        val e = effect ?: return
        if (!e.hasControl()) return
        runCatching { e.setTargetGain(pending); if (updateEnabled) e.enabled = pending > 0; actualEnabled.value = e.enabled }
            .onFailure { AppLog.w("LoudnessFx", "parameter update failed", it) }
    }
    fun detach() {
        commitGain()
        effect?.let { e ->
            runCatching { e.setControlStatusListener(null) }
            runCatching { e.setEnableStatusListener(null) }
            runCatching { e.release() }
        }
        effect = null; session = 0; mounted.value = false; hasControl.value = false; actualEnabled.value = false
    }
    fun release() = detach()
}
