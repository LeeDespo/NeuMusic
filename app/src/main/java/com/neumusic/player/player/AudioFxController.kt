package com.neumusic.player.player

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import com.neumusic.player.data.AppLog
import com.neumusic.player.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow

/** Called on the player application thread; each host owns/releases its own effects. */
object AudioFxController {
    val sessionId = MutableStateFlow(0)
    val systemPanelAvailable = MutableStateFlow(false)
    var onPcmStateChanged: ((enabled: Boolean, playing: Boolean) -> Unit)? = null
    private var context: Context? = null
    private var enabled = false
    private var playing = false
    fun init(context: Context, @Suppress("UNUSED_PARAMETER") processor: VizProcessor) {
        this.context = context.applicationContext
        DynamicsFxHost.initSettings()
        LoudnessHost.initSettings()
        VizHost.init(context)
        SpatialInfo.init(context)
        enabled = Prefs.barViz
        VizHost.setEnabled(enabled)
        refreshSystemPanel()
    }
    fun attach(id: Int) {
        if (id == sessionId.value) return
        detach()
        if (id <= 0) return
        sessionId.value = id
        independently("Equalizer") { EqualizerHost.attach(id) }
        independently("Dynamics") { DynamicsFxHost.attach(id) }
        independently("Loudness") { LoudnessHost.attach(id) }
        independently("Visualizer") { VizHost.attach(id) }
        broadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, id)
        refreshSystemPanel()
        setPlaying(playing)
        AppLog.i("AudioFx", "attached session=$id")
    }
    fun detach() {
        val oldId = sessionId.value
        if (oldId > 0) broadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, oldId)
        independently("Equalizer release") { EqualizerHost.detach() }
        independently("Dynamics release") { DynamicsFxHost.detach() }
        independently("Loudness release") { LoudnessHost.detach() }
        independently("Visualizer release") { VizHost.detach() }
        sessionId.value = 0
        onPcmStateChanged?.invoke(false, false)
    }
    fun releaseAll() {
        setPlaying(false)
        detach()
        SpatialInfo.release()
        onPcmStateChanged = null
    }
    fun release() = releaseAll()
    fun setPlaying(value: Boolean) {
        playing = value
        VizHost.setPlaying(value)
        onPcmStateChanged?.invoke(enabled && !VizHost.usingFft.value, value)
        SpatialInfo.refresh()
    }
    fun setVisualizationEnabled(value: Boolean) {
        enabled = value
        VizHost.setEnabled(value)
        VizHost.setPlaying(playing)
        onPcmStateChanged?.invoke(enabled && !VizHost.usingFft.value, playing)
    }
    fun retryFft(): Boolean {
        val success = VizHost.retryFft(sessionId.value)
        onPcmStateChanged?.invoke(enabled && !success, playing)
        return success
    }
    private fun panelIntent() = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId.value)
        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context?.packageName)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    private fun refreshSystemPanel() {
        systemPanelAvailable.value = context?.packageManager?.let { panelIntent().resolveActivity(it) != null } ?: false
    }
    fun systemPanelIntent(): Intent? {
        refreshSystemPanel()
        return if (systemPanelAvailable.value && sessionId.value > 0) panelIntent() else null
    }
    private fun broadcast(action: String, id: Int) {
        val ctx = context ?: return
        independently("session broadcast") { ctx.sendBroadcast(Intent(action)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, id)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, ctx.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)) }
    }
    private inline fun independently(name: String, block: () -> Unit) {
        runCatching(block).onFailure { AppLog.w("AudioFx", "$name failed", it) }
    }
}
