package com.kairos.app.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePersonalWorkoutScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: CreatePersonalWorkoutViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CreatePersonalWorkoutViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showAddCustom by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(ui.done) { if (ui.done) onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Create / Edit workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (ui.editingId != null && ui.locked) {
                        TextButton(onClick = vm::enableEdit) { Text("Edit") }
                    }
                },
            )
        },
    ) { pad ->
        if (ui.loading) {
            Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.padding(32.dp))
            }
            return@Scaffold
        }

        val enabled = !ui.locked

        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Name: type a new one, or pick one of your saved workouts to edit.
            NameCombo(
                name = ui.name,
                enabled = enabled,
                existing = ui.myWorkouts.map { it.id to it.name },
                onName = vm::onName,
                onPickExisting = vm::selectExisting,
                onNew = { vm.startNew("") },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RollPicker(
                    label = "Type",
                    selectedLabel = ui.types.firstOrNull { it.key == ui.typeKey }?.label ?: "",
                    options = ui.types.map { it.key to it.label },
                    onSelect = vm::onType,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                if (ui.capLabel != null) {
                    OutlinedTextField(
                        value = ui.cap,
                        onValueChange = vm::onCap,
                        label = { Text(ui.capLabel!!) },
                        enabled = enabled,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider()
            Text("Exercises", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            ui.rows.forEachIndexed { i, row ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (enabled) {
                            IconButton(onClick = { vm.removeRow(i) }) { Icon(Icons.Filled.Close, "Remove") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricField("Reps", row.reps, { vm.onReps(i, it) }, enabled, Modifier.weight(1f))
                        MetricField("Weight", row.weight, { vm.onWeight(i, it) }, enabled, Modifier.weight(1f))
                        MetricField("Dist", row.distance, { vm.onDistance(i, it) }, enabled, Modifier.weight(1f))
                    }
                }
                HorizontalDivider()
            }

            if (enabled) {
                if (ui.pool.isNotEmpty()) {
                    RollPicker(
                        label = "Add an exercise",
                        selectedLabel = "",
                        options = ui.pool.map { it.id to it.name },
                        onSelect = vm::addExercise,
                        enabled = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedButton(onClick = { showAddCustom = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(KairosIcons.Plus, null, Modifier.width(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add custom exercise")
                }
            }

            OutlinedTextField(
                value = ui.instructions,
                onValueChange = vm::onInstructions,
                label = { Text("Instructions (optional)") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )

            if (ui.error != null) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (ui.editingId != null && !ui.locked) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete workout", color = MaterialTheme.colorScheme.error) }
            }

            Button(
                onClick = vm::submit,
                enabled = ui.canSave && !ui.saving && enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (ui.saving) {
                    CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Save workout")
            }
            Spacer(Modifier.width(1.dp))
        }
    }

    if (showAddCustom) {
        AddCustomExerciseDialog(
            onDismiss = { showAddCustom = false },
            onAdd = { name -> vm.addCustomExercise(name) { showAddCustom = false } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteWorkout() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            title = { Text("Delete this workout?") },
            text = { Text("It'll be removed from your workouts. Copies you've shared are unaffected.") },
        )
    }
}

@Composable
private fun NameCombo(
    name: String,
    enabled: Boolean,
    existing: List<Pair<String, String>>,
    onName: (String) -> Unit,
    onPickExisting: (String) -> Unit,
    onNew: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("Workout name") },
            placeholder = { Text("New workout name") },
            readOnly = !enabled,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(KairosIcons.ChevronDown, "Saved workouts", Modifier.rotate(if (expanded) 180f else 0f))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column {
                    OptionRow("＋ New workout") { onNew(); expanded = false }
                    existing.forEach { (id, label) ->
                        OptionRow(label) { onPickExisting(id); expanded = false }
                    }
                    if (existing.isEmpty()) {
                        Text(
                            "No saved workouts yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun AddCustomExerciseDialog(onDismiss: () -> Unit, onAdd: (name: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (name.trim().length >= 2) onAdd(name.trim()) },
                enabled = name.trim().length >= 2,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("New exercise") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Exercise name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/** A dropdown that rolls open/closed smoothly (instead of appearing instantly). */
@Composable
private fun RollPicker(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .clickable(enabled = enabled) { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    selectedLabel.ifBlank { "Choose…" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selectedLabel.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                KairosIcons.ChevronDown, null,
                Modifier.rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column {
                    options.forEach { (key, lbl) ->
                        OptionRow(lbl) { onSelect(key); expanded = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}
