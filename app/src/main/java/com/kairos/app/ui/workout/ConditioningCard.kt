package com.kairos.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.ConditioningPlanDto

internal val TYPE_LABEL = mapOf(
    "AMRAP" to "AMRAP",
    "FOR_TIME" to "For time",
    "MAX_SETS" to "Max sets",
    "FOR_REPS" to "For reps",
    "STATIONS" to "Stations",
    "TIMED_STATIONS" to "Timed stations",
    "PYRAMID" to "Pyramid",
    "TABATA" to "Tabata",
    "EMOM" to "EMOM",
)

/**
 * A planned conditioning workout on the day (the web's ConditioningCard and
 * the mockup's "Planned"): what it is, the result to beat, Start (the timer),
 * Log a result without it, Skip, and how it felt once logged.
 */
@Composable
internal fun ConditioningCard(
    workout: ConditioningPlanDto,
    skipped: Boolean,
    /** The result and the version done (null when there's only one). */
    onLog: (Int, String?) -> Unit,
    onSkip: (Boolean) -> Unit,
    onFeedback: (String?, String?) -> Unit,
) {
    var timing by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var version by remember(workout.plannedWorkoutId) { mutableStateOf(if (workout.scaled != null) workout.version else "RX") }
    val movements = if (version == "SCALED") workout.scaled ?: workout.movements else workout.movements
    val logVersion = if (workout.scaled != null) version else null
    val plan = remember(workout, version) { timerPlanFor(workout.type, workout.capSec, movements.map { it.name }) }
    val isTime = workout.resultMetric == "DURATION"

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(workout.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                (TYPE_LABEL[workout.type] ?: workout.type) + (plan.totalSec?.let { " · ${clockText(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            workout.about?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (workout.scaled != null) {
                VersionToggle(value = version, onChange = { version = it })
            }
            movements.forEach { m -> Text(m.line, style = MaterialTheme.typography.bodyMedium) }
            val meta = listOfNotNull(
                workout.today?.let { "Today: $it" },
                workout.last?.let { "Your last: $it" },
                workout.best?.let { "Household best: ${it.name} ${it.result}" },
            )
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (workout.today != null) {
                EffortChooser(
                    question = "How did it feel?",
                    effort = workout.effort,
                    hurt = workout.hurt,
                    onChange = onFeedback,
                )
            }
            if (manual) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { ch -> ch.isDigit() || ch == ':' } },
                    label = { Text(if (isTime) "${workout.resultLabel} (mm:ss)" else workout.resultLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val v: Int? = if (isTime) {
                            Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})$""").find(value.trim())?.let { m ->
                                m.groupValues[1].ifEmpty { "0" }.toInt() * 3600 + m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt()
                            }
                        } else {
                            value.trim().toIntOrNull()?.takeIf { it > 0 }
                        }
                        if (v == null || v <= 0) {
                            error = if (isTime) "Time as mm:ss." else "Enter the ${workout.resultLabel.lowercase()}."
                        } else {
                            error = null
                            manual = false
                            value = ""
                            onLog(v, logVersion)
                        }
                    }) { Text("Save") }
                    TextButton(onClick = { manual = false }) { Text("Cancel") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!skipped) {
                        Button(
                            // Logging from the timer replaces today's result: ask first.
                            onClick = { if (workout.today != null) confirmRestart = true else timing = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (workout.today != null) "Start again" else "Start")
                        }
                        OutlinedButton(onClick = { manual = true }) {
                            Text(if (workout.today != null) "Edit result" else "Log a result")
                        }
                    }
                    if (workout.today == null) {
                        TextButton(onClick = { onSkip(!skipped) }) { Text(if (skipped) "Skipped" else "Skip") }
                    }
                }
            }
        }
    }

    if (confirmRestart) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Already logged today") },
            text = { Text("You logged ${workout.today} today. A new result from the timer will replace it.") },
            confirmButton = {
                TextButton(onClick = { confirmRestart = false; timing = true }) { Text("Start timer") }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancel") } },
        )
    }

    if (timing) {
        ConditioningTimer(
            workout = workout.copy(movements = movements),
            onLog = { v, _ ->
                timing = false
                onLog(v, logVersion)
            },
            onClose = { timing = false },
        )
    }
}

/** Rx or Scaled. */
@Composable
internal fun VersionToggle(value: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoicePill(label = "Rx", selected = value == "RX") { onChange("RX") }
        ChoicePill(label = "Scaled", selected = value == "SCALED") { onChange("SCALED") }
    }
}
