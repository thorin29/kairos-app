package com.kairos.app.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.rememberContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WEEKDAYS = listOf(
    "MO" to "Mon", "TU" to "Tue", "WE" to "Wed", "TH" to "Thu",
    "FR" to "Fri", "SA" to "Sat", "SU" to "Sun",
)
private val REMINDER_PRESETS = listOf(10 to "10 min", 15 to "15 min", 30 to "30 min", 60 to "1 hr", 1440 to "1 day")
private val COLORS = listOf(
    "" to "Default", "#2563eb" to "Blue", "#059669" to "Green", "#dc2626" to "Red",
    "#d97706" to "Orange", "#7c3aed" to "Purple", "#0d9488" to "Teal",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarAddClassScreen(onDone: () -> Unit) {
    val container = rememberContainer()
    val vm: CalendarAddClassViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CalendarAddClassViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsState()
    LaunchedEffect(ui.done) { if (ui.done) onDone() }

    var openSelector by remember { mutableStateOf<String?>(null) }
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var showFrom by remember { mutableStateOf(false) }
    var showUntil by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add class") },
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
                !ui.canMakeClass -> Text(
                    "Only an admin can add a class.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Subject
                    FieldLabel("Subject")
                    OutlinedTextField(
                        value = ui.subject,
                        onValueChange = vm::setSubject,
                        singleLine = true,
                        placeholder = { Text("Biology, Math, Piano…") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (ui.subjectNames.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ui.subjectNames.forEach { name ->
                                FilterChip(
                                    selected = ui.subject == name,
                                    onClick = { vm.setSubject(name) },
                                    label = { Text(name) },
                                )
                            }
                        }
                    }

                    // Student (admins) / Type row is implicit — this is always Class here.
                    if (ui.isAdmin) {
                        FieldLabel("Student")
                        SelectRow(studentName(ui)) { openSelector = "student" }
                    }

                    // Meets on
                    FieldLabel("Meets on")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WEEKDAYS.forEach { (tok, label) ->
                            FilterChip(
                                selected = tok in ui.byday,
                                onClick = { vm.toggleDay(tok) },
                                label = { Text(label) },
                            )
                        }
                    }

                    if (ui.byday.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                FieldLabel("Start time")
                                SelectRow(timeLabel(ui.startMin)) { showStart = true }
                            }
                            Column(Modifier.weight(1f)) {
                                FieldLabel("End time")
                                SelectRow(timeLabel(ui.endMin)) { showEnd = true }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                FieldLabel("Runs from (opt.)")
                                SelectRow(ui.runsFrom.ifBlank { "Any" }, muted = ui.runsFrom.isBlank()) { showFrom = true }
                            }
                            Column(Modifier.weight(1f)) {
                                FieldLabel("Runs until (opt.)")
                                SelectRow(ui.runsUntil.ifBlank { "Any" }, muted = ui.runsUntil.isBlank()) { showUntil = true }
                            }
                        }
                    }

                    // Class type / Semester / Color
                    FieldLabel("Class type")
                    SelectRow(optName(ui.classTypes, ui.classTypeId, "Choose a type…"), muted = ui.classTypeId.isBlank()) { openSelector = "classType" }
                    FieldLabel("Semester")
                    SelectRow(optName(ui.terms, ui.termId, "Repeats with no end date"), muted = ui.termId.isBlank()) { openSelector = "term" }
                    FieldLabel("Color")
                    Row(
                        Modifier.fillMaxWidth().clickable { openSelector = "color" }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(shape = CircleShape, color = swatch(ui.color), modifier = Modifier.size(18.dp)) {}
                        Text(COLORS.firstOrNull { it.first == ui.color }?.second ?: "Default", modifier = Modifier.weight(1f))
                        Icon(com.kairos.app.ui.nav.KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }

                    // Shared with + Reminders
                    if (ui.students.isNotEmpty()) {
                        FieldLabel("Shared with")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ui.students.filter { it.first != ownerId(ui) }.forEach { (id, name) ->
                                FilterChip(
                                    selected = id in ui.sharedWith,
                                    onClick = { vm.toggleShared(id) },
                                    label = { Text(name) },
                                )
                            }
                        }
                    }
                    FieldLabel("Reminders")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        REMINDER_PRESETS.forEach { (min, label) ->
                            FilterChip(
                                selected = min in ui.reminders,
                                onClick = { vm.toggleReminder(min) },
                                label = { Text(label) },
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Ask about homework after class", Modifier.weight(1f))
                        Switch(checked = ui.promptHomework, onCheckedChange = vm::setHomework)
                    }

                    if (ui.error != null) {
                        Text(ui.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = vm::save,
                        enabled = !ui.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (ui.saving) {
                            CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Add class")
                    }
                    Spacer(Modifier.width(1.dp))
                }
            }
        }
    }

    // Selector overlays
    when (openSelector) {
        "student" -> SelectorOverlay("Student", onClose = { openSelector = null }) {
            ui.students.forEach { (id, name) ->
                SelectOptionRow(name, ui.studentId == id) { vm.setStudent(id); openSelector = null }
            }
        }
        "classType" -> SelectorOverlay("Class type", onClose = { openSelector = null }) {
            SelectOptionRow("Choose a type…", ui.classTypeId.isBlank()) { vm.setClassType(""); openSelector = null }
            ui.classTypes.forEach { (id, name) ->
                SelectOptionRow(name, ui.classTypeId == id) { vm.setClassType(id); openSelector = null }
            }
        }
        "term" -> SelectorOverlay("Semester", onClose = { openSelector = null }) {
            SelectOptionRow("Repeats with no end date", ui.termId.isBlank()) { vm.setTerm(""); openSelector = null }
            ui.terms.forEach { (id, name) ->
                SelectOptionRow(name, ui.termId == id) { vm.setTerm(id); openSelector = null }
            }
        }
        "color" -> SelectorOverlay("Color", onClose = { openSelector = null }) {
            COLORS.forEach { (hex, name) ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setColor(hex); openSelector = null }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(shape = CircleShape, color = swatch(hex), modifier = Modifier.size(20.dp)) {}
                    Text(name, Modifier.weight(1f))
                    RadioButton(selected = ui.color == hex, onClick = { vm.setColor(hex); openSelector = null })
                }
            }
        }
    }

    if (showStart) TimePickerDialog(ui.startMin, { vm.setStart(it); showStart = false }, { showStart = false })
    if (showEnd) TimePickerDialog(ui.endMin, { vm.setEnd(it); showEnd = false }, { showEnd = false })
    if (showFrom) ClassDatePicker(ui.runsFrom, { vm.setRunsFrom(it); showFrom = false }, { showFrom = false })
    if (showUntil) ClassDatePicker(ui.runsUntil, { vm.setRunsUntil(it); showUntil = false }, { showUntil = false })
}

