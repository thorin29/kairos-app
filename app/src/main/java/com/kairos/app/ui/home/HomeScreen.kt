package com.kairos.app.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.CategoryBarDto
import com.kairos.app.data.remote.dto.ChoreBadgeDto
import com.kairos.app.data.remote.dto.GetAheadChoreDto
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.data.remote.dto.ReadingProgressDto
import com.kairos.app.data.remote.dto.SchoolCardProgressDto
import com.kairos.app.data.remote.dto.TaskDto
import com.kairos.app.data.remote.dto.WorkoutBlocksDto
import com.kairos.app.data.remote.dto.WorkoutCardDto
import com.kairos.app.data.remote.dto.UpForGrabsDto
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.AttendanceIcon
import com.kairos.app.ui.common.AttendeesColumn
import com.kairos.app.ui.common.rememberContainer
import androidx.navigation.NavBackStackEntry
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.kairos.app.ui.character.CoopViewModel
import com.kairos.app.ui.nav.KairosIcons
import com.kairos.app.ui.nav.sectionFor
import com.kairos.app.ui.theme.KairosThemeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    person: PersonDto,
    onOpenDrawer: () -> Unit,
    onLogWorkout: (String) -> Unit,
    onOpenMoney: () -> Unit = {},
    onAssignTask: () -> Unit = {},
    onOpenChores: () -> Unit = {},
    onOpenReading: () -> Unit = {},
    onOpenSchoolWork: () -> Unit = {},
    onOpenCoop: () -> Unit = {},
    onOpenPrograms: () -> Unit = {},
    refreshKey: Int = 0,
) {
    val container = rememberContainer()
    // "Start a workout program?" for someone with no plan and nothing logged
    // lately (server 0.611+). Its own small read; the card appears when it lands.
    var invite by remember { mutableStateOf(false) }
    val inviteScope = rememberCoroutineScope()
    LaunchedEffect(refreshKey) {
        invite = runCatching { container.sessionRepository.loadPrograms().invite }.getOrDefault(false)
    }
    val vm: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.sessionRepository, container.payloadCache) }
        },
    )
    // The family goal rides its own view-model rather than the dashboard payload.
    // It needs the progression read, which is the heaviest query on the server,
    // and the home screen must not wait on it: the card appears when it arrives.
    // Cache-backed, so it paints immediately on a return visit and offline.
    val coopVm: CoopViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CoopViewModel(container.sessionRepository, container.payloadCache) }
        },
    )
    val ui by vm.ui.collectAsState()
    val coopUi by coopVm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Load on first show (refreshKey starts at 0) and again whenever we return to
    // Home (AppRoot bumps refreshKey). The ViewModel does not load in init, so this
    // is the sole trigger — reliable under our drawer navigation.
    LaunchedEffect(refreshKey) {
        vm.load()
        coopVm.load()
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
                else -> DashboardContent(
                    person, ui, vm, onOpenMoney, onOpenChores, onOpenSchoolWork,
                    onLogWorkout, onOpenReading,
                    coopUi.data, coopUi.busy, coopVm::grant, onOpenCoop,
                    invite = invite,
                    onOpenPrograms = onOpenPrograms,
                    onAnswerInvite = { answer ->
                        invite = false
                        inviteScope.launch {
                            runCatching { container.sessionRepository.answerProgramInvite(answer) }
                        }
                    },
                )
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    person: PersonDto,
    ui: HomeUiState,
    vm: HomeViewModel,
    onOpenMoney: () -> Unit = {},
    onOpenChores: () -> Unit = {},
    onOpenSchoolWork: () -> Unit = {},
    onLogWorkout: (String) -> Unit = {},
    onOpenReading: () -> Unit = {},
    coop: com.kairos.app.data.remote.dto.CoopDto? = null,
    coopBusy: Boolean = false,
    onCheckOffGoal: (String) -> Unit = {},
    onOpenCoop: () -> Unit = {},
    invite: Boolean = false,
    onOpenPrograms: () -> Unit = {},
    onAnswerInvite: (String) -> Unit = {},
) {
    val d = ui.dashboard!!
    var scheduleDetail by remember { mutableStateOf<com.kairos.app.data.remote.dto.ScheduleItemDto?>(null) }
    // The dashboard is computed per day on the server. When it can't be reached we
    // paint the last cached copy, which may be an earlier day — say so plainly
    // instead of letting yesterday pass for today. (Computed here: `remember` is
    // not legal inside the LazyColumn's LazyListScope.)
    val staleDay = remember(d.date) {
        runCatching {
            val shown = java.time.LocalDate.parse(d.date)
            if (shown == java.time.LocalDate.now()) null else shown
        }.getOrNull()
    }
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
            // The greeting with the day's progress and a chip per area (v0.566).
            item(key = "header") { GreetingCard(name = person.name, dateISO = d.date, bars = d.categories) }

            if (staleDay != null) {
                item(key = "stale-day") { StaleDayNotice(staleDay) }
            }

            d.money?.let { m ->
                if (m.pendingApprovals > 0 || m.rewardMonths > 0) {
                    item(key = "money-reminder") { MoneyReminder(m, onOpenMoney) }
                }
            }


            if (d.sportPrompts.isNotEmpty() || ui.sportFeel.isNotEmpty()) {
                item(key = "sport-prompts") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.sportFeel.forEach { f ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    com.kairos.app.ui.workout.EffortChooser(
                                        question = "How hard was ${f.title.ifBlank { "it" }}?",
                                        effort = f.effort,
                                        hurt = f.hurt,
                                        onChange = { e, h ->
                                            vm.sportFeedback(eventId = f.eventId, effort = e, hurt = h)
                                            // The check mark saves and clears the card (v0.467).
                                            if (e != null && e != f.effort) vm.closeSportFeel(f.eventId)
                                        },
                                    )
                                    androidx.compose.material3.TextButton(onClick = { vm.closeSportFeel(f.eventId) }) {
                                        Text("Skip")
                                    }
                                }
                            }
                        }
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
                if (d.workoutOverdue > 0 || d.workoutRest != null) catOrder.add("EXERCISE")
                if (d.reading.isNotEmpty()) catOrder.add("READING")
                if (d.getAhead.isNotEmpty()) catOrder.add("CHORE")
                if (d.alwaysOpen.isNotEmpty()) catOrder.add("CHORE")
                if (d.upForGrabs.isNotEmpty()) catOrder.add("CHORE")

                // Fixed order, matching the web dashboard: Bible reading, Chores,
                // School, Workouts, then anything else (in its arrival order).
                val homeOrder =
                    listOf("BIBLE", "READING", "CHORE", "SCHOOL", "EXERCISE", "WORK", "APPOINTMENT", "OTHER")
                // The areas as tiles (v0.566), two across, in the same order.
                val tiles = mutableListOf<@Composable (Modifier) -> Unit>()
                catOrder
                    .sortedBy { c ->
                        homeOrder.indexOf(c).let { if (it < 0) Int.MAX_VALUE else it }
                    }
                    .forEach { cat ->
                        when (cat) {
                            "READING" -> tiles.add { m -> ReadingHomeSection(d.reading, onOpenReading, modifier = m) }
                            "CHORE" -> tiles.add { m ->
                                WorkSummaryCard(
                                    title = "Chores",
                                    category = "CHORE",
                                    overdue = d.overdue.filter { it.category == "CHORE" },
                                    today = d.groups.firstOrNull { it.category == "CHORE" }?.items ?: emptyList(),
                                    getAheadCount = d.getAhead.size,
                                    upForGrabs = d.upForGrabs,
                                    badges = d.choreBadges,
                                    onOpen = onOpenChores,
                                    modifier = m,
                                )
                            }
                            "SCHOOL" -> tiles.add { m ->
                                WorkSummaryCard(
                                    title = "School",
                                    category = "SCHOOL",
                                    overdue = d.overdue.filter { it.category == "SCHOOL" },
                                    today = d.groups.firstOrNull { it.category == "SCHOOL" }?.items ?: emptyList(),
                                    getAheadCount = 0,
                                    onOpen = onOpenSchoolWork,
                                    modifier = m,
                                )
                            }
                            "EXERCISE" -> tiles.add { m ->
                                WorkoutSummaryCard(
                                    today = d.groups.firstOrNull { it.category == "EXERCISE" }?.items ?: emptyList(),
                                    overdueCount = d.workoutOverdue,
                                    rest = d.workoutRest,
                                    extraLogged = d.workoutExtraLogged,
                                    remaining = d.workoutRemaining.orEmpty(),
                                    blocks = d.workoutBlocks,
                                    card = d.workoutCard,
                                    onOpen = { onLogWorkout(d.date) },
                                    modifier = m,
                                )
                            }
                            else -> {
                                val group = d.groups.firstOrNull { it.category == cat }
                                val label = when (cat) {
                                    "BIBLE" -> "Bible"
                                    "WORK" -> "Tasks"
                                    else -> group?.label ?: labelForCategory(cat)
                                }
                                tiles.add { m ->
                                    TaskTile(
                                        category = cat,
                                        label = label,
                                        overdue = d.overdue.filter { it.category == cat }.sortedBy { it.dueDate },
                                        today = group?.items ?: emptyList(),
                                        personal = if (cat == "BIBLE") d.personalReading else null,
                                        busyIds = ui.busyIds,
                                        vm = vm,
                                        onLogWorkout = onLogWorkout,
                                        modifier = m,
                                    )
                                }
                            }
                        }
                    }
                if (tiles.isNotEmpty()) {
                    item(key = "tiles") { TileGrid(tiles) }
                }
            }

            // Shown for a goal (carried, chosen or earned), or while none is chosen
            // when the server says there's something to nudge: a vote I haven't
            // cast, or a parent's pick. Once I've voted, no card.
            coop
                ?.takeIf { c ->
                    c.carried != null ||
                        c.proposals.any { it.status == "SELECTED" || it.status == "GRANTED" } ||
                        c.homeNudge != null
                }
                ?.let { c ->
                    item(key = "family-goal") {
                        FamilyGoalCard(c, coopBusy, onCheckOffGoal, onOpenCoop)
                    }
                }


            if (invite) {
                item(key = "program-invite") {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Start a workout program?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                com.kairos.app.ui.workout.ProgramPill("See programs", filled = true, onClick = onOpenPrograms)
                                Text(
                                    "Not now",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { onAnswerInvite("later") }.padding(8.dp),
                                )
                                Text(
                                    "Don't ask again",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable { onAnswerInvite("never") }.padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (d.overdue.isEmpty() && d.groups.isEmpty() && d.personalReading == null) {
                item(key = "empty") { EmptyDay() }
            }

            item(key = "schedule") {
                SectionBlock("Today's schedule", category = "APPOINTMENT", edge = false) {
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
        androidx.compose.runtime.DisposableEffect(Unit) {
            com.kairos.app.data.diag.Breadcrumbs.drop("open ScheduleDetail")
            onDispose { com.kairos.app.data.diag.Breadcrumbs.drop("close ScheduleDetail") }
        }
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

/** Shown when Home is painting a cached day that isn't today — the server was
 *  unreachable, so there is no way to compute today's page on the phone. */
@Composable
private fun StaleDayNotice(shown: java.time.LocalDate) {
    val label = shown.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d"))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            KairosIcons.Clock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "Showing $label \u2014 the server is unreachable, so today's page hasn't loaded. " +
                "Anything you tap is saved and sent when it's back.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
    // Light amber card in light mode, dark amber-tinted card in dark mode.
    val dk = KairosThemeState.dark
    val amberBg = androidx.compose.ui.graphics.Color(if (dk) 0xFF2A2214 else 0xFFFFFBEB)
    val amberBorder = androidx.compose.ui.graphics.Color(if (dk) 0xFF6B4E16 else 0xFFFCD34D)
    val amberDk = androidx.compose.ui.graphics.Color(if (dk) 0xFFFBBF24 else 0xFFB45309)
    val amberText = androidx.compose.ui.graphics.Color(if (dk) 0xFFFDE68A else 0xFF78350F)
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

/** A compact home card for an interactive category (School work / Chores): a
 *  header + one-line summary that opens the full-screen detail on tap. */
@Composable
private fun WorkSummaryCard(
    title: String,
    category: String,
    overdue: List<TaskDto>,
    today: List<TaskDto>,
    getAheadCount: Int,
    upForGrabs: List<UpForGrabsDto> = emptyList(),
    badges: List<ChoreBadgeDto> = emptyList(),
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pendingOverdue = overdue.filter { it.status != "COMPLETE" }
    val pendingToday = today.filter { it.status != "COMPLETE" }
    // Only personally-assigned work counts toward "Complete for today!" — an
    // always-open or up-for-grabs chore doesn't earn or block it.
    val hadWork = overdue.isNotEmpty() || today.isNotEmpty()
    val completeForToday = pendingOverdue.isEmpty() && pendingToday.isEmpty() && hadWork
    val summary = buildList {
        if (pendingOverdue.isNotEmpty()) add("${pendingOverdue.size} overdue")
        if (pendingToday.isNotEmpty()) add("${pendingToday.size} today")
        if (pendingOverdue.isEmpty() && pendingToday.isEmpty() && getAheadCount > 0) add("$getAheadCount to get ahead")
    }.joinToString(" \u00b7 ").ifEmpty { "All caught up" }
    // A shared chore where I'm the one who did it longest ago (and the admin
    // hasn't left me off its Turn reminder) is called out, not left in the grey
    // line — the server decides who that is (furthestBehind).
    val myTurn = upForGrabs.filter { it.furthestBehind }
    val others = upForGrabs.filterNot { it.furthestBehind }
    val poolText = when {
        others.size == 1 -> others.first().title
        others.size > 1 -> "Up for grabs chores are available"
        else -> null
    }

    // A tile (v0.566): green when done, red with a count when overdue.
    HomeTile(
        sectionKey = sectionKeyForCategory(category),
        label = title,
        state = when {
            completeForToday -> TileState.DONE
            pendingOverdue.isNotEmpty() -> TileState.OVERDUE
            else -> TileState.NORMAL
        },
        badge = pendingOverdue.size.takeIf { it > 0 }?.toString(),
        onClick = onOpen,
        modifier = modifier,
    ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (completeForToday) "All caught up" else summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (completeForToday) ChoresGreen else MaterialTheme.colorScheme.onSurface,
                    )
                    ChoreBadgesRow(badges)
                }
                if (myTurn.isNotEmpty()) {
                    Row(
                        Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FurthestBehindBadge()
                        Column {
                            Text(
                                "Your turn: ${myTurn.joinToString { it.title }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "You did it longest ago",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (poolText != null) {
                    Text(poolText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
    }
}

/** Every home card is at least this tall (v0.458): the workout card's size
 *  (v0.436), so Chores, School, Workouts, Reading and the rest line up. */
private val HOME_CARD_MIN = 64.dp

@Composable
private fun WorkoutSummaryCard(
    today: List<TaskDto>,
    overdueCount: Int,
    rest: String? = null,
    extraLogged: Boolean = false,
    /** Started and not finished (v0.516): what's left, in place of the names. */
    remaining: List<String> = emptyList(),
    /** Two or more workouts today (v0.540): "1 of 2 workouts"; a skipped one counts. */
    blocks: WorkoutBlocksDto? = null,
    /** Two or more workouts (server 0.798+): each by name, and the plan's kinds. */
    card: WorkoutCardDto? = null,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A rest day reads "Rest day!" (v0.460): the plan's Rest, or Rest / skip
    // pressed. A skipped day where something else got done (a game, another
    // workout) reads "Complete for today!" instead.
    val restDay = blocks == null && rest != null && !(rest == "skipped" && extraLogged)
    val todayNames = if (rest != null) emptyList() else today.filter { it.status != "COMPLETE" }.map { it.title }
    // What else the day holds, under the title (server 0.628+): conditioning,
    // cardio, a game on the calendar ("Hockey game 7:30 PM").
    val extras = today.mapNotNull { it.subtitle?.takeIf { s -> s.isNotBlank() } }.distinct()
    val hadWork = today.isNotEmpty() || overdueCount > 0
    val completeForToday =
        if (blocks != null) {
            blocks.done >= blocks.total && overdueCount == 0
        } else {
            !restDay && todayNames.isEmpty() && overdueCount == 0 && (hadWork || (rest == "skipped" && extraLogged))
        }
    val blocksLine: @Composable () -> Unit = {
        if (blocks != null && blocks.total >= 2) {
            Text(
                "${blocks.done} of ${blocks.total} workouts",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // A tile (v0.566): green when done, red with a count when overdue.
    HomeTile(
        sectionKey = "workouts",
        label = "Workouts",
        state = when {
            completeForToday -> TileState.DONE
            overdueCount > 0 -> TileState.OVERDUE
            else -> TileState.NORMAL
        },
        badge = overdueCount.takeIf { it > 0 }?.toString(),
        onClick = onOpen,
        modifier = modifier,
    ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (restDay) {
                        Text(
                            "Rest day",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (overdueCount > 0) {
                            Text(
                                "$overdueCount overdue",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else if (card != null && card.items.size >= 2) {
                        blocksLine()
                        // Each workout on its own line (v0.574), a ring before it
                        // (a check once done). Three fit; past that, two and "+N more".
                        val shown = if (card.items.size > 3) card.items.take(2) else card.items
                        shown.forEach { item -> WorkoutNameLine(name = item.name, done = item.done, detail = item.detail, inProgress = remaining.isNotEmpty(), skipped = item.skipped) }
                        if (card.items.size > 3) {
                            Text(
                                "+${card.items.size - 2} more",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Older servers send no detail: the kinds go at the end.
                        if (card.kinds.isNotEmpty() && card.items.none { it.detail != null }) {
                            Text(
                                card.kinds.joinToString(" \u00b7 "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (overdueCount > 0) {
                            Text(
                                "$overdueCount overdue",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else if (completeForToday) {
                        Text(
                            "Complete for today!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ChoresGreen,
                        )
                    } else if (remaining.isNotEmpty()) {
                        // Started, not finished (v0.581): the workout and "In
                        // progress" in italics, not its remaining parts.
                        blocksLine()
                        val one = todayNames.singleOrNull()
                        Text(
                            one?.substringAfter(" \u00b7 ") ?: todayNames.firstOrNull() ?: "Workout",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "In progress",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (overdueCount > 0) {
                            Text(
                                "$overdueCount overdue",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        blocksLine()
                        val one = todayNames.singleOrNull()
                        if (one != null && one.contains(" \u00b7 ")) {
                            // One workout (v0.568): its lifts as the line ("Bench press"),
                            // the program under it with the day's extras ("5/3/1 · Rowing").
                            Text(
                                one.substringAfter(" \u00b7 "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                (listOf(one.substringBefore(" \u00b7 ")) + extras).joinToString(" \u00b7 "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // Today's workout names, up to two per line, "·"-separated.
                            todayNames.chunked(2).forEach { pair ->
                                Text(
                                    pair.joinToString(" \u00b7 "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (todayNames.isEmpty() && overdueCount == 0 && blocks == null) {
                            Text(
                                "No workout today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (one == null || !one.contains(" \u00b7 ")) extras.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Overdue on its own line, in red.
                        if (overdueCount > 0) {
                            Text(
                                "$overdueCount overdue",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
    }
}

/**
 * One workout on the home tile (v0.574): a tiny bullet and the name, both
 * green once done (v0.581), and its details under the name, lined up with it;
 * "In progress" in italics there while it's started and not done.
 */
@Composable
private fun WorkoutNameLine(name: String, done: Boolean, detail: String?, inProgress: Boolean, skipped: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .padding(top = 9.dp)
                .size(3.dp)
                .clip(CircleShape)
                .background(
                    if (done) ChoresGreen
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (skipped) 0.3f else 0.5f),
                ),
        )
        Column {
            // Didn't go (v0.582): greyed out, still listed.
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    done -> ChoresGreen
                    skipped -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                val started = inProgress && !done
                Text(
                    if (started) "In progress" else detail,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    fontStyle = if (started) androidx.compose.ui.text.font.FontStyle.Italic else null,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReadingHomeSection(reading: List<ReadingProgressDto>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    // A tile (v0.566): its books with their bars; behind is the red %.
    HomeTile(
        sectionKey = "reading",
        label = "Reading",
        state = TileState.NORMAL,
        onClick = onOpen,
        modifier = modifier,
    ) {
                // Each book (v0.568): the full title on its own line, then its bar
                // with the % at the end — red when behind.
                reading.forEach { r ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            r.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.weight(1f)) { ReadingBar(r.pct, r.behind) }
                            Text(
                                "${kotlin.math.abs(r.pct)}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (r.behind) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                // A fixed width (v0.570), so every book's bar is the same length.
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                modifier = Modifier.width(36.dp),
                            )
                        }
                    }
                }
    }
}

@Composable
private fun ReadingBar(pct: Int, behind: Boolean) {
    Box(
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
    ) {
        val frac = (if (behind) kotlin.math.abs(pct) else pct).coerceIn(0, 100) / 100f
        Box(
            Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(999.dp))
                .background(if (behind) MaterialTheme.colorScheme.error else KairosThemeState.accent),
        )
    }
}

private data class ChoreBadgeSpec(val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color, val label: String)

private val CHORE_BADGE_ICONS: Map<String, ChoreBadgeSpec> = mapOf(
    "grass" to ChoreBadgeSpec(KairosIcons.Grass, Color(0xFF16A34A), "Grass"),
    "water" to ChoreBadgeSpec(KairosIcons.Water, Color(0xFF2563EB), "Water"),
)

/** Inline badges after the Chores summary line: one tinted glyph per icon done
 *  today, with \u00d7N for always-open repeats. Unknown icons are skipped. */
@Composable
private fun ChoreBadgesRow(badges: List<ChoreBadgeDto>) {
    val shown = badges.filter { CHORE_BADGE_ICONS.containsKey(it.icon) }
    if (shown.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { b ->
            val spec = CHORE_BADGE_ICONS.getValue(b.icon)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(spec.icon, contentDescription = spec.label, tint = spec.color, modifier = Modifier.size(16.dp))
                if (b.count > 1) {
                    Text(
                        "\u00d7${b.count}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = spec.color,
                    )
                }
            }
        }
    }
}

/** Full-screen interactive Chores detail (Overdue / Today / Get ahead / Always
 *  open). Shares the home's ViewModel via [parentEntry] so completions here
 *  update the home behind it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoresDetailScreen(parentEntry: NavBackStackEntry?, onBack: () -> Unit) {
    val container = rememberContainer()
    val owner = parentEntry ?: LocalViewModelStoreOwner.current
    if (owner == null) {
        // Should not happen inside a NavHost, but never leave a blank surface —
        // show a recoverable scaffold instead of a white screen.
        WorkDetailScaffold("Chores", KairosIcons.Chores, sectionFor("chores").color, onBack) {
            Text(
                "Couldn't open this screen. Go back and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val vm: HomeViewModel = viewModel(
        viewModelStoreOwner = owner,
        factory = viewModelFactory { initializer { HomeViewModel(container.sessionRepository, container.payloadCache) } },
    )
    val ui by vm.ui.collectAsState()
    val d = ui.dashboard
    // Late work has its own page (v0.576), opened from the Late card.
    var showLate by remember { mutableStateOf(false) }
    val lateChores = d?.overdue?.filter { it.category == "CHORE" }?.sortedBy { it.dueDate }.orEmpty()
    if (showLate && lateChores.isNotEmpty()) {
        androidx.activity.compose.BackHandler { showLate = false }
        LateWorkPage(
            items = lateChores,
            groupOf = { null },
            onBack = { showLate = false },
        ) { t -> ChoreCheckRow(t, ui.busyIds, vm) }
        return
    }

    WorkDetailScaffold("Chores", KairosIcons.Chores, sectionFor("chores").color, onBack) {
        if (d == null) {
            Text("Loading\u2026", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val overdue = d.overdue.filter { it.category == "CHORE" }.sortedBy { it.dueDate }
            val today = d.groups.firstOrNull { it.category == "CHORE" }?.items ?: emptyList()
            val pending = overdue.count { it.status != "COMPLETE" } + today.count { it.status != "COMPLETE" }

            if (pending == 0 && (overdue.isNotEmpty() || today.isNotEmpty())) {
                Text("Complete for today!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ChoresGreen)
            }
            LateCard(items = overdue, groups = overdue.map { it.title }) { showLate = true }
            if (today.isNotEmpty()) {
                DetailSection("Today") {
                    today.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ChoreCheckRow(t, ui.busyIds, vm)
                    }
                }
            }
            if (d.alwaysOpen.isNotEmpty()) {
                DetailSection("Always open") {
                    d.alwaysOpen.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AlwaysOpenRow(c, ui.busyIds.contains("always-${c.id}"), vm)
                    }
                }
            }
            if (d.upForGrabs.isNotEmpty()) {
                DetailSection("Up for grabs") {
                    d.upForGrabs.forEachIndexed { i, g ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        UpForGrabsRow(g, ui.busyIds.contains("claim-${g.id}"), vm)
                    }
                }
            }
            if (d.getAhead.isNotEmpty()) {
                DetailSection("Get ahead") {
                    d.getAhead.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ChoreAheadRow(c, ui.busyIds.contains(c.taskId), vm)
                    }
                }
            }
            if (overdue.isEmpty() && today.isEmpty() && d.getAhead.isEmpty() && d.alwaysOpen.isEmpty() && d.upForGrabs.isEmpty()) {
                Text("No chores right now.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val BonusOrange = Color(0xFFEA580C)

private fun formatBonus(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

@Composable
private fun ChoreCheckRow(task: TaskDto, busyIds: Set<String>, vm: HomeViewModel) {
    val done = task.status == "COMPLETE"
    val enabled = !busyIds.contains(task.id) && task.completable
    // Overdue (incl. a chore's grace day, server 0.713) gets the behind tint.
    val behind = task.isOverdue && !done && task.completable
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (behind) Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.06f)) else Modifier)
            .clickable(enabled = enabled) { vm.toggle(task.id, done) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = null)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chores", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.isOverdue) {
                    Text(
                        " \u00b7 due ${homeShortDate(task.dueDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoreAheadRow(chore: GetAheadChoreDto, busy: Boolean, vm: HomeViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(chore.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("due ${homeShortDate(chore.dueDateISO)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (chore.bonus > 0) {
            Icon(KairosIcons.Flame, contentDescription = null, tint = BonusOrange, modifier = Modifier.size(18.dp))
            Text("+${formatBonus(chore.bonus)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = BonusOrange)
        }
        Button(
            onClick = { vm.toggle(chore.taskId, false) },
            enabled = !busy,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("Do it now")
        }
    }
}

/** Small red "!" — marks the person who's furthest behind on a shared chore. */
@Composable
private fun FurthestBehindBadge() {
    Box(
        Modifier.size(18.dp).background(Color(0xFFDC2626), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("!", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun UpForGrabsRow(item: UpForGrabsDto, busy: Boolean, vm: HomeViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (item.furthestBehind) FurthestBehindBadge()
            }
            val sub = buildString {
                append(if (item.isShared) "shared chore" else "from ${item.releasedByName}")
                if (item.isOverdue && item.dueDate.isNotBlank()) {
                    append(" \u00b7 due ${homeShortDate(item.dueDate)}")
                }
            }
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = if (item.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.isShared) {
                val last = item.lastDoneISO
                val since = last?.let { homeDaysSince(it) }
                val label = when {
                    last == null -> "You've never done this"
                    since == null -> "You last did this ${homeShortDate(last)}"
                    since <= 0L -> "You last did this today"
                    since == 1L -> "You last did this 1d ago"
                    else -> "You last did this ${since}d ago"
                }
                // Amber once you've never done it, or you're past twice the cadence.
                val stale = last == null || (item.intervalDays > 0 && since != null && since > 2L * item.intervalDays)
                val staleColor = if (KairosThemeState.dark) Color(0xFFF59E0B) else Color(0xFFB45309)
                Text(label, style = MaterialTheme.typography.bodySmall, color = if (stale) staleColor else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(
            onClick = { vm.claimChore(item.id) },
            enabled = !busy,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(if (busy) "Doing\u2026" else "Done")
        }
    }
}

/** Full-screen interactive School work detail (Overdue / Today) for the person's
 *  own school work. Progress lives on the School tab (informational). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchoolWorkDetailScreen(parentEntry: NavBackStackEntry?, onBack: () -> Unit, onAdd: () -> Unit = {}) {
    val container = rememberContainer()
    val owner = parentEntry ?: LocalViewModelStoreOwner.current
    if (owner == null) {
        // Should not happen inside a NavHost, but never leave a blank surface —
        // show a recoverable scaffold instead of a white screen.
        WorkDetailScaffold(
            title = "School",
            icon = KairosIcons.School,
            iconColor = Color(0xFF4F46E5),
            onBack = onBack,
        ) {
            Text(
                "Couldn't open this screen. Go back and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val vm: HomeViewModel = viewModel(
        viewModelStoreOwner = owner,
        factory = viewModelFactory { initializer { HomeViewModel(container.sessionRepository, container.payloadCache) } },
    )
    val ui by vm.ui.collectAsState()
    val d = ui.dashboard
    var extraSubject by remember { mutableStateOf<String?>(null) }
    // Late work has its own page (v0.576), opened from the Late card, with the
    // classes to filter by.
    var showLate by remember { mutableStateOf(false) }
    val lateSchool = d?.overdue?.filter { it.category == "SCHOOL" }?.sortedBy { it.dueDate }.orEmpty()
    if (showLate && lateSchool.isNotEmpty()) {
        androidx.activity.compose.BackHandler { showLate = false }
        LateWorkPage(
            items = lateSchool,
            groupOf = { schoolClassOf(it) },
            onBack = { showLate = false },
        ) { t -> SchoolCheckRow(t, ui.busyIds.contains(t.id), vm) }
        return
    }

    WorkDetailScaffold(
        title = "School",
        icon = KairosIcons.School,
        iconColor = Color(0xFF4F46E5),
        onBack = onBack,
        actions = {
            IconButton(onClick = onAdd) { Icon(KairosIcons.Plus, contentDescription = "Add assignment or test") }
        },
    ) {
        if (d == null) {
            Text("Loading\u2026", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val school = d.school
            val overdue = d.overdue.filter { it.category == "SCHOOL" }.sortedBy { it.dueDate }
            val today = d.groups.firstOrNull { it.category == "SCHOOL" }?.items ?: emptyList()
            val pending = overdue.count { it.status != "COMPLETE" } + today.count { it.status != "COMPLETE" }

            if (school?.targetISO != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("School year ends ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(homeShortDate(school.targetISO), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = KairosThemeState.accent)
                }
            }
            if (pending == 0 && (overdue.isNotEmpty() || today.isNotEmpty())) {
                Text("Complete for today!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ChoresGreen)
            }
            LateCard(items = overdue, groups = overdue.map { schoolClassOf(it) }) { showLate = true }
            if (today.isNotEmpty()) {
                DetailSection("Today") {
                    today.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SchoolCheckRow(t, ui.busyIds.contains(t.id), vm)
                    }
                }
            }
            if (school?.progress?.isNotEmpty() == true) {
                DetailSection("Progress") {
                    school.progress.forEachIndexed { i, pr ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SchoolProgressRow(pr)
                    }
                }
            }
            if (school?.getAhead?.isNotEmpty() == true) {
                DetailSection("Do some extra work") {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Pick a subject to pull its next lesson into today \u2014 do a little extra to finish on time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            school.getAhead.forEach { sub ->
                                OutlinedButton(onClick = { extraSubject = sub.subject }, enabled = sub.items.isNotEmpty()) {
                                    Text(sub.subject)
                                }
                            }
                        }
                    }
                }
            }
            if (overdue.isEmpty() && today.isEmpty() && school?.progress.isNullOrEmpty() && school?.getAhead.isNullOrEmpty()) {
                Text("No school work due.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // "Do some extra work" pop-up: the subject's next lesson with Add to
            // today / Subjects, mirroring the web. It advances (or closes) as the
            // dashboard refreshes after adding.
            extraSubject?.let { subjName ->
                val sub = school?.getAhead?.firstOrNull { it.subject == subjName }
                val item = sub?.items?.firstOrNull()
                if (item == null) {
                    LaunchedEffect(subjName) { extraSubject = null }
                } else {
                    val busy = ui.busyIds.contains("add-${item.taskId}")
                    Dialog(onDismissRequest = { extraSubject = null }) {
                        androidx.compose.runtime.DisposableEffect(Unit) {
                            com.kairos.app.data.diag.Breadcrumbs.drop("open SchoolExtraWork")
                            onDispose { com.kairos.app.data.diag.Breadcrumbs.drop("close SchoolExtraWork") }
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(subjName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                Text("due ${homeShortDate(item.dueISO)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { extraSubject = null }, modifier = Modifier.weight(1f)) { Text("Subjects") }
                                    Button(onClick = { vm.addSchoolToToday(item.taskId) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Add to today") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Section label + a bordered white card holding the rows (dividers between). */
@Composable
private fun DetailSection(label: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth(), content = content) }
    }
}

/** A school row's class: the first part of its subtitle, else its title. */
private fun schoolClassOf(task: TaskDto): String =
    (task.subtitle ?: "").split(" \u00b7 ").firstOrNull()?.takeIf { it.isNotBlank() } ?: task.title

/**
 * Late work at the top of an area page (v0.576): red, with the count, how far
 * back it goes and what it's in. Tap it for the Late page. Nothing still
 * late, no card.
 */
@Composable
private fun LateCard(items: List<TaskDto>, groups: List<String>, onClick: () -> Unit) {
    val open = items.filter { it.status != "COMPLETE" }
    if (open.isEmpty()) return
    val red = MaterialTheme.colorScheme.error
    val oldest = open.minOf { it.dueDate }
    val names = groups.filterIndexed { i, _ -> items[i].status != "COMPLETE" }.distinct()
    val what = names.take(3).joinToString(", ") + if (names.size > 3) " +${names.size - 3}" else ""
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, red.copy(alpha = 0.45f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(red),
                contentAlignment = Alignment.Center,
            ) {
                Text("${open.size}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("Late work", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = red)
                Text(
                    "Oldest from ${homeShortDate(oldest)}" + if (what.isNotBlank()) " \u00b7 $what" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(KairosIcons.ChevronRight, contentDescription = "Open late work", tint = red, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The Late page (v0.576): every late item, oldest first, filtered by class
 * when [groupOf] gives one. Checking one off leaves it in place until the
 * next refresh, so the list doesn't jump under a finger.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LateWorkPage(
    items: List<TaskDto>,
    groupOf: (TaskDto) -> String?,
    onBack: () -> Unit,
    row: @Composable (TaskDto) -> Unit,
) {
    val red = MaterialTheme.colorScheme.error
    val open = items.count { it.status != "COMPLETE" }
    val groups = items.mapNotNull(groupOf).groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    var filter by remember { mutableStateOf<String?>(null) }
    val shown = if (filter == null) items else items.filter { groupOf(it) == filter }
    WorkDetailScaffold(
        title = "Late work",
        icon = KairosIcons.Clock,
        iconColor = red,
        onBack = onBack,
        actions = {
            Text(
                "$open",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(end = 12.dp).clip(RoundedCornerShape(999.dp)).background(red).padding(horizontal = 10.dp, vertical = 2.dp),
            )
        },
    ) {
        if (groups.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LateChip("All ${items.size}", filter == null) { filter = null }
                groups.forEach { (g, n) -> LateChip("$g $n", filter == g) { filter = g } }
            }
        }
        DetailSection("Oldest first") {
            shown.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                row(t)
            }
        }
    }
}

@Composable
private fun LateChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun SchoolCheckRow(task: TaskDto, busy: Boolean, vm: HomeViewModel) {
    val done = task.status == "COMPLETE"
    val enabled = !busy && task.completable
    val parts = (task.subtitle ?: "").split(" \u00b7 ")
    val className = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: task.title
    val detail = (listOf(task.title) + parts.drop(1)).joinToString(" \u00b7 ")
    // Behind work carries a tint, not just red text on one line. The detail line
    // was already red when overdue, but it reads as emphasis on a date rather
    // than as a state the whole row is in. Cleared once it is done: a finished
    // assignment is not behind anything.
    val behind = task.isOverdue && !done
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (behind) {
                    Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.06f))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled) { vm.toggle(task.id, done) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = null)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                className,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (task.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SchoolProgressRow(pr: SchoolCardProgressDto) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(999.dp)).background(parseHex(pr.color)))
                Text(
                    pr.className,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (pr.paused) {
                    SchoolPaceTag(text = "paused", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (pr.pace) {
                    "behind" -> SchoolPaceTag("falling behind", SchoolAmber)
                    "ahead" -> SchoolPaceTag("getting ahead!", ChoresGreen)
                }
            }
            if (pr.paused) {
                Text(
                    "${pr.done}/${pr.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (pr.finishISO != null) {
                Text(
                    "finishes ${homeShortDate(pr.finishISO)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (pr.onTrack) MaterialTheme.colorScheme.onSurfaceVariant else SchoolAmber,
                    textDecoration = if (!pr.onTrack) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                )
            }
        }
        if (pr.catchUpRate != null) {
            val note = if (pr.catchUpDays != null) {
                "Do ${pr.catchUpRate} a day for the next ${pr.catchUpDays} school day" +
                    (if (pr.catchUpDays == 1) "" else "s") + " to finish on time."
            } else {
                "Do ${pr.catchUpRate} a day to finish on time."
            }
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = SchoolAmber,
                modifier = Modifier.padding(start = 18.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun SchoolPaceTag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

private val SchoolAmber = Color(0xFFD97706)

private fun parseHex(hex: String?): Color {
    val s = hex?.trim()?.removePrefix("#") ?: return Color(0xFF94A3B8)
    return try {
        when (s.length) {
            6 -> Color(("FF$s").toLong(16))
            8 -> Color(s.toLong(16))
            else -> Color(0xFF94A3B8)
        }
    } catch (_: NumberFormatException) {
        Color(0xFF94A3B8)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkDetailScaffold(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                        Text(title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            body()
        }
    }
}

@Composable
private fun ChoreOverlaySection(label: String, color: Color, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MiniLabel(label, color)
        content()
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
                "due ${homeShortDate(chore.dueDateISO)}" + if (chore.bonus > 0) " \u00b7 +${if (chore.bonus % 1.0 == 0.0) chore.bonus.toInt().toString() else chore.bonus.toString()} bonus" else "",
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

/** Whole days between an ISO date and today, or null if it can't be parsed. */
private fun homeDaysSince(iso: String): Long? =
    try {
        java.time.LocalDate.now().toEpochDay() - java.time.LocalDate.parse(iso).toEpochDay()
    } catch (_: Exception) {
        null
    }

/** A titled section: a small uppercase header above a card holding the rows,
 *  matching the web home so sections read as distinct blocks. */
@Composable
private fun SectionBlock(
    header: String,
    category: String? = null,
    edge: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column {
        SectionHeader(header, category)
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (category != null && edge) Modifier.sectionEdge(sectionKeyForCategory(category)) else Modifier)
                    .heightIn(min = HOME_CARD_MIN)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.Center,
                content = content,
            )
        }
    }
}

/** Maps a task category to the matching side-menu section (icon + brand color). */
// --- The home page's tiles (v0.566) ------------------------------------------

private enum class TileState { NORMAL, DONE, OVERDUE }

private val TileGreen = Color(0xFF16A34A)
private val TileRed = Color(0xFFDC2626)

/**
 * One area of the day as a tile: its icon in the section's color, its name,
 * then what's left. Light green with a check when the day's part is done;
 * light red with a count when something's overdue.
 */
@Composable
private fun HomeTile(
    sectionKey: String,
    label: String,
    state: TileState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    badge: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val section = sectionFor(sectionKey)
    val shape = RoundedCornerShape(18.dp)
    val (tint, edge) = when (state) {
        TileState.DONE -> TileGreen.copy(alpha = 0.10f) to TileGreen.copy(alpha = 0.45f)
        TileState.OVERDUE -> TileRed.copy(alpha = 0.06f) to TileRed.copy(alpha = 0.40f)
        TileState.NORMAL -> Color.Transparent to MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .background(tint)
            .border(1.dp, edge, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The area name is the tile's title (v0.568): larger than the text under it.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(section.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(section.icon, contentDescription = null, tint = section.color, modifier = Modifier.size(17.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            when {
                state == TileState.DONE -> Box(
                    Modifier.size(22.dp).clip(CircleShape).background(TileGreen),
                    contentAlignment = Alignment.Center,
                ) { Icon(KairosIcons.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(14.dp)) }
                state == TileState.OVERDUE && badge != null -> Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(TileRed).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
        content()
    }
}

/** Every home tile is at least this tall (v0.568), so the rows match. */
private val TILE_MIN = 124.dp

/** Tiles two across, the same width each; an odd one at the end spans the row. */
@Composable
private fun TileGrid(tiles: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { pair ->
            if (pair.size == 2) {
                Row(
                    Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair[0](Modifier.weight(1f).heightIn(min = TILE_MIN).fillMaxHeight())
                    pair[1](Modifier.weight(1f).heightIn(min = TILE_MIN).fillMaxHeight())
                }
            } else {
                pair[0](Modifier.fillMaxWidth().heightIn(min = TILE_MIN))
            }
        }
    }
}

/**
 * A list area (Bible reading, Tasks…) as a tile. One thing to do: tap the
 * tile and it's done (tap again to undo). Several: each line taps on its own.
 */
@Composable
private fun TaskTile(
    category: String,
    label: String,
    overdue: List<TaskDto>,
    today: List<TaskDto>,
    personal: com.kairos.app.data.remote.dto.PersonalReadingDto?,
    busyIds: Set<String>,
    vm: HomeViewModel,
    onLogWorkout: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    data class Line(val id: String, val title: String, val sub: String?, val done: Boolean, val late: Boolean, val tap: (() -> Unit)?)
    val lines = buildList {
        (overdue + today).forEach { t ->
            val done = t.status == "COMPLETE"
            val tap: (() -> Unit)? = when {
                busyIds.contains(t.id) -> null
                t.isWorkout -> ({ onLogWorkout(t.dueDate) })
                t.completable -> ({ vm.toggle(t.id, done) })
                else -> null
            }
            add(Line(id = t.id, title = t.title, sub = buildSecondary(t), done = done, late = t.isOverdue && !done, tap = tap))
        }
        personal?.let { pr ->
            add(
                Line(
                    id = "personal-reading",
                    title = "Personal bible reading",
                    sub = pr.passage,
                    done = pr.read,
                    late = false,
                    tap = if (busyIds.contains("personal-reading")) null else ({ vm.togglePersonalReading(pr.passage, pr.read) }),
                ),
            )
        }
    }
    val late = lines.count { it.late }
    val allDone = lines.isNotEmpty() && lines.all { it.done }
    val single = lines.singleOrNull()
    HomeTile(
        sectionKey = sectionKeyForCategory(category),
        label = label,
        state = when {
            allDone -> TileState.DONE
            late > 0 -> TileState.OVERDUE
            else -> TileState.NORMAL
        },
        badge = late.takeIf { it > 0 }?.toString(),
        onClick = single?.tap,
        modifier = modifier,
    ) {
        if (single != null) {
            Text(
                single.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (single.done) ChoresGreen else MaterialTheme.colorScheme.onSurface,
            )
            single.sub?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (single.late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        } else {
            val color = sectionFor(sectionKeyForCategory(category)).color
            lines.forEach { l ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (l.tap != null) Modifier.clickable(onClick = l.tap) else Modifier)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (l.done) {
                        Icon(KairosIcons.Check, contentDescription = "Done", tint = TileGreen, modifier = Modifier.size(18.dp))
                    } else {
                        Box(Modifier.size(18.dp).border(2.dp, if (l.late) TileRed else color, RoundedCornerShape(5.dp)))
                    }
                    Text(
                        l.title,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = if (l.done) TextDecoration.LineThrough else null,
                        color = when {
                            l.done -> MaterialTheme.colorScheme.onSurfaceVariant
                            l.late -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** "Morning" 4 AM – noon, "Afternoon" noon – 5 PM, "Evening" 5 PM – 4 AM (v0.566). */
private fun partOfDay(hour: Int): String = when (hour) {
    in 4..11 -> "Morning"
    in 12..16 -> "Afternoon"
    else -> "Evening"
}

/**
 * The top card (v0.568): the date, a greeting and a chip per area on the left
 * (green with a check when done, red when something's overdue), the day's
 * progress as a ring on the right.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GreetingCard(name: String, dateISO: String, bars: List<CategoryBarDto>) {
    val hour = remember { java.time.LocalTime.now().hour }
    val date = remember(dateISO) {
        runCatching {
            java.time.LocalDate.parse(dateISO).format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", java.util.Locale.US))
        }.getOrDefault("")
    }
    val total = bars.sumOf { it.total }
    val done = bars.sumOf { it.complete }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    if (date.isNotBlank()) {
                        Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${partOfDay(hour)}, $name", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (total == 0) {
                    Text("Nothing scheduled yet today.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        bars.filter { it.total > 0 || it.overdue > 0 }.forEach { b -> AreaChip(b) }
                    }
                }
            }
            if (total > 0) DayRing(done = done, total = total)
        }
    }
}

/** The day's progress as a ring with "done/total" inside (v0.568). */
@Composable
private fun DayRing(done: Int, total: Int) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val fill = KairosThemeState.accent
    val frac = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Box(
        Modifier.size(56.dp).drawBehind {
            val w = 6.dp.toPx()
            val inset = w / 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            if (frac > 0f) {
                drawArc(color = fill, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Text("$done/$total", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AreaChip(b: CategoryBarDto) {
    val section = sectionFor(sectionKeyForCategory(b.category))
    val short = when (b.category) {
        "BIBLE" -> "Bible"
        "EXERCISE" -> "Workout"
        "CHORE" -> "Chores"
        "SCHOOL" -> "School"
        "WORK" -> "Tasks"
        else -> b.label
    }
    val complete = b.total > 0 && b.complete >= b.total && b.overdue == 0
    val (color, text) = when {
        b.overdue > 0 -> TileRed to "$short \u00b7 ${b.overdue} overdue"
        complete -> TileGreen to short
        else -> section.color to "$short ${b.complete}/${b.total}"
    }
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(if (complete) KairosIcons.Check else section.icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** A home card's left edge in its section's side-menu color (v0.563).
 *  Today's schedule has none: each event keeps its calendar's color. */
private fun Modifier.sectionEdge(key: String): Modifier {
    val color = sectionFor(key).color
    return drawBehind { drawRect(color = color, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
}

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
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TaskRow(task: TaskDto, busy: Boolean, vm: HomeViewModel, onLogWorkout: (String) -> Unit) {
    val done = task.status == "COMPLETE"
    // Workouts render like the Chores/School summary rows: no checkbox marker,
    // a "Log" affordance on the right, and "Complete for today!" when done.
    if (task.isWorkout) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !busy) { onLogWorkout(task.dueDate) }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (done) {
                    Text(
                        "Complete for today!",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ChoresGreen,
                    )
                } else {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                "Log",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }
    // Workout prompts open the action sheet; ordinary completable rows toggle.
    val tappable = !busy && (task.isWorkout || task.completable)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tappable) {
                if (task.isWorkout) onLogWorkout(task.dueDate) else vm.toggle(task.id, done)
            }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(
                    KairosIcons.Check,
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
private fun AlwaysOpenRow(c: com.kairos.app.data.remote.dto.AlwaysOpenDashDto, busy: Boolean, vm: HomeViewModel) {
    val onCooldown = c.readyAtMs != null && c.readyAtMs > System.currentTimeMillis()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (c.myCount > 0) {
                Text(
                    "done ${c.myCount}\u00d7 today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = { vm.completeAlwaysOpen(c.id) },
            enabled = !busy && !onCooldown,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
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
                    KairosIcons.Check,
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

/**
 * The family goal on the home screen, and the parent's place to approve it.
 *
 * Which goal it shows, in order: one carried over from an earlier month that was
 * never checked off (first, because it is overdue \u2014 that is the whole reason
 * the carry exists), then this month's chosen reward, then this month's reward if
 * it has already been handed out, and failing all of that a nudge to go and vote.
 *
 * The check-off button only appears for a parent once the gate is met, but the
 * rule is enforced on the server too \u2014 this button is the convenience, not
 * the control.
 */
@Composable
private fun FamilyGoalCard(
    c: com.kairos.app.data.remote.dto.CoopDto,
    busy: Boolean,
    onCheckOff: (String) -> Unit,
    onOpen: () -> Unit,
) {
    val carried = c.carried
    val selected = c.proposals.firstOrNull { it.status == "SELECTED" }
    val granted = c.proposals.firstOrNull { it.status == "GRANTED" }

    // A carried goal is judged on ITS month, so its gate and its count come from
    // the carried record rather than from this month's running totals.
    val title = carried?.title ?: selected?.title ?: granted?.title
    val goalId = carried?.id ?: selected?.id
    val monthLabel = carried?.let { "from ${it.seasonLabel}" } ?: c.seasonLabel
    val gateMet = if (carried != null) carried.gateMet else c.gateMet
    val meeting = if (carried != null) carried.childrenMeeting else c.childrenMeeting
    val done = carried == null && selected == null && granted != null

    // White with a light gold outline and a soft gold glow at the edges
    // (v0.568) — Summary's gold, where the goal's progress lives.
    val gold = sectionFor("summary").color
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                // Strokes centred on the edge; the clip keeps only the inner half,
                // so stacking them fades the gold inward.
                listOf(28.dp, 18.dp, 10.dp).forEach { w ->
                    drawRoundRect(
                        color = gold.copy(alpha = 0.05f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w.toPx()),
                    )
                }
            }
            .border(1.5.dp, gold.copy(alpha = 0.35f), shape)
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    KairosIcons.Trophy,
                    contentDescription = null,
                    tint = if (done) Color(0xFF10B981) else gold,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        if (done) "Earned" else "Family goal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            title != null -> title
                            else -> "${c.proposals.size} idea${if (c.proposals.size == 1) "" else "s"} waiting"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                if (title == null) {
                    // Picking or voting is on the goal page; the pill opens it.
                    Text(
                        if (c.homeNudge == "pick") "Pick" else "Vote",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(gold)
                            .clickable { onOpen() }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                } else {
                    Text(
                        monthLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (carried != null) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (title != null && !done) {
                // A plain bar that fills from the left (v0.568) — no end dot.
                val frac = if (c.childrenTotal > 0) (meeting.toFloat() / c.childrenTotal.toFloat()).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(999.dp))
                            .background(if (gateMet) Color(0xFF10B981) else gold),
                    )
                }
                Text(
                    if (gateMet) "$meeting of ${c.childrenTotal} finished \u2014 ready to check off"
                    else "$meeting of ${c.childrenTotal} finished their month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (gateMet && c.isAdmin && goalId != null) {
                    Button(
                        onClick = { onCheckOff(goalId) },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    ) {
                        Icon(KairosIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (busy) "Saving\u2026" else "We did it")
                    }
                }
            }
    }
}
