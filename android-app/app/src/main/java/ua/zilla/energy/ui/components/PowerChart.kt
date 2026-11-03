package ua.zilla.energy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ua.zilla.energy.model.PowerPoint
import ua.zilla.energy.ui.theme.Mint
import kotlin.math.abs
import kotlin.math.max

/** Lightweight smooth line chart, drawn directly in Compose; no external chart SDK. */
@Composable
fun PowerChart(points: List<PowerPoint>, modifier: Modifier = Modifier) {
    val shown = points.takeLast(72)
    val battery = shown.mapNotNull { it.batteryW }
    val grid = shown.mapNotNull { it.gridW }
    val solar = shown.mapNotNull { it.solarW }
    val all = battery + grid + solar
    val limit = max(500.0, (all.maxOfOrNull { abs(it) } ?: 1000.0) * 1.25)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Power history", style = MaterialTheme.typography.titleMedium)
        if (shown.size < 2) {
            Box(Modifier.fillMaxWidth().height(170.dp)) {
                Text("Collecting readings…", color = Mint.muted)
            }
        } else {
            Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                val top = 8f
                val bottom = size.height - 12f
                val range = bottom - top
                val zero = top + range / 2f
                repeat(5) { tick ->
                    val y = top + range * tick / 4f
                    drawLine(Color(0xFFEAF2EC), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                drawLine(Color(0xFFB8D8C5), Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())

                fun yOf(value: Double): Float =
                    (zero - (value / limit * range / 2f).toFloat()).coerceIn(top, bottom)

                fun drawSeries(values: List<Double?>, color: Color) {
                    if (values.count { it != null } < 2) return
                    var part = mutableListOf<Offset>()
                    fun drawPart() {
                        if (part.size < 2) { part.clear(); return }
                        val path = Path().apply { moveTo(part.first().x, part.first().y) }
                        for (i in 1 until part.size) {
                            val p = part[i - 1]
                            val q = part[i]
                            val midX = (p.x + q.x) / 2f
                            path.cubicTo(midX, p.y, midX, q.y, q.x, q.y)
                        }
                        drawPath(path, color = color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                        part.clear()
                    }
                    values.forEachIndexed { i, value ->
                        if (value == null) drawPart() else {
                            val x = i * (size.width / (shown.size - 1))
                            part.add(Offset(x, yOf(value)))
                        }
                    }
                    drawPart()
                }
                drawSeries(shown.map { it.solarW }, Mint.leaf)
                drawSeries(shown.map { it.gridW }, Mint.blue)
                drawSeries(shown.map { it.batteryW }, Mint.primary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DotLabel("Solar", Mint.leaf)
            DotLabel("Grid", Mint.blue)
            DotLabel("Battery", Mint.primary)
        }
    }
}
