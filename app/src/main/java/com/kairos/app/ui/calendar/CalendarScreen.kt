package com.kairos.app.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.CalEventDto
import com.kairos.app.data.remote.dto.CalendarDto
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onOpenDrawer: () -> Unit) {
    val container = rememberContainer()
    val vm: CalendarViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CalendarViewModel(container.sessionRepository, container.settingsStore) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    var monthExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDefaultPicker by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    val data = ui.data

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
                    title = {
                        if (data != null) {
                            val expandable = ui.tab != CalTab.MONTH
                            MonthTitle(
                                label = monthName(data.date),
                                expandable = expandable,
                                expanded = monthExpanded,
                                onClick = { if (expandable) monthExpanded = !monthExpanded },
                            )
                        }
                    },
                    actions = {
                        if (data != null) {
                            TodayBox(dayNum = dayOfMonth(data.today)) {
                                monthExpanded = false
                                vm.goToday()
                            }
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).clickable { showAdd = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(KairosIcons.Plus, contentDescription = "New event")
                            }
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).clickable { showSettings = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(KairosIcons.Sliders, contentDescription = "Calendar settings")
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    },
                )
            },
        ) { inner ->
            Box(Modifier.padding(inner).fillMaxSize()) {
                when {
                    ui.loading && data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(ui.loadError ?: "Couldn't load your calendar.")
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { vm.reload() }) { Text("Retry") }
                        }
                    }
                    else -> CalendarBody(
                        ui = ui,
                        data = data,
                        vm = vm,
                        monthExpanded = monthExpanded && ui.tab != CalTab.MONTH,
                        onCollapseMonth = { monthExpanded = false },
                    )
                }
            }
        }

        // Settings drawer, sliding in from the right.
        if (data != null) {
            AnimatedVisibility(visible = showSettings, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                        .clickable { showSettings = false },
                )
            }
            AnimatedVisibility(
                visible = showSettings,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                SettingsPanel(
                    data = data,
                    tab = ui.tab,
                    defaultView = ui.defaultView,
                    vm = vm,
                    onOpenDefaultPicker = { showDefaultPicker = true },
                    onPickView = { showSettings = false },
                )
            }
        }
    }

    if (showDefaultPicker && data != null) {
        DefaultViewDialog(
            current = ui.defaultView,
            onPick = { vm.setDefaultView(it); showDefaultPicker = false },
            onDismiss = { showDefaultPicker = false },
        )
    }

    if (showAdd && data != null) {
        AddEventOverlay(vm, data, ui, onClose = { showAdd = false; vm.clearCreateError() })
    }
}

@Composable
private fun MonthTitle(label: String, expandable: Boolean, expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = expandable) { onClick() }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (expandable) {
            Spacer(Modifier.width(2.dp))
            Icon(
                KairosIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodayBox(dayNum: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(dayNum, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CalendarBody(
    ui: CalendarUiState,
    data: CalendarDto,
    vm: CalendarViewModel,
    monthExpanded: Boolean,
    onCollapseMonth: () -> Unit,
) {
    val localEvents = remember(data.events, data.timezone) {
        localizeEvents(data.events, data.timezone)
    }
    Column(Modifier.fillMaxSize()) {
        if (monthExpanded) {
            MiniMonthDropdown(data) { iso ->
                onCollapseMonth()
                vm.goToDate(iso)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(ui.tab, data.date) {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = {
                            val threshold = 64.dp.toPx()
                            if (dx <= -threshold) vm.goNext()
                            else if (dx >= threshold) vm.goPrev()
                        },
                    ) { _, amount -> dx += amount }
                },
        ) {
            when (ui.tab) {
                CalTab.MONTH -> MonthChipsView(data, localEvents, vm)
                CalTab.AGENDA -> AgendaView(localEvents, data.date)
                else -> TimeGrid(data, localEvents)
            }
        }
    }
}

// ---- Month dropdown (mini-month with dots) ----

@Composable
private fun MiniMonthDropdown(data: CalendarDto, onPick: (String) -> Unit) {
    val currentMonth = data.date.take(7)
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
            }
        }
        data.monthDays.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { iso ->
                    MiniCell(
                        iso = iso,
                        inMonth = iso.take(7) == currentMonth,
                        isToday = iso == data.today,
                        isSelected = iso == data.date,
                        dots = data.monthDots[iso].orEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { onPick(iso) }
                }
            }
        }
    }
}

