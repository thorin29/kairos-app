package com.kairos.app.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
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
@OptIn(ExperimentalLayoutApi::class)
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
        // Numbers first: the record, whether it is moving, how long since it
        // moved, and how many sessions. A single current value is a tile, not a
        // plot. The chart lives under "Show details" with everything else.
        val stats = remember(s.poolExerciseId, s.points) { liftStats(s) }
        var showDetails by remember { mutableStateOf(false) }
        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            s.best?.let { b ->
                StatTile(
                    "record",
                    fmt(b.value),
                    s.unit + (b.reps?.let { " \u00d7 $it" } ?: ""),
                    dateLabel(epochDay(b.date) ?: 0L),
                )
            }
            StatTile(
                "30 days",
                stats.delta?.let { (if (it > 0) "+" else "") + fmt(it) } ?: "\u2014",
                if (stats.delta != null) s.unit else "",
                when {
                    stats.delta == null -> "no older session"
                    stats.delta > 0 -> "still climbing"
                    else -> "flat"
                },
                up = (stats.delta ?: 0.0) > 0,
            )
            StatTile("since best", stats.sincePR.removePrefix("best ").removeSuffix(" ago"), "", "last record")
            StatTile("sessions", stats.sessions.toString(), "", "logged")
        }

        Text(
            if (showDetails) "Hide details" else "Show details",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showDetails = !showDetails }
                .padding(vertical = 6.dp, horizontal = 2.dp),
        )

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
        if (showDetails) {
            LiftDetailCards(series = series, selected = s)
            Spacer(Modifier.height(10.dp))
            Text(
                "EVERY SESSION",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
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
                        val gridSolid = Color(0x33000000)
                        val gridDash = Color(0x1F000000)
                        val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        // Solid lines at the quarter marks, dashed lines halfway between,
                        // so it's easier to read where a point lands on the scale.
                        for (i in 0..4) {
                            val y = h * i / 4f
                            drawLine(gridSolid, Offset(0f, y), Offset(w, y), 1.5f)
                        }
                        for (i in 0 until 4) {
                            val y = h * (i + 0.5f) / 4f
                            drawLine(gridDash, Offset(0f, y), Offset(w, y), 1f, pathEffect = dash)
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

            // Best weight actually lifted at each rep count. Only rep counts that
            // have been logged appear, so this fills in as reps get recorded rather
            // than showing a grid of blanks.
            if (s.repMaxes.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    s.repMaxes.forEach { r ->
                        Text(
                            "${r.reps}r \u00b7 ${fmt(r.value)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
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

/** What a lifter checks between PRs: is it moving, when did it last move, and
 *  what have the last few sessions looked like. All from real logged sets. */
/** One headline number with its unit and a line of context under it. */
@Composable
private fun StatTile(
    key: String,
    value: String,
    unit: String,
    sub: String,
    up: Boolean = false,
) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            key.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (up) Color(0xFF047857) else MaterialTheme.colorScheme.onSurface,
            )
            if (unit.isNotBlank()) {
                Text(
                    " " + unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        if (sub.isNotBlank()) {
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A heading for one of the detail cards. */
@Composable
private fun CardTitle(title: String, sub: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    if (sub.isNotBlank()) {
        Text(
            sub,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The three views behind the numbers: what you can lift at each rep count, which
 * lifts have moved in 90 days, and which days carried a logged set. Everything
 * comes from logged sets \u2014 no estimated maxes \u2014 and the only cross-lift view
 * is a percentage, because a deadlift and an overhead press share no scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiftDetailCards(series: List<ProgressSeriesDto>, selected: ProgressSeriesDto) {
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    // What you can lift \u2014 real sets only, so a rep count never lifted is absent.
    if (selected.repMaxes.isNotEmpty()) {
        CardTitle("What you can lift", selected.name)
        val max = selected.repMaxes.maxOf { it.value }.coerceAtLeast(1.0)
        selected.repMaxes.sortedBy { it.reps }.forEach { r ->
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${r.reps} rep" + if (r.reps == 1) "" else "s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(track),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((r.value / max).toFloat())
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(bar),
                    )
                }
                Text(
                    " " + fmt(r.value) + selected.unit,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(72.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    // Which lifts are moving. Percent is the one honest shared axis; the real
    // weights stay in the row so a small gain on a light lift cannot pass for
    // a big one. The scale is fixed so a single lift does not fill the track.
    val moving = series.mapNotNull { sr ->
        val cutoff = LocalDate.now().minusDays(90).toString()
        val window = sr.points.filter { it.date >= cutoff }
        if (window.size < 2) return@mapNotNull null
        val from = window.first().value
        val to = window.maxOf { it.value }
        if (from <= 0.0) return@mapNotNull null
        Triple(sr, ((to - from) / from * 100).toInt(), from to to)
    }.sortedByDescending { it.second }

    if (moving.isNotEmpty()) {
        CardTitle("Which lifts are moving", "Change over 90 days")
        val span = maxOf(30, moving.maxOf { kotlin.math.abs(it.second) })
        moving.forEach { (sr, pct, range) ->
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    sr.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(track),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((kotlin.math.abs(pct).toFloat() / span))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (pct < 10) Color(0xFFC4541F) else bar),
                    )
                }
                Text(
                    (if (pct > 0) " +" else " ") + pct + "%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(52.dp),
                )
                Text(
                    fmt(range.first) + "\u2192" + fmt(range.second),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    // Did you show up. One square per day over 16 weeks, coloured by the
    // movement logged, so every workout reads on one grid. A rested or
    // untracked day leaves no mark, which is why it says "logged".
    val palette = listOf(
        Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
        Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF4A3AA7), Color(0xFFE34948),
    )
    val logged = series.filter { it.points.isNotEmpty() }.take(8)
    val colourOf = logged.mapIndexed { i, sr -> sr.poolExerciseId to palette[i % palette.size] }.toMap()
    val byDate = mutableMapOf<String, MutableList<Color>>()
    logged.forEach { sr ->
        val c = colourOf[sr.poolExerciseId] ?: bar
        sr.points.forEach { p -> byDate.getOrPut(p.date) { mutableListOf() }.let { if (!it.contains(c)) it.add(c) } }
    }
    if (byDate.isNotEmpty()) {
        CardTitle("Did you show up", "One square per day, coloured by movement")
        val today = LocalDate.now()
        val start = today.minusDays(111).let { it.minusDays(((it.dayOfWeek.value + 6) % 7).toLong()) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("M", "", "W", "", "F", "", "").forEach { d ->
                    Text(
                        d,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(10.dp).height(13.dp),
                    )
                }
            }
            (0 until 16).forEach { w ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    (0 until 7).forEach { d ->
                        val day = start.plusDays((w * 7 + d).toLong()).toString()
                        val hits = byDate[day]
                        Box(
                            Modifier
                                .size(13.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(hits?.firstOrNull() ?: track),
                        )
                    }
                }
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            logged.forEach { sr ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colourOf[sr.poolExerciseId] ?: bar),
                    )
                    Text(
                        " " + sr.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class LiftStats(
    val delta: Double?,
    val sincePR: String,
    val sessions: Int,
    val recent: List<GraphPointDto>,
)

private fun liftStats(s: ProgressSeriesDto): LiftStats {
    val pts = s.points.sortedBy { it.date }
    val recent = pts.takeLast(5)

    // 30-day change: the latest top set against the best on or before the
    // cutoff. Null when nothing is that old — a first session is not a gain.
    val cutoff = LocalDate.now().minusDays(30).toString()
    val before = pts.filter { it.date <= cutoff }
    val latest = pts.lastOrNull()?.value
    val base = before.maxOfOrNull { it.value }
    val delta = if (latest != null && base != null) latest - base else null

    val sincePR = s.best?.let { b ->
        val days = epochDay(b.date)?.let { LocalDate.now().toEpochDay() - it } ?: 0L
        when {
            days <= 1L -> "best today"
            days < 7L -> "best $days days ago"
            else -> "best ${days / 7}w ago"
        }
    } ?: "no best yet"

    // Named arguments: a field added to LiftStats must not silently reassign
    // these (that exact mistake broke two builds).
    return LiftStats(delta = delta, sincePR = sincePR, sessions = pts.size, recent = recent)
}
