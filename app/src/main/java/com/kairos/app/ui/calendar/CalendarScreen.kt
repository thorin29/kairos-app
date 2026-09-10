package com.kairos.app.ui.calendar

import com.kairos.app.ui.common.TimeFmt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.HorizontalDivider
import com.kairos.app.ui.common.AnimatedDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.CalEventDto
import com.kairos.app.data.remote.dto.CalendarDto
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.common.AttendanceIcon
import com.kairos.app.ui.common.AttendeesColumn
import com.kairos.app.ui.nav.KairosIcons
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onOpenDrawer: () -> Unit, refreshKey: Int = 0) {
    val container = rememberContainer()
    val vm: CalendarViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CalendarViewModel(container.sessionRepository, container.settingsStore, container.appContext) }
        },
    )
    val ui by vm.ui.collectAsState()
    androidx.compose.runtime.LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.reload() }
    var monthExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDefaultPicker by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<com.kairos.app.data.remote.dto.CalEventDto?>(null) }
    var editingEvent by remember { mutableStateOf<com.kairos.app.data.remote.dto.CalEventDto?>(null) }
    val data = ui.data

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
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
                        onEventClick = { selectedEvent = it },
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
                modifier = Modifier.align(Alignment.TopEnd),
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
            // Same darker shade over the status-bar strip as the main menu.
            AnimatedVisibility(visible = showSettings, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(Color.Black.copy(alpha = 0.18f)),
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

    if (editingEvent != null && data != null) {
        val occ = data.events.firstOrNull { it.id == editingEvent!!.id }?.dayISO ?: editingEvent!!.dayISO
        AddEventOverlay(
            vm, data, ui,
            editEvent = editingEvent,
            editOccurrenceISO = occ,
            onClose = { editingEvent = null; vm.clearCreateError() },
        )
    }

    selectedEvent?.let { ev ->
        if (data != null) {
            // The recurrence engine keys occurrences by their home-tz date; the
            // tapped event may have been shifted by device-tz localisation, so
            // recover the original date from the un-localised payload.
            val occ = data.events.firstOrNull { it.id == ev.id }?.dayISO ?: ev.dayISO
            EventDetailScreen(
                event = ev,
                occurrenceISO = occ,
                canManageFamily = data.options.canManageFamily,
                ui = ui,
                vm = vm,
                customTypes = data.options.eventTypes,
                onEdit = { editingEvent = ev; selectedEvent = null },
                onClose = { selectedEvent = null; vm.clearDeleteError() },
            )
        }
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
    onEventClick: (com.kairos.app.data.remote.dto.CalEventDto) -> Unit,
) {
    val localEvents = remember(data.events, data.timezone) {
        localizeEvents(data.events, data.timezone)
    }
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = monthExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            // The mini-month is its own left/right finger-follow pager (separate
            // from the main month view, which pages vertically). Picking a day or
            // settling on a new month navigates the view behind it.
            MiniMonthPager(vm, ui.date ?: data.date, ui.navNonce) { iso -> vm.goToDate(iso) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (ui.tab) {
                CalTab.DAY -> DayPager(vm, ui.date ?: data.date, ui.navNonce, data.today, onEventClick)
                CalTab.MONTH -> MonthPager(vm, ui.date ?: data.date, ui.navNonce, onEventClick)
                CalTab.WEEK -> WeekPager(vm, ui.date ?: data.date, ui.navNonce, onEventClick)
                CalTab.THREE_DAY -> ThreeDayPager(vm, ui.date ?: data.date, ui.navNonce, onEventClick)
                else -> AgendaPager(vm, ui.date ?: data.date, ui.navNonce, data.today, onEventClick)
            }
            // A soft shadow along the top edge of the time-grid content, as if the
            // (expandable) month above is floating over it. Stays whether the month
            // dropdown is open or collapsed.
            if (ui.tab == CalTab.DAY || ui.tab == CalTab.THREE_DAY || ui.tab == CalTab.WEEK || ui.tab == CalTab.AGENDA) {
                Box(
                    Modifier.fillMaxWidth().height(6.dp).align(Alignment.TopCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.13f), Color.Transparent),
                            ),
                        ),
                )
            }
        }
    }
}