@Composable
private fun MiniCell(
    iso: String,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    dots: List<String>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val num = iso.substringAfterLast('-').trimStart('0').ifEmpty { "0" }
    Column(
        modifier.height(46.dp).clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).then(
                when {
                    isToday -> Modifier.background(MaterialTheme.colorScheme.primary)
                    isSelected -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else -> Modifier
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                num,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    isToday -> Color.White
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dots.take(3).forEach { c ->
                Box(Modifier.size(4.dp).clip(CircleShape).background(parseColor(c)))
            }
        }
    }
}

// ---- Month view (full page, uniform cells filling the screen, chips) ----

@Composable
private fun MonthChipsView(data: CalendarDto, events: List<CalEventDto>, vm: CalendarViewModel) {
    val currentMonth = data.date.take(7)
    val byDay = remember(events) { events.groupBy { it.dayISO } }
    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
            }
        }
        data.monthDays.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { iso ->
                    MonthDayCell(
                        iso = iso,
                        inMonth = iso.take(7) == currentMonth,
                        isToday = iso == data.today,
                        events = byDay[iso].orEmpty(),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) { vm.openDay(iso) }
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    iso: String,
    inMonth: Boolean,
    isToday: Boolean,
    events: List<CalEventDto>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val num = iso.substringAfterLast('-').trimStart('0').ifEmpty { "0" }
    val sorted = events.sortedWith(compareByDescending<CalEventDto> { it.allDay }.thenBy { it.startMin })
    Column(
        modifier
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .clickable { onClick() }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).then(
                if (isToday) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                num,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    isToday -> Color.White
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
            )
        }
        Spacer(Modifier.height(2.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            sorted.take(4).forEach { MonthChip(it) }
            if (sorted.size > 4) {
                Text(
                    "+${sorted.size - 4}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthChip(e: CalEventDto) {
    val color = parseColor(e.color)
    if (e.external) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(14.dp).background(color))
            Text(
                e.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
    } else {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(color)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(
                e.title,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- Agenda ----

@Composable
private fun AgendaView(events: List<CalEventDto>, date: String) {
    val shown = events
        .filter { it.dayISO == date }
        .sortedWith(compareByDescending<CalEventDto> { it.allDay }.thenBy { it.startMin })

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(dayHeading(date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                Text("Nothing scheduled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            shown.forEach { EventRow(it) }
        }
    }
}

@Composable
private fun EventRow(e: CalEventDto) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(parseColor(e.color)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                buildEventSecondary(e)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun buildEventSecondary(e: CalEventDto): String? {
    val parts = mutableListOf<String>()
    parts += if (e.allDay) "All day" else e.timeLabel
    e.location?.takeIf { it.isNotBlank() }?.let { parts += it }
    e.recurLabel?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (e.isFamily) parts += "Family"
    return parts.filter { it.isNotBlank() }.joinToString(" \u00b7 ").ifBlank { null }
}

// ---- Settings drawer (right) ----

@Composable
private fun SettingsPanel(
    data: CalendarDto,
    tab: CalTab,
    defaultView: String,
    vm: CalendarViewModel,
    onOpenDefaultPicker: () -> Unit,
    onPickView: () -> Unit,
) {
    val opt = data.options
    Surface(
        Modifier.fillMaxHeight().fillMaxWidth(0.86f),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CalTab.entries.forEach { t ->
                ViewRow(t, selected = t == tab) { vm.setTab(t); onPickView() }
            }

            DrawerDivider()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpenDefaultPicker() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Default view", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        defaultViewLabel(defaultView),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(KairosIcons.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            DrawerDivider()
            Text(
                "My calendars",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
            FilterCheck("Family events", opt.showFamily, null) { vm.savePrefs(showFamily = it) }
            FilterCheck("School work", opt.showSchoolWork, null) { vm.savePrefs(showSchoolWork = it) }
            opt.people.forEach { p ->
                FilterCheck(p.name, p.id in opt.shownPeople, parseColor(p.color)) {
                    vm.savePrefs(shownPeople = toggleId(opt.shownPeople, p.id))
                }
            }

            if (opt.subscriptions.isNotEmpty()) {
                DrawerDivider()
                Text(
                    "Other calendars",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
                opt.subscriptions.forEach { s ->
                    val label = if (s.ownerName != null) "${s.name} \u00b7 ${s.ownerName}" else s.name
                    FilterCheck(label, s.id in opt.shownSubs, parseColor(s.color)) {
                        vm.savePrefs(shownSubs = toggleId(opt.shownSubs, s.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewRow(t: CalTab, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            tabIcon(t),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            t.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun FilterCheck(label: String, checked: Boolean, color: Color?, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onToggle(!checked) }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle(it) },
            colors = if (color != null) CheckboxDefaults.colors(checkedColor = color) else CheckboxDefaults.colors(),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DrawerDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).padding(vertical = 6.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun DefaultViewDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        "agenda" to "Agenda", "day" to "Day", "three_day" to "3 Days",
        "week" to "Week", "month" to "Month", "last" to "Last view",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default view") },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == current, onClick = { onPick(value) })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// ---- helpers ----

private fun tabIcon(t: CalTab): ImageVector = when (t) {
    CalTab.AGENDA -> KairosIcons.ViewAgenda
    CalTab.DAY -> KairosIcons.ViewDay
    CalTab.THREE_DAY -> KairosIcons.ViewThreeDay
    CalTab.WEEK -> KairosIcons.ViewWeek
    CalTab.MONTH -> KairosIcons.ViewMonth
}

private fun defaultViewLabel(v: String): String = when (v) {
    "agenda" -> "Agenda"
    "day" -> "Day"
    "three_day" -> "3 Days"
    "week" -> "Week"
    "month" -> "Month"
    else -> "Last view"
}

private fun toggleId(list: List<String>, id: String): List<String> =
    if (id in list) list - id else list + id

private val MONTH_NAME = DateTimeFormatter.ofPattern("MMMM")
private val DAY_HEADING = DateTimeFormatter.ofPattern("EEEE, MMM d")

private fun monthName(iso: String): String =
    try { LocalDate.parse(iso).format(MONTH_NAME) } catch (_: Exception) { iso }

private fun dayOfMonth(iso: String): String =
    try { LocalDate.parse(iso).dayOfMonth.toString() } catch (_: Exception) { "" }

private fun dayHeading(iso: String): String =
    try { LocalDate.parse(iso).format(DAY_HEADING) } catch (_: Exception) { iso }

private fun localizeEvents(events: List<CalEventDto>, homeTz: String): List<CalEventDto> {
    val home = runCatching { ZoneId.of(homeTz) }.getOrElse { return events }
    val device = ZoneId.systemDefault()
    if (home == device) return events
    return events.map { e ->
        if (e.allDay) return@map e
        val homeStart = runCatching {
            LocalDate.parse(e.dayISO).atStartOfDay(home).plusMinutes(e.startMin.toLong())
        }.getOrNull() ?: return@map e
        val dev = homeStart.withZoneSameInstant(device)
        val newStart = dev.hour * 60 + dev.minute
        val dur = e.endMin - e.startMin
        e.copy(
            dayISO = dev.toLocalDate().toString(),
            startMin = newStart,
            endMin = newStart + dur,
            timeLabel = formatTime(newStart),
        )
    }
}

private fun formatTime(min: Int): String {
    val h = (min / 60) % 24
    val m = min % 60
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "%d:%02d %s".format(h12, m, ampm)
}

private fun parseColor(hex: String?): Color {
    val s = hex?.trim()?.removePrefix("#") ?: return Color(0xFF64748B)
    return try {
        when (s.length) {
            6 -> Color(("FF$s").toLong(16))
            8 -> Color(s.toLong(16))
            else -> Color(0xFF64748B)
        }
    } catch (_: NumberFormatException) {
        Color(0xFF64748B)
    }
}
