package com.neumusic.player.player

import android.content.Context
import android.media.AudioManager
import androidx.test.platform.app.InstrumentationRegistry
import com.neumusic.player.data.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Real Media3 playback threads and platform effects, with a deterministic muted local WAV. */
class AudioFxLifecycleTest {
    @Test fun pauseSessionRebuildAndExplicitReleaseKeepPlaybackUsable() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val sp = context.getSharedPreferences("neumusic", Context.MODE_PRIVATE)
        val saved = sp.all.toMap()
        val wav = File(context.cacheDir, "fx-test.wav")
        fun main(block: () -> Unit) = inst.runOnMainSync(block)
        fun await(label: String, predicate: () -> Boolean) {
            val deadline = System.nanoTime() + 8_000_000_000L
            while (System.nanoTime() < deadline) {
                var yes = false
                main { PlayerHost.exoPlayer()?.playerError?.let { throw AssertionError("Playback failed during $label", it) }; yes = predicate() }
                if (yes) return
                Thread.sleep(40)
            }
            fail("Timed out: $label")
        }
        val track = Track("__audio_fx_test__", "Audio FX test", "", "Test", "", "", 20, false)
        writeWav(wav)
        try {
            main {
                Prefs.init(context); AppLog.init(context)
                Prefs.eqEngine = "PRECISE"; Prefs.eqEnabled = true
                Prefs.smartEq = false; Prefs.barViz = true
                PlayerHost.resolveUrl = { wav.toURI().toString() }
                PlayerHost.init(context)
            }
            await("player creation") { PlayerHost.exoPlayer() != null }
            main { PlayerHost.exoPlayer()!!.volume = 0f; PlayerHost.playQueue(listOf(track), 0) }
            await("local PCM playback") { PlayerHost.isPlaying.value && PlayerHost.positionMs() > 100 }
            main {
                val exported = EqTextCodec.parse(requireNotNull(EqualizerHost.exportPreset(null))).document!!
                assertTrue(exported.filters.all { abs(it.q - sqrt(2.0)) < .0001 })
            }
            var sid = 0
            main { sid = AudioFxController.sessionId.value; assertTrue(sid > 0); assertTrue(EqualizerHost.mounted.value); PlayerHost.pause() }
            await("pause") { !PlayerHost.isPlaying.value }
            await("paused visualization zero") {
                VizHost.levels.value.all { it == 0f } && PlayerHost.vizProcessor.levels.value.all { it == 0f }
            }
            var paused = 0L
            main { paused = PlayerHost.positionMs(); assertEquals(sid, AudioFxController.sessionId.value); assertNotNull(PlayerHost.exoPlayer()) }
            Thread.sleep(250)
            main { assertTrue(abs(PlayerHost.positionMs() - paused) < 60); PlayerHost.toggle() }
            await("resume") { PlayerHost.isPlaying.value && PlayerHost.positionMs() > paused + 100 }
            main {
                // Semantic edit must replace original PEQ, even when returned to the same integer gain.
                val text = "Preamp: -3 dB\nFilter 1: ON PK Fc 997 Hz Gain 3 dB Q 1.4"
                EqualizerHost.importPreset("Lifecycle PEQ", text)
                EqualizerHost.selectPreset("Lifecycle PEQ")
                val generation = EqualizerHost.editGeneration.value
                val originalProgress = EqualizerHost.bandProgress(0)
                EqualizerHost.setBandProgress(0, if (originalProgress < .5f) .8f else .2f, generation)
                EqualizerHost.setBandProgress(0, originalProgress, generation)
                EqualizerHost.commitBands(generation)
                assertTrue(EqualizerHost.presets.value.first { it.name == "Lifecycle PEQ" }.filters.isEmpty())
                EqualizerHost.addPreset("Lifecycle other")
                val currentBands = EqualizerHost.bands.value.copyOf()
                EqualizerHost.setBandProgress(0, 1f, generation)
                EqualizerHost.commitBands(generation)
                assertArrayEquals(currentBands, EqualizerHost.bands.value)
                assertEquals("Lifecycle other", EqualizerHost.selectedPreset.value)
                EqualizerHost.setEngine(EqualizerHost.EqEngine.PLATFORM)
                val am = context.getSystemService(AudioManager::class.java)
                PlayerHost.exoPlayer()!!.setAudioSessionId(am.generateAudioSessionId())
            }
            await("new session") { AudioFxController.sessionId.value > 0 && AudioFxController.sessionId.value != sid }
            main {
                // Preview updates audio immediately; persistence changes only at commit.
                val old = Prefs.playSpeed
                PlayerHost.previewPlaybackParams(speed = 1.25f)
                assertEquals(old, Prefs.playSpeed, 0f)
                PlayerHost.commitPlaybackParams()
                assertEquals(1.25f, Prefs.playSpeed, 0f)
                PlayerHost.release()
            }
            await("release") { PlayerHost.exoPlayer() == null && AudioFxController.sessionId.value == 0 }
            // A resolver that ignores cancellation must still not restart after explicit close.
            val started = CompletableDeferred<Unit>()
            val delayed = CompletableDeferred<String?>()
            main {
                PlayerHost.resolveUrl = {
                    started.complete(Unit)
                    withContext(NonCancellable) { delayed.await() }
                }
                PlayerHost.playQueue(listOf(track), 0)
            }
            await("delayed resolver started") { started.isCompleted }
            main { PlayerHost.release() }
            inst.waitForIdleSync()
            delayed.complete(wav.toURI().toString())
            Thread.sleep(200)
            inst.waitForIdleSync()
            main {
                assertNull(PlayerHost.exoPlayer())
                assertNull(PlayerHost.current.value)
                assertFalse(PlayerHost.isPlaying.value)
                PlayerHost.resolveUrl = { wav.toURI().toString() }
            }
            main { PlayerHost.playQueue(listOf(track), 0) }
            await("recreated player") { PlayerHost.exoPlayer() != null }
            main { PlayerHost.exoPlayer()!!.volume = 0f }
            await("recreate after close") { PlayerHost.exoPlayer() != null && PlayerHost.isPlaying.value && PlayerHost.positionMs() > 100 }
            main { PlayerHost.release(); PlayerHost.playQueue(listOf(track), 0) }
            await("immediate close and replay") { PlayerHost.isPlaying.value && PlayerHost.current.value?.mid == track.mid }
            main { assertEquals(listOf(track), PlayerHost.queueSnapshot()) }
        } finally {
            main { PlayerHost.release() }
            inst.waitForIdleSync()
            main {
                val editor = sp.edit().clear()
                for ((key, value) in saved) when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                }
                editor.commit()
                Prefs.init(context)
                PlayerHost.resolveUrl = null
            }
            wav.delete()
        }
    }
    private fun writeWav(file: File) {
        val rate = 48000; val channels = 2; val frames = rate * 20
        val size = frames * channels * 2
        val out = ByteBuffer.allocate(size + 44).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(size + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(channels.toShort()).putInt(rate).putInt(rate * channels * 2)
            .putShort((channels * 2).toShort()).putShort(16).put("data".toByteArray()).putInt(size)
        repeat(frames) { i ->
            val sample = (sin(2 * PI * 440 * i / rate) * 3276).toInt().toShort()
            out.putShort(sample).putShort(sample)
        }
        file.writeBytes(out.array())
    }
}
