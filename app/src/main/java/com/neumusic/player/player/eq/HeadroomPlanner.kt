package com.neumusic.player.player.eq

import kotlin.math.*

object HeadroomPlanner {
    const val PROBE_COUNT = 192
    const val SAFETY_MARGIN_DB = 0.25
    fun preampDb(filters: List<Biquad.Filter>, sampleRate: Int, requestedDb: Double = 0.0): Double {
        val coefficients = filters.map { Biquad.coefficients(it, sampleRate) }
        var peak = 0.0 // Never amplify an all-cut curve.
        val maxHz = min(20000.0, sampleRate * 0.499)
        for (i in 0 until PROBE_COUNT) {
            val f = 20.0 * (maxHz / 20.0).pow(i.toDouble() / (PROBE_COUNT - 1))
            peak = max(peak, coefficients.sumOf { it.responseDb(f, sampleRate) })
        }
        // Probe each center too: a narrow imported PK can lie between scan points.
        filters.filter { it.freqHz < sampleRate / 2.0 }.forEach { filter ->
            peak = max(peak, coefficients.sumOf { it.responseDb(filter.freqHz, sampleRate) })
        }
        return min(requestedDb, -(peak + SAFETY_MARGIN_DB))
    }
}
