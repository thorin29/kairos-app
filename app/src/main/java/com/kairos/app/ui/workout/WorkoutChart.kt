package com.kairos.app.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.GraphPointDto
import com.kairos.app.data.remote.dto.ProgressSeriesDto
import com.kairos.app.ui.nav.KairosIcons
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

private val LINE = Color(0xFF0F766E)

/**
 * Weight-progress chart for one tracked movement at a time. Default = today's
 * tracked weights (or the next day that has some); the movement name below the
 * chart opens a list of your tracked movements to switch. Stepped y-scale
 * (nearest 10 lb / 5 kg). Tap a point to see its date + weight.
 */
@Composable
fun WorkoutChart(series: List<ProgressSeriesDto>, defaultId: String?) {
    if (series.isEmpty()) return

    var selectedId by remember(series, defaultId) {
        mutableStateOf(
            defaultId?.takeIf { id -> series.any { it.poolExerciseId == id } }
                ?: series.firstOrNull { it.points.isNotEmpty() }?.poolExerciseId
                ?: series.first().poolExerciseId,
        )
    }
    val s = series.firstOrNull { it.poolExerciseId == selectedId } ?: series.first()
    val points = s.points
    val kg = s.unit == "kg"
    val step = if (kg) 5.0 else 10.0

    val lo = points.minOfOrNull { it.value } ?: 0.0
    val hi = points.maxOfOrNull { it.value } ?: (step * 2)
    val yMin = floor(lo / step) * step
    var yMax = ceil(hi / step) * step
    if (yMax <= yMin) yMax = yMin + step * 2

    val xs = points.mapNotNull { epochDay(it.date) }
    val xMin = xs.minOrNull() ?: 0L
    val xMax = xs.maxOrNull() ?: 1L

    fun px(day: Long, w: Float): Float =
        if (xMax == xMin) w / 2f else ((day - xMin).toFloat() / (xMax - xMin).toFloat()) * w
    fun py(v: Double, h: Float): Float =
        if (yMax == yMin) h / 2f else (h - ((v - yMin) / (yMax - yMin)).toFloat() * h)

    var tapped by remember(selectedId) { mutableStateOf<GraphPointDto?>(null) }
    var tappedOffset by remember(selectedId) { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(160.dp)) {
            Column(
                Modifier.fillMaxHeight().width(40.dp).padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(fmt(yMax), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt((yMin + yMax) / 2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(yMin), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Box(Modifier.weight(1f).fillMaxWidth().height(160.dp)) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .pointerInput(points, selectedId) {
                            detectTapGestures { tap ->
                                var best: GraphPointDto? = null
                                var bestOffset = Offset.Zero
                                var bestD = Float.MAX_VALUE
                                for (p in points) {
                                    val d = epochDay(p.date) ?: continue
                                    val o = Offset(px(d, size.width.toFloat()), py(p.value, size.height.toFloat()))
                                    val dist = hypot(tap.x - o.x, tap.y - o.y)
                                    if (dist < bestD) { bestD = dist; best = p; bestOffset = o }
                                }
                                if (best != null && bestD < 60f) {
                                    tapped = best; tappedOffset = bestOffset
                                } else {
                                    tapped = null
                                }
                            }
                        },
                ) {
                    val w = size.width
                    val h = size.height
                    val grid = Color(0x11000000)
                    for (i in 0..4) {
                        val y = h * i / 4f
                        drawLine(grid, Offset(0f, y), Offset(w, y), 1f)
                    }
                    val pts = points.mapNotNull { p -> epochDay(p.date)?.let { Offset(px(it, w), py(p.value, h)) } }
                    for (i in 1 until pts.size) drawLine(LINE, pts[i - 1], pts[i], 3f)
                    pts.forEach { drawCircle(LINE, radius = 8f, center = it) }
                    tapped?.let { drawCircle(Color.White, radius = 4f, center = tappedOffset) }
                }

                tapped?.let { tp ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        modifier = Modifier.offset {
                            IntOffset(
                                (tappedOffset.x - 44.dp.toPx()).roundToInt().coerceAtLeast(0),
                                (tappedOffset.y - 40.dp.toPx()).roundToInt().coerceAtLeast(0),
                            )
                        },
                    ) {
                        Text(
                            "${dateLabel(epochDay(tp.date) ?: 0L)} · ${fmt(tp.value)} ${s.unit}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        if (xs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dateLabel(xMin), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (xMax != xMin) Text(dateLabel(xMax), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                "No weight logged yet.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp, top = 4.dp),
            )
        }

        // Movement selector — tap the name to switch (tracked movements only).
        Box(Modifier.padding(top = 8.dp)) {
            var open by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(4.dp))
                Icon(KairosIcons.ChevronDown, contentDescription = "Change movement", modifier = Modifier.width(16.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                series.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.name) },
                        onClick = {
                            selectedId = item.poolExerciseId
                            open = false
                        },
                    )
                }
            }
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
