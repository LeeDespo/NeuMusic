package com.neumusic.player.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neumusic.player.player.eq.*
import com.neumusic.player.shade.*
import kotlin.math.*

/** 高频拖动快照只在绘制阶段读取；响应采样缓存于绘制域，不重组页面。 */
@Composable
fun EqCurveView(snapshot: State<EqChain.ProcessingSnapshot>, precise: Boolean) {
    val colors = LocalShadeColors.current
    var cached: EqChain.ProcessingSnapshot? = remember { null }
    val points = remember { DoubleArray(160) }
    Column {
        Canvas(Modifier.fillMaxWidth().height(156.dp).shadeInset(14.dp, 3.dp, 5.dp)) {
            val current = snapshot.value
            if (cached != current) {
                val coefficients = current.filters.map { Biquad.coefficients(it, 48000) }
                val preamp = if (precise) HeadroomPlanner.preampDb(current.filters, 48000, current.preampDb) else 0.0
                for (i in points.indices) {
                    val freq = 20.0 * 1000.0.pow(i.toDouble() / (points.size - 1))
                    points[i] = coefficients.sumOf { it.responseDb(freq, 48000) } + preamp
                }
                cached = current
            }
            val inset = 12.dp.toPx()
            val w = size.width - inset * 2
            val h = size.height - inset * 2
            fun y(db: Double) = inset + (24.0 - db.coerceIn(-24.0, 24.0)).toFloat() / 48f * h
            for (db in listOf(-12.0, 0.0, 12.0)) drawLine(colors.textTertiary.copy(alpha = .18f), Offset(inset, y(db)), Offset(size.width - inset, y(db)))
            for (freq in listOf(100.0, 1000.0, 10000.0)) {
                val x = inset + (ln(freq / 20) / ln(1000.0)).toFloat() * w
                drawLine(colors.textTertiary.copy(alpha = .12f), Offset(x, inset), Offset(x, size.height - inset))
            }
            val line = Path()
            points.forEachIndexed { i, db ->
                val x = inset + w * i / (points.size - 1)
                if (i == 0) line.moveTo(x, y(db)) else line.lineTo(x, y(db))
            }
            val fill = Path().apply {
                addPath(line); lineTo(inset + w, y(0.0)); lineTo(inset, y(0.0)); close()
            }
            drawPath(fill, colors.accent.copy(alpha = .10f))
            drawPath(line, colors.accent, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (label in listOf("20 Hz", "1 kHz", "20 kHz")) Text(label, color = colors.textSecondary, fontSize = 11.sp)
        }
    }
}
