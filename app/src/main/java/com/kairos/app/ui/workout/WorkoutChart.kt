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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.GraphPointDto
import com.kairos.app.data.remote.dto.PlanWeekdayDto
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
fun WorkoutChart(
    series: List<ProgressSeriesDto>,
    defaultId: String?,
    planDays: List<PlanWeekdayDto> = emptyList(),
) {
    if (series.isEmpty()) return
    // One block per muscle group, stacked and scrollable, rather than a picker
    // that showed one movement and hid the rest. A Core + Legs day reads as two
    // headed sections instead of a dropdown you have to discover.
    // Prefer what a plan asked for. The payload now carries every movement with
    // logged history, so a rotation or plan-less person has something to show at
    // all — but someone with a weekly plan should still see their planned lifts
    // and not every one-off they ever logged.
    val withData = series.filter { it.points.isNotEmpty() }
    val universe = if (withData.any { it.tracked }) withData.filter { it.tracked } else withData

    // Only regions you actually train are live. A Chest you have never pressed
    // is drawn, but inert — a target that selects nothing is a dead end.
    val available = remember(universe) { universe.mapNotNull { it.navRegion }.distinct() }

    // What the map opens on: everything today asks for. A day that trains Core
    // and Legs lights both, because that is what the day is.
    val todayDow = remember { LocalDate.now().dayOfWeek.value % 7 }
    val todayGroups = remember(planDays, todayDow) {
        planDays.firstOrNull { it.day == todayDow }?.groups.orEmpty().toSet()
    }
    val todayNavs = remember(universe, todayGroups) {
        universe.filter { sr -> sr.shadePrimary?.let { it in todayGroups } == true }
            .mapNotNull { it.navRegion }
            .distinct()
    }
    // Nothing planned today — fall back to the most recently trained region,
    // which is all a rotation or plan-less person has.
    val latestNav = remember(universe) {
        universe.filter { it.navRegion != null && it.points.isNotEmpty() }
            .maxByOrNull { it.points.last().date }
            ?.navRegion
            ?: available.firstOrNull()
    }

    // null means "today". Picking a region narrows to it; the button comes back.
    var picked by remember { mutableStateOf<String?>(null) }
    val openOn = if (todayNavs.isNotEmpty()) todayNavs else listOfNotNull(latestNav)
    val active = picked?.let { listOf(it) } ?: openOn
    val activeSet = active.toSet()

    val shown = universe.filter { sr -> sr.navRegion?.let { it in activeSet } == true }
    // Nothing on the body selects an ungrouped movement or a full-body lift, so
    // those keep their own blocks below rather than becoming unreachable.
    val orphans = universe.filter { it.navRegion == null }

    val surface = MaterialTheme.colorScheme.surface
    // Primary wins over secondary: a muscle one shown movement trains should
    // not be dimmed because another merely assists with it.
    // Per figure, because both are always drawn now. A movement's `view` decides
    // which figure it paints: a deadlift lights the back and leaves the front
    // grey, a squat lights both. That separates them on the body without ever
    // taking a figure away from the reader.
    val fillsFront = remember(shown, surface) { fillsFor(shown, surface, "front") }
    val fillsBack = remember(shown, surface) { fillsFor(shown, surface, "back") }

    fun blocksOf(items: List<ProgressSeriesDto>) = items
        // A movement with no muscle group is its own block, titled with its
        // own name. A deadlift is not a back lift or a leg lift, and filing it
        // as either charts a 300 lb hinge next to a lat pulldown. Ungrouped
        // movements sort alphabetically among the groups: peers, not leftovers.
        .groupBy { it.muscleGroup ?: "__mv:" + it.poolExerciseId }
        .map { (key, group) ->
            Triple(
                key,
                if (key.startsWith("__mv:")) group.first().name else muscleLabelFor(key),
                group.sortedBy { it.name.lowercase() },
            )
        }
        .sortedBy { it.second.lowercase() }

    val groups = blocksOf(shown) + blocksOf(orphans)

    Column(Modifier.fillMaxWidth()) {
        if (available.isNotEmpty()) {
            Box(Modifier.fillMaxWidth()) {
                BodyMap(
                    selected = active,
                    fillsFront = fillsFront,
                    fillsBack = fillsBack,
                    onSelect = { picked = it },
                    available = available,
                    inert = MaterialTheme.colorScheme.surfaceVariant,
                    ring = MaterialTheme.colorScheme.onSurface,
                )
                if (picked != null) {
                    // The app has no Material Icons dependency; KairosIcons is
                    // the app's own set, drawn from the same path data as the
                    // web's, so this is the same glyph on both. Reset rather
                    // than a calendar: the button undoes a selection, it does
                    // not open a date.
                    Icon(
                        KairosIcons.Refresh,
                        contentDescription = "Back to today's workout",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { picked = null }
                            .padding(8.dp),
                    )
                }
            }
        }
        groups.forEach { (key, label, items) ->
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = muscleColor(key) ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
            items.forEach { item ->
                LiftBlock(s = item)
            }
        }

        // Once, not once per movement. "Which lifts are moving" and "Workout
        // days" both describe the whole plan, so a reader with six tracked
        // lifts was getting six identical copies of each.
        // Every tracked movement, not the selected group: "Lift progress" and
        // "Workout days" are about the whole plan. Feeding them the filtered
        // selection made them shrink to whichever muscle was tapped.
        ProgressSummaryCards(
            series = universe,
            planDays = planDays,
        )
    }
}

