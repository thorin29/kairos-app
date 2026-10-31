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
    onOpenCalculator: (String) -> Unit,
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
                            WorkoutActionTile(
                                icon = KairosIcons.Dumbbell,
                                label = "Calculator",
                                modifier = Modifier.weight(1f),
                                highlighted = isWeight && !ui.skipped,
                                enabled = isWeight && !ui.skipped,
                                fixedHeight = 64.dp,
                            ) { onOpenCalculator("Use for set ${vm.nextUnsavedIndex()}") }
                            WorkoutActionTile(
                                icon = KairosIcons.Check,
                                label = "Log",
                                modifier = Modifier.weight(1f),
                                highlighted = !ui.skipped,
                                enabled = !ui.leaving,
                                loading = ui.leaving,
                                filled = true,
                                fixedHeight = 64.dp,
                            ) { vm.leave() }
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
                            enabled = !ui.skipped,
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
                        if (isWeight && anyUnentered) {
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
                Column(Modifier.width(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$index", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    if (row.top) {
                        Text(
                            "TOP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
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
                            hint = row.hintR?.toString() ?: "rep",
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
                        .clickable(enabled = enabled) { vm.askRemove(row.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        KairosIcons.Close,
                        contentDescription = "Remove set $index",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
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
            .border(
                1.dp,
                if (tinted) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline,
                shape,
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon) {
            Icon(KairosIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (tinted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

private val EFFORTS = listOf("EASY" to "Easy", "SOLID" to "Solid", "HARD" to "Hard", "TOO_HARD" to "Too hard")
private val HURT_AREAS = listOf(
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
    var open by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("How did ${name.lowercase()} feel?", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EFFORTS.forEach { (key, label) ->
                    ChoicePill(label = label, selected = effort == key) {
                        onChange(if (effort == key) null else key, hurt)
                    }
                }
            }
            val hurtLabel = HURT_AREAS.firstOrNull { it.first == hurt }?.second
            ChoicePill(
                label = if (hurtLabel != null) "Hurt: ${hurtLabel.lowercase()}" else "Something hurt?",
                selected = false,
                warn = hurtLabel != null,
            ) { open = !open }
            if (open) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HURT_AREAS.forEach { (key, label) ->
                        ChoicePill(label = label, selected = false, warn = hurt == key) {
                            onChange(effort, if (hurt == key) null else key)
                            open = false
                        }
                    }
                }
            }
        }
    }
}

/** A choice pill: filled when chosen, red-tinted for a hurt area. */
@Composable
private fun ChoicePill(label: String, selected: Boolean, warn: Boolean = false, onClick: () -> Unit) {
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
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
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
