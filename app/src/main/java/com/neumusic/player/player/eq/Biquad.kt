package com.neumusic.player.player.eq

import kotlin.math.*

/** Self-authored RBJ cookbook biquads; normalized transposed direct form II. */
object Biquad {
    enum class Type { PK, LSC, HSC }
    data class Filter(val type: Type, val freqHz: Double, val gainDb: Double, val q: Double) {
        init {
            require(freqHz.isFinite() && freqHz in 10.0..24000.0)
            require(gainDb.isFinite() && gainDb in -30.0..30.0)
            require(q.isFinite() && q in 0.1..20.0)
        }
    }
    data class Coefficients(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        fun responseDb(freqHz: Double, sampleRate: Int): Double {
            val w = 2.0 * PI * freqHz / sampleRate
            fun power(c0: Double, c1: Double, c2: Double): Double {
                val re = c0 + c1 * cos(w) + c2 * cos(2 * w)
                val im = -c1 * sin(w) - c2 * sin(2 * w)
                return re * re + im * im
            }
            return 10.0 * log10((power(b0, b1, b2) / power(1.0, a1, a2)).coerceAtLeast(1e-30))
        }
    }
    fun coefficients(filter: Filter, sampleRate: Int): Coefficients {
        require(sampleRate > 0)
        // A filter above Nyquist is unavailable, never silently move its frequency.
        if (filter.freqHz >= sampleRate * 0.5 || filter.gainDb == 0.0)
            return Coefficients(1.0, 0.0, 0.0, 0.0, 0.0)
        val a = 10.0.pow(filter.gainDb / 40.0)
        val w = 2 * PI * filter.freqHz / sampleRate
        val c = cos(w)
        val alpha = sin(w) / (2 * filter.q)
        val t = 2 * sqrt(a) * alpha
        val values = when (filter.type) {
            Type.PK -> doubleArrayOf(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
            Type.LSC -> doubleArrayOf(a * ((a + 1) - (a - 1) * c + t), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - t), (a + 1) + (a - 1) * c + t, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - t)
            Type.HSC -> doubleArrayOf(a * ((a + 1) + (a - 1) * c + t), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - t), (a + 1) - (a - 1) * c + t, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - t)
        }
        val d = values[3]
        return Coefficients(values[0] / d, values[1] / d, values[2] / d, values[4] / d, values[5] / d)
    }
    class State(private var c: Coefficients) {
        private var z1 = 0.0
        private var z2 = 0.0
        fun process(x: Double): Double {
            val y = c.b0 * x + z1
            z1 = c.b1 * x - c.a1 * y + z2
            z2 = c.b2 * x - c.a2 * y
            return y
        }
        fun reset() { z1 = 0.0; z2 = 0.0 }
    }
}
