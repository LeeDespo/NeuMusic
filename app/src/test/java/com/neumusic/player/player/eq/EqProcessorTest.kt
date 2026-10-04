package com.neumusic.player.player.eq

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class EqProcessorTest {
    private fun input(rate: Int, channels: Int, encoding: Int, frames: Int, amplitude: Double): ByteBuffer {
        val bytes = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val buffer = ByteBuffer.allocateDirect(frames * channels * bytes).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { frame -> repeat(channels) { channel ->
            val v = amplitude * sin(2 * PI * 997 * frame / rate) * if (channel == 0) 1.0 else .5
            if (bytes == 4) buffer.putFloat(v.toFloat()) else buffer.putShort((v * 32767).roundToInt().toShort())
        } }
        return buffer.flip() as ByteBuffer
    }
    private fun bytes(buffer: ByteBuffer): ByteArray = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }
    @Test fun bypassIsBitExactForEverySupportedFormat() {
        for (rate in listOf(44100, 48000, 96000)) for (channels in listOf(1, 2)) for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            val processor = EqProcessor()
            processor.configure(AudioProcessor.AudioFormat(rate, channels, encoding)); processor.flush()
            val buffer = input(rate, channels, encoding, 1024, .8)
            val original = bytes(buffer)
            processor.queueInput(buffer)
            assertFalse(buffer.hasRemaining())
            assertArrayEquals(original, bytes(processor.output))
            processor.queueEndOfStream(); assertTrue(processor.isEnded)
            processor.reset()
        }
    }
    @Test fun emptySharedBufferIsSafeBeforeAndAfterReset() {
        val processor = EqProcessor()
        repeat(3) {
            processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); processor.flush()
            processor.queueInput(AudioProcessor.EMPTY_BUFFER)
            assertEquals(0, processor.output.remaining())
            processor.queueEndOfStream(); assertTrue(processor.isEnded)
            processor.reset()
        }
    }
    @Test fun duplicateOutputCanBeRequeuedWithoutSelfCopyOrPositionCorruption() {
        val processor = EqProcessor()
        processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); processor.flush()
        processor.queueInput(input(48000, 2, C.ENCODING_PCM_16BIT, 128, .8))
        val output = processor.output
        val expected = bytes(output)
        processor.queueInput(output)
        assertArrayEquals(expected, bytes(processor.output))
    }
    @Test fun linkedLimiterRunsBeforePcm16QuantizationAndPreservesChannelRatio() {
        val ceiling = 10.0.pow(-6.0 / 20)
        for (rate in listOf(44100, 48000, 96000)) for (channels in listOf(1, 2)) for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            val processor = EqProcessor()
            processor.setSnapshot(EqChain.ProcessingSnapshot(enabled = true, ceilingDb = -6.0))
            processor.configure(AudioProcessor.AudioFormat(rate, channels, encoding)); processor.flush()
            // Float deliberately exceeds full-scale: it must be limited, never quantized first.
            processor.queueInput(input(rate, channels, encoding, 4096, if (encoding == C.ENCODING_PCM_FLOAT) 4.0 else .99))
            val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
            val quantum = if (encoding == C.ENCODING_PCM_FLOAT) 1e-6 else 1.0 / 32768
            while (output.hasRemaining()) {
                val first = if (encoding == C.ENCODING_PCM_FLOAT) output.float.toDouble() else output.short / 32768.0
                assertTrue(abs(first) <= ceiling + quantum)
                if (channels == 2) {
                    val second = if (encoding == C.ENCODING_PCM_FLOAT) output.float.toDouble() else output.short / 32768.0
                    assertEquals(first * .5, second, 2 * quantum)
                }
            }
            processor.queueEndOfStream(); assertTrue(processor.isEnded)
        }
    }
    @Test fun renderedPcmResponseMatchesFilterAndAutomaticPreamp() {
        val rate = 48000
        val filter = Biquad.Filter(Biquad.Type.PK, 997.0, 12.0, 1.414)
        val processor = EqProcessor()
        processor.setSnapshot(EqChain.ProcessingSnapshot(true, listOf(filter)))
        processor.configure(AudioProcessor.AudioFormat(rate, 1, C.ENCODING_PCM_FLOAT)); processor.flush()
        val buffer = input(rate, 1, C.ENCODING_PCM_FLOAT, rate, .1)
        val original = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        processor.queueInput(buffer)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        var inputEnergy = 0.0; var outputEnergy = 0.0
        for (i in 0 until rate) {
            val x = original.float.toDouble()
            val y = output.float.toDouble()
            if (i > rate / 2) { inputEnergy += x * x; outputEnergy += y * y }
        }
        val expectedDb = Biquad.coefficients(filter, rate).responseDb(997.0, rate) + processor.appliedPreampDb
        assertEquals(expectedDb, 10 * log10(outputEnergy / inputEnergy), .001)
    }
    @Test fun gainUpdatesCrossfadeAndReconfigureClearsOldFilterDelays() {
        val processor = EqProcessor()
        processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)); processor.flush()
        processor.queueInput(input(48000, 2, C.ENCODING_PCM_FLOAT, 1024, .2)); processor.output
        processor.setSnapshot(EqChain.ProcessingSnapshot(true, listOf(Biquad.Filter(Biquad.Type.PK, 997.0, 12.0, 1.414))))
        processor.queueInput(input(48000, 2, C.ENCODING_PCM_FLOAT, 2048, .2))
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertTrue(processor.appliedPreampDb <= -12.25 + 1e-8)
        while (output.hasRemaining()) assertTrue(output.float.isFinite())
        processor.queueEndOfStream(); assertTrue(processor.isEnded)
        processor.reset()
        processor.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)); processor.flush()
        processor.queueInput(ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN))
        val silence = processor.output
        assertTrue(bytes(silence).all { it == 0.toByte() })
    }
}