/** Groups to paint on ONE figure: a movement contributes only where it shows. */
private fun fillsFor(
    shown: List<ProgressSeriesDto>,
    surface: androidx.compose.ui.graphics.Color,
    figure: String,
): Map<String, androidx.compose.ui.graphics.Color> {
    val on = shown.filter { it.view == "both" || it.view == figure }
    return buildMap {
        on.forEach { sr ->
            sr.shadeSecondary.forEach { g ->
                if (!containsKey(g)) fadedMuscleColor(g, surface)?.let { put(g, it) }
            }
        }
        // Primary wins over secondary: a muscle one movement trains is not
        // dimmed because another merely assists with it.
        on.forEach { sr ->
            val g = sr.shadePrimary
            if (g != null) muscleColor(g)?.let { put(g, it) }
        }
    }
}

private fun muscleLabelFor(raw: String): String =
    raw.split('_').joinToString(" ") { it.lowercase() }
        .replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiftBlock(
    s: ProgressSeriesDto,
) {
    val selectedId = s.poolExerciseId
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

    // The muscle group's colour, not the chart default: the dots should read as
    // the same thing the body map above just highlighted.
    val dotTone = muscleColor(s.muscleGroup) ?: LINE
    var tapped by remember(selectedId) { mutableStateOf<GraphPointDto?>(null) }
    var tappedOffset by remember(selectedId) { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxWidth()) {
        // Numbers first: the record, whether it is moving, how long since it
        // moved, and how many sessions. A single current value is a tile, not a
        // plot. The chart lives under "Show details" with everything else.
        val stats = remember(s.poolExerciseId, s.points) { liftStats(s) }
        var showDetails by remember { mutableStateOf(false) }
        val sorted = s.points.sortedBy { it.date }
        val firstDay = sorted.firstOrNull()
        val lastDay = sorted.lastOrNull()
        // Two equal columns, three rows. They were fixed-width in a wrapping
        // row before, which bunched them to the left with ragged gaps.
        Column(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                listOf<@Composable RowScope.() -> Unit>(
                    {
                        StatTile(
                            "record",
                            s.best?.let { fmt(it.value) } ?: "\u2014",
                            s.unit,
                            s.best?.let { longDate(it.date) } ?: "",
                            Modifier.weight(1f),
                        )
                    },
                    {
                        StatTile(
                            "reps",
                            s.best?.reps?.toString() ?: "\u2014",
                            if (s.best?.reps != null) "reps" else "",
                            if (s.best?.reps != null) "at the record" else "none logged yet",
                            Modifier.weight(1f),
                        )
                    },
                ),
                listOf<@Composable RowScope.() -> Unit>(
                    {
                        StatTile(
                            "30 days",
                            stats.delta?.let { (if (it > 0) "+" else "") + fmt(it) } ?: "\u2014",
                            if (stats.delta != null) s.unit else "",
                            when {
                                stats.delta == null -> "no older session"
                                stats.delta > 0 -> "still climbing"
                                else -> "flat"
                            },
                            Modifier.weight(1f),
                            up = (stats.delta ?: 0.0) > 0,
                        )
                    },
                    {
                        StatTile(
                            "since best",
                            stats.sincePR.removePrefix("best ").removeSuffix(" ago"),
                            "",
                            "last record",
                            Modifier.weight(1f),
                        )
                    },
                ),
                listOf<@Composable RowScope.() -> Unit>(
                    {
                        StatTile(
                            "sessions",
                            stats.sessions.toString(),
                            "",
                            firstDay?.let { "since " + longDate(it.date) } ?: "",
                            Modifier.weight(1f),
                        )
                    },
                    {
                        StatTile(
                            "last",
                            lastDay?.let { fmt(it.value) } ?: "\u2014",
                            lastDay?.let { s.unit + (it.reps?.let { r -> " \u00d7 " + r } ?: "") } ?: "",
                            lastDay?.let { longDate(it.date) } ?: "",
                            Modifier.weight(1f),
                        )
                    },
                ),
            ).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { row.forEach { it() } }
            }
        }

        Text(
            if (showDetails) "Hide charts" else "Additional charts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showDetails = !showDetails }
                .padding(vertical = 6.dp, horizontal = 2.dp),
        )

        if (showDetails) {
            Spacer(Modifier.height(12.dp))
            // Only when there is something in it. An empty-state card inside a
            // disclosure called "Additional charts" is a chart that isn't one.
            if (s.repMaxes.isNotEmpty()) {
                LiftRepMaxCard(selected = s)
                Spacer(Modifier.height(10.dp))
            }
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
                        // Points only, no connecting stroke: a line between two
                        // sessions draws a lift on days nobody trained.
                        pts.forEach { drawCircle(dotTone, radius = 5f, center = it) }
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

            // The rep-max pills that used to sit here said the same thing as
            // the "Best reps per weight" card directly above, detached from any
            // chart and from each other. One place is enough.

            // The whole-plan cards begin immediately below this, so an opened
            // disclosure needs a gap at its foot or the dot chart runs straight
            // into "Lift progress".
            Spacer(Modifier.height(22.dp))
        }
    }
}

