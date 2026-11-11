package com.kairos.app.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.launch
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
    heat: com.kairos.app.data.remote.dto.HeatMapDto? = null,
) {
    if (series.isEmpty()) return
    // The heat view (v0.442): a flame in the map's corner swaps the
    // primary/secondary view for hard sets a week. Kept on this phone.
    val settings = com.kairos.app.ui.common.rememberContainer().settingsStore
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val heatPref by settings.heatOn.collectAsState(initial = false)
    val heatOn = heat != null && heatPref
    var heatPicked by remember { mutableStateOf<String?>(null) }
    // A lift's page (v0.480), opened from its row, with its group's name.
    var openLift by remember { mutableStateOf<Pair<ProgressSeriesDto, String>?>(null) }
    var openCore by remember { mutableStateOf<com.kairos.app.data.remote.dto.CoreProgressMoveDto?>(null) }
    var openPlank by remember { mutableStateOf(false) }
    var openCoreAll by remember { mutableStateOf(false) }
    var openCardio by remember { mutableStateOf(false) }
    var openConditioning by remember { mutableStateOf(false) }
    // One block per muscle group, stacked and scrollable, rather than a picker
    // that showed one movement and hid the rest. A Core + Legs day reads as two
    // headed sections instead of a dropdown you have to discover.
    // Everything you've ever logged a weight for, whatever the plan or program
    // is now (v0.441). Narrowing to the plan's movements made a muscle you no
    // longer program (Arms) vanish from the body map, the records and the
    // Workout days grid, though its history was all there.
    val universe = series.filter { it.points.isNotEmpty() }

    // Only regions you actually train are live. A Chest you have never pressed
    // is drawn, but inert — a target that selects nothing is a dead end.
    // What the map opens on: everything today asks for, else the next planned
    // day's (v0.445) — what you're about to train, not what you did yesterday.
    // A day that trains Core and Legs lights both, because that is what the day is.
    val todayDow = remember { LocalDate.now().dayOfWeek.value % 7 }
    val focusOrder = remember(planDays, todayDow) { focusGroups(planDays = planDays, todayDow = todayDow) }
    val todayGroups = focusOrder.toSet()

    // Muscles with no weight charts — a core of planks and crunches — still get
    // a place on the map when planned or trained, and show their hard sets and
    // bests instead of charts (v0.445).
    val heat12 = heat?.ranges?.firstOrNull { it.key == "12w" }
    val summaryGroups = remember(universe, heat12, todayGroups) {
        val charted = universe.mapNotNull { it.shadePrimary }.toSet()
        val trained = heat12?.groups.orEmpty().filter { it.perWeek > 0.0 || it.bests.isNotEmpty() }.map { it.group }.toSet()
        ON_BODY.filter { g -> g !in charted && (g in trained || g in todayGroups) }
    }
    val navOfGroup = { g: String -> if (g == "DEADLIFT") "POSTERIOR_HIP" else g }
    val available = remember(universe, summaryGroups) {
        (universe.mapNotNull { it.navRegion } + summaryGroups.map(navOfGroup)).distinct()
    }
    val todayNavs = remember(universe, todayGroups, summaryGroups) {
        (
            universe.filter { sr -> sr.shadePrimary?.let { it in todayGroups } == true }.mapNotNull { it.navRegion } +
                summaryGroups.filter { it in todayGroups }.map(navOfGroup)
            ).distinct()
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

    val figure = figureColors(com.kairos.app.ui.theme.KairosThemeState.dark)
    val surface = figure.fade
    // Primary wins over secondary: a muscle one shown movement trains should
    // not be dimmed because another merely assists with it.
    // Per figure, because both are always drawn now. A movement's `view` decides
    // which figure it paints: a deadlift lights the back and leaves the front
    // grey, a squat lights both. That separates them on the body without ever
    // taking a figure away from the reader.
    val summaries = summaryGroups.filter { navOfGroup(it) in activeSet }
    val summaryFills = summaries.mapNotNull { g -> muscleColor(g)?.let { g to it } }.toMap()
    val fillsFront = remember(shown, surface, summaryFills) { fillsFor(shown, surface, "front") + summaryFills }
    val fillsBack = remember(shown, surface, summaryFills) { fillsFor(shown, surface, "back") + summaryFills }

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

    // Today's groups first, in the plan's order (a program's main lift leads),
    // then the rest A–Z (v0.429).
    val todayOrder = focusOrder
    fun rank(key: String): Int = todayOrder.indexOf(key).let { if (it < 0) todayOrder.size else it }
    val groups = blocksOf(shown).sortedBy { rank(it.first) } + blocksOf(orphans)

    Column(Modifier.fillMaxWidth()) {
        if (available.isNotEmpty()) {
            Box(Modifier.fillMaxWidth()) {
                if (heatOn) {
                    // The training heat map (v0.474): one picture, no ranges.
                    val training = heat?.training
                    val shade = trainFills(training)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // The same size as the muscle map (v0.482): the heart sits
                        // between the two heads, so nothing moves on switching.
                        Box(Modifier.fillMaxWidth()) {
                            BodyMap(
                                selected = emptyList(),
                                fillsFront = shade,
                                fillsBack = shade,
                                onSelect = { heatPicked = it },
                                available = trainAvailable(training),
                                inert = figure.body,
                                ring = figure.ring,
                            )
                            Box(Modifier.align(Alignment.TopCenter)) {
                                EnduranceHeart(data = training?.endurance, neutral = figure.body, onOpen = { heatPicked = "ENDURANCE" })
                            }
                        }
                        TrainKey(neutral = figure.body)
                    }
                } else {
                    BodyMap(
                        selected = active,
                        fillsFront = fillsFront,
                        fillsBack = fillsBack,
                        onSelect = { picked = it },
                        available = available,
                        inert = figure.body,
                        ring = figure.ring,
                    )
                }
                if (heat != null) {
                    HeatToggle(
                        on = heatOn,
                        onClick = { scope.launch { settings.setHeatOn(!heatOn) } },
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
                if (!heatOn && picked != null) {
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
        heatPicked?.let { nav ->
            val t = heat?.training
            val openEnduranceRow: (String) -> Unit = { row ->
                if (row == "Cardio" && heat?.cardioCharts != null) {
                    heatPicked = null
                    openCardio = true
                } else if (row == "Conditioning" && heat?.conditioningCharts != null) {
                    heatPicked = null
                    openConditioning = true
                }
            }
            TrainDetailDialog(
                title = trainTitle(nav),
                data = if (nav == "ENDURANCE") t?.endurance else t?.groups?.firstOrNull { it.group == heatGroupOf(nav) },
                neutral = figure.body,
                onDismiss = { heatPicked = null },
                onOpenRow = if (nav == "ENDURANCE") openEnduranceRow else null,
            )
        }
        summaries.sortedBy { rank(it) }.forEach { g ->
            Text(
                muscleLabelFor(g),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = muscleColor(g) ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
            val coreSummary = heat?.coreSummary
            if (g == "CORE" && coreSummary != null) {
                CoreCard(summary = coreSummary, unit = heat?.unit.orEmpty(), onOpen = { openCoreAll = true })
            } else {
                if (g == "CORE") PlankBench(tests = heat?.plank.orEmpty())
                HeatGroupSummary(data = heat12?.groups?.firstOrNull { it.group == g }, unit = heat?.unit.orEmpty())
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
            val coreSummary = heat?.coreSummary
            if (key == "CORE" && coreSummary == null) PlankBench(tests = heat?.plank.orEmpty())
            // Core in one card (v0.483), opening the Core page.
            if (key == "CORE" && coreSummary != null) {
                CoreCard(summary = coreSummary, unit = heat?.unit.orEmpty(), onOpen = { openCoreAll = true })
            } else
            // One row per lift (v0.480); a row opens its page.
            androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                items.forEachIndexed { i, item ->
                    if (i > 0) androidx.compose.material3.HorizontalDivider()
                    LiftRow(
                        s = item,
                        tone = muscleColor(key) ?: muscleColor(item.muscleGroup) ?: LINE,
                        onOpen = { openLift = item to label },
                    )
                }
            }
        }

        // Cardio and conditioning (v0.484), after the muscle groups.
        heat?.cardioCharts?.let { c ->
            Text("Cardio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = CARDIO_COLOR, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            CardioChartCard(data = c, onOpen = { openCardio = true })
        }
        heat?.conditioningCharts?.let { c ->
            Text("Conditioning", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = CONDITIONING_COLOR, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            ConditioningChartCard(data = c, onOpen = { openConditioning = true })
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
            extraDays = heat?.days.orEmpty(),
        )
    }
    val cardioCharts = heat?.cardioCharts
    if (openCardio && cardioCharts != null) CardioPageDialog(data = cardioCharts, onDismiss = { openCardio = false })
    val conditioningCharts = heat?.conditioningCharts
    if (openConditioning && conditioningCharts != null) ConditioningPageDialog(data = conditioningCharts, onDismiss = { openConditioning = false })
    val overview = heat?.coreSummary
    if (openCoreAll && overview != null) {
        CoreOverviewDialog(
            summary = overview,
            moves = heat?.coreMoves.orEmpty(),
            unit = heat?.unit.orEmpty(),
            onOpenMove = { openCore = it },
            onOpenPlank = { openPlank = true },
            onDismiss = { openCoreAll = false },
        )
    }
    if (openPlank) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { openPlank = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.IconButton(onClick = { openPlank = false }) {
                            Icon(KairosIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text("Plank test", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        PlankBench(tests = heat?.plank.orEmpty())
                    }
                }
            }
        }
    }
    openCore?.let { m ->
        val group = heat?.coreSummary?.groups?.firstOrNull { g -> g.moves.any { it.movement == m.movement } }?.label ?: "Core"
        CorePageDialog(m = m, group = group, unit = heat?.unit.orEmpty(), marks = heat?.marks.orEmpty(), onDismiss = { openCore = null })
    }
    openLift?.let { (lift, label) ->
        LiftPageDialog(s = lift, groupLabel = label, marks = heat?.marks.orEmpty(), onDismiss = { openLift = null })
    }
}

/** Groups with a place on the body (Deadlift is the lower back and glutes). */
private val ON_BODY = listOf("CHEST", "BACK", "SHOULDERS", "ARMS", "LEGS", "CORE", "DEADLIFT")

/** Today's muscle groups, else the next planned day's (v0.445). */
internal fun focusGroups(planDays: List<PlanWeekdayDto>, todayDow: Int): List<String> {
    for (ahead in 0 until 7) {
        val d = (todayDow + ahead) % 7
        val groups = planDays.firstOrNull { it.day == d }?.groups.orEmpty()
        if (groups.isNotEmpty()) return groups
    }
    return emptyList()
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
        // dimmed because another merely assists with it. Lower-back moves
        // paint the deadlift's place in their own colour; a real deadlift
        // shown with them wins (v0.482).
        on.sortedBy { if (it.muscleGroup == "LOWER_BACK") 0 else 1 }.forEach { sr ->
            val g = sr.shadePrimary
            val tone = if (sr.muscleGroup == "LOWER_BACK") muscleColor("LOWER_BACK") else g?.let { muscleColor(it) }
            if (g != null && tone != null) put(g, tone)
        }
    }
}

private fun muscleLabelFor(raw: String): String =
    raw.split('_').joinToString(" ") { it.lowercase() }
        .replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalLayoutApi::class)
/** A rep-range filter pill: filled when chosen. */
@Composable
private fun BandChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A lift as one row in its muscle group's card (v0.480; replaced six tiles):
 *  best and last set, a small line of the top set per session, and the
 *  30-day change or how long it has been flat. The row opens its page. */
@Composable
private fun LiftRow(s: ProgressSeriesDto, tone: Color, onOpen: () -> Unit) {
    val sorted = s.points.sortedBy { it.date }
    val last = sorted.lastOrNull()
    val trend = liftTrend(s)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onOpen() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                s.name.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (s.best?.let { "Best " + setText(it.value, it.reps) } ?: "No weight yet") +
                    (last?.let { " \u00b7 last " + setText(it.value, it.reps) } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val spark = sorted.takeLast(12)
        // Nothing to draw takes no room (v0.482), so the name can use it.
        if (spark.size >= 2) Canvas(Modifier.width(84.dp).height(30.dp)) {
            run {
                val lo = spark.minOf { it.value }
                val hi = spark.maxOf { it.value }
                val pts = spark.mapIndexed { i, p ->
                    Offset(
                        4.dp.toPx() + i * (size.width - 8.dp.toPx()) / (spark.size - 1),
                        size.height - 4.dp.toPx() - ((p.value - lo) / maxOf(1.0, hi - lo)).toFloat() * (size.height - 8.dp.toPx()),
                    )
                }
                for (k in 1 until pts.size) drawLine(tone, pts[k - 1], pts[k], strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawCircle(tone, radius = 2.5.dp.toPx(), center = pts.last())
            }
        }
        if (trend != null) Column(horizontalAlignment = Alignment.End) {
            when (trend) {
                is LiftTrend.Up -> {
                    Text("+" + fmt(trend.delta) + " " + s.unit, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF047857))
                    Text("30 days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is LiftTrend.Flat -> {
                    Text("Flat", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFFB45309))
                    Text("${trend.weeks} " + if (trend.weeks == 1) "week" else "weeks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

private fun setText(v: Double, reps: Int?): String = fmt(v) + (reps?.takeIf { it > 0 }?.let { " \u00d7 $it" } ?: "")

private sealed interface LiftTrend {
    data class Up(val delta: Double) : LiftTrend
    data class Flat(val weeks: Int) : LiftTrend
}

/** The web's liftTrend: climbing over 30 days, else whole weeks since the best. */
private fun liftTrend(s: ProgressSeriesDto): LiftTrend? {
    val pts = s.points.sortedBy { it.date }
    if (pts.isEmpty()) return null
    val cutoff = LocalDate.now().minusDays(30).toString()
    val before = pts.filter { it.date <= cutoff }
    if (before.isNotEmpty()) {
        val delta = (pts.last().value - before.maxOf { it.value }).roundToInt().toDouble()
        if (delta > 0) return LiftTrend.Up(delta = delta)
    }
    val best = s.best ?: return null
    val weeks = ((LocalDate.now().toEpochDay() - (epochDay(best.date) ?: LocalDate.now().toEpochDay())) / 7).toInt()
    if (before.isEmpty() && weeks < 1) return null
    return LiftTrend.Flat(weeks = weeks.coerceAtLeast(0))
}

/** The web's estimatedMax: Epley from the best set of 12 reps or fewer, down to a plate step. */
private fun estimatedMax(s: ProgressSeriesDto): Double? {
    var best = 0.0
    s.repMaxes.forEach { r ->
        if (r.reps in 1..12 && r.value > 0) {
            val e = if (r.reps == 1) r.value else r.value * (1 + r.reps / 30.0)
            if (e > best) best = e
        }
    }
    if (best <= 0) return null
    val step = if (s.unit == "kg") 2.5 else 5.0
    return floor(best / step) * step
}

/** Monday of a date's week. */
private fun mondayOf(iso: String): Long? = epochDay(iso)?.let { d ->
    val dow = LocalDate.ofEpochDay(d).dayOfWeek.value // 1 = Monday
    d - (dow - 1)
}

/**
 * One lift's page (v0.480): every session's top set, with light weeks shaded,
 * changed days (sick, hurt…) marked, the record circled and the next planned
 * top set ahead; then its numbers (an estimated max among them), the best
 * weight at each rep count and the recent sessions. A full screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiftPageDialog(
    s: ProgressSeriesDto,
    groupLabel: String,
    marks: List<com.kairos.app.data.remote.dto.HeatMarkDto>,
    onDismiss: () -> Unit,
) {
    val session = com.kairos.app.ui.common.rememberContainer().sessionRepository
    var next by remember(s.poolExerciseId) { mutableStateOf<com.kairos.app.data.remote.dto.LiftNextDto?>(null) }
    androidx.compose.runtime.LaunchedEffect(s.poolExerciseId) {
        next = runCatching { session.liftNext(movement = s.poolExerciseId) }.getOrNull()
    }
    var band by remember(s.poolExerciseId) { mutableStateOf("all") }
    val all = s.points.sortedBy { it.date }
    val points = if (band == "all") all else s.bands.firstOrNull { it.key == band }?.points ?: emptyList()
    val ahead = next?.takeIf { band == "all" }
    val tone = muscleColor(s.muscleGroup) ?: LINE
    val kg = s.unit == "kg"
    val step = if (kg) 5.0 else 10.0
    val values = points.map { it.value } + listOfNotNull(ahead?.weight)
    val lo = values.minOrNull() ?: 0.0
    val hi = values.maxOrNull() ?: (step * 2)
    val yMin = floor(lo / step) * step
    var yMax = ceil(hi / step) * step
    if (yMax <= yMin) yMax = yMin + step * 2
    val xs = points.mapNotNull { epochDay(it.date) } + listOfNotNull(ahead?.date?.let { epochDay(it) })
    val xMin = xs.minOrNull() ?: 0L
    val xMax = xs.maxOrNull() ?: 1L
    fun px(day: Long, w: Float): Float =
        if (xMax == xMin) w / 2f else ((day - xMin).toFloat() / (xMax - xMin).toFloat()) * w
    fun py(v: Double, h: Float): Float =
        if (yMax == yMin) h / 2f else (h - ((v - yMin) / (yMax - yMin)).toFloat() * h)
    val shade = all.filter { it.intent == "LIGHT" }.mapNotNull { mondayOf(it.date) }.distinct()
    val firstISO = all.firstOrNull()?.date ?: ""
    val today = LocalDate.now().toString()
    val shownMarks = marks.filter { it.date >= firstISO && it.date <= today }
    var tapped by remember(s.poolExerciseId) { mutableStateOf<GraphPointDto?>(null) }
    var tappedOffset by remember(s.poolExerciseId) { mutableStateOf(Offset.Zero) }
    val markColor = Color(0xFFB45309)
    val shadeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val surface = MaterialTheme.colorScheme.surface
    val trend = liftTrend(s)
    val est = estimatedMax(s)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Column {
                        Text(
                            s.name.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(groupLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tone)
                    }
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (s.bands.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf("all" to "All") + s.bands.map { it.key to it.label }).forEach { (key, label) ->
                                BandChip(label = label, selected = band == key) {
                                    band = key
                                    tapped = null
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(200.dp)) {
                        Column(
                            Modifier.fillMaxHeight().width(40.dp).padding(end = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(fmt(yMax), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fmt((yMin + yMax) / 2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fmt(yMin), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            Canvas(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .pointerInput(points, band) {
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
                                // Light weeks first, behind everything.
                                shade.forEach { mon ->
                                    val a = px(mon, w) - 4f
                                    val b = px(mon + 7, w) - 4f
                                    if (b > 0f && a < w) {
                                        drawRect(shadeColor, topLeft = Offset(a.coerceAtLeast(0f), 0f), size = androidx.compose.ui.geometry.Size((b.coerceAtMost(w) - a.coerceAtLeast(0f)).coerceAtLeast(6f), h))
                                    }
                                }
                                val gridSolid = Color(0x33000000)
                                val gridDash = Color(0x1F000000)
                                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                                for (i in 0..4) {
                                    val y = h * i / 4f
                                    drawLine(gridSolid, Offset(0f, y), Offset(w, y), 1.5f)
                                }
                                for (i in 0 until 4) {
                                    val y = h * (i + 0.5f) / 4f
                                    drawLine(gridDash, Offset(0f, y), Offset(w, y), 1f, pathEffect = dash)
                                }
                                // Changed days: a short amber tick at the foot.
                                shownMarks.forEach { m ->
                                    val d = epochDay(m.date) ?: return@forEach
                                    if (d in xMin..xMax) {
                                        val x = px(d, w)
                                        drawLine(markColor, Offset(x, h - 10f), Offset(x, h), strokeWidth = 4f)
                                    }
                                }
                                points.forEach { p ->
                                    val d = epochDay(p.date) ?: return@forEach
                                    val o = Offset(px(d, w), py(p.value, h))
                                    if (p.intent != null) {
                                        drawCircle(tone, radius = 5f, center = o, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
                                    } else {
                                        drawCircle(tone, radius = 5f, center = o)
                                    }
                                }
                                s.best?.takeIf { band == "all" }?.let { b ->
                                    epochDay(b.date)?.let { d ->
                                        drawCircle(tone, radius = 14f, center = Offset(px(d, w), py(b.value, h)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
                                    }
                                }
                                ahead?.let { n ->
                                    epochDay(n.date)?.let { d ->
                                        val o = Offset(px(d, w), py(n.weight, h))
                                        drawCircle(surface, radius = 7f, center = o)
                                        drawCircle(
                                            tone,
                                            radius = 7f,
                                            center = o,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 5f))),
                                        )
                                    }
                                }
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
                                        "${dateLabel(epochDay(tp.date) ?: 0L)} \u00b7 ${setText(tp.value, tp.reps)} ${s.unit}" +
                                            when (tp.intent) {
                                                "LIGHT" -> " \u00b7 light day"
                                                "TEST" -> " \u00b7 test day"
                                                else -> ""
                                            } +
                                            (shownMarks.firstOrNull { it.date == tp.date }?.let { " \u00b7 " + it.label.lowercase() } ?: ""),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (xs.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dateLabel(xMin), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (xMax != xMin) Text(dateLabel(xMax), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // What the marks are, in words.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (shade.isNotEmpty()) ChartKey(color = shadeColor, label = "Light week", square = true)
                        if (shownMarks.isNotEmpty()) ChartKey(color = markColor, label = shownMarks.map { it.label }.distinct().joinToString(", "), square = true)
                        if (ahead != null) {
                            Text(
                                "Next: " + dayShort(ahead.date) + " " + setText(ahead.weight, ahead.reps),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val sorted = all
                        val first = sorted.firstOrNull()
                        val last = sorted.lastOrNull()
                        val weeksSince = s.best?.let { b -> epochDay(b.date)?.let { ((LocalDate.now().toEpochDay() - it) / 7).toInt() } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("record", s.best?.let { setText(it.value, it.reps) } ?: "\u2014", "", s.best?.let { longDate(it.date) } ?: "none yet", Modifier.weight(1f))
                            StatTile("est. max", est?.let { fmt(it) } ?: "\u2014", if (est != null) s.unit else "", if (est != null) "one rep, estimated" else "needs a set of 12 or fewer", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(
                                "30 days",
                                when (trend) { is LiftTrend.Up -> "+" + fmt(trend.delta); is LiftTrend.Flat -> "Flat"; null -> "\u2014" },
                                if (trend is LiftTrend.Up) s.unit else "",
                                when (trend) { is LiftTrend.Up -> "still climbing"; is LiftTrend.Flat -> "no gain"; null -> "no older session" },
                                Modifier.weight(1f),
                                up = trend is LiftTrend.Up,
                            )
                            StatTile(
                                "since best",
                                when { weeksSince == null -> "\u2014"; weeksSince < 1 -> "This week"; else -> "$weeksSince " + if (weeksSince == 1) "week" else "weeks" },
                                "",
                                s.best?.let { longDate(it.date) } ?: "",
                                Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("sessions", sorted.size.toString(), "", first?.let { "since " + longDate(it.date) } ?: "", Modifier.weight(1f))
                            StatTile("last", last?.let { setText(it.value, it.reps) } ?: "\u2014", "", last?.let { longDate(it.date) } ?: "", Modifier.weight(1f))
                        }
                    }
                    if (s.repMaxes.isNotEmpty()) LiftRepMaxCard(selected = s)
                    if (all.isNotEmpty()) {
                        CardTitle("Recent sessions", "")
                        all.takeLast(5).reversed().forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(longDate(p.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    setText(p.value, p.reps) + " " + s.unit + when (p.intent) { "LIGHT" -> " \u00b7 light"; "TEST" -> " \u00b7 test"; else -> "" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ChartKey(color: Color, label: String, square: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(if (square) RoundedCornerShape(2.dp) else CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun dayShort(iso: String): String = try {
    LocalDate.parse(iso).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)
} catch (e: Exception) {
    iso
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
internal fun StatTile(
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
    /** Days a group was trained without weights (a core day of planks), v0.443. */
    extraDays: List<com.kairos.app.data.remote.dto.HeatDayDto> = emptyList(),
) {
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Spacer(Modifier.height(6.dp))


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
    extraDays.forEach { d -> d.groups.forEach { g -> gLabels.getOrPut(g) { muscleLabelFor(g) } } }
    val labelOf = { g: String -> gLabels[g] ?: muscleLabelFor(g) }
    val groupsSeen = (series.filter { it.points.isNotEmpty() }.map { keyOf(it) } + extraDays.flatMap { it.groups })
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
    extraDays.forEach { d ->
        val list = byDate.getOrPut(d.date) { mutableListOf() }
        d.groups.forEach { g -> if (!list.contains(g)) list.add(g) }
    }

    // A row for each weekday the plan uses AND each weekday something was
    // logged on in the last 16 weeks (v0.427), so switching to a plan with
    // fewer days keeps the old days until they scroll off.
    val since = LocalDate.now().minusWeeks(16).toString()
    val loggedDays = byDate.keys.filter { it >= since }.mapNotNull { d ->
        runCatching { LocalDate.parse(d).dayOfWeek.value % 7 }.getOrNull()
    }
    val rowSet = (planDays.map { it.day } + loggedDays).toSortedSet()
    val rows = if (rowSet.isNotEmpty()) rowSet.toList() else (0..6).toList()

    if (byDate.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        CardTitle("Workout days", "")
        val today = LocalDate.now()
        // Anchor to THIS week and count back, so the last column is the week
        // in progress. Going back 111 days and then snapping to Sunday moved
        // the window's start earlier without moving its end, so it finished at
        // today minus the weekday — on a Wednesday the current week was
        // missing and today never appeared at all.
        val dow = listOf("S", "M", "T", "W", "T", "F", "S")
        // Fit the weeks to the width rather than drawing a fixed sixteen. On a
        // phone sixteen columns come to ~364dp before the card's padding, so
        // the newest ones — including the week in progress — were rendered off
        // the right edge: the data was there, the screen was not. Measuring
        // instead means the current week is always the last visible column and
        // the oldest simply fall away as time moves on.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = 18.dp
        val gap = 4.dp
        val rail = 12.dp
        val weeks = (((maxWidth - rail - gap) / (cell + gap)).toInt()).coerceIn(4, 16)
        val start = today.minusDays(today.dayOfWeek.value.toLong() % 7)
            .minusWeeks((weeks - 1).toLong())
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.height(14.dp))
                rows.forEach { d ->
                    Text(
                        dow[d],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(rail).height(cell),
                    )
                }
            }
            (0 until weeks).forEach { w ->
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
                                .size(cell)
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
        }
        // Two tidy rows (v0.443): four to a row in even columns, so a
        // seventh group (Core) starts a second, lined-up row instead of
        // spilling onto a ragged one.
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          groupsSeen.chunked(4).forEach { rowGroups ->
            Row(Modifier.fillMaxWidth()) {
              (0 until 4).forEach { i ->
                val g = rowGroups.getOrNull(i)
                if (g == null) {
                    Spacer(Modifier.weight(1f))
                } else Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
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
                        maxLines = 1,
                    )
                }
              }
            }
          }
        }
    }
}

/** "1:30" for a hold, "25 reps" or "12 × 25 lb" for reps (the web's coreValueText). */
private fun coreText(kind: String, value: Double, weight: Double?, unit: String): String =
    if (kind == "time") {
        val m = (value / 60).toInt()
        val sec = (value % 60).roundToInt()
        if (m > 0) "$m:" + sec.toString().padStart(2, '0') else "$sec s"
    } else {
        fmt(value) + (weight?.takeIf { it > 0 }?.let { " \u00d7 " + fmt(it) + " " + unit } ?: " reps")
    }

private val UP_GREEN = Color(0xFF047857)
private val FLAT_AMBER = Color(0xFFB45309)
private val DOWN_RED = Color(0xFFB91C1C)

private fun pctText(n: Int): String = (if (n > 0) "+" else if (n < 0) "−" else "") + kotlin.math.abs(n) + "%"

/** The web's tagOf: up is 3% or more, down 5% or more under. */
private fun coreTag(change: Int?): String = when {
    change == null -> "new"
    change >= 3 -> "up"
    change <= -5 -> "down"
    else -> "flat"
}

private fun tagColor(tag: String, muted: Color): Color = when (tag) {
    "up" -> UP_GREEN
    "flat" -> FLAT_AMBER
    "down" -> DOWN_RED
    else -> muted
}

private fun tagText(tag: String, change: Int?): String = when (tag) {
    "up" -> "Up $change%"
    "down" -> "Down ${kotlin.math.abs(change ?: 0)}%"
    "flat" -> "Flat"
    else -> "New"
}

/** The web's moveChange: the best in the four weeks up to the last session
 *  against the first session, in percent; weighted reps by estimated max. */
private fun coreMoveChange(m: com.kairos.app.data.remote.dto.CoreProgressMoveDto): Int? {
    val pts = m.points
    if (pts.size < 2) return null
    fun score(p: com.kairos.app.data.remote.dto.CorePointDto): Double =
        if (m.kind == "time") p.value else p.weight?.takeIf { it > 0 }?.let { it * (1 + minOf(p.value, 12.0) / 30) } ?: p.value
    val base = score(pts.first())
    if (base <= 0) return null
    val lastDay = epochDay(pts.last().date) ?: return null
    val recent = pts.filter { (epochDay(it.date) ?: 0L) >= lastDay - 27 }.maxOf { score(it) }
    return ((recent / base - 1) * 100).roundToInt()
}

private fun monthShort(iso: String): String = try {
    LocalDate.parse(iso).month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)
} catch (_: Exception) {
    ""
}

/** Core's progress line (v0.483): the middle change across moves each week,
 *  with the plank tests as rings when given. */
@Composable
private fun CoreLine(
    series: List<com.kairos.app.data.remote.dto.CorePctDto>,
    tests: List<com.kairos.app.data.remote.dto.CorePctDto>?,
    color: Color,
    modifier: Modifier,
    grid: Boolean,
) {
    if (series.isEmpty()) return
    val ring = MaterialTheme.colorScheme.onSurface
    val fillRing = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val vals = listOf(0) + series.map { it.value } + tests.orEmpty().map { it.value }
    val lo = vals.min().toFloat()
    val hi = maxOf(10, vals.max()).toFloat()
    val d0 = epochDay(series.first().date) ?: 0L
    val d1 = maxOf(d0 + 1, (epochDay(series.last().date) ?: d0) + 6)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun x(iso: String) = ((epochDay(iso) ?: d0) - d0).toFloat() / (d1 - d0).toFloat() * w
        fun y(v: Int) = h - 4f - ((v - lo) / (hi - lo).coerceAtLeast(1f)) * (h - 10f)
        if (grid) {
            val step = if (hi - lo > 40) 20 else 10
            var v = (ceil(lo / step) * step).toInt()
            while (v <= hi) {
                drawLine(gridColor, Offset(0f, y(v)), Offset(w, y(v)), 1.5f)
                v += step
            }
        } else {
            drawLine(gridColor, Offset(0f, y(0)), Offset(w, y(0)), 1.5f)
        }
        val base = y(maxOf(lo.toInt(), 0))
        val path = androidx.compose.ui.graphics.Path()
        val area = androidx.compose.ui.graphics.Path()
        series.forEachIndexed { i, p ->
            if (i == 0) {
                path.moveTo(x(p.date), y(p.value))
                area.moveTo(x(p.date), base)
            } else {
                path.lineTo(x(p.date), y(p.value))
            }
            area.lineTo(x(p.date), y(p.value))
        }
        area.lineTo(x(series.last().date), base)
        area.close()
        drawPath(area, color.copy(alpha = 0.14f))
        drawPath(
            path,
            color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
        if (tests == null) {
            drawCircle(color, radius = 8f, center = Offset(x(series.last().date), y(series.last().value)))
        } else {
            tests.forEach { t ->
                val c = Offset(x(t.date), y(t.value))
                drawCircle(fillRing, radius = 8f, center = c)
                drawCircle(ring, radius = 8f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
            }
        }
    }
}

@Composable
private fun MiniStat(key: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier) {
        Text(key.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Core on the Workouts screen (v0.483; was a row per movement, v0.481): one
 * card that stays the same size however many core moves there are. Opens
 * the Core page.
 */
@Composable
private fun CoreCard(summary: com.kairos.app.data.remote.dto.CoreSummaryDto, unit: String, onOpen: () -> Unit) {
    val tone = muscleColor("CORE") ?: LINE
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val best = summary.plankBest
    androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen() }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CORE PROGRESS", style = MaterialTheme.typography.labelSmall, color = muted)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            summary.change?.let { pctText(it) } ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = summary.change?.let { tagColor(coreTag(it), muted) } ?: MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            (summary.since?.let { "since " + monthShort(it) + " · " } ?: "") + summary.recent + if (summary.recent == 1) " move" else " moves",
                            style = MaterialTheme.typography.labelMedium,
                            color = muted,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                Icon(KairosIcons.ChevronRight, contentDescription = "Open Core", tint = muted, modifier = Modifier.size(18.dp))
            }
            CoreLine(series = summary.series, tests = null, color = tone, modifier = Modifier.fillMaxWidth().height(60.dp), grid = false)
            androidx.compose.material3.HorizontalDivider()
            Row {
                MiniStat(
                    key = "Plank test",
                    value = best?.let { coreText("time", it.seconds.toDouble(), null, unit) } ?: "—",
                    sub = best?.pft?.let { "$it pts" } ?: if (best != null) "under 1:10" else "none yet",
                    modifier = Modifier.weight(1f),
                )
                MiniStat(key = "This week", value = summary.daysThisWeek.toString(), sub = if (summary.daysThisWeek == 1) "core day" else "core days", modifier = Modifier.weight(1f))
                MiniStat(key = "Improving", value = "${summary.improving} of ${summary.recent}", sub = "moves", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CoreMoveRow(text: String, sub: String, tag: String?, change: Int?, onOpen: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onOpen() }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (tag != null) Text(tagText(tag, change), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tagColor(tag, muted))
        Icon(KairosIcons.ChevronRight, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
    }
}

/**
 * The Core page (v0.483): the progress line with the plank tests, tiles,
 * and the moves done in the last eight weeks grouped by what they work. Older
 * moves are behind "Not done lately". A move opens its page on top.
 */
@Composable
private fun CoreOverviewDialog(
    summary: com.kairos.app.data.remote.dto.CoreSummaryDto,
    moves: List<com.kairos.app.data.remote.dto.CoreProgressMoveDto>,
    unit: String,
    onOpenMove: (com.kairos.app.data.remote.dto.CoreProgressMoveDto) -> Unit,
    onOpenPlank: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tone = muscleColor("CORE") ?: LINE
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val byId = moves.associateBy { it.movement }
    var idleOpen by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val best = summary.plankBest
    fun last(m: com.kairos.app.data.remote.dto.CoreProgressMoveDto): String =
        m.points.lastOrNull()?.let { "Last " + coreText(m.kind, it.value, it.weight, unit) } ?: ""
    val months = summary.series.map { monthShort(it.date) }.fold(emptyList<String>()) { acc, mo -> if (acc.lastOrNull() == mo) acc else acc + mo }
    val axisVals = listOf(0) + summary.series.map { it.value } + summary.tests.map { it.value }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text("Core", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("CORE PROGRESS", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
                                androidx.compose.material3.IconButton(onClick = { info = true }, modifier = Modifier.size(32.dp)) {
                                    Text("ⓘ", color = muted)
                                }
                            }
                            Row(Modifier.fillMaxWidth().height(150.dp)) {
                                Column(
                                    Modifier.fillMaxHeight().width(40.dp).padding(end = 4.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(pctText(maxOf(10, axisVals.max())), style = MaterialTheme.typography.labelSmall, color = muted)
                                    Text(if (axisVals.min() < 0) pctText(axisVals.min()) else "Start", style = MaterialTheme.typography.labelSmall, color = muted)
                                }
                                CoreLine(series = summary.series, tests = summary.tests, color = tone, modifier = Modifier.weight(1f).fillMaxHeight(), grid = true)
                            }
                            Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                months.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = muted) }
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                ChartKey(color = tone, label = "All moves", square = false)
                                if (summary.tests.isNotEmpty()) ChartKey(color = MaterialTheme.colorScheme.onSurface, label = "Plank test", square = false)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            key = "Plank test",
                            value = best?.let { coreText("time", it.seconds.toDouble(), null, unit) } ?: "—",
                            unit = "",
                            sub = best?.pft?.let { "$it pts" } ?: "best",
                            modifier = Modifier.weight(1f).clickable { onOpenPlank() },
                        )
                        StatTile(key = "This month", value = summary.daysThisMonth.toString(), unit = "", sub = "core days", modifier = Modifier.weight(1f))
                        StatTile(key = "New bests", value = summary.newBests30.toString(), unit = "", sub = "last 30 days", modifier = Modifier.weight(1f))
                    }
                    summary.groups.forEach { g ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(g.label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = muted, modifier = Modifier.weight(1f))
                            g.change?.let { c ->
                                val t = coreTag(c)
                                Text(if (t == "flat") "Flat" else pctText(c), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tagColor(t, muted))
                            }
                        }
                        androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth()) {
                            g.moves.forEachIndexed { i, x ->
                                val m = byId[x.movement] ?: return@forEachIndexed
                                if (i > 0) androidx.compose.material3.HorizontalDivider()
                                CoreMoveRow(text = m.name.replaceFirstChar { it.uppercase() }, sub = last(m), tag = x.tag, change = x.change, onOpen = { onOpenMove(m) })
                            }
                        }
                    }
                    if (summary.idle.isNotEmpty()) {
                        androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { idleOpen = true }) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Not done lately", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("${summary.idle.size} " + if (summary.idle.size == 1) "move" else "moves", style = MaterialTheme.typography.bodyMedium, color = muted)
                                Icon(KairosIcons.ChevronRight, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (summary.recentDays.isNotEmpty()) {
                        Text("RECENT CORE DAYS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = muted)
                        summary.recentDays.forEach { d ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(longDate(d.date), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    d.moves.joinToString(", "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = muted,
                                    modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    if (idleOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { idleOpen = false },
            title = { Text("Not done lately") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    summary.idle.forEachIndexed { i, id ->
                        val m = byId[id] ?: return@forEachIndexed
                        if (i > 0) androidx.compose.material3.HorizontalDivider()
                        CoreMoveRow(
                            text = m.name.replaceFirstChar { it.uppercase() },
                            sub = last(m) + (m.points.lastOrNull()?.let { " · " + longDate(it.date) } ?: ""),
                            tag = null,
                            change = null,
                            onOpen = { onOpenMove(m) },
                        )
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { idleOpen = false }) { Text("Close") } },
        )
    }
    if (info) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { info = false },
            title = { Text("Core progress") },
            text = {
                Text("Each move is compared with its first session: a hold by its time, reps by reps, a weighted set by its estimated max. The line is the middle of those changes, so no one move counts more than another.")
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { info = false }) { Text("Close") } },
        )
    }
}

/** A core movement's page (v0.481): each day's longest hold or most reps,
 *  changed days marked, the best circled; its numbers and recent days. */
@Composable
private fun CorePageDialog(
    m: com.kairos.app.data.remote.dto.CoreProgressMoveDto,
    group: String,
    unit: String,
    marks: List<com.kairos.app.data.remote.dto.HeatMarkDto>,
    onDismiss: () -> Unit,
) {
    val tone = muscleColor("CORE") ?: LINE
    val pts = m.points
    val lo = pts.minOfOrNull { it.value } ?: 0.0
    val hi = pts.maxOfOrNull { it.value } ?: 1.0
    val pad = maxOf(1.0, (hi - lo) * 0.1)
    val yMin = maxOf(0.0, lo - pad)
    val yMax = hi + pad
    val xs = pts.mapNotNull { epochDay(it.date) }
    val xMin = xs.minOrNull() ?: 0L
    val xMax = xs.maxOrNull() ?: 1L
    fun px(day: Long, w: Float): Float = if (xMax == xMin) w / 2f else ((day - xMin).toFloat() / (xMax - xMin).toFloat()) * w
    fun py(v: Double, h: Float): Float = h - ((v - yMin) / (yMax - yMin)).toFloat() * h
    val markColor = Color(0xFFB45309)
    val today = LocalDate.now().toString()
    val firstISO = pts.firstOrNull()?.date ?: ""
    val shownMarks = marks.filter { it.date >= firstISO && it.date <= today }
    val change = coreMoveChange(m)
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Column {
                        Text(m.name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            group + " \u00b7 " + pts.size + (if (pts.size == 1) " time" else " times") +
                                (pts.firstOrNull()?.let { " since " + longDate(it.date) } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = tone,
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth().height(180.dp)) {
                        Column(
                            Modifier.fillMaxHeight().width(44.dp).padding(end = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(coreText(m.kind, yMax, null, unit).removeSuffix(" reps"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(coreText(m.kind, yMin, null, unit).removeSuffix(" reps"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Canvas(Modifier.weight(1f).fillMaxHeight()) {
                            val w = size.width
                            val h = size.height
                            for (i in 0..4) drawLine(Color(0x33000000), Offset(0f, h * i / 4f), Offset(w, h * i / 4f), 1.5f)
                            shownMarks.forEach { mk ->
                                val d = epochDay(mk.date) ?: return@forEach
                                if (d in xMin..xMax) drawLine(markColor, Offset(px(d, w), h - 10f), Offset(px(d, w), h), strokeWidth = 4f)
                            }
                            pts.forEach { p ->
                                val d = epochDay(p.date) ?: return@forEach
                                drawCircle(tone, radius = 5f, center = Offset(px(d, w), py(p.value, h)))
                            }
                            m.best?.let { b ->
                                epochDay(b.date)?.let { d ->
                                    drawCircle(tone, radius = 14f, center = Offset(px(d, w), py(b.value, h)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
                                }
                            }
                        }
                    }
                    if (shownMarks.isNotEmpty()) ChartKey(color = markColor, label = shownMarks.map { it.label }.distinct().joinToString(", "), square = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("best", m.best?.let { coreText(m.kind, it.value, it.weight, unit) } ?: "\u2014", "", m.best?.let { longDate(it.date) } ?: "", Modifier.weight(1f))
                        StatTile("first", pts.firstOrNull()?.let { coreText(m.kind, it.value, it.weight, unit) } ?: "\u2014", "", pts.firstOrNull()?.let { longDate(it.date) } ?: "", Modifier.weight(1f))
                        StatTile(
                            "change",
                            change?.let { pctText(it) } ?: "\u2014",
                            "",
                            pts.lastOrNull()?.let { "last " + coreText(m.kind, it.value, it.weight, unit) } ?: "",
                            Modifier.weight(1f),
                            up = coreTag(change) == "up",
                        )
                    }
                    CardTitle("Recent sessions", "")
                    pts.takeLast(5).reversed().forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(longDate(p.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(coreText(m.kind, p.value, p.weight, unit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
