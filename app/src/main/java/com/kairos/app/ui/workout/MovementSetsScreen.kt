package com.kairos.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.PoolExerciseDto
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One planned movement's sets for a day, laid out as the mockup: the movement's
 * name in the top bar with Swap beside it and "Last: Oct 23" under it; each set
 * a small card reading "[165] lb × [3]" (the boxes fill the row), last time's
 * numbers greyed inside them and never typed over.
 * ✓ saves a set, × removes one (a saved set asks first). Skip / Calculator /
 * Log stay along the bottom, above the system bar. Log and Back both keep
 * anything typed but not ticked.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovementSetsScreen(
    date: String,
    plannedWorkoutId: String,
    slotId: String,
    name: String,
    metric: String,
    unit: String,
    late: Boolean,
    onDone: () -> Unit,
    /** The use button's label and the weight to start on (0 = empty bar). */
    onOpenCalculator: (String, Double) -> Unit,
    usedWeight: String?,
    onWeightConsumed: () -> Unit,
    onOpenPrograms: () -> Unit = {},
) {
    val container = rememberContainer()
    val vm: MovementSetsViewModel = viewModel(
        key = "sets:$date:$plannedWorkoutId:$slotId",
        factory = viewModelFactory {
            initializer {
                MovementSetsViewModel(
                    session = container.sessionRepository,
                    date = date,
                    plannedWorkoutId = plannedWorkoutId,
                    slotId = slotId,
                    metric = metric,
                    unit = unit,
                    late = late,
                )
            }
        },
    )
    val ui by vm.ui.collectAsState()
    var swapping by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    // Log with sets still to do asks first (v0.439); finishing early keeps them.
    var confirmLeave by remember { mutableStateOf(false) }
    if (confirmLeave) {
        val left = vm.unfinishedCount()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Finish early?") },
            text = {
                Text("$left ${if (left == 1) "set isn't" else "sets aren't"} logged yet. They'll still be here when you come back.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmLeave = false }) { Text("Keep going") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmLeave = false
                    vm.leave()
                }) { Text("Finish for now") }
            },
        )
    }
    // A timed movement's timer (v0.427): its time fills the set.
    var timing by remember { mutableStateOf(false) }

    LaunchedEffect(ui.done) { if (ui.done) onDone() }
    LaunchedEffect(usedWeight) {
        val w = usedWeight
        if (w != null) {
            vm.useWeight(w)
            onWeightConsumed()
        }
    }
    BackHandler { vm.leave() }

    val isWeight = metric == "WEIGHT"
    val swapped = ui.doingId != null && ui.doingId != slotId

    Scaffold(
        topBar = {
            // The movement is the page: its name up top with Swap beside it
            // (you're swapping what the title says), and when it was last done
            // or the save status under it.
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (swapped) ui.doingName ?: name else name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val status = when {
                            ui.status.isNotBlank() -> ui.status
                            ui.lastDate != null -> "Last: ${shortDate(ui.lastDate!!)}"
                            else -> ""
                        }
                        val sub = listOfNotNull(
                            if (swapped) "instead of $name" else null,
                            status.ifBlank { null },
                        ).joinToString(" · ")
                        if (sub.isNotBlank()) {
                            Text(
                                sub,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (status.startsWith("Not")) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.leave() }) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    MovementInfoButton(
                        name = if (swapped) ui.doingName ?: name else name,
                        onClick = { showInfo = true },
                    )
                    Box(Modifier.padding(end = 12.dp)) {
                        if (swapped) {
                            SmallPill(label = "Undo", icon = false) { vm.swapTo(null) }
                        } else if (!ui.skipped && !ui.loading) {
                            SmallPill(label = "Swap", icon = true) { vm.loadPool(); swapping = true }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!ui.loading) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    // Above the gesture bar, so rounded screen corners never
                    // clip the tiles.
                    Column(Modifier.navigationBarsPadding()) {
                        HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WorkoutActionTile(
                                icon = KairosIcons.Moon,
                                label = if (ui.skipped) "Skipped" else "Skip",
                                modifier = Modifier.weight(1f),
                                highlighted = !ui.skipped,
                                fixedHeight = 64.dp,
                            ) { vm.toggleSkip() }
                            val timed = metric == "DURATION"
                            WorkoutActionTile(
                                icon = if (timed) KairosIcons.Timer else KairosIcons.Dumbbell,
                                label = if (timed) "Timer" else "Calculator",
                                modifier = Modifier.weight(1f),
                                highlighted = (isWeight || timed) && !ui.skipped,
                                enabled = (isWeight || timed) && !ui.skipped,
                                fixedHeight = 64.dp,
                            ) {
                                if (timed) {
                                    vm.openToolFor(null)
                                    timing = true
                                } else {
                                    vm.openToolFor(null)
                                    onOpenCalculator("Use for set ${vm.nextUnsavedIndex()}", 0.0)
                                }
                            }
                            WorkoutActionTile(
                                icon = KairosIcons.Check,
                                label = "Log",
                                modifier = Modifier.weight(1f),
                                highlighted = !ui.skipped,
                                enabled = !ui.leaving,
                                loading = ui.leaving,
                                filled = true,
                                fixedHeight = 64.dp,
                            ) { if (vm.unfinishedCount() > 0) confirmLeave = true else vm.leave() }
                        }
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            if (ui.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (late) {
                        Text(
                            "${dayName(date)} · logging late",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    // Best weight at the usual rep counts; a chip turns green
                    // with today's weight once it's beaten.
                    // The program's plan for today: "Today Work up to a top set ·
                    // Strength waves · Week 2 of 4".
                    ui.suggestion?.takeIf { !ui.skipped }?.let { sg ->
                        SuggestionCard(
                            title = sg.title,
                            why = sg.why,
                            actions = sg.actions.map { it.answer to it.label },
                        ) { answer ->
                            vm.answerSuggestion(answer)
                            when (answer) {
                                "swap" -> { vm.loadPool(); swapping = true }
                                "programs" -> onOpenPrograms()
                            }
                        }
                    }

                    if (ui.targetScheme.isNotBlank()) {
                        Text(
                            buildString {
                                append("Today ")
                                append(ui.targetScheme)
                                if (ui.targetTitle.isNotBlank()) append(" · ").append(ui.targetTitle)
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    val chips = if (isWeight) vm.recordChips() else emptyList()
                    if (chips.isNotEmpty()) {
                        val now = vm.todayAt(ui.shift)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            chips.forEach { c ->
                                val today = now[c.reps]
                                RecordChip(
                                    reps = c.reps,
                                    weight = if (today != null && today > c.weight) today else c.weight,
                                    beaten = today != null && today > c.weight,
                                )
                            }
                        }
                    }

                    // Warm-up, main and supplemental sets each under their own
                    // heading, numbered from 1 (v0.431).
                    fun groupOf(r: SetRow): String = if (r.warmup) "warm" else if (r.supplemental) "supp" else "main"
                    val grouped = ui.rows.any { it.warmup || it.supplemental }
                    val supp = ui.rows.filter { it.supplemental }
                    val suppLabel = if (supp.isNotEmpty()) "${supp.size} \u00d7 ${supp.first().hintR ?: ""}" else ""
                    ui.rows.forEachIndexed { i, r ->
                        val grp = groupOf(r)
                        if (grouped && (i == 0 || groupOf(ui.rows[i - 1]) != grp)) {
                            Text(
                                when (grp) {
                                    "warm" -> "WARM-UP"
                                    "supp" -> suppLabel
                                    else -> "MAIN SETS"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = if (i == 0) 0.dp else 10.dp),
                            )
                        }
                        SetCard(
                            index = if (grouped) ui.rows.take(i).count { groupOf(it) == grp } + 1 else i + 1,
                            row = r,
                            vm = vm,
                            metric = metric,
                            unit = unit,
                            shift = ui.shift,
                            sub = when {
                                r.added -> "Added today"
                                ui.lastSets.getOrNull(i) != null ->
                                    "Last time " + MovementSetsViewModel.fmtSet(ui.lastSets[i], metric)
                                else -> ""
                            },
                            newBest = if (r.saved && vm.isNewBest(r)) vm.valueOf(r).reps else null,
                            jump = vm.jumpFor(r),
                            enabled = !ui.skipped,
                            strict = vm.strict(),
                            onPlates = { w ->
                                vm.openToolFor(r.key)
                                onOpenCalculator("Use for set ${i + 1}", w)
                            },
                        )
                    }

                    if (!ui.skipped) {
                        Row(
                            Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PillButton(label = "Add set", icon = true) { vm.addSet() }
                            if (!vm.anyEntered() && vm.hasHints()) {
                                PillButton(label = "Same as last time", tinted = true) { vm.sameAsLast() }
                            }
                        }

                        val anyUnentered = ui.rows.any { !it.saved && it.w.isBlank() && it.hintW != null }
                        if (isWeight && anyUnentered && !vm.strict()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Remaining sets",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                RoundStep("−${MovementSetsViewModel.fmt(vm.step)}") { vm.shiftBy(-vm.step) }
                                Text(
                                    when {
                                        ui.shift == 0.0 -> "0"
                                        ui.shift > 0 -> "+${MovementSetsViewModel.fmt(ui.shift)} $unit"
                                        else -> "−${MovementSetsViewModel.fmt(-ui.shift)} $unit"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    textAlign = TextAlign.Center,
                                    color = if (ui.shift == 0.0) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.widthIn(min = 36.dp),
                                )
                                RoundStep("+${MovementSetsViewModel.fmt(vm.step)}") { vm.shiftBy(vm.step) }
                            }
                        }
                    }

                    if (!ui.skipped && ui.rows.any { it.saved }) {
                        FeedbackCard(
                            name = if (swapped) ui.doingName ?: name else name,
                            effort = ui.effort,
                            hurt = ui.hurt,
                            onChange = { e, h -> vm.setFeedback(e, h) },
                        )
                    }
                }
            }
        }
    }

    ui.confirmKey?.let { key ->
        val i = ui.rows.indexOfFirst { it.key == key }
        val row = ui.rows.getOrNull(i)
        AlertDialog(
            onDismissRequest = { vm.keep() },
            title = { Text("Remove set ${i + 1}?") },
            text = { if (row != null) Text(MovementSetsViewModel.fmtSet(vm.valueOf(row), metric)) },
            confirmButton = {
                TextButton(onClick = { vm.remove(key) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { vm.keep() }) { Text("Keep") } },
        )
    }

    if (swapping) {
        SetsSwapDialog(
            pool = ui.pool,
            slotId = slotId,
            title = "Swap $name",
            onPick = { vm.swapTo(it); swapping = false },
            onDismiss = { swapping = false },
        )
    }
    if (showInfo) {
        MovementInfoDialog(
            name = if (swapped) ui.doingName ?: name else name,
            about = ui.about,
            onDismiss = { showInfo = false },
        )
    }
    if (timing) {
        // The conditioning timer as a stopwatch; its time fills the set.
        ConditioningTimer(
            workout = com.kairos.app.data.remote.dto.ConditioningPlanDto(
                name = if (swapped) ui.doingName ?: name else name,
                type = "FOR_TIME",
                resultLabel = "Time",
                resultMetric = "DURATION",
            ),
            onLog = { seconds, _ ->
                timing = false
                vm.useSeconds(seconds)
            },
            onClose = { timing = false },
        )
    }
}

/** One set: its number, "[165] lb × [3]", ✓ and ×, and last time's set. */
@Composable
private fun SetCard(
    index: Int,
    row: SetRow,
    vm: MovementSetsViewModel,
    metric: String,
    unit: String,
    shift: Double,
    sub: String,
    /** Reps of a set no earlier set matched, or null. */
    newBest: Int?,
    /** Percent over the recent top, when it's a big jump. */
    jump: Int? = null,
    enabled: Boolean,
    /** A program fixed this set's weight: plates instead of remove (v0.427). */
    strict: Boolean = false,
    onPlates: (Double) -> Unit = {},
) {
    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (row.saved) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(Modifier.width(if (row.pct != null) 34.dp else 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$index", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    row.pct?.let { p ->
                        Text(
                            "${kotlin.math.round(p * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
                when (metric) {
                    "WEIGHT" -> {
                        SetField(
                            value = row.w,
                            hint = vm.hintW(row, shift)?.let { MovementSetsViewModel.fmt(it) } ?: "wt",
                            label = "Weight, set $index",
                            decimal = true,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { vm.onWeight(row.key, it) }
                        UnitText(unit)
                        UnitText("×")
                        SetField(
                            value = row.r,
                            hint = row.hintR?.let { "$it${if (row.plus) "+" else ""}" } ?: "rep",
                            label = "Reps, set $index",
                            decimal = false,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { vm.onReps(row.key, it) }
                    }
                    "DURATION" -> {
                        SetField(
                            value = row.s,
                            hint = row.hintS?.toString() ?: "0",
                            label = "Seconds, set $index",
                            decimal = false,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { vm.onSeconds(row.key, it) }
                        UnitText("sec")
                    }
                    else -> {
                        SetField(
                            value = row.r,
                            hint = row.hintR?.toString() ?: "0",
                            label = "Reps, set $index",
                            decimal = false,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { vm.onReps(row.key, it) }
                        UnitText("reps")
                    }
                }
                // ✓ fills in once the set is saved.
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (row.saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            if (row.saved) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            CircleShape,
                        )
                        .clickable(enabled = enabled) { vm.tick(row.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        KairosIcons.Check,
                        contentDescription = if (row.saved) "Undo set $index" else "Done: set $index",
                        tint = if (row.saved) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled) {
                            if (strict) onPlates(vm.valueOf(row).weight ?: 0.0) else vm.askRemove(row.key)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (strict) KairosIcons.Dumbbell else KairosIcons.Close,
                        contentDescription = if (strict) "Plates for set $index" else "Remove set $index",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (strict) 18.dp else 16.dp),
                    )
                }
            }
            if (sub.isNotBlank() || newBest != null) {
                Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 8.dp)) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (newBest != null) {
                        Text(
                            "New best for $newBest ${if (newBest == 1) "rep" else "reps"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = goodGreen(),
                        )
                    }
                }
            }
            if (jump != null) {
                Text(
                    "$jump% over your recent top. Make sure it's the weight you meant.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = warnAmber(),
                    modifier = Modifier.padding(start = 28.dp, end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun UnitText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The Log workout card's number box: same width, hint greyed inside. */
@Composable
private fun SetField(
    value: String,
    hint: String,
    label: String,
    decimal: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.semantics { contentDescription = label },
    )
}

/** −5 / +5 for the remaining sets: white circles, as in the mockup. */
@Composable
private fun RoundStep(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Add set / Same as last time: the Swap pill's shape, a size up. */
@Composable
private fun PillButton(label: String, icon: Boolean = false, tinted: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(
                if (tinted) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                else MaterialTheme.colorScheme.surface,
            )
            // Accent-outlined like every secondary button (v0.435).
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon) {
            Icon(KairosIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Amber for a check-this note, as on the web. */
@Composable
private fun warnAmber() = if (com.kairos.app.ui.theme.KairosThemeState.dark) Color(0xFFFCD34D) else Color(0xFFB45309)

/**
 * One suggestion: what and why, with its choices (the first filled). Never
 * applied on its own; answering hides it for the day. Shared with the log page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SuggestionCard(
    title: String,
    why: String,
    actions: List<Pair<String, String>>,
    onAnswer: (String) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEachIndexed { i, (answer, label) ->
                ChoicePill(label = label, selected = i == 0) { onAnswer(answer) }
            }
        }
    }
}

/** Green for a record, as on the web (emerald). */
@Composable
private fun goodGreen() = if (com.kairos.app.ui.theme.KairosThemeState.dark) Color(0xFF6EE7B7) else Color(0xFF047857)

/** "5 reps 165": a best at one rep count. */
@Composable
private fun RecordChip(reps: Int, weight: Double, beaten: Boolean) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(
                if (beaten) goodGreen().copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
            )
            .border(1.dp, if (beaten) goodGreen().copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            "$reps ${if (reps == 1) "rep" else "reps"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            MovementSetsViewModel.fmt(weight),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (beaten) goodGreen() else MaterialTheme.colorScheme.onSurface,
        )
    }
}

internal val EFFORTS = listOf("EASY" to "Easy", "SOLID" to "Solid", "HARD" to "Hard", "TOO_HARD" to "Too hard")
internal val HURT_AREAS = listOf(
    "NECK" to "Neck",
    "SHOULDER" to "Shoulder",
    "ELBOW" to "Elbow",
    "WRIST" to "Wrist",
    "UPPER_BACK" to "Upper back",
    "LOWER_BACK" to "Lower back",
    "HIP" to "Hip",
    "KNEE" to "Knee",
    "ANKLE" to "Ankle",
    "OTHER" to "Somewhere else",
)

/** "How did bench feel?" Easy / Solid / Hard / Too hard, and Something hurt?
 *  opening the areas. Same words and keys as the web (catalog.ts). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackCard(
    name: String,
    effort: String?,
    hurt: String?,
    onChange: (String?, String?) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            EffortChooser(
                question = "How did ${name.lowercase()} feel?",
                effort = effort,
                hurt = hurt,
                onChange = onChange,
            )
        }
    }
}

/**
 * "How did it feel?" for any workout (v0.426.1, the web's EffortPicker): a
 * four-step slider (Easy, Solid, Hard, Too hard) with a check to lock it in,
 * and a small bandage for "something hurt" that opens the areas in a dialog.
 * Moving the slider after locking asks for the check again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EffortChooser(
    question: String,
    effort: String?,
    hurt: String?,
    onChange: (String?, String?) -> Unit,
) {
    val saved = EFFORTS.indexOfFirst { it.first == effort }
    var draft by remember(effort) { mutableStateOf<Float?>(null) }
    var open by remember { mutableStateOf(false) }
    val value = draft ?: (if (saved >= 0) saved.toFloat() else 1f)
    val unset = saved < 0 && draft == null
    val index = kotlin.math.round(value).toInt().coerceIn(0, EFFORTS.lastIndex)
    val locked = saved >= 0 && (draft == null || index == saved)
    val hurtLabel = HURT_AREAS.firstOrNull { it.first == hurt }?.second
    val primary = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(question, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (hurtLabel != null) {
                Text(hurtLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = { open = true }) {
                Icon(
                    KairosIcons.Bandage,
                    contentDescription = if (hurtLabel != null) "Hurt: $hurtLabel" else "Something hurt?",
                    tint = if (hurtLabel != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Slider(
                    value = value,
                    onValueChange = { draft = it },
                    valueRange = 0f..EFFORTS.lastIndex.toFloat(),
                    steps = EFFORTS.size - 2,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = if (unset) MaterialTheme.colorScheme.outline else primary,
                        activeTrackColor = if (unset) MaterialTheme.colorScheme.outlineVariant else primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    EFFORTS.forEachIndexed { i, (_, label) ->
                        val on = !unset && i == index
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.clickable { draft = i.toFloat() },
                        )
                    }
                }
            }
            if (locked) {
                androidx.compose.material3.FilledIconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
                    Icon(KairosIcons.Check, contentDescription = "Saved: ${EFFORTS[saved].second}")
                }
            } else {
                androidx.compose.material3.OutlinedIconButton(
                    onClick = {
                        onChange(EFFORTS[index].first, hurt)
                        draft = null
                    },
                    border = androidx.compose.foundation.BorderStroke(2.dp, primary),
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(KairosIcons.Check, contentDescription = "Save: ${EFFORTS[index].second}", tint = primary)
                }
            }
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Something hurt?") },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HURT_AREAS.forEach { (key, label) ->
                        ChoicePill(label = label, selected = false, warn = hurt == key) {
                            onChange(effort, key)
                            open = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
            dismissButton = if (hurt != null) {
                {
                    TextButton(onClick = {
                        onChange(effort, null)
                        open = false
                    }) { Text("Nothing hurt") }
                }
            } else {
                null
            },
        )
    }
}

/** A choice pill: filled when chosen, red-tinted for a hurt area. */
@Composable
internal fun ChoicePill(label: String, selected: Boolean, warn: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val err = MaterialTheme.colorScheme.error
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primary
                    warn -> err.copy(alpha = 0.08f)
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .border(
                1.dp,
                when {
                    selected -> MaterialTheme.colorScheme.primary
                    warn -> err.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.outline
                },
                shape,
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                selected -> MaterialTheme.colorScheme.onPrimary
                warn -> err
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** The Log workout card's Swap pill. */
@Composable
private fun SmallPill(label: String, icon: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon) {
            Icon(KairosIcons.Swap, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** "2026-10-19" -> "Oct 19". */
private fun shortDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMM d"))
} catch (_: Exception) {
    iso
}

/** "Thursday, October 29". */
private fun dayName(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
} catch (_: Exception) {
    iso
}

/** Same-category movements, by muscle group, with a search box — the log
 *  page's swap picker. */
@Composable
private fun SetsSwapDialog(
    pool: List<PoolExerciseDto>,
    slotId: String,
    title: String,
    onPick: (PoolExerciseDto) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var peek by remember { mutableStateOf<String?>(null) }
    val current = pool.firstOrNull { it.id == slotId }
    val q = query.trim().lowercase()
    val groups = remember(pool, q, current?.id) {
        pool.asSequence()
            .filter { it.id != slotId }
            .filter { current == null || it.category == current.category }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .groupBy { it.muscleGroup }
            .map { (mg, items) ->
                val label = mg?.split('_')?.joinToString(" ") { it.lowercase() }?.replaceFirstChar { it.uppercase() } ?: "Other"
                Triple(mg == null, label, items.sortedBy { it.name.lowercase() })
            }
            .sortedWith(compareBy<Triple<Boolean, String, List<PoolExerciseDto>>> { it.first }.thenBy { it.second.lowercase() })
    }
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = title,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search movements") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (groups.isEmpty()) {
                Text(
                    if (pool.isEmpty()) "Loading movements…" else "No movements match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    groups.forEach { (_, label, items) ->
                        Text(
                            label.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
                        )
                        items.forEach { ex ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    ex.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onPick(ex) }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                )
                                MovementInfoButton(
                                    name = ex.name,
                                    onClick = { peek = if (peek == ex.id) null else ex.id },
                                    tint = if (peek == ex.id) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (peek == ex.id) {
                                MovementAboutInline(name = ex.name, about = ex.about)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One movement of a Core day's screen: what [CoreWorkoutScreen] needs to open its logger. */
data class CoreMoveRef(val slotId: String, val name: String, val metric: String, val unit: String)

/** Route-safe form of the moves: one per line, fields tab-separated. */
fun encodeCoreMoves(moves: List<CoreMoveRef>): String =
    moves.joinToString("\n") { listOf(it.slotId, it.name, it.metric, it.unit).joinToString("\t") { f -> f.replace('\t', ' ').replace('\n', ' ') } }

fun decodeCoreMoves(raw: String): List<CoreMoveRef> =
    raw.split('\n').mapNotNull { line ->
        val f = line.split('\t')
        if (f.size < 4) null else CoreMoveRef(slotId = f[0], name = f[1], metric = f[2], unit = f[3])
    }

/**
 * A Core day on one screen (v0.446): each movement in its own section, the
 * way 5/3/1's warm-up and main sets are, and one Log for the whole workout.
 * Each movement keeps its own logger state (the same one its own page uses),
 * so sets save as they're ticked; Log saves anything typed and goes back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreWorkoutScreen(
    date: String,
    plannedWorkoutId: String,
    title: String,
    moves: List<CoreMoveRef>,
    late: Boolean,
    onDone: () -> Unit,
) {
    val container = rememberContainer()
    val vms = moves.map { m ->
        key(m.slotId) {
            viewModel<MovementSetsViewModel>(
                key = "sets:$date:$plannedWorkoutId:${m.slotId}",
                factory = viewModelFactory {
                    initializer {
                        MovementSetsViewModel(
                            session = container.sessionRepository,
                            date = date,
                            plannedWorkoutId = plannedWorkoutId,
                            slotId = m.slotId,
                            metric = m.metric,
                            unit = m.unit,
                            late = late,
                        )
                    }
                },
            )
        }
    }
    val states = vms.map { it.ui.collectAsState().value }
    var finishing by remember { mutableStateOf(false) }
    val allDone = states.all { it.done }
    LaunchedEffect(finishing, allDone) { if (finishing && allDone) onDone() }
    val finish = {
        if (!finishing) {
            finishing = true
            vms.forEach { it.leave() }
        }
    }
    BackHandler { finish() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { finish() }) { Icon(KairosIcons.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        WorkoutActionTile(
                            icon = KairosIcons.Check,
                            label = "Log",
                            modifier = Modifier.weight(1f),
                            highlighted = true,
                            enabled = !finishing,
                            loading = finishing,
                            filled = true,
                            fixedHeight = 64.dp,
                        ) { finish() }
                    }
                }
            }
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (late) {
                Text(
                    "${dayName(date)} · logging late",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            moves.forEachIndexed { i, m ->
                CoreMoveSection(vm = vms[i], name = m.name, metric = m.metric, unit = m.unit)
            }
        }
    }
}

/** One movement's section on the Core day screen: name, today's target, its sets. */
@Composable
private fun CoreMoveSection(vm: MovementSetsViewModel, name: String, metric: String, unit: String) {
    val ui by vm.ui.collectAsState()
    var showInfo by remember { mutableStateOf(false) }
    var timing by remember { mutableStateOf(false) }
    val shown = ui.doingName ?: name
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                shown.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MovementInfoButton(name = shown, onClick = { showInfo = true })
            SmallPill(label = if (ui.skipped) "Skipped" else "Skip", icon = false) { vm.toggleSkip() }
        }
        if (ui.loading) {
            CircularProgressIndicator(Modifier.size(20.dp))
            return@Column
        }
        if (ui.targetScheme.isNotBlank()) {
            Text("Today ${ui.targetScheme}", style = MaterialTheme.typography.labelLarge)
        }
        if (!ui.skipped) {
            ui.rows.forEachIndexed { i, r ->
                SetCard(
                    index = i + 1,
                    row = r,
                    vm = vm,
                    metric = metric,
                    unit = unit,
                    shift = ui.shift,
                    sub = when {
                        r.added -> "Added today"
                        ui.lastSets.getOrNull(i) != null ->
                            "Last time " + MovementSetsViewModel.fmtSet(ui.lastSets[i], metric)
                        else -> ""
                    },
                    newBest = if (r.saved && vm.isNewBest(r)) vm.valueOf(r).reps else null,
                    jump = vm.jumpFor(r),
                    enabled = true,
                    strict = vm.strict(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(label = "Add set", icon = true) { vm.addSet() }
                if (metric == "DURATION") {
                    PillButton(label = "Timer") {
                        vm.openToolFor(null)
                        timing = true
                    }
                }
                if (!vm.anyEntered() && vm.hasHints()) {
                    PillButton(label = "Same as last time", tinted = true) { vm.sameAsLast() }
                }
            }
            if (ui.rows.any { it.saved }) {
                FeedbackCard(
                    name = shown,
                    effort = ui.effort,
                    hurt = ui.hurt,
                    onChange = { e, h -> vm.setFeedback(e, h) },
                )
            }
        }
    }
    ui.confirmKey?.let { k ->
        val i = ui.rows.indexOfFirst { it.key == k }
        val row = ui.rows.getOrNull(i)
        AlertDialog(
            onDismissRequest = { vm.keep() },
            title = { Text("Remove set ${i + 1}?") },
            text = { if (row != null) Text(MovementSetsViewModel.fmtSet(vm.valueOf(row), metric)) },
            confirmButton = { TextButton(onClick = { vm.remove(k) }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { vm.keep() }) { Text("Keep") } },
        )
    }
    if (showInfo) {
        MovementInfoDialog(name = shown, about = ui.about, onDismiss = { showInfo = false })
    }
    if (timing) {
        ConditioningTimer(
            workout = com.kairos.app.data.remote.dto.ConditioningPlanDto(
                name = shown,
                type = "FOR_TIME",
                resultLabel = "Time",
                resultMetric = "DURATION",
            ),
            onLog = { seconds, _ ->
                timing = false
                vm.useSeconds(seconds)
            },
            onClose = { timing = false },
        )
    }
}