private fun epochDay(iso: String): Long? = try {
    LocalDate.parse(iso).toEpochDay()
} catch (e: Exception) {
    null
}

/** "29 Sep" \u2014 a date a person reads, not 9/29. */
private fun longDate(iso: String): String = try {
    val d = LocalDate.parse(iso)
    d.dayOfMonth.toString() + " " + MONTHS[d.monthValue - 1]
} catch (e: Exception) {
    ""
}

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

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
    modifier: Modifier = Modifier,
    up: Boolean = false,
) {
    Column(
        modifier
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
        // Muted, matching the web. These are labels, not the content; painting
        // them in the theme accent made the whole screen one colour.
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
/** The one card that is about THIS movement: what it has actually lifted at
 *  each rep count. Everything else in the old detail panel described the
 *  whole plan, so it moved to ProgressSummaryCards and renders once.
 */
@Composable
private fun LiftRepMaxCard(
    selected: ProgressSeriesDto,
) {
    val bar = muscleColor(selected.muscleGroup) ?: MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    // What you can lift \u2014 real sets only, so a rep count never lifted is absent.
    if (selected.repMaxes.isEmpty()) {
        // A card that simply disappears reads as a feature that was never
        // built. Say why it is empty instead.
        CardTitle("Best reps per weight", "")
        Text(
            "Nothing yet \u2014 this fills in as you log reps beside the weight.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(10.dp))
    }
    if (selected.repMaxes.isNotEmpty()) {
        CardTitle("Best reps per weight", selected.name)
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

}

/** Cards that describe the WHOLE plan rather than one movement: which lifts
 *  are moving, and which days were trained. These used to live inside each
 *  movement’s "Show details", which meant a reader with six tracked lifts got
 *  six identical copies of the same two charts. They render once, at the end.
 */
@Composable
private fun ProgressSummaryCards(
    series: List<ProgressSeriesDto>,
    planDays: List<PlanWeekdayDto>,
) {
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Spacer(Modifier.height(6.dp))

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
        CardTitle("Lift progress", "Change over 90 days")
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

    // Workout days. One square per day, coloured by MUSCLE GROUP \u2014 the
    // question is which days were trained, not which bar was held. Only
    // weekdays the plan uses get a row: a row for a day nobody trains is noise,
    // and a rotation plan has no weekday shape to draw at all.
    val palette = listOf(
        Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
        Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF4A3AA7), Color(0xFFE34948),
    )
    // Keyed the same way the blocks are, so an ungrouped movement gets its own
    // colour and its own name in the legend rather than sharing an "Other".
    val keyOf = { sr: ProgressSeriesDto -> sr.muscleGroup ?: "__mv:" + sr.poolExerciseId }
    val gLabels = mutableMapOf<String, String>()
    series.filter { it.points.isNotEmpty() }.forEach { sr ->
        gLabels.getOrPut(keyOf(sr)) {
            if (sr.muscleGroup != null) muscleLabelFor(sr.muscleGroup!!) else sr.name
        }
    }
    val labelOf = { g: String -> gLabels[g] ?: muscleLabelFor(g) }
    val groupsSeen = series.filter { it.points.isNotEmpty() }
        .map { keyOf(it) }
        .distinct()
        .sortedBy { labelOf(it).lowercase() }
    // The muscle group's own colour, so a square means the same thing here as
    // it does on the body map. The positional palette is only for ungrouped
    // movements, which have no fixed colour of their own.
    val colourOf = groupsSeen.mapIndexed { i, g ->
        g to (muscleColor(g) ?: palette[i % palette.size])
    }.toMap()
    val byDate = mutableMapOf<String, MutableList<String>>()
    series.forEach { sr ->
        val g = keyOf(sr)
        sr.points.forEach { p ->
            byDate.getOrPut(p.date) { mutableListOf() }.let { if (!it.contains(g)) it.add(g) }
        }
    }

    val rows = if (planDays.isNotEmpty()) planDays.map { it.day }.distinct().sorted()
    else (0..6).toList()

    if (byDate.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        CardTitle("Workout days", "")
        val today = LocalDate.now()
        // Anchor to THIS week and count back, so the last column is the week
        // in progress. Going back 111 days and then snapping to Sunday moved
        // the window's start earlier without moving its end, so it finished at
        // today minus the weekday — on a Wednesday the current week was
        // missing and today never appeared at all.
        val start = today.minusDays(today.dayOfWeek.value.toLong() % 7).minusWeeks(15)
        val dow = listOf("S", "M", "T", "W", "T", "F", "S")
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.height(14.dp))
                rows.forEach { d ->
                    Text(
                        dow[d],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(12.dp).height(18.dp),
                    )
                }
            }
            (0 until 16).forEach { w ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val firstOfCol = start.plusDays((w * 7).toLong())
                    val prevMonth = if (w == 0) -1 else start.plusDays(((w - 1) * 7).toLong()).monthValue
                    Text(
                        if (firstOfCol.monthValue != prevMonth) MONTHS[firstOfCol.monthValue - 1] else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(14.dp),
                    )
                    rows.forEach { d ->
                        val day = start.plusDays((w * 7 + d).toLong()).toString()
                        val hits = byDate[day].orEmpty()
                        val cs = hits.mapNotNull { colourOf[it] }
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .then(
                                    when {
                                        // Two reads best as a diagonal split,
                                        // matching the web.
                                        cs.size == 2 -> Modifier.background(
                                            Brush.linearGradient(
                                                0f to cs[0],
                                                0.5f to cs[0],
                                                0.5f to cs[1],
                                                1f to cs[1],
                                                start = Offset.Zero,
                                                end = Offset.Infinite,
                                            ),
                                        )
                                        // Three and four cut from the centre —
                                        // thirds as wedges, four as quadrants —
                                        // which stays readable because every
                                        // piece meets in the middle.
                                        cs.size == 3 || cs.size == 4 -> Modifier.background(
                                            Brush.sweepGradient(
                                                *cs.flatMapIndexed { i, c ->
                                                    listOf(
                                                        (i.toFloat() / cs.size) to c,
                                                        ((i + 1).toFloat() / cs.size) to c,
                                                    )
                                                }.toTypedArray(),
                                            ),
                                        )
                                        // Five or more would be slivers from the
                                        // centre, so those become equal bands.
                                        cs.size > 4 -> Modifier.background(
                                            Brush.horizontalGradient(
                                                // Hard stops: each colour runs
                                                // its full band and the next
                                                // starts at the same offset, so
                                                // the bands do not blend.
                                                *cs.flatMapIndexed { i, c ->
                                                    listOf(
                                                        (i.toFloat() / cs.size) to c,
                                                        ((i + 1).toFloat() / cs.size) to c,
                                                    )
                                                }.toTypedArray(),
                                            ),
                                        )
                                        else -> Modifier.background(cs.firstOrNull() ?: track)
                                    },
                                ),
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
            groupsSeen.forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colourOf[g] ?: bar),
                    )
                    Text(
                        " " + labelOf(g),
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
