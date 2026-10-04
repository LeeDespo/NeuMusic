package com.neumusic.player.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.Spatializer
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow

/** Read-only capability and route state. This app does not force-enable spatialization. */
object SpatialInfo {
    data class State(val supported: Boolean = false, val available: Boolean = false,
        val enabled: Boolean = false, val headTracker: Boolean = false,
        val multichannelCapable: Boolean = false, val level: Int = 0)
    val state = MutableStateFlow(State())
    private var holder: Holder? = null
    fun init(context: Context) {
        release()
        if (Build.VERSION.SDK_INT >= 32) runCatching { holder = Holder(context.applicationContext); refresh() }
    }
    fun refresh() {
        if (Build.VERSION.SDK_INT >= 32) runCatching { holder?.refresh() }
    }
    fun release() {
        if (Build.VERSION.SDK_INT >= 32) runCatching { holder?.release() }
        holder = null
        state.value = State()
    }
    @RequiresApi(32)
    private class Holder(context: Context) {
        private val spatializer = context.getSystemService(AudioManager::class.java).spatializer
        private val listener = object : Spatializer.OnSpatializerStateChangedListener {
            override fun onSpatializerAvailableChanged(s: Spatializer, available: Boolean) = refresh()
            override fun onSpatializerEnabledChanged(s: Spatializer, enabled: Boolean) = refresh()
        }
        private val headListener = Spatializer.OnHeadTrackerAvailableListener { _, _ -> refresh() }
        init {
            spatializer.addOnSpatializerStateChangedListener(context.mainExecutor, listener)
            spatializer.addOnHeadTrackerAvailableListener(context.mainExecutor, headListener)
        }
        fun refresh() {
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            val format = AudioFormat.Builder().setSampleRate(48000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1).build()
            state.value = State(true, spatializer.isAvailable, spatializer.isEnabled,
                spatializer.isHeadTrackerAvailable, spatializer.canBeSpatialized(attrs, format), spatializer.immersiveAudioLevel)
        }
        fun release() {
            runCatching { spatializer.removeOnSpatializerStateChangedListener(listener) }
            runCatching { spatializer.removeOnHeadTrackerAvailableListener(headListener) }
        }
    }
}
