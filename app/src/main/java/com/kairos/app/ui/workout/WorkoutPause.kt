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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.kairos.app.ui.common.QuickDatePickerDialog
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
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

private val PAUSE_REASONS = listOf("SICK" to "Sick", "HURT" to "Injured", "VACATION" to "Vacation", "OTHER" to "Other")
private val PAUSE_LENGTHS = listOf(2L to "3 days", 6L to "1 week", 13L to "2 weeks")

private fun pauseSpan(p: WorkoutPauseDto, today: String): String =
    if (p.from > today) pauseDay(p.from) + " through " + pauseDay(p.to) else "Through " + pauseDay(p.to)

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
 * Workout pauses on the Workouts screen (v0.488; v0.490 a summary that opens
 * the full page): any pause running or ahead, else "Pause workouts".
 */
@Composable
internal fun WorkoutPauseCard(refreshKey: Int, onOpen: () -> Unit) {
    val session = rememberContainer().sessionRepository
    var pauses by remember { mutableStateOf<List<WorkoutPauseDto>?>(null) }
    LaunchedEffect(refreshKey) { pauses = runCatching { session.workoutPauses() }.getOrNull() ?: pauses }
    val list = pauses ?: return
    val today = LocalDate.now().toString()
    val first = list.firstOrNull()
    OutlinedCard(Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (first == null) "Pause workouts" else (if (first.from > today) "Pause ahead" else "Workouts paused") + " · " + first.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (first == null) FontWeight.Normal else FontWeight.SemiBold,
                )
                if (first != null) {
                    Text(
                        pauseSpan(first, today) + if (list.size > 1) " · ${list.size - 1} more" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Pause workouts, a full page (v0.490): household pauses (from Admin) and the
 * person's own, each of theirs with End or Remove; a new one with a reason and
 * its exact first and last day, so one can be planned ahead.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutPauseScreen(onBack: () -> Unit) {
    val session = rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var pauses by remember { mutableStateOf<List<WorkoutPauseDto>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("SICK") }
    var from by remember { mutableStateOf(today) }
    var to by remember { mutableStateOf(today.plusDays(2)) }
    var picking by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { pauses = runCatching { session.workoutPauses() }.getOrNull() ?: emptyList() }
    fun run(block: suspend () -> List<WorkoutPauseDto>) {
        busy = true
        error = null
        scope.launch {
            runCatching { block() }
                .onSuccess { pauses = it }
                .onFailure { error = it.message ?: "Couldn’t save it." }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pause workouts") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(KairosIcons.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val list = pauses.orEmpty()
            if (list.isNotEmpty()) {
                Text("PAUSES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.forEach { p ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.label + if (p.kind == "household") " · household" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(pauseSpan(p, today.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (p.kind == "personal") {
                                TextButton(onClick = { run { session.endWorkoutPause(p.id) } }, enabled = !busy) {
                                    Text(if (p.from > today.toString()) "Remove" else "End")
                                }
                            }
                        }
                    }
                }
            }

            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("NEW PAUSE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Why?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PAUSE_REASONS.forEach { (k, label) -> PauseChip(label = label, on = reason == k) { reason = k } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("First day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { picking = "from" }) { Text(pauseDay(from.toString())) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Last day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { picking = "to" }) { Text(pauseDay(to.toString())) }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PAUSE_LENGTHS.forEach { (n, label) ->
                            val d = from.plusDays(n)
                            PauseChip(label = label, on = to == d) { to = d }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = { run { session.startWorkoutPause(reason = reason, from = from.toString(), until = to.toString()) } },
                        enabled = !busy && !to.isBefore(from),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (from.isAfter(today)) "Plan pause" else "Start pause") }
                }
            }
        }
    }

    when (picking) {
        "from" -> QuickDatePickerDialog(
            initial = from,
            minDate = today,
            maxDate = today.plusDays(365),
            title = "First day",
            onPick = {
                from = it
                if (to.isBefore(it)) to = it
                if (to.isAfter(it.plusDays(179))) to = it.plusDays(179)
                picking = null
            },
            onDismiss = { picking = null },
        )
        "to" -> QuickDatePickerDialog(
            initial = to,
            minDate = from,
            maxDate = from.plusDays(179),
            title = "Last day",
            onPick = {
                to = it
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}
