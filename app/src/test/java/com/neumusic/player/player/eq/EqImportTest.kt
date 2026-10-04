package com.neumusic.player.player.eq

import com.neumusic.player.data.EqTextCodec
import com.neumusic.player.data.EqPresetStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.math.*

class EqImportTest {
    @Test fun allBundledAutoEqFilesParseAndRoundTrip() {
        val file = listOf(File("src/main/assets/autoeq/catalog.json"), File("app/src/main/assets/autoeq/catalog.json")).first { it.exists() }
        val catalog = JSONObject(file.readText())
        val entries = catalog.getJSONArray("entries")
        assertEquals(218, entries.length())
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val text = entry.getString("text")
            val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
            assertEquals(entry.getString("name"), entry.getString("sha256"), hash)
            val result = EqTextCodec.parse(text)
            assertNull("${entry.getString("name")}: ${result.error}", result.error)
            assertTrue(result.document!!.filters.isNotEmpty())
            assertEquals(result.document, EqTextCodec.parse(EqTextCodec.export(result.document!!)).document)
        }
    }
    @Test fun presetV1KeepsGainsAndMigratesStableIdentityWithoutInventingFrequencies() {
        val text = """{"presets":[{"name":"Mine","gains":[100,200,0,-200,-100],"builtin":false}]}"""
        val first = EqPresetStore.decode(text).single()
        assertEquals(first.id, EqPresetStore.decode(text).single().id)
        assertArrayEquals(intArrayOf(100,200,0,-200,-100), first.gains)
        assertTrue(first.frequenciesHz.isEmpty())
        assertEquals("user", first.source)
        assertEquals(0.0, first.preampDb, 0.0)
    }
    @Test fun invalidV2ParametersDoNotBecomeSilentZeroDefaults() {
        assertTrue(EqPresetStore.decode("""{"v":2,"presets":[{"name":"bad","gains":[0],"frequenciesHz":[0]}]}""").isEmpty())
        assertTrue(EqPresetStore.decode("""{"v":2,"presets":[{"name":"bad","gains":[0],"preampDb":999}]}""").isEmpty())
    }
    @Test fun fitterRecoversKnownCurveRatherThanUsingResponseAsFilterGains() {
        val expected = intArrayOf(0, 450, -250, 0, 200, -300, 150, 0, -100, 0)
        val original = EqChain.graphicFilters(expected)
        val fit = EqFitter.fitFilters(original, EqChain.FREQUENCIES_HZ, sqrt(2.0), -12.0, 12.0)
        assertTrue("RMS ${fit.rmsErrorDb}", fit.rmsErrorDb < .02)
        for (i in expected.indices) assertEquals(expected[i].toDouble(), fit.gains[i].toDouble(), 2.0)
        val sampled = EqChain.sampleResponse(original, EqChain.FREQUENCIES_HZ)
        assertFalse(expected.contentEquals(sampled))
    }
    @Test fun arbitraryGraphicNodesFitWithinBoundsAndReportResidual() {
        val freq = listOf(20.0, 50.0, 137.0, 500.0, 1700.0, 7000.0, 20000.0)
        val gains = listOf(0.0, 3.0, 4.0, 1.0, -2.0, -1.0, 0.0)
        val fit = EqFitter.fitGraphic(freq, gains, EqChain.FREQUENCIES_HZ, sqrt(2.0), -12.0, 12.0)
        assertTrue(fit.gains.all { it in -1200..1200 })
        assertTrue("RMS ${fit.rmsErrorDb}", fit.rmsErrorDb < 1.0)
        assertEquals(2.5, EqFitter.interpolateGraphic(listOf(100.0, 1000.0), listOf(0.0, 5.0), sqrt(100000.0)), 1e-8)
        val limited = EqFitter.fitGraphic(freq, List(freq.size) { 25.0 }, EqChain.FREQUENCIES_HZ, sqrt(2.0), -12.0, 12.0)
        assertTrue(limited.rmsErrorDb > 1.0)
    }
}
