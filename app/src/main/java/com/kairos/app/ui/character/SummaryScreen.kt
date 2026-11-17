package com.kairos.app.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.CompletionDayDto
import com.kairos.app.data.remote.dto.CompletionRowDto
import com.kairos.app.data.remote.dto.DayLogDto
import com.kairos.app.data.remote.dto.SummaryDto
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FILTERS = listOf("all" to "All", "chores" to "Chores", "bible" to "Bible", "school" to "School", "workouts" to "Workouts")

data class SummaryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: SummaryDto? = null,
    val view: String = "week",
    val at: String? = null,
    val filter: String = "all",
    /** The open day log: person, day, and what it says once loaded. */
    val dayFor: CompletionRowDto? = null,
    val day: String? = null,
    val log: DayLogDto? = null,
)

class SummaryViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(SummaryUiState())
    val ui: StateFlow<SummaryUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        val s = _ui.value
        _ui.update { it.copy(loading = it.data == null, error = null) }
        viewModelScope.launch {
            try {
                val d = session.loadSummary(view = s.view, at = s.at, filter = s.filter)
                _ui.update { it.copy(loading = false, data = d) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Couldn't load the summary.") }
            }
        }
    }

    fun setView(view: String) {
        _ui.update { it.copy(view = view, at = null) }
        load()
    }

    fun setFilter(f: String) {
        _ui.update { it.copy(filter = f) }
        load()
    }

    /** One week or month back (-1) or on (+1). */
    fun shift(by: Int) {
        val d = _ui.value.data ?: return
        val from = runCatching { LocalDate.parse(d.from) }.getOrNull() ?: return
        val next = if (d.view == "month") from.plusMonths(by.toLong()) else from.plusWeeks(by.toLong())
        _ui.update { it.copy(at = next.toString()) }
        load()
    }

    fun openDay(row: CompletionRowDto, date: String) {
        _ui.update { it.copy(dayFor = row, day = date, log = null) }
        loadDay()
    }

    fun stepDay(by: Int) {
        val s = _ui.value
        val today = s.data?.today ?: return
        val d = runCatching { LocalDate.parse(s.day) }.getOrNull() ?: return
        val next = d.plusDays(by.toLong()).toString()
        if (next > today) return
        _ui.update { it.copy(day = next, log = null) }
        loadDay()
    }

    fun closeDay() = _ui.update { it.copy(dayFor = null, day = null, log = null) }

    private fun loadDay() {
        val s = _ui.value
        val row = s.dayFor ?: return
        val day = s.day ?: return
        viewModelScope.launch {
            runCatching { session.loadDayLog(userId = row.id, date = day) }
                .onSuccess { log -> _ui.update { if (it.day == day && it.dayFor?.id == row.id) it.copy(log = log) else it } }
        }
    }
}

private fun parseHex(hex: String?): Color = runCatching {
    Color(android.graphics.Color.parseColor(if (hex.isNullOrBlank()) "#64748b" else hex))
}.getOrDefault(Color(0xFF64748B))

/** A cell's fill from the person's colour: fuller as more got done. */
private fun fillFor(c: Color, pct: Int): Color = c.copy(alpha = if (pct >= 100) 1f else if (pct >= 75) 0.7f else if (pct >= 50) 0.42f else 0.18f)

private fun shortDay(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE", Locale.US))
}.getOrDefault(iso)

private fun rangeTitle(d: SummaryDto): String = runCatching {
    val from = LocalDate.parse(d.from)
    val to = LocalDate.parse(d.to)
    if (d.view == "month") {
        from.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
    } else {
        val f = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        "${from.format(f)} – ${to.format(f)}"
    }
}.getOrDefault("")

