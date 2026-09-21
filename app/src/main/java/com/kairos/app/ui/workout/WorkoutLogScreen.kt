package com.kairos.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
            initializer { WorkoutLogViewModel(container.sessionRepository, date) }
        },
    )
    val ui by vm.ui.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    // Which card's movement launched the calculator, so its returned weight goes
    // to the right input.
    var pendingTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

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
                                KairosIcons.DateRange,
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
                            "Overdue",
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
                        todayBlocks.forEach { block ->
                            WorkoutBlockCard(block, vm, openCalc)
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
    onClick: () -> Unit,
) {
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
            modifier = modifier.height(84.dp),
        ) { WorkoutActionTileBody(icon, label, tint, loading) }
    } else {
        OutlinedCard(onClick = onClick, enabled = enabled, modifier = modifier.height(84.dp)) {
            WorkoutActionTileBody(icon, label, tint, loading)
        }
    }
}

@Composable
private fun WorkoutActionTileBody(
    icon: ImageVector,
    label: String,
    tint: Color,
    loading: Boolean,
) {
    Column(
        Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = tint)
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
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
    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors = if (block.isOverdue) {
            CardDefaults.outlinedCardColors(containerColor = Color(0xFFFEF2F2))
        } else {
            CardDefaults.outlinedCardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Text(
                block.inputs.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                    label = if (skipped) "skipped" else "Rest / skip",
                    modifier = Modifier.weight(1f),
                    highlighted = !skipped,
                ) { vm.setBlockSkipped(block.key, !skipped) }
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    m.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (m.skipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (m.skipped) TextDecoration.LineThrough else null,
                )
                if (maxHint && !m.skipped) {
                    Text(
                        "today's max",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!m.skipped) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = m.value,
                    onValueChange = { vm.onValue(planId, m.poolExerciseId, it) },
                    placeholder = { Text(if (maxHint) "today's max" else "0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(160.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(m.unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                "Skipped",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
