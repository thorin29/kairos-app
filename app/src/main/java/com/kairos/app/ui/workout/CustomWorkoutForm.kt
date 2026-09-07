package com.kairos.app.ui.workout

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.rememberContainer

/** The "Log something else" entry point: a button that opens a step-by-step
 *  wizard (type -> details -> log), so the options aren't all crowded at once. */
@Composable
fun CustomWorkoutForm(date: String, onLogged: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Log something else")
    }
    if (open) {
        LogSomethingWizard(
            date = date,
            onLogged = { open = false; onLogged() },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun LogSomethingWizard(date: String, onLogged: () -> Unit, onDismiss: () -> Unit) {
    val container = rememberContainer()
    val vm: CustomWorkoutViewModel = viewModel(
        key = "logwizard-$date",
        factory = viewModelFactory {
            initializer { CustomWorkoutViewModel(container.sessionRepository, date) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ui.done) { if (ui.done) onLogged() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (ui.step == WizardStep.TYPE) onDismiss() else vm.back() }) {
                        Icon(
                            if (ui.step == WizardStep.TYPE) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (ui.step == WizardStep.TYPE) "Close" else "Back",
                        )
                    }
                    Text(stepTitle(ui), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }

                if (ui.loading) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.padding(32.dp))
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        when (ui.step) {
                            WizardStep.TYPE ->
                                ui.categories.forEach { c -> PickRow(c.label) { vm.pickType(c.key) } }
                            WizardStep.MUSCLE -> {
                                val muscles = ui.musclesWithExercises
                                if (muscles.isEmpty()) EmptyNote("No weights exercises in the pool yet.")
                                else muscles.forEach { m -> PickRow(m.label) { vm.pickMuscle(m.key) } }
                            }
                            WizardStep.EXERCISE -> {
                                val exs = ui.exercisesForStep
                                if (exs.isEmpty()) EmptyNote("No exercises here yet.")
                                else exs.forEach { e -> PickRow(e.name) { vm.pickExercise(e.id) } }
                            }
                            WizardStep.WORKOUT -> {
                                if (ui.hiitWorkouts.isEmpty()) EmptyNote("No HIIT/CrossFit workouts yet — create one first.")
                                else ui.hiitWorkouts.forEach { w -> PickRow(w.name) { vm.pickWorkout(w.id) } }
                            }
                            WizardStep.VALUE -> ValueStep(ui, vm)
                        }
                        Spacer(Modifier.width(1.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueStep(ui: CustomUiState, vm: CustomWorkoutViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val what = when {
            ui.isHiit -> ui.selectedHiit?.name ?: ""
            ui.isWeights || ui.category?.isPool == true -> ui.exercisesForStep.firstOrNull { it.id == ui.exerciseId }?.name ?: ""
            else -> ui.category?.label ?: ""
        }
        if (what.isNotBlank()) {
            Text(what, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        // For pool categories that support both a time and reps (sport/stretch/etc.)
        val metrics = if (ui.isHiit) emptyList() else ui.category?.metrics.orEmpty()
        if (metrics.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { m ->
                    val sel = ui.metricKey == m.key
                    Text(
                        m.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                1.dp,
                                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(20.dp),
                            )
                            .clickable { vm.pickMetric(m.key) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = ui.value,
                onValueChange = vm::onValue,
                label = { Text(ui.resultLabel) },
                placeholder = { Text("0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            if (ui.resultUnit.isNotBlank()) {
                Text(
                    ui.resultUnit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }

        if (ui.showLoad) {
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
            Text("Log it")
        }
    }
}

@Composable
private fun PickRow(label: String, onClick: () -> Unit) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
        )
        HorizontalDivider()
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

private fun stepTitle(ui: CustomUiState): String = when (ui.step) {
    WizardStep.TYPE -> "What did you do?"
    WizardStep.MUSCLE -> "Muscle group"
    WizardStep.EXERCISE -> "Exercise"
    WizardStep.WORKOUT -> "Which workout?"
    WizardStep.VALUE -> "Log it"
}
