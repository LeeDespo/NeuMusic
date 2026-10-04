package com.neumusic.player.player

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.annotation.RequiresApi
import com.neumusic.player.data.AppLog
import com.neumusic.player.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Session effects remain independent of the selected equalizer engine. */
object DynamicsFxHost {
    val available = MutableStateFlow(false)
    val mounted = MutableStateFlow(false)
    val hasControl = MutableStateFlow(false)
    val actualEnabled = MutableStateFlow(false)
    val dvc = MutableStateFlow(false)
    val dvcMode = MutableStateFlow("off")
    val balance = MutableStateFlow(0)
    val limiterEnabled = MutableStateFlow(true)
    val limiterThreshold = MutableStateFlow(-2f)
    val limiterRelease = MutableStateFlow(60f)
    private var holder: Holder? = null
    private var pendingBalance = 0
    private var pendingThreshold = -2f
    private var pendingRelease = 60f
    private var session = 0
    private var settingsLoaded = false
    private var balanceDirty = false
    private var thresholdDirty = false
    private var releaseDirty = false

    /** Also called before any audio session exists, so UI reflects saved settings. */
    fun initSettings() {
        if (settingsLoaded) return
        dvcMode.value = Prefs.eqDvcMode
        dvc.value = dvcMode.value != "off"
        balance.value = Prefs.eqBalance.coerceIn(-100, 100)
        limiterEnabled.value = Prefs.eqLimiterEnabled
        limiterThreshold.value = Prefs.eqLimiterThreshold.coerceIn(-6f, -.1f)
        limiterRelease.value = Prefs.eqLimiterRelease.coerceIn(1.5f, 500f)
        pendingBalance = balance.value
        pendingThreshold = limiterThreshold.value
        pendingRelease = limiterRelease.value
        settingsLoaded = true
    }

    fun attach(sessionId: Int) {
        if (sessionId <= 0 || Build.VERSION.SDK_INT < 28) return
        if (session == sessionId && mounted.value) return
        detach()
        initSettings()
        runCatching {
            holder = Holder(sessionId)
            session = sessionId
            available.value = true
            mounted.value = true
            apply()
        }.onFailure {
            detach()
            available.value = false
            AppLog.w("DynamicsFx", "attach failed session=$sessionId", it)
        }
    }
    fun detach() {
        commitBalance()
        commitLimiterThreshold()
        commitLimiterRelease()
        if (Build.VERSION.SDK_INT >= 28) holder?.release()
        holder = null
        session = 0
        mounted.value = false
        hasControl.value = false
        actualEnabled.value = false
    }
    fun release() = detach()
    fun setDvc(v: Boolean) = setDvcMode(if (v) "medium" else "off")
    fun setDvcMode(value: String) {
        initSettings()
        val mode = value.takeIf { it in listOf("off", "light", "medium", "strong", "night") } ?: "off"
        dvcMode.value = mode
        dvc.value = mode != "off"
        Prefs.eqDvcMode = mode
        Prefs.eqDvc = dvc.value
        apply()
    }
    fun previewBalance(v: Int) {
        initSettings()
        val next = v.coerceIn(-100, 100)
        if (next == pendingBalance) return
        pendingBalance = next
        balanceDirty = pendingBalance != balance.value
        if (Build.VERSION.SDK_INT >= 28) runCatching { holder?.applyBalance() }
            .onFailure { AppLog.w("DynamicsFx", "balance update failed", it) }
    }
    fun commitBalance() {
        if (!settingsLoaded || !balanceDirty) return
        balanceDirty = false
        if (Prefs.eqBalance != pendingBalance) Prefs.eqBalance = pendingBalance
        balance.value = pendingBalance
        AppLog.i("DynamicsFx", "eqBalance.commit value=$pendingBalance")
    }
    fun commitBalance(v: Int) { previewBalance(v); commitBalance() }
    fun setBalance(v: Int) = previewBalance(v)
    fun setLimiterEnabled(v: Boolean) { initSettings(); limiterEnabled.value = v; Prefs.eqLimiterEnabled = v; apply() }
    fun previewLimiterThreshold(v: Float) { initSettings(); if (v.isFinite()) { pendingThreshold = v.coerceIn(-6f, -.1f); thresholdDirty = pendingThreshold != limiterThreshold.value; apply() } }
    fun commitLimiterThreshold() {
        if (!settingsLoaded || !thresholdDirty) return
        thresholdDirty = false
        if (Prefs.eqLimiterThreshold != pendingThreshold) Prefs.eqLimiterThreshold = pendingThreshold
        limiterThreshold.value = pendingThreshold
        AppLog.i("DynamicsFx", "limiterThreshold.commit value=$pendingThreshold")
    }
    fun commitLimiterThreshold(v: Float) { previewLimiterThreshold(v); commitLimiterThreshold() }
    fun previewLimiterRelease(v: Float) { initSettings(); if (v.isFinite()) { pendingRelease = v.coerceIn(1.5f, 500f); releaseDirty = pendingRelease != limiterRelease.value; apply() } }
    fun commitLimiterRelease() {
        if (!settingsLoaded || !releaseDirty) return
        releaseDirty = false
        if (Prefs.eqLimiterRelease != pendingRelease) Prefs.eqLimiterRelease = pendingRelease
        limiterRelease.value = pendingRelease
        AppLog.i("DynamicsFx", "limiterRelease.commit value=$pendingRelease")
    }
    fun commitLimiterRelease(v: Float) { previewLimiterRelease(v); commitLimiterRelease() }
    private fun apply(updateEnabled: Boolean = true) {
        if (Build.VERSION.SDK_INT >= 28) runCatching { holder?.apply(updateEnabled) }
            .onFailure { AppLog.w("DynamicsFx", "parameter update failed", it) }
    }

