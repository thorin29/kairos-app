package com.kairos.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.rememberContainer

/** Pushed screen (from a Home workout prompt) for a specific day; pops on save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLogScreen(date: String, onDone: () -> Unit) {
    val container = rememberContainer()
    val vm: WorkoutLogViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WorkoutLogViewModel(container.sessionRepository, date) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.done) { if (ui.done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.planName ?: "Log workout") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        WorkoutLogContent(Modifier.padding(inner), vm)
    }
}

/** Shared body: today's planned movements (one value each), save, mark/rest.
 *  Reused by the Workouts page and the pushed log screen. */
@Composable
fun WorkoutLogContent(modifier: Modifier, vm: WorkoutLogViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize()) {
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.loadError != null -> Text(
                ui.loadError!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!ui.loggable || ui.inputs.isEmpty()) {
                    item {
                        Text(
                            "No scheduled workouts today. You can still mark the day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    if (ui.planName != null) {
                        item {
                            Text(
                                ui.planName!!,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    items(ui.inputs, key = { it.poolExerciseId }) { m ->
                        MovementRow(m, vm)
                    }
                    item {
                        Button(
                            onClick = vm::save,
                            enabled = !ui.saving,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            if (ui.saving) {
                                CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Save workout")
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = vm::markDone,
                            enabled = !ui.saving,
                            modifier = Modifier.weight(1f),
                        ) { Text("Mark done") }
                        OutlinedButton(
                            onClick = vm::restDay,
                            enabled = !ui.saving,
                            modifier = Modifier.weight(1f),
                        ) { Text("Rest day") }
                    }
                }
                if (ui.actionError != null) {
                    item {
                        Text(
                            ui.actionError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementRow(m: MovementInput, vm: WorkoutLogViewModel) {
    OutlinedTextField(
        value = m.value,
        onValueChange = { vm.onValue(m.poolExerciseId, it) },
        label = { Text("${m.name} (${m.unit})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
