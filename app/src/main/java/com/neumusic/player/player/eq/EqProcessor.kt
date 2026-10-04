package com.neumusic.player.player.eq

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Playback-thread DSP. Always configured, byte-copy bypass, 10ms crossfade between EQ banks. */
class EqProcessor : BaseAudioProcessor() {
    private var snapshot = EqChain.ProcessingSnapshot()
    private var rate = 48000
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var states = emptyList<List<Biquad.State>>()
    private var previousStates = emptyList<List<Biquad.State>>()
    private var previousPreamp = 1.0
    private var transitionFrames = 0
    private var transitionRemaining = 0
    private var configured = false
    private var frame = DoubleArray(2)
    private val limiter = LinkedPeakLimiter()
    var appliedPreampDb = 0.0
        private set
    fun setSnapshot(value: EqChain.ProcessingSnapshot) {
        if (snapshot == value) return
        val previousEnabled = snapshot.enabled
        val previousDb = appliedPreampDb
        val previous = states
        snapshot = value.copy(filters = value.filters.toList())
        rebuild()
        if (configured) {
            previousStates = if (previousEnabled) previous else List(channels) { emptyList() }
            previousPreamp = if (previousEnabled) 10.0.pow(previousDb / 20.0) else 1.0
            transitionFrames = max(1, rate / 100) // 10 ms; preserve the previous bank's delay state.
            transitionRemaining = transitionFrames
        }
    }
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT)
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        rate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        require(rate > 0 && channels > 0)
        encoding = inputAudioFormat.encoding
        frame = DoubleArray(channels)
        configured = true
        rebuild()
        transitionRemaining = 0
        previousStates = emptyList()
        return inputAudioFormat
    }
    private fun rebuild() {
        states = List(channels) { snapshot.filters.map { Biquad.State(Biquad.coefficients(it, rate)) } }
        appliedPreampDb = HeadroomPlanner.preampDb(snapshot.filters, rate, snapshot.preampDb)
    }
    override fun onFlush() {
        states.flatten().forEach { it.reset() }
        previousStates = emptyList(); transitionRemaining = 0
        limiter.reset()
    }
    override fun onReset() { onFlush(); configured = false; states = emptyList() }
    override fun queueInput(inputBuffer: ByteBuffer) {
        // Media3 can deliver its shared EMPTY_BUFFER during drain/flush. replaceOutputBuffer(0)
        // returns that same object; put(inputBuffer) would then be an illegal self-copy.
        if (!inputBuffer.hasRemaining()) return
        val source = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val output = replaceOutputBuffer(source.remaining()).order(ByteOrder.LITTLE_ENDIAN)
        if (!snapshot.enabled && transitionRemaining == 0) {
            output.put(source)
            inputBuffer.position(inputBuffer.limit())
            output.flip()
            return
        }
        val bytes = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        require(source.remaining() % (bytes * channels) == 0) { "Incomplete PCM frame" }
        val preamp = if (snapshot.enabled) 10.0.pow(appliedPreampDb / 20.0) else 1.0
        while (source.hasRemaining()) {
            val mix = if (transitionRemaining > 0) 1.0 - transitionRemaining.toDouble() / transitionFrames else 1.0
            for (c in 0 until channels) {
                var x = if (bytes == 4) source.float.toDouble() else source.short / 32768.0
                if (!x.isFinite()) x = 0.0
                var value = x * preamp
                if (snapshot.enabled) states[c].forEach { value = it.process(value) }
                if (transitionRemaining > 0) {
                    var old = x * previousPreamp
                    previousStates[c].forEach { old = it.process(old) }
                    value = old * (1.0 - mix) + value * mix
                }
                frame[c] = value
            }
            // Limit the float/double intermediate BEFORE quantizing to PCM16.
            if (snapshot.limiterEnabled && (snapshot.enabled || transitionRemaining > 0)) limiter.process(frame, rate, snapshot.ceilingDb, snapshot.releaseMs)
            for (value in frame) {
                if (bytes == 4) output.putFloat(value.toFloat())
                else output.putShort((value * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            if (transitionRemaining > 0 && --transitionRemaining == 0) previousStates = emptyList()
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }
}
