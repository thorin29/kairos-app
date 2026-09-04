package com.kairos.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
            initializer { CalendarViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    var monthExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val data = ui.data

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

    if (showSettings && data != null) {
        SettingsSheet(data, ui.tab, vm, onDismiss = { showSettings = false })
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
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
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

// ---- Month view (full page, event chips) ----

@Composable
private fun MonthChipsView(data: CalendarDto, events: List<CalEventDto>, vm: CalendarViewModel) {
    val currentMonth = data.date.take(7)
    val byDay = remember(events) { events.groupBy { it.dayISO } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
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
            Row(Modifier.fillMaxWidth().heightIn(min = 92.dp)) {
                week.forEach { iso ->
                    MonthDayCell(
                        iso = iso,
                        inMonth = iso.take(7) == currentMonth,
                        isToday = iso == data.today,
                        events = byDay[iso].orEmpty(),
                        modifier = Modifier.weight(1f),
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
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .clickable { onClick() }
            .padding(3.dp),
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
            sorted.take(3).forEach { MonthChip(it) }
            if (sorted.size > 3) {
                Text(
                    "+${sorted.size - 3}",
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
        // Subscribed/feed events: light chip with a colour bar.
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
        Text(
            dayHeading(date),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
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
            Box(
                Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(parseColor(e.color)),
            )
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

// ---- Settings sheet (view + filters) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(data: CalendarDto, tab: CalTab, vm: CalendarViewModel, onDismiss: () -> Unit) {
    val opt = data.options
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("View", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            CalTab.entries.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable { vm.setTab(t); onDismiss() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = t == tab, onClick = { vm.setTab(t); onDismiss() })
                    Text(t.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Show", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            ToggleRow("Family events", opt.showFamily) { vm.savePrefs(showFamily = it) }
            ToggleRow("School work", opt.showSchoolWork) { vm.savePrefs(showSchoolWork = it) }

            Spacer(Modifier.height(10.dp))
            Text("People", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            opt.people.forEach { p ->
                CheckRow(p.name, parseColor(p.color), p.id in opt.shownPeople) {
                    vm.savePrefs(shownPeople = toggleId(opt.shownPeople, p.id))
                }
            }

            if (opt.subscriptions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Subscriptions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                opt.subscriptions.forEach { s ->
                    val label = if (s.ownerName != null) "${s.name} \u00b7 ${s.ownerName}" else s.name
                    CheckRow(label, parseColor(s.color), s.id in opt.shownSubs) {
                        vm.savePrefs(shownSubs = toggleId(opt.shownSubs, s.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onChange(it) })
    }
}

@Composable
private fun CheckRow(label: String, dot: Color, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- helpers ----

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

/**
 * Converts each timed event from the home timezone to the device's current
 * timezone via its real instant (DST-safe), so events show at the wall-clock
 * time they actually occur where you are. All-day events don't move; at home
 * (device tz == home tz) nothing shifts.
 */
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
