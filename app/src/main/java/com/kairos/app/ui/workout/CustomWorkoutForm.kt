package com.kairos.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

/** "Log a different workout" — the ad-hoc form (Type / Record / Exercise /
 *  Result / Notes), mirroring the web CustomWorkoutForm. */
@Composable
fun CustomWorkoutForm(date: String, onLogged: () -> Unit) {
    val container = rememberContainer()
    val vm: CustomWorkoutViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CustomWorkoutViewModel(container.sessionRepository, date) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.done) { if (ui.done) onLogged() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Log a different workout",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (ui.loading) {
            CircularProgressIndicator(Modifier.padding(8.dp))
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DropdownField(
                label = "Type",
                selectedLabel = ui.category?.label ?: "",
                options = ui.categories.map { it.key to it.label },
                onSelect = vm::onCategory,
                modifier = Modifier.weight(1f),
            )
            val metrics = ui.category?.metrics.orEmpty()
            if (metrics.size > 1) {
                DropdownField(
                    label = "Record",
                    selectedLabel = ui.metric?.label ?: "",
                    options = metrics.map { it.key to it.label },
                    onSelect = vm::onMetric,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (ui.category?.isPool == true) {
            val exs = ui.exercisesForCategory
            if (exs.isEmpty()) {
                Text(
                    "No exercises in the pool for this type yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DropdownField(
                    label = "Exercise",
                    selectedLabel = exs.firstOrNull { it.id == ui.exerciseId }?.name ?: "",
                    options = exs.map { it.id to it.name },
                    onSelect = vm::onExercise,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = ui.value,
                onValueChange = vm::onValue,
                label = { Text(ui.metric?.label ?: "Result") },
                placeholder = { Text("0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            if ((ui.metric?.unit ?: "").isNotBlank()) {
                Text(
                    ui.metric!!.unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }

        if (ui.category?.load == true) {
            OutlinedTextField(
                value = ui.load,
                onValueChange = vm::onLoad,
                label = { Text("Load (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = ui.notes,
            onValueChange = vm::onNotes,
            label = { Text("Notes (optional)") },
            placeholder = { Text("Rounds, splits, how it felt…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (ui.error != null) {
            Text(ui.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(onClick = vm::submit, enabled = !ui.saving, modifier = Modifier.fillMaxWidth()) {
            if (ui.saving) {
                CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Log workout")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                )
            }
        }
    }
}
