package com.neumusic.player

import android.app.Instrumentation
import android.os.Bundle
import androidx.compose.runtime.MonotonicFrameClock
import com.neumusic.player.ui.player.VinylGeo
import com.neumusic.player.ui.player.VinylPhase
import com.neumusic.player.ui.player.VinylTurntableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.abs

/** No network, audio writes, or screenshots: exercise the real visual state machine. */
class VinylReviewInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            runBlocking(Dispatchers.Main + TestFrameClock) {
                val scope = CoroutineScope(coroutineContext + SupervisorJob())
                try {
                    val state = VinylTurntableState(scope)
                    var position = 0L
                    state.positionSource = { position }
                    state.durationSource = { 100_000L }
                    state.onTrackChanged("same-cover", "track-a")
                    state.start()
                    state.setPlaying(true)
                    suspend fun until(predicate: () -> Boolean) {
                        withTimeout(12_000) { while (!predicate()) delay(20) }
                    }
                    until { state.phase == VinylPhase.playing }
                    position = 75_000
                    delay(100)
                    check(abs(state.tipRadius - VinylGeo.radiusForProgress(0.75f)) < 0.001f)
                    // Near the end, do not park and start the same record again (especially at 0.5x).
                    position = 99_900
                    state.onPosition(position, 100_000)
                    delay(2_000)
                    check(state.phase == VinylPhase.playing) { "Premature end parking" }
                    state.onTrackChanged("same-cover", "track-b")
                    until { state.phase == VinylPhase.discSwap }
                    delay(400) // New request arrives after the new cover has begun entering.
                    state.onTrackChanged("latest-cover", "track-c")
                    until {
                        check(state.phase != VinylPhase.discAccel || state.cover == "latest-cover") {
                            "Accelerated the stale cover while a newer request was pending"
                        }
                        state.cover == "latest-cover" && state.phase == VinylPhase.playing
                    }
                    state.setPlaying(false)
                    until { state.phase == VinylPhase.idle }
                    state.onTrackChanged("", "track-d")
                    delay(150)
                    state.onTrackChanged("paused-latest", "track-e")
                    delay(600)
                    check(state.phase == VinylPhase.idle && state.cover == "latest-cover") {
                        "Paused track changes bypassed the quiet window"
                    }
                    until { state.cover == "paused-latest" && state.phase == VinylPhase.idle }
                    check(state.platterSpeed == 0f)
                    result.putString("stream", "PASS: progress, no end re-entry, same cover identities, late swap request, paused quiet window\n")
                } finally { scope.cancel() }
            }
            finish(0, result)
        } catch (error: Throwable) {
            result.putString("stream", "FAIL: ${error.stackTraceToString()}\n")
            finish(1, result)
        }
    }

    private object TestFrameClock : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            return onFrame(System.nanoTime())
        }
    }
}
