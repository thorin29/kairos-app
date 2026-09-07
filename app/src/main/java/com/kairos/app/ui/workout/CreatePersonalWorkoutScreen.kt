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
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.RollPicker
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
    val ui by vm.ui.collectAsState()
    var showAddCustom by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
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
                OutlinedButton(onClick = { showManage = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit custom exercises")
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

    if (showManage) {
        ManageExercisesDialog(
            exercises = ui.myExercises.map { it.id to it.name },
            onRename = vm::renameExercise,
            onDelete = vm::deleteExercise,
            onClose = { showManage = false },
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
private fun ManageExercisesDialog(
    exercises: List<Pair<String, String>>,
    onRename: (id: String, name: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onClose: () -> Unit,
) {
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text("Custom exercises", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                if (exercises.isEmpty()) {
                    Text(
                        "You haven't added any custom exercises yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        exercises.forEach { (id, original) ->
                            var name by remember(id) { mutableStateOf(original) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = { onRename(id, name.trim()) },
                                    enabled = name.trim().length >= 2 && name.trim() != original,
                                ) { Text("Save") }
                                IconButton(onClick = { confirmDeleteId = id }) {
                                    Icon(KairosIcons.Trash, "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            confirmButton = {
                TextButton(onClick = { onDelete(id); confirmDeleteId = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel") } },
            title = { Text("Delete this exercise?") },
            text = { Text("It'll be removed from your exercise list and from any of your workouts that use it.") },
        )
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
