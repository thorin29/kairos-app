package com.kairos.app.ui.workout

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kairos.app.data.remote.dto.PoolExerciseDto
import com.kairos.app.ui.common.AnimatedDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.CircularProgressIndicator
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.delay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kairos.app.ui.common.rememberContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The Log workout page. Shows every planned workout for the day (Core, Arms) as
 * its own card with a value per movement and a Log button, a per-movement Skip
 * (do part, skip the rest), and an Expire action to close a missed workout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLogScreen(
    date: String,
    onDone: () -> Unit,
    onOpenCalculator: () -> Unit,
    usedWeight: String?,
    onWeightConsumed: () -> Unit,
) {
    val container = rememberContainer()
    val vm: WorkoutLogViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WorkoutLogViewModel(container.sessionRepository, date, container.payloadCache) }
        },
    )
    val ui by vm.ui.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    // Which card's movement launched the calculator, so its returned weight goes
    // to the right input.
    // Survives navigating to the calculator and back (a plain remember is wiped
    // when this screen is disposed), so the returned weight reaches the card it
    // came from.
    var pendingTarget by rememberSaveable(
        stateSaver = listSaver(
            save = { pair -> pair?.let { listOf(it.first, it.second) } ?: emptyList() },
            restore = { if (it.size == 2) it[0] to it[1] else null },
        ),
    ) { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(ui.done) { if (ui.done) onDone() }

    LaunchedEffect(usedWeight) {
        val w = usedWeight
        val target = pendingTarget
        if (w != null && target != null) {
            vm.onValue(target.first, target.second, w)
            pendingTarget = null
            onWeightConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log workout") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ui.loadError != null -> Text(
                    ui.loadError!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ui.date?.let { d ->
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Icon(
                                KairosIcons.Calendar,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Logging for ${longDate(d)}")
                        }
                    }

                    val overdueBlocks = ui.blocks.filter { it.isOverdue }
                    val todayBlocks = ui.blocks.filter { !it.isOverdue }
                    val openCalc: (String, String) -> Unit = { blockKey, exId ->
                        pendingTarget = blockKey to exId
                        onOpenCalculator()
                    }

                    if (overdueBlocks.isNotEmpty()) {
                        Text(
                            if (overdueBlocks.size == 1) "Overdue workout" else "Overdue workouts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        overdueBlocks.forEach { block ->
                            WorkoutBlockCard(block, vm, openCalc)
                        }
                    }

                    if (todayBlocks.isNotEmpty()) {
                        Text(
                            "Today's plan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        // Plans sharing a muscle group go on ONE card — "Chest"
                        // once at the top, each workout with its own fields and
                        // buttons under a divider. A plan with no muscle group
                        // keeps its own card (grouping by name would merge two
                        // unrelated plans both called "Workout").
                        groupByMuscle(todayBlocks).forEach { group ->
                            if (group.blocks.size == 1) {
                                WorkoutBlockCard(group.blocks.first(), vm, openCalc)
                            } else {
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            group.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        group.blocks.forEach { block ->
                                            // No divider here: BlockBody draws its
                                            // own above the fields, and two rules
                                            // together read as a double line.
                                            // hideName is always on — the group is
                                            // named at the top and each movement
                                            // names itself, so the plan name would
                                            // be a third copy ("Chest / Chest").
                                            BlockBody(block, vm, openCalc, hideName = true)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (overdueBlocks.isEmpty()) {
                        Text(
                            "No scheduled workouts today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (ui.actionError != null) {
                        Text(
                            ui.actionError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    ui.date?.let { d ->
                        CustomWorkoutForm(date = d, onLogged = onDone)
                    }
                }
            }
        }
    }

    val picking = ui.date
    if (showDatePicker && picking != null) {
        WorkoutDatePickerOverlay(
            dateIso = picking,
            onPick = { vm.setDate(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }

    ui.conflict?.let { c ->
        AlertDialog(
            onDismissRequest = { vm.dismissConflict() },
            title = { Text("Already logged") },
            text = {
                Text(
                    "You already logged ${c.name} today: ${c.summary}. " +
                        "Update it with the new value, or cancel?",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmReplace() }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissConflict() }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WorkoutActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    filled: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val tileHeight = if (compact) 56.dp else 84.dp
    // Icon/text color. A filled tile reads white on the system-color fill while
    // it's the primary action, then settles to the theme color once it greys out.
    // An outlined tile is the theme color while actionable and eases to grey after.
    val contentTarget = when {
        filled && highlighted -> MaterialTheme.colorScheme.onPrimary
        filled -> MaterialTheme.colorScheme.primary
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tint by animateColorAsState(contentTarget, animationSpec = tween(700), label = "tileTint")
    if (filled) {
        // "Log weight": starts as the system color with a white icon/text, then
        // slowly fades to a greyed-out button carrying theme-color icon/text once
        // the block is logged. The fill is pinned across the disabled (saving)
        // state too, so the in-progress button doesn't flash grey.
        val containerTarget =
            if (highlighted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
        val container by animateColorAsState(containerTarget, animationSpec = tween(700), label = "tileFill")
        Card(
            onClick = onClick,
            enabled = enabled,
            colors = CardDefaults.cardColors(
                containerColor = container,
                disabledContainerColor = container,
            ),
            modifier = modifier.height(tileHeight),
        ) { WorkoutActionTileBody(icon, label, tint, loading, compact) }
    } else {
        OutlinedCard(onClick = onClick, enabled = enabled, modifier = modifier.height(tileHeight)) {
            WorkoutActionTileBody(icon, label, tint, loading, compact)
        }
    }
}

@Composable
private fun WorkoutActionTileBody(
    icon: ImageVector,
    label: String,
    tint: Color,
    loading: Boolean,
    compact: Boolean = false,
) {
    val iconSize = if (compact) 16.dp else 22.dp
    Column(
        Modifier.fillMaxSize().padding(if (compact) 4.dp else 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(iconSize), strokeWidth = 2.dp, color = tint)
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
        Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
        Text(
            label,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = tint,
        )
    }
}

@Composable
private fun WorkoutBlockCard(
    block: WorkoutBlock,
    vm: WorkoutLogViewModel,
    onOpenCalculatorFor: (String, String) -> Unit,
) {
    // Overdue and today's cards are the same card. The only difference is the
    // background; they used to be two bodies with different button sizes and
    // wording, which made one screen look like two features.
    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors = if (block.isOverdue) {
            CardDefaults.outlinedCardColors(containerColor = Color(0xFFFEF2F2))
        } else {
            CardDefaults.outlinedCardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BlockBody(block, vm, onOpenCalculatorFor)
        }
    }
}

/** Same-muscle plans share a card; anything without a muscle group stands alone. */
private data class MuscleGroupCard(val key: String, val label: String, val blocks: List<WorkoutBlock>)

/** One muscle group's movements in the swap picker, under its own heading. */
private data class SwapGroup(
    val key: String,
    val label: String,
    val items: List<PoolExerciseDto>,
)

private fun groupByMuscle(blocks: List<WorkoutBlock>): List<MuscleGroupCard> {
    val out = mutableListOf<MuscleGroupCard>()
    val index = mutableMapOf<String, Int>()
    blocks.forEach { b ->
        val key = b.muscleGroup?.takeIf { it.isNotBlank() }?.let { "mg:$it" } ?: "solo:${b.key}"
        val at = index[key]
        if (at == null) {
            index[key] = out.size
            out.add(
                MuscleGroupCard(
                    key = key,
                    label = b.muscleGroup?.takeIf { it.isNotBlank() }?.let { muscleLabel(it) } ?: b.name,
                    blocks = listOf(b),
                ),
            )
        } else {
            out[at] = out[at].copy(blocks = out[at].blocks + b)
        }
    }
    return out
}

/** "UPPER_BACK" -> "Upper back". The server sends the enum name. */
private fun muscleLabel(raw: String): String =
    raw.split('_').joinToString(" ") { it.lowercase() }
        .replaceFirstChar { it.uppercase() }

/** The contents of a planned block: name, movements, and its own action row.
 *  Used both as a card of its own and stacked inside a shared muscle card. */
@Composable
private fun BlockBody(
    block: WorkoutBlock,
    vm: WorkoutLogViewModel,
    onOpenCalculatorFor: (String, String) -> Unit,
    hideName: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!hideName) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    block.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (block.logged) {
                    Icon(
                        KairosIcons.Check,
                        contentDescription = "Logged",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
            // No summary list of movement names here: each movement names
            // itself directly above its own entry field, and printing them
            // again under the muscle group said everything twice.
            HorizontalDivider()
            block.inputs.forEach { m -> MovementRow(block.key, m, vm) }

            val skipped = block.inputs.isNotEmpty() && block.inputs.all { it.skipped }
            // After a log, flash "logged" briefly, then settle on "edit weight".
            var justLogged by remember(block.key) { mutableStateOf(false) }
            LaunchedEffect(block.logged) {
                if (block.logged) {
                    justLogged = true
                    delay(2500)
                    justLogged = false
                } else {
                    justLogged = false
                }
            }
            val logLabel = when {
                block.logged && justLogged -> "logged"
                block.logged -> "edit weight"
                else -> "Log weight"
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutActionTile(
                    icon = KairosIcons.Moon,
                    label = if (skipped && !block.isOverdue) "skipped" else "Rest / skip",
                    modifier = Modifier.weight(1f),
                    highlighted = block.isOverdue || !skipped,
                ) {
                    // On an overdue card this means "I'm not doing that day", so
                    // it has to rest the day on the server. The local toggle left
                    // the task PENDING and the card came straight back.
                    if (block.isOverdue) block.date?.let { vm.restDay(it) }
                    else vm.setBlockSkipped(block.key, !skipped)
                }
                WorkoutActionTile(
                    icon = KairosIcons.Dumbbell,
                    label = "Calculator",
                    modifier = Modifier.weight(1f),
                    highlighted = true,
                ) {
                    onOpenCalculatorFor(
                        block.key,
                        block.inputs.firstOrNull()?.poolExerciseId ?: "",
                    )
                }
                WorkoutActionTile(
                    icon = KairosIcons.Dumbbell,
                    label = logLabel,
                    modifier = Modifier.weight(1f),
                    highlighted = !block.logged,
                    enabled = !block.saving,
                    loading = block.saving,
                    filled = true,
                ) { vm.saveBlock(block.key) }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDatePickerOverlay(
    dateIso: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(dateIso))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(utcMillisToIso(it)) }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

private fun isoToUtcMillis(iso: String): Long =
    try {
        LocalDate.parse(iso, DateTimeFormatter.ISO_DATE)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (_: Exception) {
        Instant.now().toEpochMilli()
    }

private fun utcMillisToIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        .format(DateTimeFormatter.ISO_DATE)

@Composable
private fun MovementRow(planId: String, m: MovementInput, vm: WorkoutLogViewModel) {
    val maxHint = m.metric == "WEIGHT"
    var swapping by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // The movement always names itself, with its swap control on the same
        // line — the muscle group heads the card and no longer repeats these
        // names, so this row is the only place the movement is named.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                m.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (m.skipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (m.skipped) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            // Swap this movement for a variation TODAY only — front squat for back
            // squat. The weekly plan is untouched; next week comes back as planned.
            if (!m.skipped) {
                if (m.swappedFromName != null) {
                    Text(
                        "Undo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .clickable { vm.undoSwap(planId, m.poolExerciseId) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                } else {
                    // An outlined pill, not a bare word: the first version was a
                    // plain label and nobody could tell it was tappable.
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { vm.loadPool(); swapping = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            KairosIcons.Swap,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "Swap",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        if (!m.skipped) {
            if (m.swappedFromName != null) {
                Text(
                    "instead of ${m.swappedFromName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // One fixed width for the weight box on every card. It used to be
            // flexible on overdue and fixed on today's, so the same field was
            // two different sizes on one screen. Reps sits hard right.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = m.value,
                    onValueChange = { vm.onValue(planId, m.poolExerciseId, it) },
                    placeholder = { Text(if (maxHint) "weight" else "0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(128.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(m.unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Reps for the top set. Optional, and only for weights: without it
                // 185 x 5 and 185 x 12 log identically and the months of rep
                // progress between weight jumps never show up anywhere.
                if (maxHint) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "\u00d7",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = m.reps,
                        onValueChange = { vm.onReps(planId, m.poolExerciseId, it) },
                        placeholder = { Text("reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(92.dp),
                    )
                }
            }
        } else {
            Text(
                "Skipped",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (swapping) {
        SwapMovementDialog(planId, m, vm) { swapping = false }
    }
}

/**
 * Pick a variation for one movement, for today only. Same-category movements
 * first (swapping a bench press for a plank is not the point), the current
 * movement's muscle group floated to the top, and a search box because the pool
 * is long. Nothing is written here — the choice only changes what the Log button
 * sends.
 */
@Composable
private fun SwapMovementDialog(
    planId: String,
    m: MovementInput,
    vm: WorkoutLogViewModel,
    onDismiss: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    var query by remember { mutableStateOf("") }
    val current = ui.pool.firstOrNull { it.id == m.poolExerciseId }
    val q = query.trim().lowercase()

    // Grouped by muscle group, with a heading per group. Floating the current
    // group to the top of a flat list was not enough: everything below it ran
    // together alphabetically across every muscle group, so the list read as
    // one jumble. The movement's own group comes first, then the rest
    // alphabetically, with unassigned movements last under "Other".
    val groups = remember(ui.pool, q, current?.id) {
        ui.pool
            .asSequence()
            .filter { it.id != m.poolExerciseId }
            .filter { current == null || it.category == current.category }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .groupBy { it.muscleGroup }
            .map { (mg, items) ->
                SwapGroup(
                    key = mg ?: "_other",
                    label = if (mg != null) muscleLabel(mg) else "Other",
                    items = items.sortedBy { it.name.lowercase() },
                )
            }
            // Straight alphabetical by muscle group, movements alphabetical
            // inside each. Floating the current group to the top meant the list
            // started somewhere different every time it opened.
            .sortedWith(
                compareBy<SwapGroup> { it.key == "_other" }
                    .thenBy { it.label.lowercase() },
            )
    }
    val total = groups.sumOf { it.items.size }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "Swap ${m.name}",
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Today only \u2014 your plan keeps ${m.swappedFromName ?: m.name}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search movements") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                ui.poolLoading -> Text(
                    "Loading movements\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                total == 0 -> Text(
                    if (ui.pool.isEmpty()) "Couldn't load the movement list." else "No movements match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Column(
                    Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                ) {
                    groups.forEach { g ->
                        Text(
                            g.label.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
                        )
                        g.items.forEach { ex ->
                            Text(
                                ex.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        vm.swapMovement(planId, m.poolExerciseId, ex)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Verb-noun for the log button, matching the web (weight/time/distance/reps). */
private fun logNoun(inputs: List<MovementInput>): String {
    val metrics = inputs.map { it.metric }.toSet()
    if (metrics.size != 1) return "workout"
    return when (metrics.first()) {
        "WEIGHT" -> "weight"
        "REPS" -> "reps"
        "DISTANCE" -> "distance"
        "METERS" -> "meters"
        "DURATION" -> "time"
        else -> "workout"
    }
}

/** "2026-09-03" -> "9/3/2026". */
private fun longDate(iso: String): String = try {
    val p = iso.split("-")
    "${p[1].toInt()}/${p[2].toInt()}/${p[0]}"
} catch (e: Exception) {
    iso
}
