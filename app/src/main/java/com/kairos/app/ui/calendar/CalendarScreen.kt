package com.kairos.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val data = ui.data
            when {
                ui.loading && data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.loadError ?: "Couldn't load your calendar.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.setTab(ui.tab) }) { Text("Retry") }
                    }
                }
                else -> CalendarContent(ui, data, vm)
            }
        }
    }
}

@Composable
private fun CalendarContent(ui: CalendarUiState, data: CalendarDto, vm: CalendarViewModel) {
    val dayLike = ui.tab == CalTab.AGENDA || ui.tab == CalTab.DAY
    var showOptions by remember { mutableStateOf(false) }
    val localEvents = remember(data.events, data.timezone) {
        localizeEvents(data.events, data.timezone)
    }
    Column(Modifier.fillMaxSize()) {
        // View selector + filters + Today
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewMenu(ui.tab, onSelect = { vm.setTab(it) })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showOptions = true }) { Text("Filters") }
            TextButton(onClick = { vm.goToday() }) { Text("Today") }
        }

        // Nav row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.goPrev() }) {
                Icon(KairosIcons.ChevronLeft, contentDescription = "Previous")
            }
            Text(
                if (dayLike) dayHeading(data.date) else data.heading,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { vm.goNext() }) {
                Icon(KairosIcons.ChevronRight, contentDescription = "Next")
            }
        }

        // Content — horizontal swipe pages prev/next (coexists with vertical scroll).
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
                CalTab.AGENDA -> AgendaView(localEvents, data.date)
                CalTab.MONTH -> MonthView(data, vm)
                else -> TimeGrid(data, localEvents)
            }
        }
    }

    if (showOptions) {
        OptionsSheet(data, vm, onDismiss = { showOptions = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSheet(data: CalendarDto, vm: CalendarViewModel, onDismiss: () -> Unit) {
    val opt = data.options
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Show", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ToggleRow("Family events", opt.showFamily) { vm.savePrefs(showFamily = it) }
            ToggleRow("School work", opt.showSchoolWork) { vm.savePrefs(showSchoolWork = it) }

            Spacer(Modifier.height(8.dp))
            Text("People", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            opt.people.forEach { p ->
                CheckRow(p.name, parseColor(p.color), p.id in opt.shownPeople) {
                    vm.savePrefs(shownPeople = toggleId(opt.shownPeople, p.id))
                }
            }

            if (opt.subscriptions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
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
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onChange(it) })
    }
}

@Composable
private fun CheckRow(label: String, dot: Color, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun toggleId(list: List<String>, id: String): List<String> =
    if (id in list) list - id else list + id

@Composable
private fun ViewMenu(tab: CalTab, onSelect: (CalTab) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                .clickable { open = true }
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tab.label, style = MaterialTheme.typography.labelLarge)
            Icon(KairosIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CalTab.entries.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.label) },
                    onClick = { open = false; onSelect(t) },
                )
            }
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
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
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
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(parseColor(e.color)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val secondary = buildEventSecondary(e)
                if (secondary != null) {
                    Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// ---- Month ----

@Composable
private fun MonthView(data: CalendarDto, vm: CalendarViewModel) {
    val currentMonth = data.date.take(7) // YYYY-MM
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach { d ->
                Text(
                    d,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            }
        }
        data.monthDays.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { iso ->
                    MonthCell(
                        iso = iso,
                        inMonth = iso.take(7) == currentMonth,
                        isToday = iso == data.today,
                        dots = data.monthDots[iso].orEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = { vm.openDay(iso) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    iso: String,
    inMonth: Boolean,
    isToday: Boolean,
    dots: List<String>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val dayNum = iso.substringAfterLast('-').trimStart('0').ifEmpty { "0" }
    Column(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                dayNum,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    isToday -> Color.White
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dots.take(3).forEach { c ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(parseColor(c)))
            }
        }
    }
}

// ---- helpers ----

private val DAY_HEADING = DateTimeFormatter.ofPattern("EEE, MMM d")

private fun dayHeading(iso: String): String =
    try {
        LocalDate.parse(iso).format(DAY_HEADING)
    } catch (_: Exception) {
        iso
    }

/**
 * Converts each timed event from the home timezone to the device's current
 * timezone via its real instant (DST-safe), so events show at the wall-clock
 * time they actually occur where you are. All-day events don't move. When the
 * device and home share a zone (i.e. at home) nothing shifts.
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
