package com.kairos.app.ui.calendar

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kairos.app.data.remote.dto.CalendarDto
import com.kairos.app.data.remote.dto.CreateEventRequest
import com.kairos.app.ui.nav.KairosIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventOverlay(
    vm: CalendarViewModel,
    data: CalendarDto,
    ui: CalendarUiState,
    editEvent: com.kairos.app.data.remote.dto.CalEventDto? = null,
    editOccurrenceISO: String? = null,
    onClose: () -> Unit,
) {
    val editing = editEvent != null
    var title by remember { mutableStateOf(editEvent?.title ?: "") }
    var allDay by remember { mutableStateOf(editEvent?.allDay ?: false) }
    val initDate = editEvent?.dayISO?.ifBlank { data.date } ?: data.date.ifBlank { data.today }
    var startDateIso by remember { mutableStateOf(initDate) }
    var endDateIso by remember { mutableStateOf(initDate) }
    val defaultStart = remember {
        val n = java.time.LocalTime.now()
        val m = ((n.hour * 60 + n.minute + 29) / 30) * 30
        m.coerceIn(0, 22 * 60)
    }
    var startMin by remember { mutableStateOf(editEvent?.startMin ?: defaultStart) }
    var endMin by remember { mutableStateOf(editEvent?.endMin?.takeIf { it > (editEvent.startMin) } ?: (editEvent?.startMin?.plus(60) ?: (defaultStart + 60))) }
    var location by remember { mutableStateOf(editEvent?.location ?: "") }
    var repeat by remember { mutableStateOf("NONE") }
    var isFamily by remember { mutableStateOf(editEvent?.isFamily ?: false) }
    var kind by remember { mutableStateOf(editEvent?.kind?.ifBlank { "APPOINTMENT" } ?: "APPOINTMENT") }
    var eventTypeId by remember { mutableStateOf(editEvent?.eventTypeId) }
    var participants by remember {
        mutableStateOf(
            editEvent?.let { e -> e.memberIds.filter { it != e.ownerId }.toSet() } ?: emptySet(),
        )
    }
    var showPeople by remember { mutableStateOf(false) }
    val canFamily = data.options.canManageFamily
    val customTypes = data.options.eventTypes

    val homeTz = data.timezone
    val deviceTz = remember { ZoneId.systemDefault().id }
    val tzOptions = remember(homeTz, deviceTz) {
        if (homeTz == deviceTz) listOf(homeTz) else listOf(homeTz, deviceTz)
    }
    // For an edit, the shown times are already device-local, so default to the
    // device tz to preserve the same moment; for a new event, default home.
    var tz by remember { mutableStateOf(if (editing) deviceTz else homeTz) }

    var showStartDate by remember { mutableStateOf(false) }
    var showEndDate by remember { mutableStateOf(false) }
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var openSelector by remember { mutableStateOf<String?>(null) }
    var showScope by remember { mutableStateOf(false) }

    fun submit(scope: String?) {
        val start = if (allDay) null else hhmm(startMin)
        val end = if (allDay) null else hhmm(endMin)
        val zone = if (allDay) null else tz
        vm.updateEvent(
            com.kairos.app.data.remote.dto.UpdateEventRequest(
                eventId = editEvent!!.eventId,
                title = title.trim(),
                allDay = allDay,
                date = startDateIso,
                start = start,
                end = end,
                endDate = endDateIso,
                location = location.trim().ifBlank { null },
                timezone = zone,
                scope = scope,
                occurrenceISO = editOccurrenceISO,
                isFamily = if (isFamily) true else null,
                kind = kind,
                eventTypeId = eventTypeId,
                participants = participants.toList(),
            ),
        ) { onClose() }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clickable { onClose() }, contentAlignment = Alignment.Center) {
                    Text("\u2715", style = MaterialTheme.typography.titleMedium)
                }
                Text(if (editing) "Edit event" else "New event", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                TextButton(
                    enabled = !ui.creating && title.trim().length >= 2,
                    onClick = {
                        val start = if (allDay) null else hhmm(startMin)
                        val end = if (allDay) null else hhmm(endMin)
                        val zone = if (allDay) null else tz
                        if (editing) {
                            if (editEvent!!.recurring) {
                                showScope = true
                            } else {
                                submit(null)
                            }
                        } else {
                            vm.createEvent(
                                CreateEventRequest(
                                    title = title.trim(),
                                    allDay = allDay,
                                    date = startDateIso,
                                    start = start,
                                    end = end,
                                    endDate = endDateIso,
                                    location = location.trim().ifBlank { null },
                                    timezone = zone,
                                    repeat = if (repeat == "NONE") null else repeat,
                                    isFamily = if (isFamily) true else null,
                                    kind = kind,
                                    eventTypeId = eventTypeId,
                                    participants = participants.toList().ifEmpty { null },
                                ),
                            ) { onClose() }
                        }
                    },
                ) { Text(if (ui.creating) "Saving\u2026" else "Save") }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    decorationBox = { inner ->
                        if (title.isEmpty()) {
                            Text(
                                "Add Title",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )

                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("All day", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }

                // Start / end: date opens the calendar, time opens the clock. No
                // lines between them.
                PlainDateTimeRow(
                    dateText = formatDate(startDateIso),
                    timeText = if (allDay) null else hhmmLabel(startMin),
                    onDate = { showStartDate = true },
                    onTime = { showStart = true },
                )
                PlainDateTimeRow(
                    dateText = formatDate(endDateIso),
                    timeText = if (allDay) null else hhmmLabel(endMin),
                    onDate = { showEndDate = true },
                    onTime = { showEnd = true },
                )

                if (!allDay && tzOptions.size > 1) {
                    SectionLine()
                    SelectRow(KairosIcons.Globe, tzLabel(tz, homeTz, deviceTz)) { openSelector = "timezone" }
                }
                if (canFamily) {
                    SectionLine()
                    SelectRow(KairosIcons.Calendar, if (isFamily) "Family calendar" else "My calendar") { openSelector = "calendar" }
                }
                SectionLine()
                SelectRow(KairosIcons.Book, typeLabel(kind, eventTypeId, customTypes)) { openSelector = "type" }

                if (!editing) {
                    SectionLine()
                    SelectRow(KairosIcons.Repeat, repeatLabel(repeat)) { openSelector = "repeat" }
                }

                SectionLine()
                SelectRow(
                    KairosIcons.Chores,
                    if (participants.isEmpty()) "Add participants"
                    else "${participants.size} " + if (participants.size == 1) "person" else "people",
                    muted = participants.isEmpty(),
                ) { showPeople = true }

                SectionLine()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(KairosIcons.Home, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    BasicTextField(
                        value = location,
                        onValueChange = { location = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (location.isEmpty()) {
                                Text("Add location", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        },
                    )
                }

                ui.createError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showStartDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(startDateIso))
        DatePickerDialog(
            onDismissRequest = { showStartDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val iso = utcMillisToIso(it)
                        startDateIso = iso
                        if (endDateIso < iso) endDateIso = iso
                    }
                    showStartDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    if (showEndDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(endDateIso))
        DatePickerDialog(
            onDismissRequest = { showEndDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val iso = utcMillisToIso(it)
                        endDateIso = if (iso < startDateIso) startDateIso else iso
                    }
                    showEndDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showStart) {
        TimePickerDialog(startMin, onConfirm = { m -> startMin = m; if (endDateIso == startDateIso && endMin <= m) endMin = (m + 60).coerceAtMost(23 * 60 + 59); showStart = false }, onDismiss = { showStart = false })
    }
    if (showEnd) {
        TimePickerDialog(endMin, onConfirm = { m -> endMin = m; showEnd = false }, onDismiss = { showEnd = false })
    }

    if (showPeople) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPeople = false },
            title = { Text("Share with") },
            text = {
                Column {
                    data.options.people.forEach { p ->
                        val checked = p.id in participants
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                participants = participants.toMutableSet().apply { if (!add(p.id)) remove(p.id) }
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    participants = participants.toMutableSet().apply { if (it) add(p.id) else remove(p.id) }
                                },
                            )
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPeople = false }) { Text("Done") } },
        )
    }

    if (showScope) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showScope = false },
            title = { Text("Edit repeating event") },
            text = {
                Column {
                    Text(
                        "This event",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { showScope = false; submit("single") }.padding(vertical = 12.dp),
                    )
                    Text(
                        "All events",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { showScope = false; submit("series") }.padding(vertical = 12.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showScope = false }) { Text("Cancel") } },
        )
    }

    when (openSelector) {
        "timezone" -> SelectorOverlay("Time zone", onClose = { openSelector = null }) {
            tzOptions.forEach { z ->
                SelectOptionRow(tzLabel(z, homeTz, deviceTz), z == tz) { tz = z; openSelector = null }
            }
        }
        "calendar" -> SelectorOverlay("Calendar", onClose = { openSelector = null }) {
            SelectOptionRow("My calendar", !isFamily) { isFamily = false; openSelector = null }
            SelectOptionRow("Family calendar", isFamily) { isFamily = true; openSelector = null }
        }
        "type" -> SelectorOverlay("Type", onClose = { openSelector = null }) {
            listOf(
                "APPOINTMENT" to "Appointment",
                "CLASS" to "Class",
                "WORK" to "Work shift",
                "BIRTHDAY" to "Birthday",
                "OTHER" to "Other",
            ).forEach { (k, label) ->
                SelectOptionRow(label, eventTypeId == null && kind == k) {
                    kind = k
                    eventTypeId = null
                    if (k == "BIRTHDAY") { allDay = true; repeat = "YEARLY" }
                    openSelector = null
                }
            }
            customTypes.forEach { ct ->
                SelectOptionRow(ct.name, eventTypeId == ct.id) { kind = "OTHER"; eventTypeId = ct.id; openSelector = null }
            }
        }
        "repeat" -> SelectorOverlay("Repeats", onClose = { openSelector = null }) {
            listOf("NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY").forEach { r ->
                SelectOptionRow(repeatLabel(r), repeat == r) { repeat = r; openSelector = null }
            }
        }
    }
}

