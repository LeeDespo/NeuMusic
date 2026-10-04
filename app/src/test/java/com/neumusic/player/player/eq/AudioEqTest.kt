package com.neumusic.player.player.eq

import com.neumusic.player.data.EqTextCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AudioEqTest {
    @Test fun peakingGainMatchesCenterForRates() {
        for (rate in listOf(44100, 48000, 96000)) for (db in listOf(-12.0, 0.0, 12.0)) {
            val f = Biquad.Filter(Biquad.Type.PK, 1000.0, db, sqrt(2.0))
            assertEquals(db, Biquad.coefficients(f, rate).responseDb(1000.0, rate), 1e-8)
        }
    }
    @Test fun shelvesReachCorrectEndpoints() {
        val low = Biquad.coefficients(Biquad.Filter(Biquad.Type.LSC, 200.0, 9.0, 0.707), 48000)
        val high = Biquad.coefficients(Biquad.Filter(Biquad.Type.HSC, 4000.0, -8.0, 0.707), 48000)
        assertEquals(9.0, low.responseDb(0.0, 48000), 1e-8)
        assertEquals(0.0, low.responseDb(24000.0, 48000), 1e-8)
        assertEquals(-8.0, high.responseDb(24000.0, 48000), 1e-8)
    }
    @Test fun impulseAndSineAgreeWithAnalyticResponse() {
        val filter = Biquad.Filter(Biquad.Type.PK, 500.0, 6.0, 1.414)
        val c = Biquad.coefficients(filter, 48000)
        val state = Biquad.State(c)
        var inputEnergy = 0.0; var outputEnergy = 0.0
        for (i in 0 until 48000) {
            val x = 0.1 * sin(2 * PI * 500 * i / 48000)
            val y = state.process(x)
            assertTrue(y.isFinite())
            if (i > 24000) { inputEnergy += x * x; outputEnergy += y * y }
        }
        assertEquals(c.responseDb(500.0, 48000), 10 * log10(outputEnergy / inputEnergy), 0.001)
    }
    @Test fun headroomOnlyAttenuatesAndAccountsForNarrowPeak() {
        val fs = listOf(Biquad.Filter(Biquad.Type.PK, 1377.0, 12.0, 20.0))
        assertTrue(HeadroomPlanner.preampDb(fs, 48000) <= -12.25 + 1e-8)
        assertEquals(-20.0, HeadroomPlanner.preampDb(fs, 48000, -20.0), 1e-8)
        assertTrue(HeadroomPlanner.preampDb(emptyList(), 48000) <= 0.0)
    }
    @Test fun linkedLimiterPreservesStereoRatioAndCeiling() {
        val limiter = LinkedPeakLimiter()
        val ceiling = 10.0.pow(-0.18 / 20)
        repeat(5000) {
            val frame = doubleArrayOf(4.0, 2.0)
            limiter.process(frame, 48000)
            assertTrue(abs(frame[0]) <= ceiling + 1e-12)
            assertEquals(2.0, frame[0] / frame[1], 1e-10)
        }
        val frame = doubleArrayOf(0.2, 0.1)
        limiter.process(frame, 48000)
        assertTrue(frame[0] < 0.2)
        repeat(48000) { frame[0] = 0.2; frame[1] = 0.1; limiter.process(frame, 48000) }
        assertEquals(0.2, frame[0], 0.0001)
    }
    @Test fun importExportRetainsPreampAndFilterParameters() {
        val text = "Preamp: -3.75 dB\nFilter 1: ON PK Fc 1000 Hz Gain 3.5 dB Q 1.41\nFilter 2: ON LS Fc 80 Hz Gain -2 dB Q 0.707"
        val first = EqTextCodec.parse(text)
        assertNull(first.error)
        val second = EqTextCodec.parse(EqTextCodec.export(first.document!!))
        assertEquals(first.document, second.document)
    }
    @Test fun graphicNodesKeepFrequenciesAndRejectGuessedUnits() {
        val result = EqTextCodec.parse("Preamp: -2 dB\nGraphicEQ: 20 5.5; 137 2; 20000 -1")
        assertEquals(listOf(20.0, 137.0, 20000.0), result.document!!.frequenciesHz)
        assertEquals(5.5, result.document!!.gainsDb[0], 0.0)
        assertNotNull(EqTextCodec.parse("GraphicEQ: 20 55; 100 0").error)
        assertNotNull(EqTextCodec.parse("GraphicEQ: 100 1; 20 2").error)
        assertNotNull(EqTextCodec.parse("Preamp: NaN dB\nGraphicEQ: 20 0").error)
        assertNotNull(EqTextCodec.parse("Filter 1: ON PK Fc 1000 Hz Gain 2 dB Q 0").error)
    }
    @Test fun responseSamplingMatchesCascadeAtActualDeviceFrequencies() {
        val fs = EqChain.graphicFilters(intArrayOf(0, 600, 0, -300, 0, 0, 0, 0, 0, 0))
        val actual = listOf(65.0, 250.0, 1000.0, 4000.0, 14000.0)
        val sampled = EqChain.sampleResponse(fs, actual)
        for (i in actual.indices) {
            val db = fs.sumOf { Biquad.coefficients(it, 48000).responseDb(actual[i], 48000) }
            assertEquals(db, sampled[i] / 100.0, 0.0051)
        }
    }
}
