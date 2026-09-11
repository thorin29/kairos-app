package com.kairos.app.ui.tasks

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import com.kairos.app.ui.common.TimeFmt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import com.kairos.app.ui.common.RollPicker
import com.kairos.app.ui.common.rememberContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.kairos.app.ui.common.SentenceCaps

private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
private val NICE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignTaskScreen(parentEntry: NavBackStackEntry?, editTaskId: String? = null, onClose: () -> Unit) {
    val container = rememberContainer()
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val owner = parentEntry ?: LocalViewModelStoreOwner.current!!
    val vm: TasksViewModel = viewModel(
        viewModelStoreOwner = owner,
        factory = viewModelFactory { initializer { TasksViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()
    val scope = rememberCoroutineScope()
    val seenRecurNoDue by container.settingsStore.seenRecurNoDue.collectAsState(initial = false)
    val data = ui.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editTaskId != null) "Edit task" else "Assign a task") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { inner ->
        if (data == null) {
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        // Put the current user (a parent/admin) first and default to them, rather
        // than whichever person the server happened to list first (often a child).
        val orderedPeople = remember(data.canActFor, data.meId) {
            data.canActFor.filter { it.id == data.meId } +
                data.canActFor.filter { it.id != data.meId }
        }
        var selectedPerson by remember { mutableStateOf<String?>(null) }
        val personId = selectedPerson ?: orderedPeople.firstOrNull()?.id ?: data.meId
        var title by remember { mutableStateOf("") }
        var hasDue by remember { mutableStateOf(false) }
        var dueDate by remember { mutableStateOf(LocalDate.now().toString()) }
        var showDate by remember { mutableStateOf(false) }

        // Advanced (optional): due date + recurrence, mirroring web admin.
        var advanced by remember { mutableStateOf(false) }
        var repeats by remember { mutableStateOf(false) }
        var freq by remember { mutableStateOf("WEEKLY") }
        var interval by remember { mutableStateOf("1") }
        var byday by remember { mutableStateOf(emptySet<String>()) }
        var endMode by remember { mutableStateOf("NEVER") }
        var count by remember { mutableStateOf("10") }
        var until by remember { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }
        var showUntil by remember { mutableStateOf(false) }
        var monthStart by remember { mutableStateOf(LocalDate.now().toString()) }
        var showStart by remember { mutableStateOf(false) }
        var showRecurNotice by remember { mutableStateOf(false) }
        var notify by remember { mutableStateOf(false) }
        var notifyMin by remember { mutableStateOf(9 * 60) }
        var showNotifyTime by remember { mutableStateOf(false) }
        var showDeleteConfirm by remember { mutableStateOf(false) }

        // Edit mode: load the task's form and pre-fill everything once.
        var prefilled by remember { mutableStateOf(false) }
        LaunchedEffect(editTaskId) {
            if (editTaskId == null || prefilled) return@LaunchedEffect
            val e = runCatching { container.sessionRepository.loadTaskEdit(editTaskId) }.getOrNull()
                ?: return@LaunchedEffect
            selectedPerson = e.userId
            title = e.title
            advanced = e.recurring || e.dueDate != null || e.notifyMinutes != null
            hasDue = !e.recurring && e.dueDate != null
            if (e.dueDate != null) dueDate = e.dueDate
            repeats = e.recurring
            freq = e.freq
            interval = e.interval.toString()
            if (e.byday.isNotEmpty()) byday = e.byday.toSet()
            endMode = e.endMode
            if (e.maxCount != null) count = e.maxCount.toString()
            if (e.until.isNotBlank()) until = e.until
            if (e.startDate.isNotBlank()) monthStart = e.startDate
            notify = e.notifyMinutes != null
            if (e.notifyMinutes != null) notifyMin = e.notifyMinutes
            prefilled = true
        }

        val personName = orderedPeople.firstOrNull { it.id == personId }?.name ?: ""
        val dueLabel = try { LocalDate.parse(dueDate).format(NICE) } catch (_: Exception) { dueDate }
        val untilLabel = try { LocalDate.parse(until).format(NICE) } catch (_: Exception) { until }
        val startLabel = try { LocalDate.parse(monthStart).format(NICE) } catch (_: Exception) { monthStart }

        Column(
            Modifier.padding(inner).fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        focusManager.clearFocus()
                    }
                }
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (orderedPeople.size > 1) {
                        RollPicker("For", personName, orderedPeople.map { it.id to it.name }, { selectedPerson = it }, !ui.busy, Modifier.fillMaxWidth())
                    }
                    Labeled("Task") { Field(title, { title = it.take(120) }, "e.g. Wash the car") }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Advanced", style = MaterialTheme.typography.bodyLarge)
                            Text("Due date, recurrence, and reminders", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = advanced, onCheckedChange = { advanced = it })
                    }

                    if (advanced) {
                        if (!repeats) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Due date", style = MaterialTheme.typography.bodyLarge)
                                Text("Optional \u2014 otherwise it's due today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = hasDue, onCheckedChange = { hasDue = it })
                        }
                        if (hasDue) {
                            Box(
                                Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                    .clickable { showDate = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Text(dueLabel, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Repeat", style = MaterialTheme.typography.bodyLarge)
                                Text("Make this a recurring task", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = repeats, onCheckedChange = { on ->
                                repeats = on
                                if (on) {
                                    hasDue = false
                                    if (!seenRecurNoDue) {
                                        showRecurNotice = true
                                        scope.launch { container.settingsStore.setSeenRecurNoDue() }
                                    }
                                }
                            })
                        }

                        if (repeats) {
                            RollPicker("Frequency", freqLabel(freq), listOf("DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly"), { freq = it }, !ui.busy, Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Every", style = MaterialTheme.typography.bodyLarge)
                                Box(Modifier.width(64.dp)) {
                                    Field(interval, { interval = it.filter { c -> c.isDigit() }.take(2) }, "1")
                                }
                                Text(freqUnit(freq), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (freq == "MONTHLY") {
                                Text("Starts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box(
                                    Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                        .clickable { showStart = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    Text(startLabel, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            if (freq == "WEEKLY") {
                                Text("On", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    WEEKDAYS.forEach { (code, label) ->
                                        val on = code in byday
                                        Box(
                                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { byday = if (on) byday - code else byday + code }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            RollPicker("Ends", endLabel(endMode), listOf("NEVER" to "Never", "COUNT" to "After a number of times", "UNTIL" to "On a date"), { endMode = it }, !ui.busy, Modifier.fillMaxWidth())
                            if (endMode == "COUNT") {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("After", style = MaterialTheme.typography.bodyLarge)
                                    Box(Modifier.width(72.dp)) {
                                        Field(count, { count = it.filter { c -> c.isDigit() }.take(3) }, "10")
                                    }
                                    Text("times", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (endMode == "UNTIL") {
                                Box(
                                    Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                        .clickable { showUntil = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    Text(untilLabel, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }

                        if (repeats || hasDue) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Remind me", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (repeats) "Alert at this time on each date" else "Alert on the due date",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = notify, onCheckedChange = { notify = it })
                            }
                            if (notify) {
                                Box(
                                    Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                        .clickable { showNotifyTime = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    Text(TimeFmt.clock(notifyMin), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val wantNotify = advanced && notify && (repeats || hasDue)
                    val recur = if (advanced && repeats) {
                        com.kairos.app.data.remote.dto.RecurRequest(
                            freq = freq,
                            interval = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            byday = if (freq == "WEEKLY") byday.toList() else emptyList(),
                            startDate = if (freq == "MONTHLY") monthStart else LocalDate.now().toString(),
                            endMode = endMode,
                            maxCount = if (endMode == "COUNT") count.toIntOrNull() else null,
                            until = if (endMode == "UNTIL") until else "",
                            notifyMinutes = if (wantNotify) notifyMin else null,
                        )
                    } else {
                        null
                    }
                    val due = if (advanced && hasDue && !repeats) dueDate else null
                    val oneOffNotify = if (wantNotify && !repeats) notifyMin else null
                    if (editTaskId != null) {
                        scope.launch {
                            runCatching {
                                container.sessionRepository.updateTask(
                                    editTaskId, personId, title.trim(), due, recur, oneOffNotify,
                                )
                            }
                            com.kairos.app.data.notifications.NotificationWorker.enqueueOnce(context)
                            onClose()
                        }
                    } else {
                        vm.add(personId, title.trim(), due, recur, oneOffNotify) {
                            if (wantNotify) {
                                com.kairos.app.data.notifications.NotificationWorker.enqueueOnce(context)
                            }
                            onClose()
                        }
                    }
                },
                enabled = !ui.busy && personId.isNotBlank() && title.trim().length >= 2,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (editTaskId != null) "Save" else "Assign") }

            if (editTaskId != null) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete task", color = MaterialTheme.colorScheme.error) }
            }
        }

        if (showDate) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = try {
                    LocalDate.parse(dueDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                },
            )
            DatePickerDialog(
                onDismissRequest = { showDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { ms ->
                            dueDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().format(ISO)
                        }
                        showDate = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
            ) {
                DatePicker(state = state)
            }
        }

        if (showNotifyTime) {
            TaskTimePickerDialog(
                initialMin = notifyMin,
                onConfirm = { notifyMin = it; showNotifyTime = false },
                onDismiss = { showNotifyTime = false },
            )
        }

        if (showDeleteConfirm && editTaskId != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete this task?") },
                text = {
                    Text(
                        if (repeats) "This removes the whole repeating series."
                        else "This removes the task.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            runCatching { container.sessionRepository.deleteTask(editTaskId) }
                            com.kairos.app.data.notifications.NotificationWorker.enqueueOnce(context)
                            onClose()
                        }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                },
            )
        }

        if (showRecurNotice) {
            AlertDialog(
                onDismissRequest = { showRecurNotice = false },
                title = { Text("No due dates for recurrent tasks") },
                text = { Text("Recurrent tasks do not use due dates, the end date is determined through recurrence settings.") },
                confirmButton = { TextButton(onClick = { showRecurNotice = false }) { Text("Got it") } },
            )
        }

        if (showStart) {
            val sstate = rememberDatePickerState(
                initialSelectedDateMillis = try {
                    LocalDate.parse(monthStart).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                },
            )
            DatePickerDialog(
                onDismissRequest = { showStart = false },
                confirmButton = {
                    TextButton(onClick = {
                        sstate.selectedDateMillis?.let { ms ->
                            monthStart = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().format(ISO)
                        }
                        showStart = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showStart = false }) { Text("Cancel") } },
            ) {
                DatePicker(state = sstate)
            }
        }
        if (showUntil) {
            val ustate = rememberDatePickerState(
                initialSelectedDateMillis = try {
                    LocalDate.parse(until).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                },
            )
            DatePickerDialog(
                onDismissRequest = { showUntil = false },
                confirmButton = {
                    TextButton(onClick = {
                        ustate.selectedDateMillis?.let { ms ->
                            until = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().format(ISO)
                        }
                        showUntil = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showUntil = false }) { Text("Cancel") } },
            ) {
                DatePicker(state = ustate)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TaskTimePickerDialog(initialMin: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = androidx.compose.material3.rememberTimePickerState(
        initialHour = initialMin / 60,
        initialMinute = initialMin % 60,
        is24Hour = TimeFmt.military,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
                }
            }
        }
    }
}

private val WEEKDAYS = listOf(
    "SU" to "Su", "MO" to "Mo", "TU" to "Tu", "WE" to "We", "TH" to "Th", "FR" to "Fr", "SA" to "Sa",
)

private fun dowCode(d: java.time.DayOfWeek): String =
    listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")[d.value - 1]

private fun freqLabel(f: String) = when (f) { "DAILY" -> "Daily"; "MONTHLY" -> "Monthly"; else -> "Weekly" }

private fun freqUnit(f: String) = when (f) { "DAILY" -> "days"; "MONTHLY" -> "months"; else -> "weeks" }

private fun endLabel(e: String) = when (e) { "COUNT" -> "After a number of times"; "UNTIL" -> "On a date"; else -> "Never" }

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String) {
    Box(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            keyboardOptions = SentenceCaps,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            },
        )
    }
}
