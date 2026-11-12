package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.WorkoutPauseDto
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

private fun pauseDay(iso: String): String = try {
    val d = LocalDate.parse(iso)
    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) + ", " + d.month.getDisplayName(TextStyle.SHORT, Locale.US) + " " + d.dayOfMonth
} catch (_: Exception) {
    iso
}

private val PAUSE_REASONS = listOf("SICK" to "Sick", "HURT" to "Injured", "OTHER" to "Other")
private val PAUSE_LENGTHS = listOf(2L to "3 days", 6L to "1 week", 13L to "2 weeks")

@Composable
private fun PauseChip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The person's workout pause (v0.488), in the Workouts menu: the pause running
 * or ahead with End, or "Pause workouts" to start one from today. A household
 * pause (Admin) shows here too, without End.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutPausePanel() {
    val session = com.kairos.app.ui.common.rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    var pauses by remember { mutableStateOf<List<WorkoutPauseDto>?>(null) }
    var open by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("SICK") }
    var days by remember { mutableStateOf(2L) }
    var busy by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    LaunchedEffect(Unit) { pauses = runCatching { session.workoutPauses() }.getOrNull() }
    val list = pauses ?: return
    val mine = list.firstOrNull { it.kind == "personal" }
    fun run(block: suspend () -> List<WorkoutPauseDto>) {
        busy = true
        scope.launch {
            runCatching { block() }.onSuccess { pauses = it }
            busy = false
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (list.filter { it.kind == "household" } + listOfNotNull(mine)).forEach { p ->
            val ahead = p.from > today.toString()
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (ahead) "Pause ahead" else "Workouts paused") + " · " + p.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (ahead) pauseDay(p.from) + " through " + pauseDay(p.to) else "Through " + pauseDay(p.to),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (p.kind == "personal") {
                        TextButton(onClick = { run { session.endWorkoutPause(id = p.id) } }, enabled = !busy) { Text("End") }
                    }
                }
            }
        }
        if (mine == null) {
            OutlinedCard(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                    reason = "SICK"
                    days = 2L
                    open = true
                },
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Pause workouts", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Pause workouts") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Why?", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PAUSE_REASONS.forEach { (k, label) -> PauseChip(label = label, on = reason == k) { reason = k } }
                    }
                    Text("From today through", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PAUSE_LENGTHS.forEach { (n, label) -> PauseChip(label = label, on = days == n) { days = n } }
                    }
                    Text(pauseDay(today.plusDays(days).toString()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val until = today.plusDays(days).toString()
                        run { session.startWorkoutPause(reason = reason, until = until) }
                        open = false
                    },
                    enabled = !busy,
                ) { Text("Start pause") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}
