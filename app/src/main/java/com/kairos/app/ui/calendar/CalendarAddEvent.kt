package com.kairos.app.ui.calendar

import com.kairos.app.ui.common.TimeFmt
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.alpha
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.window.Dialog
import com.kairos.app.data.remote.dto.CalendarDto
import com.kairos.app.data.remote.dto.CreateEventRequest
import com.kairos.app.ui.nav.KairosIcons
import com.kairos.app.ui.common.AnimatedDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.kairos.app.ui.common.SentenceCaps

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
    val container = com.kairos.app.ui.common.rememberContainer()
    val meId = remember {
        (container.sessionRepository.state.value as? com.kairos.app.data.session.SessionState.Ready)?.person?.id
    }
    // The event's owner shouldn't appear in "share with" (you can't share with
    // yourself). For a new event that's me; for an edit it's the event's owner.
    val ownerId = editEvent?.ownerId ?: meId
    var title by remember { mutableStateOf(editEvent?.title ?: "") }
    var allDay by remember { mutableStateOf(editEvent?.allDay ?: false) }
    val initDate = editEvent?.dayISO?.ifBlank { data.date } ?: data.date.ifBlank { data.today }
    var startDateIso by remember { mutableStateOf(initDate) }
    var endDateIso by remember { mutableStateOf(initDate) }
    val defaultStart = remember {
        // Round the current time UP to the next 15-minute slot, so a new event
        // never defaults to a time that's already passed.
        val now = java.time.LocalTime.now()
        (((now.hour * 60 + now.minute + 14) / 15) * 15).coerceIn(0, 22 * 60)
    }
    var startMin by remember { mutableStateOf(editEvent?.startMin ?: defaultStart) }
    var endMin by remember { mutableStateOf(editEvent?.endMin?.takeIf { it > (editEvent.startMin) } ?: (editEvent?.startMin?.plus(60) ?: (defaultStart + 60))) }
    var location by remember { mutableStateOf(editEvent?.location ?: "") }
    var addressSearchOpen by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf("NONE") }
    var isFamily by remember { mutableStateOf(editEvent?.isFamily ?: false) }
    var kind by remember { mutableStateOf(editEvent?.kind?.ifBlank { "APPOINTMENT" } ?: "APPOINTMENT") }
    var eventTypeId by remember { mutableStateOf(editEvent?.eventTypeId) }
    var participants by remember {
        mutableStateOf(
            editEvent?.let { e -> e.memberIds.filter { it != e.ownerId }.toSet() } ?: emptySet(),
        )
    }
    var reminders by remember { mutableStateOf(editEvent?.reminders ?: emptyList()) }
    val reminderDefaults by container.settingsStore.reminderDefaults.collectAsState(initial = null)
    // Whether the person has hand-edited reminders; if so we never auto-change them.
    var reminderTouched by remember { mutableStateOf(editing) }
    // Seed a new event's reminder from the per-type default once settings load.
    LaunchedEffect(reminderDefaults) {
        val defs = reminderDefaults
        if (!editing && !reminderTouched && defs != null) {
            val d = com.kairos.app.data.settings.ReminderDefaults.effective(defs, kind)
            reminders = if (d >= 0) listOf(d) else emptyList()
        }
    }
    var showCustomReminder by remember { mutableStateOf(false) }
    // Whether the reminders include me. New events default on; on an edit it
    // reflects the current recipient list, so turning it off removes just me.
    var remindMe by remember {
        mutableStateOf(
            if (editing) meId != null && editEvent?.reminderUserIds?.contains(meId) == true
            else true,
        )
    }
    // The recipients to save: the base list (everyone for a family event, me for
    // a personal one, or the existing set on an edit), with me added or removed
    // per the "Remind me" switch. Never touches other people's reminders.
    fun recipients(): List<String> {
        if (reminders.isEmpty()) return emptyList()
        val base = when {
            editing -> editEvent?.reminderUserIds ?: emptyList()
            isFamily -> data.options.people.map { it.id }
            else -> listOfNotNull(meId)
        }
        val set = base.toMutableSet()
        meId?.let { if (remindMe) set.add(it) else set.remove(it) }
        return set.toList()
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
                reminders = reminders,
                reminderUserIds = recipients(),
            ),
        ) { onClose() }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (addressSearchOpen) {
            AddressSearchScreen(
                initial = location,
                repo = container.sessionRepository,
                onDismiss = { addressSearchOpen = false },
                onPick = {
                    location = it
                    addressSearchOpen = false
                },
            )
        } else {
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
                                    reminders = reminders,
                                    reminderUserIds = recipients(),
                                ),
                            ) { onClose() }
                        }
                    },
                ) { Text(if (ui.creating) "Saving\u2026" else "Save") }
            }

            Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    keyboardOptions = SentenceCaps,
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
                    KairosIcons.Share,
                    if (participants.isEmpty()) "Add participants"
                    else "${participants.size} " + if (participants.size == 1) "person" else "people",
                    muted = participants.isEmpty(),
                ) { showPeople = true }

                SectionLine()
                LocationField(
                    value = location,
                    onOpenSearch = { addressSearchOpen = true },
                )

                SectionLine()
                if (reminders.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Remind me about this",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = remindMe, onCheckedChange = { remindMe = it })
                    }
                }
                reminders.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(KairosIcons.Bell, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Text(reminderLabel(m), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Box(Modifier.size(28.dp).clickable { reminders = reminders - m; reminderTouched = true }, contentAlignment = Alignment.Center) {
                            Text("\u2715", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { openSelector = "reminder" }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        KairosIcons.Bell,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp).then(if (reminders.isEmpty()) Modifier else Modifier.alpha(0f)),
                    )
                    Text("Add notification", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }

                ui.createError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
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
        TimePickerDialog(startMin, onConfirm = { m ->
            startMin = m
            endMin = (m + typeDurationMin(eventTypeId, customTypes)).coerceAtMost(23 * 60 + 59)
            showStart = false
        }, onDismiss = { showStart = false })
    }
    if (showEnd) {
        TimePickerDialog(endMin, onConfirm = { m -> endMin = m; showEnd = false }, onDismiss = { showEnd = false })
    }

    if (showPeople) {
        AnimatedDialog(
            onDismissRequest = { showPeople = false },
            title = "Share with",
            content = {
                Column {
                    data.options.people.filter { ownerId == null || it.id != ownerId }.forEach { p ->
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
        AnimatedDialog(
            onDismissRequest = { showScope = false },
            title = "Edit repeating event",
            content = {
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

    if (showCustomReminder) {
        var amount by remember { mutableStateOf("15") }
        var unit by remember { mutableStateOf(1) }
        AnimatedDialog(
            onDismissRequest = { showCustomReminder = false },
            title = "Custom notification",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = amount,
                            onValueChange = { v -> amount = v.filter { it.isDigit() }.take(4) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (amount.isEmpty()) {
                                    Text("15", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                inner()
                            },
                        )
                        Text("before the event", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "Minutes", 60 to "Hours", 1440 to "Days", 10080 to "Weeks").forEach { (mult, label) ->
                            val sel = unit == mult
                            Box(
                                Modifier
                                    .weight(1f)
                                    .border(1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { unit = mult }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = amount.toIntOrNull()
                    if (n != null && n > 0) {
                        val m = (n * unit).coerceAtMost(40320)
                        reminders = (reminders + m).distinct().sorted(); reminderTouched = true
                    }
                    showCustomReminder = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showCustomReminder = false }) { Text("Cancel") } },
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
                "APPOINTMENT" to "Event",
                "CLASS" to "Class",
                "WORK" to "Work shift",
                "BIRTHDAY" to "Birthday",
                "OTHER" to "Medical / Dental",
            ).forEach { (k, label) ->
                SelectOptionRow(label, eventTypeId == null && kind == k) {
                    kind = k
                    eventTypeId = null
                    if (!editing && !reminderTouched) {
                        val d = com.kairos.app.data.settings.ReminderDefaults.effective(reminderDefaults ?: emptyMap(), k)
                        reminders = if (d >= 0) listOf(d) else emptyList()
                    }
                    if (k == "BIRTHDAY") { allDay = true; repeat = "YEARLY" }
                    endMin = (startMin + typeDurationMin(null, customTypes)).coerceAtMost(23 * 60 + 59)
                    openSelector = null
                }
            }
            customTypes.forEach { ct ->
                SelectOptionRow(ct.name, eventTypeId == ct.id) {
                    kind = "OTHER"
                    eventTypeId = ct.id
                    endMin = (startMin + typeDurationMin(ct.id, customTypes)).coerceAtMost(23 * 60 + 59)
                    if (!editing && !reminderTouched) {
                        reminders = ct.defaultReminder?.let { listOf(it) } ?: emptyList()
                    }
                    openSelector = null
                }
            }
        }
        "repeat" -> SelectorOverlay("Repeats", onClose = { openSelector = null }) {
            listOf("NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY").forEach { r ->
                SelectOptionRow(repeatLabel(r), repeat == r) { repeat = r; openSelector = null }
            }
        }
        "reminder" -> SelectorOverlay("Add notification", onClose = { openSelector = null }) {
            listOf(0, 10, 15, 30, 60, 1440, 10080).forEach { m ->
                SelectOptionRow(reminderLabel(m), reminders.contains(m)) {
                    reminders = (reminders + m).distinct().sorted(); reminderTouched = true
                    openSelector = null
                }
            }
            SelectOptionRow("Custom\u2026", false) {
                openSelector = null
                showCustomReminder = true
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
    val state = rememberTimePickerState(initialHour = initialMin / 60, initialMinute = initialMin % 60, is24Hour = TimeFmt.military)
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
        "OTHER" -> "Medical / Dental"
        else -> "Event"
    }
}

private fun reminderLabel(minutes: Int): String {
    if (minutes <= 0) return "At time of event"
    fun unit(n: Int, one: String) = "$n $one${if (n == 1) "" else "s"} before"
    return when {
        minutes % 10080 == 0 -> unit(minutes / 10080, "week")
        minutes % 1440 == 0 -> unit(minutes / 1440, "day")
        minutes % 60 == 0 -> unit(minutes / 60, "hour")
        else -> unit(minutes, "minute")
    }
}

private fun repeatLabel(v: String): String = when (v) {
    "DAILY" -> "Daily"
    "WEEKLY" -> "Weekly"
    "MONTHLY" -> "Monthly"
    "YEARLY" -> "Yearly"
    else -> "Does not repeat"
}

private fun typeDurationMin(eventTypeId: String?, types: List<com.kairos.app.data.remote.dto.CalEventTypeDto>): Int =
    eventTypeId?.let { id -> types.firstOrNull { it.id == id }?.defaultMinutes }?.takeIf { it > 0 } ?: 60

private fun hhmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

private fun hhmmLabel(min: Int): String = TimeFmt.clock(min)

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