/**
 * Month view as a finger-follow pager (Slice 2). Each page is a month grid;
 * neighbour months are pre-fetched so they slide in populated.
 */
@Composable
private fun MonthPager(
    vm: CalendarViewModel,
    anchorDate: String,
    navNonce: Int,
    onEventClick: (com.kairos.app.data.remote.dto.CalEventDto) -> Unit,
) {
    val monthPages by vm.monthPages.collectAsState()
    val base = remember { java.time.LocalDate.parse(anchorDate).withDayOfMonth(1) }
    val center = 6000
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = center,
        pageCount = { 12001 },
    )
    fun keyFor(page: Int): String = base.plusMonths((page - center).toLong()).toString()

    LaunchedEffect(pagerState.currentPage) {
        for (o in -1..1) vm.ensureMonth(keyFor(pagerState.currentPage + o))
    }
    val settledKey = keyFor(pagerState.settledPage)
    LaunchedEffect(settledKey, monthPages[settledKey]) {
        vm.onMonthSettled(settledKey)
    }
    LaunchedEffect(navNonce) {
        if (navNonce == 0) return@LaunchedEffect
        val target = center + java.time.temporal.ChronoUnit.MONTHS.between(
            base, java.time.LocalDate.parse(anchorDate).withDayOfMonth(1),
        ).toInt()
        if (target in 0 until 12001 && target != pagerState.currentPage) {
            pagerState.animateToPageQuick(target)
        }
    }

    androidx.compose.foundation.pager.VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val pd = monthPages[keyFor(page)]
        if (pd != null) {
            val evs = remember(pd.events, pd.timezone) { localizeEvents(pd.events, pd.timezone) }
            MonthChipsView(pd, evs, vm, onEventClick)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        }
    }
}

/**
 * Day view as a finger-follow pager (Slice 1). Each page is one day; neighbours
 * are pre-fetched into the view model's cache so they slide in populated. Snap
 * and fling come from HorizontalPager.
 */
@Composable
private fun DayPager(
    vm: CalendarViewModel,
    anchorDate: String,
    navNonce: Int,
    today: String,
    onEventClick: (com.kairos.app.data.remote.dto.CalEventDto) -> Unit,
) {
    val pages by vm.pages.collectAsState()
    // Fixed origin for the page↔date mapping; must NOT re-key on anchorDate or the
    // mapping would shift every time the pager settles.
    val base = remember { java.time.LocalDate.parse(anchorDate) }
    val center = 10000
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = center,
        pageCount = { 20001 },
    )
    // Shared vertical scroll: the frozen hour axis and every day column move
    // together, and it opens near the current hour.
    val vScroll = rememberTimeGridScroll()
    fun dateFor(page: Int): String = base.plusDays((page - center).toLong()).toString()

    LaunchedEffect(pagerState.currentPage) {
        for (o in -2..2) vm.ensureDay(dateFor(pagerState.currentPage + o))
    }
    val settledIso = dateFor(pagerState.settledPage)
    LaunchedEffect(settledIso, pages[settledIso]) {
        vm.onDaySettled(settledIso)
    }
    LaunchedEffect(navNonce) {
        if (navNonce == 0) return@LaunchedEffect
        val target = center + java.time.temporal.ChronoUnit.DAYS.between(
            base, java.time.LocalDate.parse(anchorDate),
        ).toInt()
        if (target in 0 until 20001 && target != pagerState.currentPage) {
            pagerState.animateToPageQuick(target)
        }
    }

    Row(Modifier.fillMaxSize()) {
        // Frozen left column: the snapped day's date + the hour axis.
        DayAxisColumn(settledIso, today, vScroll)
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            val iso = dateFor(page)
            val pd = pages[iso]
            val evs = if (pd != null) {
                remember(pd.events, pd.timezone) { localizeEvents(pd.events, pd.timezone) }
            } else {
                emptyList()
            }
            DayGridPage(
                events = evs,
                iso = iso,
                today = today,
                nowColor = pd?.nowColor ?: "",
                loading = pd == null,
                scroll = vScroll,
                onEventClick = onEventClick,
            )
        }
    }
}

/**
 * 3-day view (Slice 3): a frozen hour axis beside a snapping LazyRow of single
 * days, each a third of the width so three show at once and a swipe snaps to the
 * nearest day. LazyRow's snap fling behaves cleanly at any drag distance (the
 * custom-PageSize HorizontalPager mis-snapped near a full-page advance).
 */
