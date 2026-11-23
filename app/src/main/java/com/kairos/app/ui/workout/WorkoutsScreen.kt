package com.kairos.app.ui.workout

import com.kairos.app.ui.tour.tourTarget
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.data.remote.dto.ProgressSeriesDto
import com.kairos.app.data.remote.dto.WorkoutProgressDto
import com.kairos.app.data.remote.dto.WeeklyActivityDto
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.data.local.PayloadCacheStore
import kotlinx.serialization.builtins.ListSerializer
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.launch

/**
 * The Workouts page (launcher): progress, the plan card (v0.540: Edit /
 * Change / End) and nine buttons — log, routines, browse; HIIT, calculator,
 * Family WOD; Pause, Recent, Saved plans.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    onOpenDrawer: () -> Unit,
    onLogWorkout: (String) -> Unit,
    onOpenRecent: () -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenCreatePersonal: () -> Unit,
    /** Warm-ups: look through, edit, create (v0.432). */
    onOpenWarmups: () -> Unit = {},
    onOpenEditPlan: () -> Unit,
    onOpenPrograms: () -> Unit = {},
    /** Programs, straight to the list to change program (v0.427). */
    onChangeProgram: () -> Unit = onOpenPrograms,
    refreshKey: Int = 0,
    /** Family WOD and Pause workouts, full pages (v0.490). */
    onOpenFamilyWod: () -> Unit = {},
    onOpenPause: () -> Unit = {},
    /** Saved plans: My plans, Household, save / share / export / import (v0.540). */
    onOpenSavedPlans: () -> Unit = {},
    /** Add a program beside the running one (v0.543). */
    onAddProgram: () -> Unit = {},
    /** The plateau card's Try: that program's setup (v0.546). */
    onTryProgram: (String) -> Unit = {},
) {
    val container = rememberContainer()
    val vm: WorkoutLogViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WorkoutLogViewModel(container.sessionRepository, null, container.payloadCache) }
        },
    )
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<WorkoutProgressDto?>(null) }
    var week by remember { mutableStateOf<List<WeeklyActivityDto>>(emptyList()) }
    var training by remember { mutableStateOf<com.kairos.app.data.remote.dto.WeeklyTrainingDto?>(null) }
    var programs by remember { mutableStateOf<com.kairos.app.data.remote.dto.ProgramsDto?>(null) }

    // Fetch Progress + This Week and write both back to the cache. Shared by the
    // initial load and the manual refresh so every successful fetch updates BOTH
    // the UI and the cache (the refresh path previously updated only the UI).
    suspend fun refreshProgressWeek() {
        val pid = container.sessionRepository.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD
        runCatching { container.sessionRepository.loadWorkoutProgress() }
            .getOrNull()?.let {
                progress = it
                runCatching { container.payloadCache.writeAs("workout-progress", "main", pid, WorkoutProgressDto.serializer(), it) }
            }
        runCatching { container.sessionRepository.loadWeek() }
            .getOrNull()?.let {
                week = it
                runCatching { container.payloadCache.writeAs("workout-week", "main", pid, ListSerializer(WeeklyActivityDto.serializer()), it) }
            }
        runCatching { container.sessionRepository.loadPrograms() }.getOrNull()?.let { programs = it }
        runCatching { container.sessionRepository.loadWeeklyTraining() }
            .getOrNull()?.let {
                training = it
                runCatching {
                    container.payloadCache.writeAs("workout-weekly", "main", pid, com.kairos.app.data.remote.dto.WeeklyTrainingDto.serializer(), it)
                }
            }
    }

    LaunchedEffect(ui.savedTick) {
        val pid = container.sessionRepository.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD
        // Cold-start seed so Progress and This Week paint offline too.
        if (progress == null) {
            runCatching { container.payloadCache.readAs("workout-progress", "main", pid, WorkoutProgressDto.serializer()) }
                .getOrNull()?.let { progress = it }
        }
        if (week.isEmpty()) {
            runCatching { container.payloadCache.readAs("workout-week", "main", pid, ListSerializer(WeeklyActivityDto.serializer())) }
                .getOrNull()?.let { week = it }
        }
        if (training == null) {
            runCatching {
                container.payloadCache.readAs("workout-weekly", "main", pid, com.kairos.app.data.remote.dto.WeeklyTrainingDto.serializer())
            }.getOrNull()?.let { training = it }
        }
        refreshProgressWeek()
        if (ui.savedTick > 0) snackbar.showSnackbar("Updated")
    }
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            vm.load()
            refreshProgressWeek()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workouts") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            if (ui.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    progress?.takeIf { it.series.isNotEmpty() }?.let { p ->
                        OutlinedCard(Modifier.fillMaxWidth().tourTarget("workouts.progress")) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "Progress",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(8.dp))
                                WorkoutChart(p.series, p.defaultId, p.planDays, heat = p.heat)
                            }
                        }
                    }

                    // Next to Workout days (v0.480), which closes the Progress card.
                    training?.takeIf { t -> t.weeks.any { it.days > 0 } }?.let {
                        Box(Modifier.tourTarget("workouts.weekly")) { WeeklyTrainingCard(it) }
                    }

                    if (week.isNotEmpty()) {
                        OutlinedCard(Modifier.fillMaxWidth().tourTarget("workouts.week")) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "This week",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                week.forEach { w ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(w.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Text(
                                            "${w.count}\u00d7" + if (w.detail.isNotBlank()) " \u00b7 ${w.detail}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    programs?.let { p ->
                      Box(Modifier.tourTarget("workouts.programs")) {
                        PlanCard(
                            data = p,
                            onOpen = onOpenPrograms,
                            onAdd = onAddProgram,
                            onEdit = onOpenEditPlan,
                            onChange = onChangeProgram,
                            onEndProgram = { restore ->
                                scope.launch {
                                    runCatching { container.sessionRepository.endProgram(restore) }
                                    refreshProgressWeek()
                                    vm.load()
                                }
                            },
                            onEndPlan = {
                                scope.launch {
                                    runCatching { container.sessionRepository.setWeeklyEnd(java.time.LocalDate.now().toString()) }
                                    refreshProgressWeek()
                                    vm.load()
                                }
                            },
                            onAnswer = { answer ->
                                scope.launch {
                                    runCatching { container.sessionRepository.answerProgramInvite(answer) }
                                    programs = p.copy(invite = false)
                                }
                            },
                        )
                      }
                      // No new best in 2+ main groups for 4 weeks (v0.546).
                      p.plateau?.let { pl ->
                        PlateauCard(
                            plateau = pl,
                            onTry = onTryProgram,
                            onNewPlan = onChangeProgram,
                            onAnswer = { answer ->
                                programs = p.copy(plateau = null)
                                scope.launch {
                                    runCatching { container.sessionRepository.answerPlateau(answer) }
                                    if (answer == "swap") onOpenEditPlan()
                                    refreshProgressWeek()
                                    vm.load()
                                }
                            },
                        )
                      }
                    }


                    // The TODAY card is gone: it listed the day's plan names
                    // ("Core \u00b7 Core \u00b7 Legs") and said nothing the Progress
                    // section above does not, which now heads each block with
                    // its muscle group.

                    // Overdue days are a different thing from today, and "Rest /
                    // skip" below only ever clears TODAY. Without this, skipping
                    // from here looked like it did nothing: the late count is for
                    // an earlier date and stayed pending.
                    val overdueDays = ui.blocks.filter { it.isOverdue }.mapNotNull { it.date }.distinct()
                    if (overdueDays.isNotEmpty()) {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (overdueDays.size == 1) "1 overdue workout" else "${overdueDays.size} overdue workouts",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        "Log them, or skip to clear them for good.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                ActionCard(
                                    KairosIcons.Moon, "Skip overdue", Modifier.width(110.dp),
                                    enabled = !ui.saving,
                                ) { vm.restOverdue() }
                            }
                        }
                    }

                    // The Edit plan button went to the plan card (v0.540): the
                    // nine are log, routines, browse; HIIT, calculator, Family
                    // WOD; Pause, Recent, Saved plans.
                    Row(Modifier.tourTarget("workouts.plan"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(
                            KairosIcons.Dumbbell, "Log workout", Modifier.weight(1f),
                            highlighted = true,
                            enabled = ui.date != null,
                        ) { ui.date?.let(onLogWorkout) }
                        // Rest / skip moved to the day itself (v0.432).
                        // No subtitle (v0.480): three words wrapped past the card's height.
                        ActionCard(
                            KairosIcons.Flame, "Routines", Modifier.weight(1f),
                        ) { onOpenWarmups() }
                        ActionCard(
                            KairosIcons.Book, "Browse workouts", Modifier.weight(1f),
                        ) { onOpenBrowse() }
                    }

                  Column(Modifier.tourTarget("workouts.tools"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(
                            KairosIcons.Plus, "HIIT / CrossFit", Modifier.weight(1f),
                            subtitle = "Create / edit",
                        ) { onOpenCreatePersonal() }
                        ActionCard(
                            KairosIcons.Dumbbell, "Calculator", Modifier.weight(1f),
                        ) { onOpenCalculator() }
                        // Family WOD and Pause say what's on under the label
                        // (v0.494): the next WOD, a pause running or ahead.
                        val fw = familyWodLine(refreshKey)
                        ActionCard(
                            KairosIcons.Users, "Family WOD", Modifier.weight(1f),
                            highlighted = fw?.second == true,
                            subtitle = fw?.first,
                        ) { onOpenFamilyWod() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(
                            KairosIcons.Pause, "Pause", Modifier.weight(1f),
                            subtitle = pauseLine(refreshKey),
                        ) { onOpenPause() }
                        ActionCard(
                            KairosIcons.Clock, "Recent", Modifier.weight(1f),
                        ) { onOpenRecent() }
                        ActionCard(
                            KairosIcons.Bookmark, "Saved plans", Modifier.weight(1f),
                        ) { onOpenSavedPlans() }
                    }
                  }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val tint =
        if (highlighted) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(88.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The kinds a week's bar is split by (v0.481), named like the workout
 *  categories in fixed colours, never the theme's (v0.482). */
private data class WeekKind(val key: String, val label: String, val color: Color?)

private val WEEK_KINDS = listOf(
    WeekKind(key = "lift", label = "Strength", color = Color(0xFF4F46E5)),
    // v0.538: core and holds count as themselves.
    WeekKind(key = "core", label = "Core", color = Color(0xFFD97706)),
    WeekKind(key = "iso", label = "Isometric", color = Color(0xFFDB2777)),
    // v0.496: colours no muscle group uses, so Cardio never reads as Chest,
    // Conditioning as Core or Sport as Upper back.
    WeekKind(key = "conditioning", label = "Conditioning", color = Color(0xFFC026D3)),
    WeekKind(key = "cardio", label = "Cardio", color = Color(0xFF0EA5E9)),
    WeekKind(key = "sport", label = "Sport", color = Color(0xFF65A30D)),
    WeekKind(key = "other", label = "Mobility", color = Color(0xFF9AA3AD)),
)

private fun kindCount(k: com.kairos.app.data.remote.dto.TrainingKindsDto?, key: String): Int = when (key) {
    "lift" -> k?.lift ?: 0
    "core" -> k?.core ?: 0
    "iso" -> k?.iso ?: 0
    "conditioning" -> k?.conditioning ?: 0
    "cardio" -> k?.cardio ?: 0
    "sport" -> k?.sport ?: 0
    else -> k?.other ?: 0
}

/**
 * Weekly training (v0.481; the web's): a bar per week for the last 12, split
 * by the workouts and parts done (v0.538: lifting, core, holds, conditioning,
 * cardio, each sport event, mobility; never warm-ups), a dashed mark
 * at the days the plan had, and weeks trained in a row. Tap a week for its
 * seven days. Turning up, not weight.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WeeklyTrainingCard(t: com.kairos.app.data.remote.dto.WeeklyTrainingDto) {
    var picked by remember(t) { mutableStateOf(t.weeks.lastIndex) }
    val accent = MaterialTheme.colorScheme.primary
    fun colorOf(key: String): Color = WEEK_KINDS.firstOrNull { it.key == key }?.color ?: accent
    fun total(w: com.kairos.app.data.remote.dto.TrainingWeekDto): Int =
        WEEK_KINDS.sumOf { kindCount(w.kinds, it.key) }.takeIf { it > 0 } ?: w.days
    val max = maxOf(7, t.weeks.maxOfOrNull { maxOf(total(it), it.plannedParts ?: 0) } ?: 0)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val used = WEEK_KINDS.filter { k -> t.weeks.any { kindCount(it.kinds, k.key) > 0 } }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Weekly training",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text("${t.inARow}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (t.inARow == 1) " week in a row" else " weeks in a row",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            Row(
                Modifier.fillMaxWidth().height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                t.weeks.forEachIndexed { i, w ->
                    androidx.compose.foundation.Canvas(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { picked = i },
                    ) {
                        val h = size.height - 4.dp.toPx()
                        val unit = h / max
                        var y = h
                        val segs = WEEK_KINDS.filter { kindCount(w.kinds, it.key) > 0 }
                        if (segs.isEmpty() && w.days > 0) {
                            drawRect(colorOf("lift"), topLeft = Offset(0f, h - w.days * unit), size = Size(size.width, w.days * unit))
                        }
                        segs.forEach { k ->
                            val hh = kindCount(w.kinds, k.key) * unit
                            drawRect(colorOf(k.key), topLeft = Offset(0f, y - hh), size = Size(size.width, (hh - 1.dp.toPx()).coerceAtLeast(1f)))
                            y -= hh
                        }
                        // Planned parts plus sport events (v0.540); none before they were saved.
                        val plannedLine = w.plannedParts ?: 0
                        if (plannedLine > 0) {
                            val py = h - plannedLine * unit
                            drawLine(
                                muted.copy(alpha = 0.7f),
                                Offset(0f, py),
                                Offset(size.width, py),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                            )
                        }
                        if (i == picked) drawRect(ink, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx()))
                    }
                }
            }
            // Dates under the bars (v0.482).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 4, 8).forEach { i ->
                    t.weeks.getOrNull(i)?.let { Text(weekLabel(it.start), style = MaterialTheme.typography.labelSmall, color = muted) }
                }
                Text("This week", style = MaterialTheme.typography.labelSmall, color = muted)
            }
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                used.forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colorOf(k.key)))
                        Text(" " + k.label, style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                }
                Text("- - Planned", style = MaterialTheme.typography.labelSmall, color = muted)
            }
            t.weeks.getOrNull(picked)?.let { w ->
                androidx.compose.material3.HorizontalDivider()
                Text(
                    if (picked == t.weeks.lastIndex) "This week" else "Week of ${weekLabel(w.start)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val done = w.dayList.count { it.status == "done" }.takeIf { w.dayList.isNotEmpty() } ?: w.days
                Text(
                    (if (w.planned > 0) "${minOf(done, w.planned)} of ${w.planned} planned days" else "${w.days} ${if (w.days == 1) "day" else "days"}") +
                        " \u00b7 ${w.sets} ${if (w.sets == 1) "set" else "sets"}" +
                        if (w.minutes > 0) " \u00b7 ${w.minutes} min" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                if (w.dayList.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val dows = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        w.dayList.forEachIndexed { i, d ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(dows.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall, color = muted)
                                val k1 = d.kinds.firstOrNull()
                                val k2 = d.kinds.getOrNull(1)
                                val dayShape = RoundedCornerShape(6.dp)
                                androidx.compose.foundation.Canvas(
                                    Modifier
                                        .padding(vertical = 3.dp)
                                        .size(width = 34.dp, height = 28.dp)
                                        .clip(dayShape)
                                        .then(
                                            if (k1 == null) {
                                                Modifier.border(
                                                    1.dp,
                                                    when (d.status) {
                                                        "missed" -> Color(0xFFD97706)
                                                        "planned" -> muted.copy(alpha = 0.6f)
                                                        else -> MaterialTheme.colorScheme.outlineVariant
                                                    },
                                                    dayShape,
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                ) {
                                    if (k1 != null) {
                                        if (k2 == null) {
                                            drawRect(colorOf(k1))
                                        } else {
                                            drawRect(colorOf(k2))
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(size.width, 0f)
                                                lineTo(0f, size.height)
                                                close()
                                            }
                                            drawPath(path, colorOf(k1))
                                        }
                                    }
                                }
                                Text(
                                    d.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = muted,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "2026-10-25" -> "Oct 25". */
private fun weekLabel(iso: String): String = try {
    java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
} catch (_: Exception) {
    iso
}

/**
 * The plan card (v0.540; the web's): the program or your own plan, the week
 * at a glance, and Edit / Change / End. It replaces the Edit plan button.
 * Without a plan, the invite (no plan and nothing logged lately) or New plan.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlanCard(
    data: com.kairos.app.data.remote.dto.ProgramsDto,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onChange: () -> Unit,
    onEndProgram: (restore: Boolean) -> Unit,
    onEndPlan: () -> Unit,
    onAnswer: (String) -> Unit,
    /** Add a program beside the running one (v0.543). */
    onAdd: () -> Unit = {},
) {
    val a = data.active
    val plan = data.plan
    var ending by remember { mutableStateOf(false) }
    val links: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onChange) { Text("Change") }
            TextButton(onClick = { ending = true }) { Text(text = "End", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    when {
        a != null -> OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            if (a.cycle > 1) "Cycle ${a.cycle} \u00b7 ${a.label}" else a.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (plan != null && plan.others.isNotEmpty()) {
                        Text(
                            "With " + listWords(plan.others),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The success mark (v0.542): the last cycle and what went up.
                    if (a.cycle > 1) {
                        Text(
                            "\u2191 Cycle ${a.cycle - 1} done \u00b7 ${a.lastRaises.size} of ${a.maxes.size} lifts went up",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF047857),
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF059669).copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
                if (a.blockDone) {
                    Box(Modifier.padding(end = 8.dp)) {
                        ProgramPill(label = "What's next?", filled = true, modifier = Modifier.fillMaxWidth(), onClick = onOpen)
                    }
                }
                // Programs beside the first (v0.543): each its week and What's next.
                data.also.forEach { x ->
                    HorizontalDivider(Modifier.padding(end = 8.dp))
                    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(x.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            if (x.cycle > 1) "Cycle ${x.cycle} \u00b7 ${x.label}" else x.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (x.blockDone) {
                        Box(Modifier.padding(end = 8.dp)) {
                            ProgramPill(label = "What's next?", filled = true, modifier = Modifier.fillMaxWidth(), onClick = onOpen)
                        }
                    }
                }
                plan?.let { Box(Modifier.padding(end = 8.dp)) { WeekStrip(days = it.strip) } }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = onChange) { Text("Change") }
                    TextButton(onClick = onAdd) { Text("Add program") }
                    TextButton(onClick = if (data.also.isEmpty()) ({ ending = true }) else onOpen) {
                        Text(text = "End", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (ending) {
                AnimatedDialog(
                    onDismissRequest = { ending = false },
                    title = "End ${a.name}?",
                    confirmButton = {
                        TextButton(onClick = {
                            ending = false
                            onEndProgram(true)
                        }) { Text("Go back to my old plan") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            ending = false
                            onEndProgram(false)
                        }) { Text("Keep this plan") }
                    },
                    content = {},
                )
            }
        }
        plan != null && plan.hasPlan && !data.invite -> OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("My plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        plan.ends?.let { "Ends " + planEndDay(it) } ?: "No end date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.padding(end = 8.dp)) { WeekStrip(days = plan.strip) }
                links()
            }
            if (ending) {
                AnimatedDialog(
                    onDismissRequest = { ending = false },
                    title = "End my plan?",
                    confirmButton = {
                        TextButton(onClick = {
                            ending = false
                            onEndPlan()
                        }) { Text("End today") }
                    },
                    dismissButton = { TextButton(onClick = { ending = false }) { Text("Cancel") } },
                    content = {},
                )
            }
        }
        data.invite -> OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Start a workout program?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgramPill("See programs", filled = true, onClick = onOpen)
                    Text(
                        "Not now",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onAnswer("later") }.padding(8.dp),
                    )
                    Text(
                        "Don't ask again",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onAnswer("never") }.padding(8.dp),
                    )
                }
            }
        }
        else -> OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("No plan yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ProgramPill(label = "New plan", filled = true, modifier = Modifier.fillMaxWidth(), onClick = onChange)
            }
        }
    }
}

