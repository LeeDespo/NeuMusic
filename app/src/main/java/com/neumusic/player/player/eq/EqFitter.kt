package com.neumusic.player.player.eq

import kotlin.math.*

/** Self-authored bounded nonlinear least squares. Results are approximations, not response samples. */
object EqFitter {
    data class Fit(val gains: IntArray, val rmsErrorDb: Double)
    fun fitFilters(filters: List<Biquad.Filter>, centers: List<Double>, q: Double, minDb: Double, maxDb: Double, sampleRate: Int = 48000): Fit {
        val coefficients = filters.map { Biquad.coefficients(it, sampleRate) }
        return fit(centers, q, minDb, maxDb, sampleRate) { f -> coefficients.sumOf { it.responseDb(f, sampleRate) } }
    }
    fun fitGraphic(frequencies: List<Double>, gainsDb: List<Double>, centers: List<Double>, q: Double, minDb: Double, maxDb: Double, sampleRate: Int = 48000): Fit {
        require(frequencies.isNotEmpty() && frequencies.size == gainsDb.size)
        require(frequencies.zipWithNext().all { it.first < it.second })
        return fit(centers, q, minDb, maxDb, sampleRate) { f -> interpolateGraphic(frequencies, gainsDb, f) }
    }
    fun interpolateGraphic(frequencies: List<Double>, gainsDb: List<Double>, f: Double): Double {
        if (f <= frequencies.first()) return gainsDb.first()
        if (f >= frequencies.last()) return gainsDb.last()
        val hi = frequencies.indexOfFirst { it >= f }
        val lo = hi - 1
        val t = ln(f / frequencies[lo]) / ln(frequencies[hi] / frequencies[lo])
        return gainsDb[lo] * (1 - t) + gainsDb[hi] * t
    }
    private fun fit(centers: List<Double>, q: Double, minDb: Double, maxDb: Double, sampleRate: Int, target: (Double) -> Double): Fit {
        if (centers.isEmpty()) return Fit(IntArray(0), 0.0)
        require(centers.all { it > 0 && it < sampleRate / 2.0 })
        val probes = (0 until 128).map { 20.0 * (min(20000.0, sampleRate * .499) / 20.0).pow(it / 127.0) } + centers
        val desired = probes.map(target).toDoubleArray()
        var gains = DoubleArray(centers.size)
        fun responses(values: DoubleArray): Array<DoubleArray> = Array(values.size) { j ->
            val c = Biquad.coefficients(Biquad.Filter(Biquad.Type.PK, centers[j], values[j], q), sampleRate)
            DoubleArray(probes.size) { c.responseDb(probes[it], sampleRate) }
        }
        fun error(parts: Array<DoubleArray>): Double = probes.indices.sumOf { i -> (desired[i] - parts.sumOf { it[i] }).pow(2) }
        repeat(10) {
            val parts = responses(gains)
            val residual = DoubleArray(probes.size) { i -> desired[i] - parts.sumOf { it[i] } }
            val derivative = Array(gains.size) { j ->
                val c = Biquad.coefficients(Biquad.Filter(Biquad.Type.PK, centers[j], (gains[j] + .05).coerceAtMost(30.0), q), sampleRate)
                DoubleArray(probes.size) { i -> (c.responseDb(probes[i], sampleRate) - parts[j][i]) / .05 }
            }
            val matrix = Array(gains.size) { a -> DoubleArray(gains.size + 1) { b ->
                if (b == gains.size) probes.indices.sumOf { derivative[a][it] * residual[it] }
                else probes.indices.sumOf { derivative[a][it] * derivative[b][it] } + if (a == b) .02 else 0.0
            } }
            // Pivoted elimination of the normal equations with light diagonal regularization.
            for (a in gains.indices) {
                val pivot = (a until gains.size).maxBy { abs(matrix[it][a]) }
                val tmp = matrix[a]; matrix[a] = matrix[pivot]; matrix[pivot] = tmp
                val denominator = matrix[a][a]
                if (abs(denominator) < 1e-12) continue
                for (b in a..gains.size) matrix[a][b] /= denominator
                for (row in gains.indices) if (row != a) {
                    val scale = matrix[row][a]
                    for (b in a..gains.size) matrix[row][b] -= scale * matrix[a][b]
                }
            }
            val oldError = error(parts)
            var step = 1.0
            for (attempt in 0 until 6) {
                val candidate = DoubleArray(gains.size) { j -> (gains[j] + step * matrix[j][gains.size]).coerceIn(minDb, maxDb) }
                if (error(responses(candidate)) <= oldError) { gains = candidate; break }
                step *= .5
            }
        }
        val rounded = gains.map { (it * 100).roundToInt() }.toIntArray()
        val actual = responses(DoubleArray(gains.size) { rounded[it] / 100.0 })
        return Fit(rounded, sqrt(error(actual) / probes.size))
    }
}
