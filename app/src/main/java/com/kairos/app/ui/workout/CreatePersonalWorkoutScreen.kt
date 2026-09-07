package com.kairos.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.rememberContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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

    LaunchedEffect(ui.done) { if (ui.done) onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Create workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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

        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = ui.name,
                onValueChange = vm::onName,
                label = { Text("Workout name") },
                placeholder = { Text("e.g. Leg Burner") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(
                    label = "Type",
                    selectedLabel = ui.types.firstOrNull { it.key == ui.typeKey }?.label ?: "",
                    options = ui.types.map { it.key to it.label },
                    onSelect = vm::onType,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = ui.capSec,
                    onValueChange = vm::onCap,
                    label = { Text("Cap (sec)") },
                    placeholder = { Text("—") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()
            Text("Movements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            ui.rows.forEachIndexed { i, row ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.removeRow(i) }) { Icon(Icons.Filled.Close, "Remove") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricField("Reps", row.reps, { vm.onReps(i, it) }, Modifier.weight(1f))
                        MetricField("Weight", row.weight, { vm.onWeight(i, it) }, Modifier.weight(1f))
                        MetricField("Dist", row.distance, { vm.onDistance(i, it) }, Modifier.weight(1f))
                    }
                }
                HorizontalDivider()
            }

            // Add an existing movement from the pool (shared + your own).
            if (ui.pool.isNotEmpty()) {
                PickerField(
                    label = "Add a movement",
                    selectedLabel = "",
                    options = ui.pool.map { it.id to it.name },
                    onSelect = vm::addMovement,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedButton(onClick = { showAddCustom = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(KairosIcons.Plus, null, Modifier.width(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add custom movement")
            }

            OutlinedTextField(
                value = ui.notes,
                onValueChange = vm::onNotes,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (ui.error != null) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = vm::submit,
                enabled = ui.canSave && !ui.saving,
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
        AddCustomMovementDialog(
            categories = ui.categories.filter { it.isPool }.map { it.key to it.label },
            onDismiss = { showAddCustom = false },
            onAdd = { cat, name -> vm.addCustomMovement(cat, name) { showAddCustom = false } },
        )
    }
}

@Composable
private fun MetricField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomMovementDialog(
    categories: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onAdd: (category: String, name: String) -> Unit,
) {
    var category by remember { mutableStateOf(categories.firstOrNull()?.first ?: "") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (name.trim().length >= 2 && category.isNotBlank()) onAdd(category, name.trim()) },
                enabled = name.trim().length >= 2 && category.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("New movement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(
                    label = "Category",
                    selectedLabel = categories.firstOrNull { it.first == category }?.second ?: "",
                    options = categories,
                    onSelect = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Movement name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerField(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        androidx.compose.material3.ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, lbl) ->
                DropdownMenuItem(text = { Text(lbl) }, onClick = { onSelect(key); expanded = false })
            }
        }
    }
}
