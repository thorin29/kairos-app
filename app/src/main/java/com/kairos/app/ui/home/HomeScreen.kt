package com.kairos.app.ui.home

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.CategoryBarDto
import com.kairos.app.data.remote.dto.GetAheadChoreDto
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.data.remote.dto.TaskDto
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.AttendanceIcon
import com.kairos.app.ui.common.AttendeesColumn
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import com.kairos.app.ui.nav.sectionFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    person: PersonDto,
    onOpenDrawer: () -> Unit,
    onLogWorkout: (String) -> Unit,
    onOpenMoney: () -> Unit = {},
    onAssignTask: () -> Unit = {},
    refreshKey: Int = 0,
) {
    val container = rememberContainer()
    val vm: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Load on first show (refreshKey starts at 0) and again whenever we return to
    // Home (AppRoot bumps refreshKey). The ViewModel does not load in init, so this
    // is the sole trigger — reliable under our drawer navigation.
    LaunchedEffect(refreshKey) {
        vm.load()
    }

    LaunchedEffect(ui.actionError) {
        ui.actionError?.let {
            snackbar.showSnackbar(it)
            vm.clearActionError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
                actions = {
                    IconButton(onClick = onAssignTask) {
                        Icon(KairosIcons.Plus, contentDescription = "Assign a task")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Box(
            Modifier
                .padding(inner)
                .fillMaxSize(),
        ) {
            when {
                ui.loading && ui.dashboard == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                ui.dashboard == null -> ErrorState(ui.loadError, onRetry = vm::load)
                else -> DashboardContent(person, ui, vm, onOpenMoney)
            }

            ui.workoutSheet?.let { task -> WorkoutSheet(task, vm, onLogWorkout) }
        }
    }
}

@Composable
private fun WorkoutSheet(task: TaskDto, vm: HomeViewModel, onLogWorkout: (String) -> Unit) {
    // A centered animated dialog (not a bottom sheet): the sheet's window kept
    // leaking across navigation and freezing, so this uses the shared reliable
    // dialog instead — it opens every time and disposes cleanly on navigation.
    AnimatedDialog(onDismissRequest = vm::dismissWorkout, title = task.title) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { vm.dismissWorkout(); onLogWorkout(task.dueDate) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Log workout") }

            when (task.status) {
                "COMPLETE" -> OutlinedButton(
                    onClick = { vm.dismissWorkout(); vm.undoWorkout(task) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Mark as not done") }
                "SKIPPED" -> Text(
                    "Marked as a rest day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> OutlinedButton(
                    onClick = { vm.dismissWorkout(); vm.restDay(task) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Rest day") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(person: PersonDto, ui: HomeUiState, vm: HomeViewModel, onOpenMoney: () -> Unit = {}) {
    val d = ui.dashboard!!
    var scheduleDetail by remember { mutableStateOf<com.kairos.app.data.remote.dto.ScheduleItemDto?>(null) }
    PullToRefreshBox(
        isRefreshing = ui.refreshing,
        onRefresh = vm::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { HeaderCard(person.name, d.percent) }

            d.money?.let { m ->
                if (m.pendingApprovals > 0 || m.rewardMonths > 0) {
                    item(key = "money-reminder") { MoneyReminder(m, onOpenMoney) }
                }
            }

            if (d.categories.isNotEmpty()) {
                item(key = "bars") { CategoryBars(d.categories) }
            }

            if (d.sportPrompts.isNotEmpty()) {
                item(key = "sport-prompts") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        d.sportPrompts.forEach { p ->
                            SportPromptCard(p, d.date, ui.busyIds.contains("sport-${p.eventId}-${p.dateISO}"), vm)
                        }
                    }
                }
            }

            // Overdue ("carried over") work now sits under its own category —
            // oldest first, above today's items — instead of one lumped list.
            run {
                val catOrder = LinkedHashSet<String>()
                d.groups.forEach { catOrder.add(it.category) }
                d.overdue.forEach { catOrder.add(it.category) }
                if (d.personalReading != null) catOrder.add("BIBLE")
                if (d.getAhead.isNotEmpty()) catOrder.add("CHORE")
                if (d.alwaysOpen.isNotEmpty()) catOrder.add("CHORE")

                catOrder.forEach { cat ->
                    if (cat == "CHORE") {
                        // Chores get their own pop-up-style card (Overdue / Today /
                        // Get ahead / Always open), keeping the home tidy — like School.
                        item(key = "chores-card") {
                            ChoresHomeCard(
                                overdue = d.overdue.filter { it.category == "CHORE" }.sortedBy { it.dueDate },
                                today = d.groups.firstOrNull { it.category == "CHORE" }?.items ?: emptyList(),
                                getAhead = d.getAhead,
                                alwaysOpen = d.alwaysOpen,
                                busyIds = ui.busyIds,
                                vm = vm,
                            )
                        }
                        return@forEach
                    }
                    val group = d.groups.firstOrNull { it.category == cat }
                    val overdueItems = d.overdue.filter { it.category == cat }.sortedBy { it.dueDate }
                    val todayItems = group?.items ?: emptyList()
                    val label = group?.label ?: labelForCategory(cat)
                    item(key = "cat-$cat") {
                        SectionBlock(label, cat) {
                            overdueItems.forEach { task -> TaskRow(task, ui.busyIds.contains(task.id), vm) }
                            todayItems.forEach { task -> TaskRow(task, ui.busyIds.contains(task.id), vm) }
                            if (cat == "BIBLE" && d.personalReading != null) {
                                PersonalReadingRow(d.personalReading, ui.busyIds.contains("personal-reading"), vm)
                            }
                        }
                    }
                }
            }

            if (d.overdue.isEmpty() && d.groups.isEmpty() && d.personalReading == null) {
                item(key = "empty") { EmptyDay() }
            }

            if (d.upForGrabs.isNotEmpty()) {
                item(key = "grabs") {
                    SectionBlock("Up for grabs") {
                        d.upForGrabs.forEach { c -> UpForGrabsRow(c, ui.busyIds.contains("claim-${c.id}"), vm) }
                    }
                }
            }

            item(key = "schedule") {
                SectionBlock("Today's schedule") {
                    if (d.schedule.isEmpty()) {
                        Text(
                            "Nothing scheduled today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        )
                    } else {
                        d.schedule.forEachIndexed { i, ev ->
                            if (i > 0) {
                                Box(
                                    Modifier.fillMaxWidth().height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                            ScheduleRow(ev) { scheduleDetail = ev }
                        }
                    }
                }
            }
        }
    }

    scheduleDetail?.let { ScheduleDetailDialog(it) { scheduleDetail = null } }
}

/** A centered detail popup for a home-dashboard schedule item — the same info
 *  as a calendar event's detail (everyone on it, time, location, notes). */
@Composable
private fun ScheduleDetailDialog(
    ev: com.kairos.app.data.remote.dto.ScheduleItemDto,
    onDismiss: () -> Unit,
) {
    AnimatedDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(parseScheduleColor(ev.color)))
                Spacer(Modifier.width(8.dp))
                Text(ev.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("When", if (ev.allDay) "All day" else ev.timeLabel)
                if (ev.attendees.any { it.state.isNotBlank() }) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Who", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ev.attendees.forEach { a ->
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AttendanceIcon(a.state, Modifier.size(18.dp))
                                Text(a.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                } else if (ev.attendees.isNotEmpty()) {
                    DetailLine("Who", ev.attendees.joinToString("\n") { it.name })
                } else if (ev.ownerName.isNotBlank()) {
                    DetailLine("Who", ev.ownerName)
                }
                if (!ev.location.isNullOrBlank()) DetailLine("Where", ev.location!!)
                if (!ev.notes.isNullOrBlank()) DetailLine("Notes", ev.notes!!)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun sportDayLabel(iso: String): String = try {
    java.time.LocalDate.parse(iso)
        .dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
} catch (e: Exception) { "" }

@Composable
private fun SportPromptCard(
    prompt: com.kairos.app.data.remote.dto.SportPromptDto,
    today: String,
    busy: Boolean,
    vm: HomeViewModel,
) {
    val extra =
        if (today.isNotBlank() && prompt.dateISO.isNotBlank() && prompt.dateISO != today)
            sportDayLabel(prompt.dateISO)
        else ""
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Did you do ${prompt.title}?" + if (extra.isNotBlank()) " \u00b7 $extra" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.answerSport(prompt.eventId, prompt.dateISO, true) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Yes") }
                OutlinedButton(
                    onClick = { vm.answerSport(prompt.eventId, prompt.dateISO, false) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("No") }
            }
        }
    }
}

@Composable
private fun MoneyReminder(m: com.kairos.app.data.remote.dto.DashboardMoneyDto, onReview: () -> Unit) {
    val amberBg = androidx.compose.ui.graphics.Color(0xFFFFFBEB)
    val amberBorder = androidx.compose.ui.graphics.Color(0xFFFCD34D)
    val amberDk = androidx.compose.ui.graphics.Color(0xFFB45309)
    val amberText = androidx.compose.ui.graphics.Color(0xFF78350F)
    val amberBtn = androidx.compose.ui.graphics.Color(0xFFD97706)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(amberBg)
            .border(1.dp, amberBorder, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (m.pendingApprovals > 0) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.Icon(KairosIcons.Dollar, contentDescription = null, tint = amberDk, modifier = Modifier.size(16.dp))
                androidx.compose.material3.Text(
                    "${m.pendingApprovals} " + (if (m.pendingApprovals == 1) "transaction" else "transactions") + " to approve",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = amberText,
                )
            }
        }
        if (m.rewardMonths > 0) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.Icon(KairosIcons.Bible, contentDescription = null, tint = amberDk, modifier = Modifier.size(16.dp))
                androidx.compose.material3.Text(
                    "Bible reading " + (if (m.rewardMonths == 1) "reward" else "rewards") + " ready" +
                        (if (m.rewardMonths > 1) " (${m.rewardMonths} months)" else ""),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = amberText,
                )
            }
        }
        Row(
            Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(amberBtn)
                .clickable { onReview() }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            androidx.compose.material3.Text(
                "Review",
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

private val ChoresGreen = Color(0xFF059669)

/** Home Chores card: a tidy summary that expands to the overlay layout
 *  (Overdue / Today / Get ahead), matching the web pop-up. Shows
 *  "Complete for today!" once nothing chore-related is pending. */
@Composable
private fun ChoresHomeCard(
    overdue: List<TaskDto>,
    today: List<TaskDto>,
    getAhead: List<GetAheadChoreDto>,
    alwaysOpen: List<com.kairos.app.data.remote.dto.AlwaysOpenDashDto>,
    busyIds: Set<String>,
    vm: HomeViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    val pendingOverdue = overdue.filter { it.status != "COMPLETE" }
    val pendingToday = today.filter { it.status != "COMPLETE" }
    val hadChores = overdue.isNotEmpty() || today.isNotEmpty()
    val completeForToday = pendingOverdue.isEmpty() && pendingToday.isEmpty() && hadChores

    val summary = buildList {
        if (pendingOverdue.isNotEmpty()) add("${pendingOverdue.size} overdue")
        if (pendingToday.isNotEmpty()) add("${pendingToday.size} today")
        if (pendingOverdue.isEmpty() && pendingToday.isEmpty() && getAhead.isNotEmpty()) add("${getAhead.size} to get ahead")
    }.joinToString(" \u00b7 ").ifEmpty { "All caught up" }

    Column {
        SectionHeader("Chores", "CHORE")
        Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (completeForToday) "Complete for today!" else summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (completeForToday) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            completeForToday -> ChoresGreen
                            pendingOverdue.isNotEmpty() -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Text(if (expanded) "Hide" else "Open", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (expanded) {
                    if (overdue.isNotEmpty()) {
                        MiniLabel("Overdue", MaterialTheme.colorScheme.error)
                        overdue.forEach { TaskRow(it, busyIds.contains(it.id), vm) }
                    }
                    if (today.isNotEmpty()) {
                        MiniLabel("Today")
                        today.forEach { TaskRow(it, busyIds.contains(it.id), vm) }
                    }
                    if (getAhead.isNotEmpty()) {
                        MiniLabel("Get ahead")
                        getAhead.forEach { AheadChoreRow(it, busyIds.contains(it.taskId), vm) }
                    }
                    if (alwaysOpen.isNotEmpty()) {
                        MiniLabel("Always open")
                        alwaysOpen.forEach { AlwaysOpenRow(it, busyIds.contains("always-${it.id}"), vm) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniLabel(text: String, color: Color = Color.Unspecified) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun AheadChoreRow(chore: GetAheadChoreDto, busy: Boolean, vm: HomeViewModel) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(999.dp))
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                .clickable(enabled = !busy) { vm.toggle(chore.taskId, false) },
        )
        Column(Modifier.weight(1f)) {
            Text(chore.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "due ${homeShortDate(chore.dueDateISO)}" + if (chore.bonus > 0) " \u00b7 +${chore.bonus} bonus" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun homeShortDate(iso: String): String {
    val p = iso.split("-")
    if (p.size != 3) return iso
    val m = p[1].toIntOrNull() ?: return iso
    val d = p[2].toIntOrNull() ?: return iso
    return "$m/$d"
}

/** A titled section: a small uppercase header above a card holding the rows,
 *  matching the web home so sections read as distinct blocks. */
@Composable
private fun SectionBlock(header: String, category: String? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column {
        SectionHeader(header, category)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun HeaderCard(name: String, percent: Int?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Hi, $name", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))
            if (percent == null) {
                Text(
                    "Nothing scheduled yet today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "$percent% done today",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(8.dp))
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryBars(bars: List<CategoryBarDto>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            bars.forEach { bar ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            bar.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (bar.overdue > 0) {
                            Text(
                                "${bar.overdue} late",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Text(
                            "${bar.complete}/${bar.total}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(6.dp))
                    LinearProgressIndicator(
                        progress = { if (bar.total > 0) bar.complete / bar.total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

/** Maps a task category to the matching side-menu section (icon + brand color). */
private fun sectionKeyForCategory(category: String): String = when (category) {
    "BIBLE" -> "bible"
    "CHORE" -> "chores"
    "SCHOOL" -> "school"
    "EXERCISE" -> "workouts"
    "WORK" -> "tasks"
    "APPOINTMENT" -> "calendar"
    else -> "tasks"
}

/** Fallback label for a category that only has overdue (carried-over) items and
 *  so has no group row from the server. */
private fun labelForCategory(category: String): String = when (category) {
    "BIBLE" -> "Bible reading"
    "CHORE" -> "Chores"
    "SCHOOL" -> "School"
    "EXERCISE" -> "Workouts"
    "WORK" -> "Tasks"
    "APPOINTMENT" -> "Appointments"
    else -> "Other"
}

@Composable
private fun SectionHeader(label: String, category: String? = null) {
    val section = category?.let { sectionFor(sectionKeyForCategory(it)) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    ) {
        if (section != null) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = section.color,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TaskRow(task: TaskDto, busy: Boolean, vm: HomeViewModel) {
    val done = task.status == "COMPLETE"
    // Workout prompts open the action sheet; ordinary completable rows toggle.
    val tappable = !busy && (task.isWorkout || task.completable)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tappable) {
                if (task.isWorkout) vm.openWorkout(task) else vm.toggle(task.id, done)
            }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(
                    Icons.Filled.Check,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.primary,
                )
                task.isWorkout -> HollowMarker()
                task.completable -> Checkbox(checked = false, onCheckedChange = null)
                else -> HollowMarker()
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done || task.stale) TextDecoration.LineThrough else null,
                color = if (done || task.stale) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            val secondary = buildSecondary(task)
            if (secondary != null) {
                Text(
                    secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun UpForGrabsRow(c: com.kairos.app.data.remote.dto.UpForGrabsDto, busy: Boolean, vm: HomeViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(if (c.isShared) "shared chore" else "released from ${c.releasedByName}")
                    if (c.isOverdue) append(" \u00b7 due ${shortDate(c.dueDate)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (c.isOverdue) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = { vm.claimChore(c.id) }, enabled = !busy) {
            Text(if (busy) "Taking\u2026" else "Take it")
        }
    }
}

@Composable
private fun AlwaysOpenRow(c: com.kairos.app.data.remote.dto.AlwaysOpenDashDto, busy: Boolean, vm: HomeViewModel) {
    val onCooldown = c.readyAtMs != null && c.readyAtMs > System.currentTimeMillis()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.bodyLarge)
            if (c.myCount > 0) {
                Text(
                    "done ${c.myCount}\u00d7 today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = { vm.completeAlwaysOpen(c.id) }, enabled = !busy && !onCooldown) {
            Text(if (busy) "\u2026" else if (onCooldown) "Not back yet" else "Done")
        }
    }
}

@Composable
private fun ScheduleRow(ev: com.kairos.app.data.remote.dto.ScheduleItemDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 2.dp).width(4.dp).height(36.dp)
                .clip(RoundedCornerShape(2.dp)).background(parseScheduleColor(ev.color)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ev.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                append(if (ev.allDay) "All day" else ev.timeLabel)
                if (!ev.location.isNullOrBlank()) append(" \u00b7 ${ev.location}")
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        AttendeesColumn(
            attendees = ev.attendees,
            fallbackLabel = ev.ownerName,
            modifier = Modifier.widthIn(max = 130.dp),
        )
    }
}

private fun parseScheduleColor(hex: String?): Color {
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

@Composable
private fun PersonalReadingRow(reading: com.kairos.app.data.remote.dto.PersonalReadingDto, busy: Boolean, vm: HomeViewModel) {
    val done = reading.read
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) { vm.togglePersonalReading(reading.passage, done) }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                done -> Icon(
                    Icons.Filled.Check,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.primary,
                )
                else -> Checkbox(checked = false, onCheckedChange = null)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Personal bible reading",
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                reading.passage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A small hollow circle used where a checkbox would be, for non-toggle rows. */
@Composable
private fun HollowMarker() {
    Box(
        Modifier
            .size(18.dp)
            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

/** Secondary line: school detail, workout state, and/or an overdue due-date. */
private fun buildSecondary(task: TaskDto): String? {
    val parts = mutableListOf<String>()
    task.subtitle?.let { parts += it }
    if (task.stale) parts += "expired"
    if (task.isWorkout) {
        when (task.status) {
            "SKIPPED" -> parts += "rest day"
            "COMPLETE" -> {} // strike-through already conveys done
            else -> parts += "tap to log"
        }
    }
    if (task.isOverdue) parts += "due ${shortDate(task.dueDate)}"
    return parts.joinToString(" · ").ifBlank { null }
}

/** "2026-09-02" -> "9/2". Falls back to the raw string if it can't parse. */
private fun shortDate(iso: String): String {
    val p = iso.split("-")
    return if (p.size == 3) "${p[1].toIntOrNull() ?: p[1]}/${p[2].toIntOrNull() ?: p[2]}" else iso
}

@Composable
private fun EmptyDay() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "All clear — nothing left today.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorState(message: String?, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message ?: "Couldn't load your day.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(16.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}
