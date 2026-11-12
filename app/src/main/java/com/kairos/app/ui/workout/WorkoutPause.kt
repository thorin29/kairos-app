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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
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

private fun shortDay(iso: String): String = try {
    val d = LocalDate.parse(iso)
    d.month.getDisplayName(TextStyle.SHORT, Locale.US) + " " + d.dayOfMonth
} catch (_: Exception) {
    iso
}

/**
 * What the Workouts screen's Pause button says under its label (v0.494):
 * "Until Nov 14" while one runs, "From Nov 20" for one ahead, else nothing.
 */
@Composable
internal fun pauseLine(refreshKey: Int): String? {
    val session = rememberContainer().sessionRepository
    var pauses by remember { mutableStateOf<List<WorkoutPauseDto>?>(null) }
    LaunchedEffect(refreshKey) { pauses = runCatching { session.workoutPauses() }.getOrNull() ?: pauses }
    val first = pauses?.firstOrNull() ?: return null
    val today = LocalDate.now().toString()
    return if (first.from > today) "From " + shortDay(first.from) else "Until " + shortDay(first.to)
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
    // The range on the calendar (v0.494): first tap the first day, second tap
    // the last; a tap after that starts again.
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
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
                    PauseRangeCalendar(
                        from = from,
                        to = to,
                        today = today,
                        onTap = { d ->
                            val f = from
                            when {
                                f == null || to != null -> {
                                    from = d
                                    to = null
                                }
                                d.isBefore(f) -> from = d
                                d.isAfter(f.plusDays(179)) -> to = f.plusDays(179)
                                else -> to = d
                            }
                        },
                    )
                    val f = from
                    val t = to
                    Text(
                        when {
                            f == null -> "Tap the first day"
                            t == null -> pauseDay(f.toString()) + " \u2192 tap the last day"
                            else -> pauseDay(f.toString()) + " \u2192 " + pauseDay(t.toString()) +
                                " \u00b7 " + (java.time.temporal.ChronoUnit.DAYS.between(f, t) + 1).let { n -> if (n == 1L) "1 day" else "$n days" }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            val ff = from
                            val tt = to
                            if (ff != null && tt != null) {
                                run { session.startWorkoutPause(reason = reason, from = ff.toString(), until = tt.toString()) }
                                from = null
                                to = null
                            }
                        },
                        enabled = !busy && from != null && to != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (from?.isAfter(today) == true) "Plan pause" else "Start pause") }
                }
            }
        }
    }
}

/**
 * A month you can page through (today up to a year on), for picking a pause's
 * range (v0.494): the first and last day filled, the days between tinted,
 * today ringed, days before today greyed out.
 */
@Composable
private fun PauseRangeCalendar(from: LocalDate?, to: LocalDate?, today: LocalDate, onTap: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(java.time.YearMonth.from(from ?: today)) }
    val first = java.time.YearMonth.from(today)
    val last = java.time.YearMonth.from(today.plusDays(365))
    val accent = MaterialTheme.colorScheme.primary
    val band = accent.copy(alpha = 0.16f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }, enabled = month.isAfter(first)) {
                Icon(KairosIcons.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.US) + " " + month.year,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month.isBefore(last)) {
                Icon(KairosIcons.ChevronRight, contentDescription = "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                Text(
                    d,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val lead = month.atDay(1).dayOfWeek.value % 7
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val d = week.getOrNull(i)
                    val inRange = d != null && from != null && to != null && !d.isBefore(from) && !d.isAfter(to)
                    val isEnd = d != null && (d == from || d == to)
                    val off = d == null || d.isBefore(today)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            // The band runs between the ends; half-width at each end.
                            .background(
                                when {
                                    !inRange || from == to -> Color.Transparent
                                    else -> band
                                },
                            )
                            .then(if (!off) Modifier.clickable { onTap(d!!) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (d != null) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (isEnd) accent else Color.Transparent)
                                    .then(
                                        if (d == today && !isEnd) {
                                            Modifier.border(1.dp, accent, androidx.compose.foundation.shape.CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    d.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isEnd) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isEnd -> MaterialTheme.colorScheme.onPrimary
                                        off -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