/** "Arms", "Arms and Core", "Arms, Core and Mobility". */
private fun listWords(xs: List<String>): String =
    if (xs.size <= 1) xs.joinToString("") else xs.dropLast(1).joinToString(", ") + " and " + xs.last()

/** "2026-12-20" → "Dec 20". */
private fun planEndDay(iso: String): String =
    runCatching { java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US)) }.getOrDefault(iso)

private val STRIP_DAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

private fun stripColor(kind: String): Color = when (kind) {
    "lift" -> Color(0xFF4F46E5)
    "core" -> Color(0xFFD97706)
    "iso" -> Color(0xFFDB2777)
    "conditioning" -> CONDITIONING_COLOR
    "cardio" -> CARDIO_COLOR
    "other" -> Color(0xFF9AA3AD)
    "rest" -> Color(0xFFCBD5E1)
    else -> Color.Transparent
}

/** The week at a glance (v0.540): a bar per weekday in its kind's colour and
 *  the day's main workout. */
@Composable
private fun WeekStrip(days: List<com.kairos.app.data.remote.dto.StripDayDto>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(STRIP_DAYS.getOrElse(d.day) { "" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(stripColor(d.kind))
                        .then(
                            if (d.kind == "none") Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)) else Modifier,
                        ),
                )
                Text(
                    d.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Plateau (v0.546; server 0.752): two or more main groups with no new best in
 * 4 weeks. Your own plan: try a program (preview), a light week, swap the
 * movements. A program that runs on: New plan or a light week. Keep going.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlateauCard(
    plateau: com.kairos.app.data.remote.dto.PlateauDto,
    onTry: (String) -> Unit,
    onNewPlan: () -> Unit,
    onAnswer: (String) -> Unit,
) {
    var preview by remember { mutableStateOf(false) }
    val p = plateau.program
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(plateau.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(plateau.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p != null) {
                PlateauRow(title = "Try ${p.name}", sub = "${p.days.size} days \u00b7 ${p.weeks} weeks", action = "Preview", onClick = { preview = true })
                HorizontalDivider()
                PlateauRow(title = "Light week", sub = "Everything at 60%, then back", action = null, onClick = { onAnswer("light") })
                HorizontalDivider()
                PlateauRow(
                    title = "Swap the movements",
                    sub = plateau.groups.joinToString(", ") { it.movement.name },
                    action = null,
                    onClick = { onAnswer("swap") },
                )
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p == null) {
                    ProgramPill(label = "New plan", filled = true, onClick = onNewPlan)
                    ProgramPill(label = "Light week", onClick = { onAnswer("light") })
                }
                ProgramPill(label = "Keep going", onClick = { onAnswer("keep") })
            }
        }
    }
    if (preview && p != null) {
        AnimatedDialog(
            onDismissRequest = { preview = false },
            title = p.name,
            confirmButton = {
                TextButton(onClick = {
                    preview = false
                    onTry(p.key)
                }) { Text("Start ${p.name}") }
            },
            dismissButton = { TextButton(onClick = { preview = false }) { Text("Cancel") } },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    p.days.forEach { d -> Text(d, style = MaterialTheme.typography.bodyMedium) }
                }
            },
        )
    }
}

@Composable
private fun PlateauRow(title: String, sub: String, action: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (action != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        } else {
            Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