@Composable
private fun ThreeDayPager(
    vm: CalendarViewModel,
    anchorDate: String,
    navNonce: Int,
    onEventClick: (com.kairos.app.data.remote.dto.CalEventDto) -> Unit,
) {
    val pages by vm.pages.collectAsState()
    val vScroll = rememberTimeGridScroll()
    val base = remember { java.time.LocalDate.parse(anchorDate) }
    val center = 10000
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = center)
    val snapFling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(listState)
    fun dateFor(index: Int): String = base.plusDays((index - center).toLong()).toString()

    LaunchedEffect(listState.firstVisibleItemIndex) {
        for (o in -1..4) vm.ensureDay(dateFor(listState.firstVisibleItemIndex + o))
    }
    // Sync the anchor once scrolling settles (not mid-scroll, avoiding feedback).
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            vm.onDaySettled(dateFor(listState.firstVisibleItemIndex))
        }
    }
    LaunchedEffect(navNonce) {
        if (navNonce == 0) return@LaunchedEffect
        val target = center + java.time.temporal.ChronoUnit.DAYS.between(
            base, java.time.LocalDate.parse(anchorDate),
        ).toInt()
        if (target in 0 until 20001 && target != listState.firstVisibleItemIndex) {
            // Quick directional slide: jump next to the target, then animate a day.
            val from = if (target > listState.firstVisibleItemIndex) target - 1 else target + 1
            listState.scrollToItem(from)
            listState.animateScrollToItem(target)
        }
    }

    ThreeDayGrid(
        listState = listState,
        snapFling = snapFling,
        scroll = vScroll,
        itemCount = 20001,
        dateFor = ::dateFor,
        dayData = { iso ->
            pages[iso]?.let { pd ->
                ThreeDayData(
                    events = localizeEvents(pd.events, pd.timezone),
                    today = pd.today,
                    nowColor = pd.nowColor,
                )
            }
        },
        onEventClick = onEventClick,
    )
}

/**
 * Week view as a finger-follow pager (Slice 2b). Each page is a Sun–Sat week;
 * neighbour weeks are pre-fetched so they slide in populated.
 */
@Composable
private fun WeekPager(
    vm: CalendarViewModel,
    anchorDate: String,
    navNonce: Int,
    onEventClick: (com.kairos.app.data.remote.dto.CalEventDto) -> Unit,
) {
    val weekPages by vm.weekPages.collectAsState()
    val base = remember {
        val d = java.time.LocalDate.parse(anchorDate)
        d.minusDays((d.dayOfWeek.value % 7).toLong())
    }
    val center = 8000
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = center,
        pageCount = { 16001 },
    )
    val vScroll = rememberTimeGridScroll()
    fun keyFor(page: Int): String = base.plusWeeks((page - center).toLong()).toString()

    LaunchedEffect(pagerState.currentPage) {
        for (o in -1..1) vm.ensureWeek(keyFor(pagerState.currentPage + o))
    }
    val settledKey = keyFor(pagerState.settledPage)
    LaunchedEffect(settledKey, weekPages[settledKey]) {
        vm.onWeekSettled(settledKey)
    }
    LaunchedEffect(navNonce) {
        if (navNonce == 0) return@LaunchedEffect
        val d = java.time.LocalDate.parse(anchorDate)
        val ws = d.minusDays((d.dayOfWeek.value % 7).toLong())
        val target = center + java.time.temporal.ChronoUnit.WEEKS.between(base, ws).toInt()
        if (target in 0 until 16001 && target != pagerState.currentPage) {
            pagerState.animateToPageQuick(target)
        }
    }

    Row(Modifier.fillMaxSize()) {
        WeekAxisColumn(vScroll)
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            val pd = weekPages[keyFor(page)]
            if (pd != null) {
                val evs = remember(pd.events, pd.timezone) { localizeEvents(pd.events, pd.timezone) }
                WeekGridPage(
                    days = pd.rangeDays,
                    events = evs,
                    today = pd.today,
                    nowColor = pd.nowColor,
                    scroll = vScroll,
                    onEventClick = onEventClick,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
        }
    }
}