    // Isolate API 28 classes from the minSdk 26 object surface.
    @RequiresApi(28)
    private class Holder(sessionId: Int) {
        private val effect = DynamicsProcessing(0, sessionId,
            DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, false, 1, true, 1, false, 1, true).build())
        init {
            hasControl.value = effect.hasControl()
            effect.setControlStatusListener { _, control ->
                hasControl.value = control
                AppLog.i("DynamicsFx", "control=$control")
                if (control) DynamicsFxHost.apply(updateEnabled = false)
            }
            effect.setEnableStatusListener { _, enabled -> actualEnabled.value = enabled }
        }
        fun apply(updateEnabled: Boolean = true) {
            if (!effect.hasControl()) return
            val mode = dvcMode.value
            val parameters = when (mode) {
                "light" -> floatArrayOf(-20f, 1.5f, 20f, 150f, 0f)
                "strong" -> floatArrayOf(-40f, 6f, 5f, 250f, 6f)
                "night" -> floatArrayOf(-45f, 8f, 5f, 300f, 8f)
                else -> floatArrayOf(-30f, 4f, 20f, 200f, 3f)
            }
            for (channel in 0 until effect.channelCount) {
                val mbc = effect.getMbcByChannelIndex(channel)
                mbc.isEnabled = mode != "off"
                mbc.setBand(0, DynamicsProcessing.MbcBand(true, 20000f,
                    parameters[2], parameters[3], parameters[1], parameters[0],
                    6f, -90f, 1f, 0f, parameters[4]))
                effect.setMbcByChannelIndex(channel, mbc)

            }
            effect.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, limiterEnabled.value,
                0, 1f, pendingRelease, 10f, pendingThreshold, 0f))
            applyBalance(updateEnabled)
        }
        fun applyBalance(updateEnabled: Boolean = true) {
            if (!effect.hasControl()) return
            val pan = pendingBalance / 100f
            for (channel in 0 until effect.channelCount) {
                effect.setInputGainbyChannel(channel, when (channel) {
                    0 -> -maxOf(0f, pan) * 6f
                    1 -> -maxOf(0f, -pan) * 6f
                    else -> 0f
                })
            }
            if (updateEnabled) effect.enabled = dvcMode.value != "off" || pendingBalance != 0 || limiterEnabled.value
            actualEnabled.value = effect.enabled
        }
        fun release() {
            runCatching { effect.setControlStatusListener(null) }
            runCatching { effect.setEnableStatusListener(null) }
            runCatching { effect.release() }
        }
    }
}
