package com.kairos.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.PoolExerciseDto
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons

private val SET_FIELD_W = 76.dp

/**
 * One planned movement's sets for a day. Last time's sets sit in the boxes as
 * grey hints and are never typed over; ✓ saves a set, × removes one (a saved
 * set asks first), Add set goes at the bottom. Log and Back both keep anything
 * typed but not ticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val isTime = metric == "DURATION"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(ui.doingName ?: name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = { vm.leave() }) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            if (ui.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Box
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Swap for the day, or what it replaced and Undo.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ui.doingId != null && ui.doingId != slotId) {
                        Text(
                            "instead of $name",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Pill(label = "Undo") { vm.swapTo(null) }
                    } else if (!ui.skipped) {
                        Pill(label = "Swap", withIcon = true) { vm.loadPool(); swapping = true }
                    }
                }

                ui.rows.forEachIndexed { i, r ->
                    SetRowView(
                        index = i + 1,
                        row = r,
                        vm = vm,
                        isWeight = isWeight,
                        isTime = isTime,
                        unit = unit,
                        shift = ui.shift,
                        enabled = !ui.skipped,
                    )
                }

                if (!ui.skipped) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.addSet() }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(KairosIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Add set", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }

                    val anyUnentered = ui.rows.any { !it.saved && it.w.isBlank() && it.hintW != null }
                    if (isWeight && anyUnentered) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = { vm.shiftBy(-vm.step) }) { Text("−${MovementSetsViewModel.fmt(vm.step)}") }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Remaining sets", style = MaterialTheme.typography.labelMedium)
                                if (ui.shift != 0.0) {
                                    Text(
                                        (if (ui.shift > 0) "+" else "−") + MovementSetsViewModel.fmt(kotlin.math.abs(ui.shift)) + " " + unit,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            OutlinedButton(onClick = { vm.shiftBy(vm.step) }) { Text("+${MovementSetsViewModel.fmt(vm.step)}") }
                        }
                    }

                    if (!vm.anyEntered() && vm.hasHints()) {
                        OutlinedButton(onClick = { vm.sameAsLast() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Same as last time")
                        }
                    }
                }

                if (ui.status.isNotBlank()) {
                    Text(
                        ui.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ui.status.startsWith("Not")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkoutActionTile(
                        icon = KairosIcons.Moon,
                        label = if (ui.skipped) "Skipped" else "Skip",
                        modifier = Modifier.weight(1f),
                        highlighted = !ui.skipped,
                    ) { vm.toggleSkip() }
                    if (isWeight) {
                        WorkoutActionTile(
                            icon = KairosIcons.Dumbbell,
                            label = "Calculator",
                            modifier = Modifier.weight(1f),
                            highlighted = !ui.skipped,
                            enabled = !ui.skipped,
                        ) { onOpenCalculator("Use for set ${vm.nextUnsavedIndex()}") }
                    }
                    WorkoutActionTile(
                        icon = KairosIcons.Check,
                        label = "Log",
                        modifier = Modifier.weight(1f),
                        highlighted = vm.canLog() || ui.skipped,
                        enabled = !ui.leaving,
                        loading = ui.leaving,
                        filled = true,
                    ) { vm.leave() }
                }
            }
        }
    }

    ui.confirmKey?.let { key ->
        val n = ui.rows.indexOfFirst { it.key == key } + 1
        AlertDialog(
            onDismissRequest = { vm.keep() },
            title = { Text("Remove set $n?") },
            confirmButton = { TextButton(onClick = { vm.remove(key) }) { Text("Remove") } },
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
}

@Composable
private fun SetRowView(
    index: Int,
    row: SetRow,
    vm: MovementSetsViewModel,
    isWeight: Boolean,
    isTime: Boolean,
    unit: String,
    shift: Double,
    enabled: Boolean,
) {
    val hintW = vm.hintW(row, shift)?.let { MovementSetsViewModel.fmt(it) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "$index",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
        )
        if (isWeight) {
            SetField(value = row.w, hint = hintW ?: unit, enabled = enabled, decimal = true) { vm.onWeight(row.key, it) }
            Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SetField(value = row.r, hint = row.hintR?.toString() ?: "reps", enabled = enabled, decimal = false) { vm.onReps(row.key, it) }
        } else if (isTime) {
            SetField(value = row.s, hint = row.hintS?.toString() ?: "0", enabled = enabled, decimal = false) { vm.onSeconds(row.key, it) }
            Text("sec", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SetField(value = row.r, hint = row.hintR?.toString() ?: "reps", enabled = enabled, decimal = false) { vm.onReps(row.key, it) }
            Text("reps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { vm.tick(row.key) }, enabled = enabled) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (row.saved) Modifier.padding(0.dp)
                        else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    KairosIcons.Check,
                    contentDescription = if (row.saved) "Saved" else "Save set",
                    tint = if (row.saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (row.saved) 24.dp else 18.dp),
                )
            }
        }
        IconButton(onClick = { vm.askRemove(row.key) }, enabled = enabled) {
            Icon(
                KairosIcons.Close,
                contentDescription = "Remove set",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SetField(
    value: String,
    hint: String,
    enabled: Boolean,
    decimal: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.widthIn(max = SET_FIELD_W),
    )
}

@Composable
private fun Pill(label: String, withIcon: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (withIcon) {
            Icon(KairosIcons.Swap, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Same-category movements, by muscle group, with a search box. */
@Composable
private fun SetsSwapDialog(
    pool: List<PoolExerciseDto>,
    slotId: String,
    title: String,
    onPick: (PoolExerciseDto) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
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
                            Text(
                                ex.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPick(ex) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