/**
 * Animate to [target] but only ever animate the final step: for a far jump we
 * snap to the page just before/after the target (on the correct side) and then
 * animate the last page, so tapping "Today" looks like a quick slide back in the
 * right direction instead of an instant change or a long scroll.
 */
private suspend fun androidx.compose.foundation.pager.PagerState.animateToPageQuick(target: Int) {
    val cur = currentPage
    if (target == cur) return
    if (kotlin.math.abs(target - cur) <= 1) {
        animateScrollToPage(target)
    } else {
        scrollToPage(if (target > cur) target - 1 else target + 1)
        animateScrollToPage(target)
    }
}

// ---- Month dropdown (mini-month with dots) ----

/**
 * Mini-month picker as a left/right finger-follow pager. Each page is a month;
 * neighbours are pre-fetched. Picking a day or settling on a new month navigates
 * the view behind the dropdown.
 */
@Composable
private fun MiniMonthPager(
    vm: CalendarViewModel,
    anchorDate: String,
    navNonce: Int,
    onPickDay: (String) -> Unit,
) {
    val monthPages by vm.monthPages.collectAsState()
    val base = remember { java.time.LocalDate.parse(anchorDate).withDayOfMonth(1) }
    val center = 6000
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = center,
        pageCount = { 12001 },
    )
    fun keyFor(page: Int): String = base.plusMonths((page - center).toLong()).toString()

    LaunchedEffect(pagerState.currentPage) {
        for (o in -1..1) vm.ensureMonth(keyFor(pagerState.currentPage + o))
    }
    val settledKey = keyFor(pagerState.settledPage)
    LaunchedEffect(settledKey) {
        // Navigate only on an actual month change (skip the initial settle).
        if (settledKey.take(7) != anchorDate.take(7)) onPickDay(settledKey)
    }
    LaunchedEffect(navNonce) {
        if (navNonce == 0) return@LaunchedEffect
        val target = center + java.time.temporal.ChronoUnit.MONTHS.between(
            base, java.time.LocalDate.parse(anchorDate).withDayOfMonth(1),
        ).toInt()
        if (target in 0 until 12001 && target != pagerState.currentPage) {
            pagerState.animateToPageQuick(target)
        }
    }

    androidx.compose.foundation.pager.HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().height(310.dp),
    ) { page ->
        val pd = monthPages[keyFor(page)]
        if (pd != null) {
            MiniMonthDropdown(pd, onPickDay)
        } else {
            Box(Modifier.fillMaxWidth().height(310.dp))
        }
    }
}

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
private fun MonthChipsView(data: CalendarDto, events: List<CalEventDto>, vm: CalendarViewModel, onEventClick: (CalEventDto) -> Unit) {
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
                        onEventClick = onEventClick,
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
    onEventClick: (CalEventDto) -> Unit,
    onClick: () -> Unit,
) {
    val num = iso.substringAfterLast('-').trimStart('0').ifEmpty { "0" }
    val sorted = events.sortedWith(compareByDescending<CalEventDto> { it.allDay }.thenBy { it.startMin })
    val todayTint = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent
    Column(
        modifier
            .background(todayTint)
            .drawBehind {
                // Single 1px lines (right + bottom) so shared edges aren't doubled
                // like a full per-cell border — matches the time-grid line weight.
                drawLine(MonthLine, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1f)
                drawLine(MonthLine, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            }
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
            sorted.take(4).forEach { ev -> MonthChip(ev) { onEventClick(ev) } }
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
private fun MonthChip(e: CalEventDto, onClick: () -> Unit) {
    val color = parseColor(e.color)
    if (e.external) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onClick() },
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
                .clickable { onClick() }.padding(horizontal = 4.dp, vertical = 1.dp),
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

/**
 * Agenda as a day pager — swipe left/right to change day, matching the other
 * views. Reuses the day cache; each page is one day's agenda list.
 */
@Composable
private fun AgendaPager(
    vm: CalendarViewModel,
    anchorDate: String,
    navNonce: Int,
    today: String,
    onEventClick: (CalEventDto) -> Unit,
) {
    val pages by vm.pages.collectAsState()
    val base = remember { java.time.LocalDate.parse(anchorDate) }
    val center = 10000
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = center,
        pageCount = { 20001 },
    )
    fun dateFor(page: Int): String = base.plusDays((page - center).toLong()).toString()

    LaunchedEffect(pagerState.currentPage) {
        for (o in -2..2) vm.ensureDay(dateFor(pagerState.currentPage + o))
    }
    val settledIso = dateFor(pagerState.settledPage)
    LaunchedEffect(settledIso, pages[settledIso]) {
        vm.onDaySettled(settledIso)
    }
    LaunchedEffect(navNonce) {
        if (navNonce == 0) return@LaunchedEffect
        val target = center + java.time.temporal.ChronoUnit.DAYS.between(
            base, java.time.LocalDate.parse(anchorDate),
        ).toInt()
        if (target in 0 until 20001 && target != pagerState.currentPage) {
            pagerState.animateToPageQuick(target)
        }
    }

    androidx.compose.foundation.pager.HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val iso = dateFor(page)
        val pd = pages[iso]
        if (pd != null) {
            val evs = remember(pd.events, pd.timezone) { localizeEvents(pd.events, pd.timezone) }
            AgendaView(evs, iso, today, onEventClick)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun AgendaView(events: List<CalEventDto>, date: String, today: String, onEventClick: (CalEventDto) -> Unit) {
    val shown = events
        .filter { it.dayISO == date }
        .sortedWith(compareByDescending<CalEventDto> { it.allDay }.thenBy { it.startMin })

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            agendaHeading(date),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                Text("Nothing scheduled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            shown.forEach { ev -> EventRow(ev) { onEventClick(ev) } }
        }
    }
}

@Composable
private fun EventRow(e: CalEventDto, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(parseColor(e.color)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                buildEventSecondary(e)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            AttendeesColumn(
                attendees = e.attendees,
                fallbackLabel = e.whoLabel,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
}

private fun buildEventSecondary(e: CalEventDto): String? {
    val parts = mutableListOf<String>()
    parts += if (e.allDay) "All day" else e.timeLabel
    e.location?.takeIf { it.isNotBlank() }?.let { parts += it }
    e.recurLabel?.takeIf { it.isNotBlank() }?.let { parts += it }
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
    var picker by remember { mutableStateOf<ColorSlot?>(null) }
    Surface(
        Modifier.fillMaxHeight().fillMaxWidth(0.80f)
            .statusBarsPadding()
            .clip(RoundedCornerShape(topStart = 22.dp)),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
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

            DrawerDivider()
            val cp = opt.colorPrefs
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(KairosIcons.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Text("Colors", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            // System = follow the shared/web settings (no options). Custom = your
            // own event colors, everyone else's greyed out automatically.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ModeChip("Custom", cp.personalizeColors, Modifier.weight(1f)) {
                    if (!cp.personalizeColors) vm.savePrefs(personalizeColors = true, othersMode = "grey")
                }
                ModeChip("System", !cp.personalizeColors, Modifier.weight(1f)) {
                    if (cp.personalizeColors) vm.savePrefs(personalizeColors = false)
                }
            }

            if (cp.personalizeColors) {
                Spacer(Modifier.size(6.dp))
                ColorField("Current time", cp.nowColor, opt.nowSystemColor) {
                    picker = ColorSlot("Current time", cp.nowColor, opt.nowSystemColor,
                        { vm.savePrefs(nowColor = it) }, { vm.savePrefs(nowColor = null) },
                        palette = NowPalette)
                }
                listOf("APPOINTMENT" to "Events", "CLASS" to "Class", "WORK" to "Work", "BIRTHDAY" to "Birthdays").forEach { (k, lbl) ->
                    ColorField(lbl, cp.kindColors[k], opt.meColor) {
                        picker = ColorSlot(lbl, cp.kindColors[k], opt.meColor,
                            { vm.savePrefs(kindColors = cp.kindColors + (k to it)) },
                            { vm.savePrefs(kindColors = cp.kindColors - k) })
                    }
                }
                ColorField("Holidays", cp.holidayColor, opt.holidaySystemColor) {
                    picker = ColorSlot("Holidays", cp.holidayColor, opt.holidaySystemColor,
                        { vm.savePrefs(holidayColor = it) }, { vm.savePrefs(holidayColor = null) })
                }
                ColorField("Family", cp.familyColor, opt.familySystemColor) {
                    picker = ColorSlot("Family", cp.familyColor, opt.familySystemColor,
                        { vm.savePrefs(familyColor = it) }, { vm.savePrefs(familyColor = null) })
                }
                opt.eventTypes.forEach { t ->
                    ColorField(t.name, cp.eventTypeColors[t.id], t.color) {
                        picker = ColorSlot(t.name, cp.eventTypeColors[t.id], t.color,
                            { vm.savePrefs(eventTypeColors = cp.eventTypeColors + (t.id to it)) },
                            { vm.savePrefs(eventTypeColors = cp.eventTypeColors - t.id) })
                    }
                }
                opt.subscriptions.forEach { s ->
                    ColorField(s.name, cp.subColors[s.id], s.color) {
                        picker = ColorSlot(s.name, cp.subColors[s.id], s.color,
                            { vm.savePrefs(subColors = cp.subColors + (s.id to it)) },
                            { vm.savePrefs(subColors = cp.subColors - s.id) })
                    }
                }
            }
        }
    }
    picker?.let { slot -> ColorPickerDialog(slot) { picker = null } }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class ColorSlot(
    val title: String,
    val current: String?,
    val fallback: String,
    val onPick: (String) -> Unit,
    val onClear: () -> Unit,
    val palette: List<String> = CalPalette,
)

private val MonthLine = androidx.compose.ui.graphics.Color(0xFFCBD5E1)
private val CalPalette = listOf(
    "#2563eb", "#3b82f6", "#0ea5e9", "#0891b2", "#0d9488", "#059669",
    "#16a34a", "#65a30d", "#ca8a04", "#d97706", "#ea580c", "#dc2626",
    "#e11d48", "#db2777", "#c026d3", "#9333ea", "#7c3aed", "#6366f1",
    "#475569", "#334155", "#78716c", "#111827",
)

// Bright, high-contrast colors for the current-time line so it's easy to spot.
// White reads on the dark grid at night; black on the light grid by day.
private val NowPalette = listOf(
    "#2563eb", "#ef4444", "#f97316", "#10b981", "#06b6d4", "#a855f7",
    "#ec4899", "#facc15", "#000000", "#ffffff",
)

@Composable
private fun ColorField(label: String, current: String?, fallback: String, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen() }.padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(parseColor(current ?: fallback))
                .border(if (current == null) 1.dp else 0.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (current == null) {
            Text("Default", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ColorPickerDialog(slot: ColorSlot, onClose: () -> Unit) {
    // Pending selection: a hex, or null for "default". Applied only on Confirm.
    var selected by remember(slot.title) { mutableStateOf(slot.current) }
    AnimatedDialog(
        onDismissRequest = onClose,
        title = slot.title,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                slot.palette.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { hex ->
                            Swatch(
                                color = parseColor(hex),
                                selected = selected?.equals(hex, ignoreCase = true) == true,
                                onClick = { selected = hex },
                            )
                        }
                    }
                }
                HorizontalDivider()
                // Default option: the fallback color, chosen when nothing custom is set.
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable { selected = null }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Swatch(
                        color = parseColor(slot.fallback),
                        selected = selected == null,
                        onClick = { selected = null },
                    )
                    Text("Default", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sel = selected
                if (sel == null) slot.onClear() else slot.onPick(sel)
                onClose()
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** A color swatch; when selected it gets a teal ring with a small gap. */
@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier,
            )
            .clickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
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
    Box(Modifier.fillMaxWidth().height(1.dp).padding(vertical = 6.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun DefaultViewDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        "agenda" to "Agenda", "day" to "Day", "three_day" to "3 Days",
        "week" to "Week", "month" to "Month", "last" to "Last view",
    )
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "Default view",
        content = {
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

// ---- Event detail (full screen) ----

private fun eventTypeName(
    event: CalEventDto,
    customTypes: List<com.kairos.app.data.remote.dto.CalEventTypeDto>,
): String =
    event.eventTypeId?.let { id -> customTypes.firstOrNull { it.id == id }?.name }
        ?: when (event.kind) {
            "CLASS" -> "Class"
            "WORK" -> "Work shift"
            "BIRTHDAY" -> "Birthday"
            "OTHER" -> "Medical / Dental"
            else -> "Event"
        }

@Composable
private fun EventDetailScreen(
    event: CalEventDto,
    occurrenceISO: String,
    canManageFamily: Boolean,
    ui: CalendarUiState,
    vm: CalendarViewModel,
    customTypes: List<com.kairos.app.data.remote.dto.CalEventTypeDto>,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val stored = event.eventId.isNotBlank()
    // Admin/system-created events (holidays, profile birthdays, school work, family
    // events, subscribed calendars) can't be deleted from here.
    val systemKind = event.kind == "HOLIDAY" || event.kind == "BIRTHDAY" || event.kind == "SCHOOLWORK"
    val editable = stored && !event.external && !systemKind && (!event.isFamily || canManageFamily)
    val deletable = stored && !event.external && !systemKind && !event.isFamily

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) {
                    Text("\u2715", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.weight(1f))
                if (editable) {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable { onEdit() }, contentAlignment = Alignment.Center) {
                        Icon(KairosIcons.Pencil, contentDescription = "Edit")
                    }
                }
                if (deletable) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .clickable { confirmDelete = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(KairosIcons.Trash, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.width(5.dp).height(64.dp).clip(RoundedCornerShape(3.dp)).background(parseColor(event.color)))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(event.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(dayHeading(event.dayISO), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (event.allDay) "All day" else rangeLabel(event.startMin, event.endMin),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        event.recurLabel?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        event.location?.takeIf { it.isNotBlank() }?.let { loc ->
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { com.kairos.app.ui.common.Maps.open(ctx, loc) }
                                    .padding(vertical = 2.dp),
                            ) {
                                Icon(
                                    KairosIcons.MapPin,
                                    contentDescription = "Navigate",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(loc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        event.notes?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        // Event type name.
                        Text(
                            eventTypeName(event, customTypes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        // The reminders currently set on this event.
                        if (event.reminders.isNotEmpty()) {
                            event.reminders.sorted().forEach { m ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        KairosIcons.Bell,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Text(
                                        com.kairos.app.data.settings.ReminderDefaults.label(m),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        // Per-person attendance for sport events (owner + participants).
                        if (event.attendees.any { it.state.isNotBlank() }) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                event.attendees.forEach { a ->
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AttendanceIcon(a.state, Modifier.size(18.dp))
                                        Text(a.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(KairosIcons.Calendar, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(event.calendarName?.takeIf { it.isNotBlank() } ?: event.whoLabel.ifBlank { "Calendar" }, style = MaterialTheme.typography.bodyLarge)
                }

                ui.deleteError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        if (event.recurring) {
            AnimatedDialog(
                onDismissRequest = { confirmDelete = false },
                title = "Delete repeating event",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        DeleteScopeRow("This event", !ui.deleting) {
                            confirmDelete = false; vm.deleteEvent(event.eventId, "one", occurrenceISO) { onClose() }
                        }
                        DeleteScopeRow("This and following events", !ui.deleting) {
                            confirmDelete = false; vm.deleteEvent(event.eventId, "future", occurrenceISO) { onClose() }
                        }
                        DeleteScopeRow("All events", !ui.deleting) {
                            confirmDelete = false; vm.deleteEvent(event.eventId, "all", null) { onClose() }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        } else {
            AnimatedDialog(
                onDismissRequest = { confirmDelete = false },
                title = "Delete event?",
                content = { Text("This can't be undone.") },
                confirmButton = {
                    TextButton(
                        enabled = !ui.deleting,
                        onClick = { confirmDelete = false; vm.deleteEvent(event.eventId, "all", null) { onClose() } },
                    ) { Text(if (ui.deleting) "Deleting\u2026" else "Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun DeleteScopeRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }.padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

private fun rangeLabel(startMin: Int, endMin: Int): String {
    val dur = (endMin - startMin).coerceAtLeast(0)
    val h = dur / 60
    val m = dur % 60
    val durStr = when {
        h > 0 && m > 0 -> "$h hr $m min"
        h > 0 -> if (h == 1) "1 hour" else "$h hours"
        else -> "$m min"
    }
    return "${clock(startMin)} \u2192 ${clock(endMin)} ($durStr)"
}

private fun clock(min: Int): String = TimeFmt.clock(min)

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

private fun agendaHeading(iso: String): String =
    try { LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("EEEE d")) } catch (_: Exception) { iso }

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

private fun formatTime(min: Int): String = TimeFmt.clock(min)

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
