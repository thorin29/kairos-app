package com.kairos.app.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.ProgressSeriesDto
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

private val PALETTE = listOf(
    Color(0xFF0F766E), Color(0xFF2563EB), Color(0xFFD97706), Color(0xFF7C3AED),
    Color(0xFFDC2626), Color(0xFF059669), Color(0xFF0891B2), Color(0xFFDB2777),
)

/** A weight-progress line chart (max weight per day per movement) with a legend
 *  to toggle movements — the app version of the web LineChart. */
@Composable
fun WorkoutChart(series: List<ProgressSeriesDto>) {
    if (series.isEmpty()) return
    val colorOf = remember(series) {
        series.mapIndexed { i, s -> s.poolExerciseId to PALETTE[i % PALETTE.size] }.toMap()
    }
    var hidden by remember(series) { mutableStateOf(setOf<String>()) }
    val visible = series.filter { it.poolExerciseId !in hidden }

    val allPoints = visible.flatMap { s -> s.points.map { it } }
    val xs = allPoints.mapNotNull { epochDay(it.date) }
    val ys = allPoints.map { it.value }
    val xMin = xs.minOrNull() ?: 0L
    val xMax = xs.maxOrNull() ?: 1L
    var yLo = ys.minOrNull() ?: 0.0
    var yHi = ys.maxOrNull() ?: 1.0
    val padY = ((yHi - yLo) * 0.1).coerceAtLeast(1.0)
    yLo -= padY
    yHi += padY
    val unit = visible.firstOrNull { it.points.isNotEmpty() }?.unit ?: series.firstOrNull()?.unit ?: ""

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(160.dp)) {
            // Y axis labels (max on top, min on bottom).
            Column(
                Modifier.width(40.dp).padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(fmt(yHi), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(yLo), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Canvas(Modifier.weight(1f).fillMaxWidth().height(160.dp)) {
                val w = size.width
                val h = size.height
                val gridColor = Color(0x11000000)
                drawLine(gridColor, Offset(0f, 0f), Offset(w, 0f), 1f)
                drawLine(gridColor, Offset(0f, h / 2), Offset(w, h / 2), 1f)
                drawLine(gridColor, Offset(0f, h), Offset(w, h), 1f)

                fun px(day: Long): Float =
                    if (xMax == xMin) w / 2f
                    else ((day - xMin).toFloat() / (xMax - xMin).toFloat()) * w
                fun py(v: Double): Float =
                    if (yHi == yLo) h / 2f
                    else (h - ((v - yLo) / (yHi - yLo)).toFloat() * h)

                visible.forEach { s ->
                    val c = colorOf[s.poolExerciseId] ?: PALETTE[0]
                    val pts = s.points.mapNotNull { p -> epochDay(p.date)?.let { d -> Offset(px(d), py(p.value)) } }
                    for (i in 1 until pts.size) {
                        drawLine(c, pts[i - 1], pts[i], 3f)
                    }
                    pts.forEach { drawCircle(c, radius = 4f, center = it) }
                }
            }
        }

        // X axis labels (first / last date).
        if (xs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dateLabel(xMin), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (xMax != xMin) {
                    Text(dateLabel(xMax), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Legend — tap a pill to toggle a movement.
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            series.forEach { s ->
                val on = s.poolExerciseId !in hidden
                val c = colorOf[s.poolExerciseId] ?: PALETTE[0]
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable {
                            hidden = if (on) hidden + s.poolExerciseId else hidden - s.poolExerciseId
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) c else MaterialTheme.colorScheme.outline))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        s.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (unit.isNotBlank()) {
            Text(
                "Max $unit per day",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun epochDay(iso: String): Long? = try {
    LocalDate.parse(iso).toEpochDay()
} catch (e: Exception) {
    null
}

private fun dateLabel(epochDay: Long): String = try {
    val d = LocalDate.ofEpochDay(epochDay)
    "${d.monthValue}/${d.dayOfMonth}"
} catch (e: Exception) {
    ""
}

private fun fmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
