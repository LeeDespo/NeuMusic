package com.neumusic.player.player.eq

import kotlin.math.*

object EqChain {
    val FREQUENCIES_HZ = List(10) { 31.25 * 2.0.pow(it) }
    data class ProcessingSnapshot(
        val enabled: Boolean = false,
        val filters: List<Biquad.Filter> = emptyList(),
        val preampDb: Double = 0.0,
        val limiterEnabled: Boolean = true,
        val ceilingDb: Double = -0.18,
        val releaseMs: Double = 100.0,
    )
    fun graphicFilters(gains: IntArray, frequencies: List<Double> = FREQUENCIES_HZ, q: Double = sqrt(2.0)): List<Biquad.Filter> {
        require(gains.size == frequencies.size)
        return gains.mapIndexed { i, gain -> Biquad.Filter(Biquad.Type.PK, frequencies[i], gain / 100.0, q) }
    }
    fun responseDb(snapshot: ProcessingSnapshot, freqHz: Double, sampleRate: Int = 48000): Double =
        snapshot.filters.sumOf { Biquad.coefficients(it, sampleRate).responseDb(freqHz, sampleRate) } +
            HeadroomPlanner.preampDb(snapshot.filters, sampleRate, snapshot.preampDb)

    /** Approximation only: response samples are NOT a fit or exact inverse EQ. */
    fun sampleResponse(filters: List<Biquad.Filter>, frequencies: List<Double>, sampleRate: Int = 48000): IntArray =
        frequencies.map { f -> (filters.sumOf { Biquad.coefficients(it, sampleRate).responseDb(f, sampleRate) } * 100).roundToInt() }.toIntArray()
}

/** Instant attack, exponential release and one common gain for the entire sample frame. */
class LinkedPeakLimiter {
    private var gain = 1.0
    fun reset() { gain = 1.0 }
    fun process(frame: DoubleArray, sampleRate: Int, ceilingDb: Double = -0.18, releaseMs: Double = 100.0) {
        val ceiling = 10.0.pow(ceilingDb / 20.0)
        val peak = frame.maxOfOrNull { abs(it) } ?: 0.0
        val desired = if (peak > ceiling) ceiling / peak else 1.0
        val release = exp(-1.0 / (sampleRate * releaseMs.coerceAtLeast(1.0) / 1000.0))
        gain = if (desired < gain) desired else desired + (gain - desired) * release
        for (c in frame.indices) frame[c] *= gain
    }
}