@Composable
private fun SectionLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun PlainDateTimeRow(dateText: String, timeText: String?, onDate: () -> Unit, onTime: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            dateText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).clickable { onDate() }.padding(vertical = 12.dp),
        )
        if (timeText != null) {
            Text(
                timeText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable { onTime() }.padding(vertical = 12.dp, horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun SelectRow(icon: ImageVector, value: String, muted: Boolean = false, onClick: () -> Unit) {
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
private fun SelectorOverlay(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
                        Icon(KairosIcons.ChevronLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
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
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
                }
            }
        }
    }
}

// ---- helpers ----

private fun typeLabel(kind: String, eventTypeId: String?, customTypes: List<com.kairos.app.data.remote.dto.CalEventTypeDto>): String {
    if (eventTypeId != null) {
        return customTypes.firstOrNull { it.id == eventTypeId }?.name ?: "Type"
    }
    return when (kind) {
        "CLASS" -> "Class"
        "WORK" -> "Work shift"
        "BIRTHDAY" -> "Birthday"
        "OTHER" -> "Other"
        else -> "Appointment"
    }
}

private fun repeatLabel(v: String): String = when (v) {
    "DAILY" -> "Daily"
    "WEEKLY" -> "Weekly"
    "MONTHLY" -> "Monthly"
    "YEARLY" -> "Yearly"
    else -> "Does not repeat"
}

private fun hhmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

private fun hhmmLabel(min: Int): String {
    val h = min / 60
    val m = min % 60
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
    return "%d:%02d %s".format(h12, m, ampm)
}

private val DATE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

private fun formatDate(iso: String): String =
    try { LocalDate.parse(iso).format(DATE_FMT) } catch (_: Exception) { iso }

private fun tzLabel(tz: String, home: String, device: String): String = when (tz) {
    home -> "Home \u00b7 ${shortZone(tz)}"
    device -> "This phone \u00b7 ${shortZone(tz)}"
    else -> shortZone(tz)
}

private fun shortZone(tz: String): String = tz.substringAfterLast('/').replace('_', ' ')

private fun isoToUtcMillis(iso: String): Long =
    try { LocalDate.parse(iso, ISO).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    catch (_: Exception) { Instant.now().toEpochMilli() }

private fun utcMillisToIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(ISO)
