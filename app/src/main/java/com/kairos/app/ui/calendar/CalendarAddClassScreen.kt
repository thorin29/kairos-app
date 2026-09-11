package com.kairos.app.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAYS = listOf(
    "MO" to "Monday", "TU" to "Tuesday", "WE" to "Wednesday", "TH" to "Thursday",
    "FR" to "Friday", "SA" to "Saturday", "SU" to "Sunday",
)
private val REMINDER_OPTIONS = listOf(0, 10, 15, 30, 60, 10080)
private val COLORS = listOf(
    "" to "Default", "#2563eb" to "Blue", "#059669" to "Green", "#dc2626" to "Red",
    "#d97706" to "Orange", "#7c3aed" to "Purple", "#0d9488" to "Teal",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarAddClassScreen(
    replaceEventId: String?,
    prefill: ClassPrefill,
    onDone: () -> Unit,
) {
    val container = rememberContainer()
    val vm: CalendarAddClassViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CalendarAddClassViewModel(container.sessionRepository, replaceEventId, prefill) }
        },
    )
    val ui by vm.ui.collectAsState()
    LaunchedEffect(ui.done) { if (ui.done) onDone() }

    var selector by remember { mutableStateOf<String?>(null) }
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var showFrom by remember { mutableStateOf(false) }
    var showUntil by remember { mutableStateOf(false) }
    var showAddSubject by remember { mutableStateOf(false) }
    var showCustomReminder by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (replaceEventId != null) "Make a class" else "Add class") },
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
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.size(8.dp))
                    SelectRow(KairosIcons.Book, ui.subject.ifBlank { "Subject" }, muted = ui.subject.isBlank()) { selector = "subject" }
                    if (ui.isAdmin) {
                        SectionLine()
                        SelectRow(KairosIcons.Share, studentName(ui), muted = ui.studentId.isBlank()) { selector = "student" }
                    }
                    SectionLine()
                    SelectRow(KairosIcons.Calendar, daysLabel(ui.byday), muted = ui.byday.isEmpty()) { selector = "days" }
                    if (ui.byday.isNotEmpty()) {
                        SectionLine()
                        SelectRow(KairosIcons.Bell, "Starts " + timeLabel(ui.startMin)) { showStart = true }
                        SectionLine()
                        SelectRow(KairosIcons.Bell, "Ends " + timeLabel(ui.endMin)) { showEnd = true }
                        SectionLine()
                        SelectRow(KairosIcons.Calendar, "Runs from " + (ui.runsFrom.ifBlank { "any" }), muted = ui.runsFrom.isBlank()) { showFrom = true }
                        SectionLine()
                        SelectRow(KairosIcons.Calendar, "Runs until " + (ui.runsUntil.ifBlank { "any" }), muted = ui.runsUntil.isBlank()) { showUntil = true }
                    }
                    SectionLine()
                    SelectRow(KairosIcons.Book, optName(ui.classTypes, ui.classTypeId, "Class type"), muted = ui.classTypeId.isBlank()) { selector = "classType" }
                    SectionLine()
                    SelectRow(KairosIcons.Book, optName(ui.terms, ui.termId, "Semester"), muted = ui.termId.isBlank()) { selector = "term" }
                    SectionLine()
                    Row(
                        Modifier.fillMaxWidth().clickable { selector = "color" }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(shape = CircleShape, color = swatch(ui.color), modifier = Modifier.size(22.dp)) {}
                        Text(colorName(ui.color), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    SectionLine()
                    SelectRow(
                        KairosIcons.Share,
                        if (ui.sharedWith.isEmpty()) "Shared with" else "${ui.sharedWith.size} shared",
                        muted = ui.sharedWith.isEmpty(),
                    ) { selector = "shared" }

                    // Reminders
                    SectionLine()
                    ui.reminders.sorted().forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(KairosIcons.Bell, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                            Text(reminderLabel(m), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Box(Modifier.size(28.dp).clickable { vm.removeReminder(m) }, contentAlignment = Alignment.Center) {
                                Text("\u2715", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { selector = "reminder" }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(KairosIcons.Bell, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Text("Add notification", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }

                    // Location
                    SectionLine()
                    OutlinedTextField(
                        value = ui.location,
                        onValueChange = vm::setLocation,
                        singleLine = true,
                        placeholder = { Text("Add location") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )

                    SectionLine()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Ask about homework after class", Modifier.weight(1f))
                        Switch(checked = ui.promptHomework, onCheckedChange = vm::setHomework)
                    }

                    if (ui.error != null) {
                        Text(ui.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = vm::save, enabled = !ui.saving, modifier = Modifier.fillMaxWidth()) {
                        if (ui.saving) {
                            CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (replaceEventId != null) "Save class" else "Add class")
                    }
                    Spacer(Modifier.size(16.dp))
                }
            }
        }
    }

    when (selector) {
        "subject" -> SelectorOverlay("Subject", onClose = { selector = null }) {
            Row(
                Modifier.fillMaxWidth().clickable { selector = null; showAddSubject = true }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+ Add a new subject", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            ui.subjects.forEach { name ->
                SelectOptionRow(name, ui.subject == name) { vm.setSubject(name); selector = null }
            }
        }
        "student" -> SelectorOverlay("Student", onClose = { selector = null }) {
            ui.students.forEach { (id, name) ->
                SelectOptionRow(name, ui.studentId == id) { vm.setStudent(id); selector = null }
            }
        }
        "days" -> SelectorOverlay("Meets on", onClose = { selector = null }) {
            DAYS.forEach { (tok, label) ->
                val on = tok in ui.byday
                Row(
                    Modifier.fillMaxWidth().clickable {
                        vm.setDays(if (on) ui.byday - tok else ui.byday + tok)
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = on, onCheckedChange = { vm.setDays(if (it) ui.byday + tok else ui.byday - tok) })
                }
            }
        }
        "classType" -> SelectorOverlay("Class type", onClose = { selector = null }) {
            SelectOptionRow("Choose a type\u2026", ui.classTypeId.isBlank()) { vm.setClassType(""); selector = null }
            ui.classTypes.forEach { (id, name) ->
                SelectOptionRow(name, ui.classTypeId == id) { vm.setClassType(id); selector = null }
            }
        }
        "term" -> SelectorOverlay("Semester", onClose = { selector = null }) {
            SelectOptionRow("Repeats with no end date", ui.termId.isBlank()) { vm.setTerm(""); selector = null }
            ui.terms.forEach { (id, name) ->
                SelectOptionRow(name, ui.termId == id) { vm.setTerm(id); selector = null }
            }
        }
        "color" -> SelectorOverlay("Color", onClose = { selector = null }) {
            COLORS.forEach { (hex, name) ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setColor(hex); selector = null }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(shape = CircleShape, color = swatch(hex), modifier = Modifier.size(20.dp)) {}
                    Text(name, Modifier.weight(1f))
                    if (ui.color == hex) Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
        "shared" -> SelectorOverlay("Shared with", onClose = { selector = null }) {
            ui.students.filter { it.first != (if (ui.isAdmin) ui.studentId else "") }.forEach { (id, name) ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.toggleShared(id) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, Modifier.weight(1f))
                    Switch(checked = id in ui.sharedWith, onCheckedChange = { vm.toggleShared(id) })
                }
            }
        }
        "reminder" -> SelectorOverlay("Add notification", onClose = { selector = null }) {
            REMINDER_OPTIONS.forEach { m ->
                SelectOptionRow(reminderLabel(m), m in ui.reminders) { vm.addReminder(m); selector = null }
            }
            SelectOptionRow("Custom\u2026", false) { selector = null; showCustomReminder = true }
        }
    }

    if (showStart) TimePickerDialog(ui.startMin, { vm.setStart(it); showStart = false }, { showStart = false })
    if (showEnd) TimePickerDialog(ui.endMin, { vm.setEnd(it); showEnd = false }, { showEnd = false })
    if (showFrom) ClassDatePicker(ui.runsFrom, { vm.setRunsFrom(it); showFrom = false }, { showFrom = false })
    if (showUntil) ClassDatePicker(ui.runsUntil, { vm.setRunsUntil(it); showUntil = false }, { showUntil = false })
    if (showAddSubject) AddSubjectDialog({ vm.setSubject(it); showAddSubject = false }, { showAddSubject = false })
    if (showCustomReminder) CustomReminderDialog({ vm.addReminder(it); showCustomReminder = false }, { showCustomReminder = false })
}

private fun studentName(ui: ClassFormUiState): String =
    ui.students.firstOrNull { it.first == ui.studentId }?.second ?: "Student"

private fun optName(opts: List<Pair<String, String>>, id: String, empty: String): String =
    opts.firstOrNull { it.first == id }?.second ?: empty

private fun daysLabel(days: Set<String>): String =
    if (days.isEmpty()) "Meets on" else DAYS.filter { it.first in days }.joinToString(", ") { it.second.take(3) }

private fun colorName(hex: String): String = COLORS.firstOrNull { it.first == hex }?.second ?: "Default"

private fun swatch(hex: String): androidx.compose.ui.graphics.Color =
    if (hex.isBlank()) androidx.compose.ui.graphics.Color(0xFFE2E8F0)
    else try { androidx.compose.ui.graphics.Color(("FF" + hex.removePrefix("#")).toLong(16)) }
    catch (_: Exception) { androidx.compose.ui.graphics.Color(0xFFE2E8F0) }

private fun timeLabel(min: Int): String {
    val h = min / 60; val m = min % 60
    val h12 = when { h % 12 == 0 -> 12; else -> h % 12 }
    return String.format(Locale.US, "%d:%02d %s", h12, m, if (h < 12) "AM" else "PM")
}

private fun reminderLabel(minutes: Int): String {
    if (minutes <= 0) return "At time of class"
    fun unit(n: Int, one: String) = "$n $one${if (n == 1) "" else "s"} before"
    return when {
        minutes % 10080 == 0 -> unit(minutes / 10080, "week")
        minutes % 1440 == 0 -> unit(minutes / 1440, "day")
        minutes % 60 == 0 -> unit(minutes / 60, "hour")
        else -> unit(minutes, "minute")
    }
}

@Composable
private fun SectionLine() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SelectRow(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, muted: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
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
                        Icon(KairosIcons.ChevronLeft, contentDescription = "Back")
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
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AddSubjectDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "New subject",
        confirmButton = { TextButton(onClick = { if (text.trim().length >= 2) onAdd(text.trim()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text("Biology, Math, Piano\u2026") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CustomReminderDialog(onAdd: (Int) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(1440) }
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "Custom notification",
        confirmButton = {
            TextButton(onClick = {
                val n = amount.trim().toIntOrNull()
                if (n != null && n > 0) onAdd(n * unit)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() } },
                singleLine = true,
                placeholder = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Minutes", 60 to "Hours", 1440 to "Days", 10080 to "Weeks").forEach { (mult, label) ->
                    val on = unit == mult
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { unit = mult },
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMin: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMin / 60, initialMinute = initialMin % 60, is24Hour = false)
    AnimatedDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { TimePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassDatePicker(iso: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(iso))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.selectedDateMillis?.let { utcMillisToIso(it) } ?: "") }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onPick(""); onDismiss() }) { Text("Clear") } },
    ) { DatePicker(state = state) }
}

private fun isoToUtcMillis(iso: String): Long =
    try { LocalDate.parse(iso, DateTimeFormatter.ISO_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    catch (_: Exception) { Instant.now().toEpochMilli() }

private fun utcMillisToIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_DATE)