/**
 * Summary (v0.529, server 0.733), last in the menu: the family goal and the
 * completion grid. A parent's phone shows everyone; a child's, only them.
 * Week or Month, an area filter; a day's share in the person's colour, "–"
 * with nothing due, a dot when late work was finished that day, today
 * outlined, days to come dashed. Tap a day for that person's day log.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SummaryScreen(onOpenDrawer: () -> Unit, onOpenCoop: () -> Unit) {
    val container = rememberContainer()
    val vm: SummaryViewModel = viewModel(factory = viewModelFactory { initializer { SummaryViewModel(container.sessionRepository) } })
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Summary") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val data = ui.data
            when {
                ui.loading && data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.error ?: "Couldn't load the summary.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    FamilyGoalCard(data.familyGoal, onClick = onOpenCoop)
                    CompletionCard(data = data, ui = ui, vm = vm)
                }
            }
        }
    }

    val row = ui.dayFor
    val day = ui.day
    if (row != null && day != null) {
        ModalBottomSheet(onDismissRequest = { vm.closeDay() }) {
            DayLogSheet(name = row.name, day = day, today = ui.data?.today ?: day, log = ui.log, onStep = { vm.stepDay(it) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompletionCard(data: SummaryDto, ui: SummaryUiState, vm: SummaryViewModel) {
    val single = data.rows.size == 1
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "COMPLETION",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (single && data.view == "month") {
                    Text(data.rows[0].total?.let { "$it%" } ?: "–", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            // Week / Month.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)).padding(2.dp),
            ) {
                listOf("week" to "Week", "month" to "Month").forEach { (k, label) ->
                    val on = data.view == k
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { if (!on) vm.setView(k) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { vm.shift(-1) }.padding(horizontal = 10.dp))
                Text(rangeTitle(data), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { vm.shift(1) }.padding(horizontal = 10.dp))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FILTERS.forEach { (k, label) ->
                    val on = ui.filter == k
                    Box(
                        Modifier.height(32.dp).clip(RoundedCornerShape(50))
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                            .clickable { vm.setFilter(k) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (data.view == "month" && single) {
                MonthCalendar(row = data.rows[0], data = data, onTap = { r, d -> vm.openDay(r, d) })
            } else if (data.view == "month") {
                data.rows.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Initial(row)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            row.days.chunked(16).forEach { chunk ->
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    chunk.forEach { d -> DayCell(row, d, data.today, small = true, label = null, modifier = Modifier.weight(1f).height(14.dp)) { vm.openDay(row, d.date) } }
                                    repeat(16 - chunk.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        Text(row.total?.let { "$it%" } ?: "–", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                    }
                }
            } else {
                // Week: a header, then one row each.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!single) Spacer(Modifier.width(28.dp))
                    data.rows.firstOrNull()?.days?.forEach { d ->
                        val today = d.date == data.today
                        Text(
                            "${shortDay(d.date).take(1)}\n${d.date.takeLast(2).trimStart('0')}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                            color = if (today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text("Wk", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.width(38.dp))
                }
                data.rows.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!single) Initial(row)
                        row.days.forEach { d ->
                            DayCell(row, d, data.today, small = false, label = null, modifier = Modifier.weight(1f).height(40.dp)) { vm.openDay(row, d.date) }
                        }
                        Text(row.total?.let { "$it%" } ?: "–", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(38.dp))
                    }
                }
            }
            Legend()
        }
    }
}

@Composable
private fun MonthCalendar(row: CompletionRowDto, data: SummaryDto, onTap: (CompletionRowDto, String) -> Unit) {
    val first = runCatching { LocalDate.parse(data.from) }.getOrNull() ?: return
    val lead = first.dayOfWeek.value % 7 // Sunday first
    val cells: List<CompletionDayDto?> = List(lead) { null } + row.days
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d ->
                    if (d == null) Spacer(Modifier.weight(1f).aspectRatio(1f))
                    else DayCell(row, d, data.today, small = false, label = d.date.takeLast(2).trimStart('0'), modifier = Modifier.weight(1f).aspectRatio(1f)) { onTap(row, d.date) }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(row: CompletionRowDto, d: CompletionDayDto, today: String, small: Boolean, label: String?, modifier: Modifier, onTap: () -> Unit) {
    val shape = RoundedCornerShape(if (small) 3.dp else 8.dp)
    val ink = MaterialTheme.colorScheme.onSurface
    val isToday = d.date == today
    var m = modifier.clip(shape)
    if (isToday) m = modifier.border(2.dp, ink, shape).padding(3.dp).clip(shape)
    if (d.future) {
        Box(m.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape))
        return
    }
    val c = parseHex(row.color)
    val pct = d.pct
    val bg = if (pct == null) MaterialTheme.colorScheme.surfaceVariant else fillFor(c, pct)
    val fg = if (pct != null && pct >= 75) Color.White else ink
    Box(m.background(bg).clickable { onTap() }, contentAlignment = Alignment.Center) {
        if (!small) {
            Text(
                label ?: (pct?.let { "$it%" } ?: "–"),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (pct == null) MaterialTheme.colorScheme.onSurfaceVariant else fg,
            )
        }
        if (d.late) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(if (small) 1.dp else 4.dp)
                    .size(if (small) 3.dp else 6.dp).clip(CircleShape).background(fg),
            )
        }
    }
}

@Composable
private fun Initial(row: CompletionRowDto) {
    val c = parseHex(row.color)
    Box(Modifier.size(28.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
        Text(row.name.take(1), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val swatch = Color(0xFF64748B)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = muted)
            listOf(30, 60, 80, 100).forEach { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(fillFor(swatch, it))) }
            Text("All done", style = MaterialTheme.typography.labelSmall, color = muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            Text("Nothing due", style = MaterialTheme.typography.labelSmall, color = muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
            Text("Late work done that day", style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

@Composable
private fun DayLogSheet(name: String, day: String, today: String, log: DayLogDto?, onStep: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavDot(label = "‹", enabled = true) { onStep(-1) }
            val title = when (day) {
                today -> "Today"
                runCatching { LocalDate.parse(today).minusDays(1).toString() }.getOrDefault("") -> "Yesterday"
                else -> runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)) }.getOrDefault(day)
            }
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            NavDot(label = "›", enabled = day < today) { onStep(1) }
        }
        if (log == null) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (log.items.isEmpty()) {
            Text("Nothing scheduled this day.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("${log.doneCount} of ${log.assigned} done", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            log.items.forEachIndexed { i, it ->
                if (i > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        it.skipped -> Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                        it.done -> Text("✓", color = Color(0xFF047857), fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                        else -> Box(Modifier.size(14.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    }
                    Text(
                        it.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (it.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val tag = it.doneOn?.let { d -> "Done ${shortDay(d)}" } ?: it.from?.let { d -> "From ${shortDay(d)}" }
                    if (tag != null) {
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(categoryLabel(it.category), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun categoryLabel(c: String): String = when (c) {
    "CHORE" -> "CHORES"
    "BIBLE" -> "BIBLE"
    "SCHOOL" -> "SCHOOL"
    "EXERCISE" -> "WORKOUTS"
    "APPOINTMENT" -> "EVENTS"
    "WORK" -> "WORK"
    else -> "TASKS"
}

@Composable
private fun NavDot(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.35f))
    }
}
