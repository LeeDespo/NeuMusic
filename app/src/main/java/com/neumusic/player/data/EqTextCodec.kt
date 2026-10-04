package com.neumusic.player.data

import com.neumusic.player.player.eq.Biquad
import java.util.Locale

/** Strict interchange parser. Frequencies are Hz, gains are dB; no magnitude guessing. */
object EqTextCodec {
    data class Document(val preampDb: Double = 0.0, val filters: List<Biquad.Filter> = emptyList(), val frequenciesHz: List<Double> = emptyList(), val gainsDb: List<Double> = emptyList())
    data class ParseResult(val document: Document?, val accepted: Int, val skipped: Int, val error: String? = null)
    private const val NUM = "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?"
    private val preamp = Regex("Preamp:\\s*($NUM)\\s*dB", RegexOption.IGNORE_CASE)
    private val filter = Regex("Filter\\s+\\d+:\\s*(ON|OFF)\\s+(PK|LSC|HSC|LS|HS)\\s+Fc\\s+($NUM)\\s*Hz\\s+Gain\\s+($NUM)\\s*dB\\s+Q\\s+($NUM)", RegexOption.IGNORE_CASE)
    fun parse(text: String): ParseResult {
        if (text.length > 262144) return ParseResult(null, 0, 0, "文件过大")
        var amp = 0.0
        var seenAmp = false
        var skipped = 0
        var graphic: Pair<List<Double>, List<Double>>? = null
        val filters = mutableListOf<Biquad.Filter>()
        for (raw in text.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val p = preamp.matchEntire(line)
            val f = filter.matchEntire(line)
            when {
                p != null -> {
                    val value = p.groupValues[1].toDoubleOrNull()
                    if (seenAmp || value == null || !value.isFinite() || value !in -60.0..30.0)
                        return ParseResult(null, filters.size, skipped, "前级增益无效或重复")
                    amp = value; seenAmp = true
                }
                f != null -> {
                    if (f.groupValues[1].equals("OFF", true)) { skipped++; continue }
                    val type = when (f.groupValues[2].uppercase()) { "LS", "LSC" -> Biquad.Type.LSC; "HS", "HSC" -> Biquad.Type.HSC; else -> Biquad.Type.PK }
                    val parsed = runCatching { Biquad.Filter(type, f.groupValues[3].toDouble(), f.groupValues[4].toDouble(), f.groupValues[5].toDouble()) }.getOrNull()
                    if (parsed == null) return ParseResult(null, filters.size, skipped, "滤波器频率、增益或Q超出范围")
                    filters += parsed
                }
                line.startsWith("GraphicEQ:", true) -> {
                    if (graphic != null) return ParseResult(null, 0, skipped, "图形曲线重复")
                    val nodes = line.substringAfter(':').split(';').filter { it.isNotBlank() }.map { it.trim().split(Regex("\\s+")) }
                    if (nodes.isEmpty() || nodes.size > 1024 || nodes.any { it.size != 2 }) return ParseResult(null, 0, skipped, "图形曲线格式无效")
                    val freq = nodes.map { it[0].toDoubleOrNull() ?: Double.NaN }
                    val gains = nodes.map { it[1].toDoubleOrNull() ?: Double.NaN }
                    if (freq.any { !it.isFinite() || it !in 10.0..24000.0 } || freq.zipWithNext().any { it.first >= it.second } || gains.any { !it.isFinite() || it !in -30.0..30.0 })
                        return ParseResult(null, 0, skipped, "图形曲线须按Hz递增，增益须为-30..30dB")
                    graphic = freq to gains
                }
                // Unknown DSP directives would change the meaning: reject instead of silently dropping.
                else -> return ParseResult(null, filters.size, skipped, "不支持的指令：${line.take(48)}")
            }
            if (filters.size > 64) return ParseResult(null, filters.size, skipped, "滤波器过多（最多64个）")
        }
        if (filters.isNotEmpty() && graphic != null) return ParseResult(null, 0, skipped, "不能混用参数滤波与图形曲线")
        if (filters.isEmpty() && graphic == null) return ParseResult(null, 0, skipped, "没有启用的滤波器或曲线")
        val doc = Document(amp, filters.toList(), graphic?.first.orEmpty(), graphic?.second.orEmpty())
        return ParseResult(doc, if (graphic != null) graphic.first.size else filters.size, skipped)
    }
    private fun n(value: Double) = String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
    fun export(document: Document): String = buildString {
        append("Preamp: ${n(document.preampDb)} dB\n")
        if (document.filters.isNotEmpty()) document.filters.forEachIndexed { i, f ->
            append("Filter ${i + 1}: ON ${f.type.name} Fc ${n(f.freqHz)} Hz Gain ${n(f.gainDb)} dB Q ${n(f.q)}\n")
        } else {
            require(document.frequenciesHz.size == document.gainsDb.size && document.frequenciesHz.isNotEmpty())
            append("GraphicEQ: ")
            append(document.frequenciesHz.indices.joinToString("; ") { "${n(document.frequenciesHz[it])} ${n(document.gainsDb[it])}" })
            append('\n')
        }
    }
}