private fun studentName(ui: ClassFormUiState): String =
    ui.students.firstOrNull { it.first == ui.studentId }?.second ?: "Choose"

private fun ownerId(ui: ClassFormUiState): String =
    if (ui.isAdmin) ui.studentId else ""

private fun optName(opts: List<Pair<String, String>>, id: String, empty: String): String =
    opts.firstOrNull { it.first == id }?.second ?: empty

private fun swatch(hex: String): Color =
    if (hex.isBlank()) Color(0xFFE2E8F0) else try {
        Color(("FF" + hex.removePrefix("#")).toLong(16))
    } catch (_: Exception) { Color(0xFFE2E8F0) }

private fun timeLabel(min: Int): String {
    val h = min / 60; val m = min % 60
    val am = h < 12
    val h12 = when { h % 12 == 0 -> 12; else -> h % 12 }
    return String.format(Locale.US, "%d:%02d %s", h12, m, if (am) "AM" else "PM")
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
}

@Composable
private fun SelectRow(value: String, muted: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(com.kairos.app.ui.nav.KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SelectorOverlay(title: String, onClose: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(com.kairos.app.ui.nav.KairosIcons.ChevronLeft, contentDescription = "Back")
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SelectOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMin: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMin / 60, initialMinute = initialMin % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassDatePicker(iso: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(iso))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.selectedDateMillis?.let { utcMillisToIso(it) } ?: "") }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { onPick(""); onDismiss() }) { Text("Clear") } },
    ) { DatePicker(state = state) }
}

private fun isoToUtcMillis(iso: String): Long =
    try { LocalDate.parse(iso, DateTimeFormatter.ISO_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    catch (_: Exception) { Instant.now().toEpochMilli() }

private fun utcMillisToIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_DATE)
