package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.AddPoolExercise
import com.kairos.app.data.remote.dto.AddPoolRequest
import com.kairos.app.data.remote.dto.PlanOptionsDto

private data class Pick(val tracked: Boolean, val metric: String)
private val DAY_LABELS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkoutSheet(
    day: Int,
    options: PlanOptionsDto,
    busy: Boolean,
    onAddPool: (AddPoolRequest) -> Unit,
    onAddHiit: (String) -> Unit,
    onMarkRest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rest by remember { mutableStateOf(false) }
    var categoryKey by remember { mutableStateOf(options.categories.firstOrNull()?.key ?: "WEIGHTS") }
    var muscleKey by remember { mutableStateOf(options.muscleGroups.firstOrNull()?.key ?: "") }
    var picked by remember { mutableStateOf<Map<String, Pick>>(emptyMap()) }
    var hiitId by remember { mutableStateOf("") }

    val category = options.categories.firstOrNull { it.key == categoryKey }
    val kind = category?.kind ?: "pool"
    val defMetric = category?.defaultMetric ?: ""
    val metrics = category?.metrics.orEmpty()

    val exercises = options.exercises.filter {
        it.category == categoryKey && (kind != "weights" || it.muscleGroup == muscleKey)
    }

    val canSave = when {
        rest -> true
        kind == "hiit" -> hiitId.isNotBlank()
        kind == "metricOnly" -> true
        else -> picked.isNotEmpty()
    }

    fun submit() {
        if (!canSave || busy) return
        when {
            rest -> onMarkRest()
            kind == "hiit" -> onAddHiit(hiitId)
            else -> onAddPool(
                AddPoolRequest(
                    day = day,
                    category = categoryKey,
                    muscleGroup = if (kind == "weights") muscleKey else null,
                    exercises = if (kind == "metricOnly") emptyList()
                    else picked.map { (id, p) -> AddPoolExercise(id, p.tracked, p.metric) },
                ),
            )
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Add to ${DAY_LABELS[day]}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            // Category (Rest + real categories)
            Dropdown(
                label = "Category",
                selected = if (rest) "Rest day" else (category?.label ?: ""),
                options = buildList {
                    add("REST" to "Rest day")
                    options.categories.forEach { add(it.key to it.label) }
                },
                onSelect = { key ->
                    if (key == "REST") rest = true
                    else {
                        rest = false
                        categoryKey = key
                        picked = emptyMap()
                        hiitId = ""
                    }
                },
            )

            when {
                rest -> Note("Marks ${DAY_LABELS[day]} as a planned rest day — no workout expected.")
                kind == "weights" -> {
                    Dropdown(
                        label = "Muscle group",
                        selected = options.muscleGroups.firstOrNull { it.key == muscleKey }?.label ?: "",
                        options = options.muscleGroups.map { it.key to it.label },
                        onSelect = { muscleKey = it; picked = emptyMap() },
                    )
                    ExerciseList(exercises.map { it.id to it.name }, picked, metrics, defMetric) { picked = it }
                }
                kind == "hiit" -> {
                    if (options.hiitWorkouts.isEmpty()) {
                        Note("No named workouts yet. Build one on the web, or log one from “Log a different workout”.")
                    } else {
                        Dropdown(
                            label = "Workout",
                            selected = options.hiitWorkouts.firstOrNull { it.id == hiitId }?.name ?: "Pick a workout…",
                            options = options.hiitWorkouts.map { it.id to (it.name + if (it.personal) " (yours)" else "") },
                            onSelect = { hiitId = it },
                        )
                    }
                }
                kind == "metricOnly" -> Note("Adds a ${category?.label?.lowercase() ?: ""} day — you'll log it on completion.")
                else -> {
                    if (exercises.isEmpty()) Note("No ${category?.label?.lowercase() ?: ""} movements in the pool yet.")
                    else ExerciseList(exercises.map { it.id to it.name }, picked, metrics, defMetric) { picked = it }
                }
            }

            Button(onClick = { submit() }, enabled = canSave && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Adding…" else "Add workout")
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
    )
}

@Composable
private fun ExerciseList(
    items: List<Pair<String, String>>,
    picked: Map<String, Pick>,
    metrics: List<com.kairos.app.data.remote.dto.PlanMetricDto>,
    defMetric: String,
    onChange: (Map<String, Pick>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Exercises", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { (id, name) ->
            val p = picked[id]
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        if (p != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = p != null,
                        onCheckedChange = {
                            onChange(
                                if (p != null) picked - id
                                else picked + (id to Pick(true, defMetric)),
                            )
                        },
                    )
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                if (p != null) {
                    Row(
                        Modifier.padding(start = 12.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = p.tracked,
                            onCheckedChange = { onChange(picked + (id to p.copy(tracked = it))) },
                        )
                        Text("Log a metric", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (p.tracked && metrics.size > 1) {
                            Dropdown(
                                label = "",
                                selected = metrics.firstOrNull { it.key == p.metric }?.label ?: "",
                                options = metrics.map { it.key to it.label },
                                onSelect = { onChange(picked + (id to p.copy(metric = it))) },
                                compact = true,
                            )
                        } else if (p.tracked && metrics.size == 1) {
                            Text(metrics[0].label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    compact: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            Row(
                Modifier
                    .then(if (compact) Modifier else Modifier.fillMaxWidth())
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected, style = MaterialTheme.typography.bodyMedium)
                Text("  ▾", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(key); open = false })
                }
            }
        }
    }
}
