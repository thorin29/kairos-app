package com.kairos.app.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.data.remote.dto.PlankTestDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The Marine Corps PFT plank: 1:10 passes (40 points), 3:45 is full marks. */
private const val PFT_PLANK_MIN_SEC = 70
private const val PFT_PLANK_MAX_SEC = 225

private fun plankClock(sec: Int): String = "${sec / 60}:" + (sec % 60).toString().padStart(2, '0')

private val SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

private fun dayOf(iso: String): LocalDate? = runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()

private fun shortDate(iso: String): String = dayOf(iso)?.format(SHORT_DATE) ?: iso

private fun points(t: PlankTestDto): String = t.pft?.let { "$it pts" } ?: "under 1:10"

/**
 * Core's benchmark (v0.452): the plank test — a day's longest plank. Best and
 * latest with their PFT points, the change since the test before, and every
 * test on a chart against the PFT's pass (1:10) and full marks (3:45).
 */
@Composable
internal fun PlankBench(tests: List<PlankTestDto>) {
    // Laid out like a lift's numbers (the Suitcase carry tiles): a title, then
    // two columns of tiles, no frame (v0.453).
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Plank test",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (tests.isEmpty()) {
            Text(
                "No plank logged yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val best = tests.maxBy { it.seconds }
        val last = tests.last()
        val prev = tests.getOrNull(tests.size - 2)
        val change = prev?.let { last.seconds - it.seconds }
        val toMax = (PFT_PLANK_MAX_SEC - best.seconds).coerceAtLeast(0)
        val sinceBest = dayOf(best.date)?.let { ChronoUnit.DAYS.between(it, LocalDate.now()).coerceAtLeast(0) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                key = "best",
                value = plankClock(best.seconds),
                unit = "",
                sub = "${points(best)} · ${shortDate(best.date)}",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                key = "last",
                value = plankClock(last.seconds),
                unit = "",
                sub = "${points(last)} · ${shortDate(last.date)}",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                key = "change",
                value = change?.let { (if (it >= 0) "+" else "−") + plankClock(kotlin.math.abs(it)) } ?: "—",
                unit = "",
                sub = if (change == null) "first test" else "since the test before",
                modifier = Modifier.weight(1f),
                up = change != null && change > 0,
            )
            StatTile(
                key = "to 100 pts",
                value = if (toMax == 0) "Done" else plankClock(toMax),
                unit = "",
                sub = if (toMax == 0) "3:45 reached" else "more than your best",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                key = "since best",
                value = when (sinceBest) {
                    null -> "—"
                    0L -> "today"
                    else -> "$sinceBest"
                },
                unit = if (sinceBest != null && sinceBest > 0L) "days" else "",
                sub = "last record",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                key = "tests",
                value = "${tests.size}",
                unit = "",
                sub = "since ${shortDate(tests.first().date)}",
                modifier = Modifier.weight(1f),
            )
        }
        if (tests.size > 1) PlankChart(tests = tests)
    }
}

/** Every test over time, with dashed lines at the pass and full marks. */
@Composable
private fun PlankChart(tests: List<PlankTestDto>) {
    val accent = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val days = tests.map { dayOf(it.date)?.toEpochDay() ?: 0L }
    val d0 = days.first()
    val d1 = days.last()
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val top = maxOf(PFT_PLANK_MAX_SEC, tests.maxOf { it.seconds }) * 1.05f
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val padY = 6.dp.toPx()
            val padL = 30.dp.toPx()
            val padR = 4.dp.toPx()
            val h = size.height - padY * 2
            val w = size.width - padL - padR
            fun x(d: Long): Float = if (d1 == d0) padL + w / 2 else padL + (d - d0).toFloat() / (d1 - d0) * w
            fun y(sec: Float): Float = padY + (1f - sec / top) * h
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            listOf(PFT_PLANK_MIN_SEC, PFT_PLANK_MAX_SEC).forEach { sec ->
                val gy = y(sec.toFloat())
                drawLine(
                    color = guide,
                    start = Offset(padL, gy),
                    end = Offset(size.width - padR, gy),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash,
                )
                val label = measurer.measure(plankClock(sec), TextStyle(fontSize = 10.sp))
                drawText(
                    label,
                    color = labelColor,
                    topLeft = Offset(padL - label.size.width - 4.dp.toPx(), gy - label.size.height / 2f),
                )
            }
            val path = Path()
            tests.forEachIndexed { i, t ->
                val px = x(days[i])
                val py = y(t.seconds.toFloat())
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path = path, color = accent, style = Stroke(width = 2.dp.toPx()))
            tests.forEachIndexed { i, t ->
                drawCircle(color = accent, radius = 3.dp.toPx(), center = Offset(x(days[i]), y(t.seconds.toFloat())))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 30.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(shortDate(tests.first().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(shortDate(tests.last().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
