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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.rememberContainer

/**
 * The Log workout page. Mirrors the web (src/app/person/[id]/workout-launcher +
 * workout-card TodayPlan): a "Today's plan" card with a "today's max" input per
 * movement and a Log button. The "Log a different workout" section (the custom
 * ad-hoc form) is the next increment.
 */
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
                title = { Text("Log workout") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    ui.date?.let {
                        Text(
                            "Logging for ${longDate(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (!ui.loggable || ui.inputs.isEmpty()) {
                        Text(
                            "No scheduled workouts today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Today's plan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ui.planName?.let {
                                    Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    ui.inputs.joinToString(" · ") { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                HorizontalDivider()
                                ui.inputs.forEach { m -> MovementRow(m, vm) }
                                Button(
                                    onClick = vm::save,
                                    enabled = !ui.saving,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (ui.saving) {
                                        CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("Log ${logNoun(ui.inputs)}")
                                }
                            }
                        }
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
}

@Composable
private fun MovementRow(m: MovementInput, vm: WorkoutLogViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(m.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "today's max",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = m.value,
            onValueChange = { vm.onValue(m.poolExerciseId, it) },
            placeholder = { Text("today's max") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(128.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(m.unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
