package com.kairos.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.data.remote.dto.CalEventDto
import com.kairos.app.data.remote.dto.CalendarDto
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val HOUR_H = 56.dp
private val GUTTER = 52.dp
private val HEADER_H = 46.dp
private val WEEK_HEADER_H = 76.dp
private const val HOURS = 24

/** A vertical scroll state for the time grid that opens near the current hour
 *  (one hour above), so an afternoon view doesn't start pinned at 1 AM. */
@Composable
fun rememberTimeGridScroll(): androidx.compose.foundation.ScrollState {
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val h = (java.time.LocalTime.now().hour - 1).coerceIn(0, HOURS - 1)
        scroll.scrollTo(with(density) { (HOUR_H * h).toPx() }.toInt())
    }
    return scroll
}

private data class Placed(val e: CalEventDto, val col: Int, val cols: Int)

/** Greedy overlap layout: split each cluster of mutually-overlapping events into
 *  side-by-side columns. */
private fun placeEvents(evs: List<CalEventDto>): List<Placed> {
    val sorted = evs.sortedWith(compareBy({ it.startMin }, { it.endMin }))
    val out = mutableListOf<Placed>()
    var i = 0
    while (i < sorted.size) {
        var clusterEnd = sorted[i].endMin
        val cluster = mutableListOf(sorted[i])
        var j = i + 1
        while (j < sorted.size && sorted[j].startMin < clusterEnd) {
            cluster.add(sorted[j])
            clusterEnd = maxOf(clusterEnd, sorted[j].endMin)
            j++
        }
        val colEnds = mutableListOf<Int>()
        val colOf = IntArray(cluster.size)
        cluster.forEachIndexed { ci, e ->
            var col = colEnds.indexOfFirst { it <= e.startMin }
            if (col == -1) { col = colEnds.size; colEnds.add(e.endMin) } else colEnds[col] = e.endMin
            colOf[ci] = col
        }
        val cols = maxOf(1, colEnds.size)
        cluster.forEachIndexed { ci, e -> out.add(Placed(e, colOf[ci], cols)) }
        i = j
    }
    return out
}

@Composable
fun TimeGrid(data: CalendarDto, events: List<CalEventDto>, onEventClick: (CalEventDto) -> Unit, modifier: Modifier = Modifier) {
    val days = data.rangeDays
    val now = data.nowColor
    val gridColor = MaterialTheme.colorScheme.outline

    Column(modifier.fillMaxSize()) {
        // Day headers, aligned with the columns below.
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(GUTTER))
            days.forEach { iso ->
                DayHeader(iso, isToday = iso == data.today, modifier = Modifier.weight(1f))
            }
        }

        // All-day strip (only when there's something).
        val allDayByDay = days.associateWith { d ->
            events.filter { it.allDay && it.dayISO == d }
        }
        if (allDayByDay.values.any { it.isNotEmpty() }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Spacer(Modifier.width(GUTTER))
                days.forEach { iso ->
                    Column(
                        Modifier.weight(1f).padding(horizontal = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        allDayByDay[iso].orEmpty().take(3).forEach { e -> AllDayChip(e) { onEventClick(e) } }
                    }
                }
            }
        }

        // Permanent divider: dates + all-day events sit above it, hour slots below.
        Box(Modifier.fillMaxWidth().height(1.dp).background(gridColor))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberTimeGridScroll()),
        ) {
            Row(Modifier.fillMaxWidth().height(HOUR_H * HOURS)) {
                // Hour gutter
                Column(Modifier.width(GUTTER)) {
                    for (h in 0 until HOURS) {
                        Box(Modifier.height(HOUR_H)) {
                            if (h > 0) {
                                Text(
                                    hourLabel(h),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.offset(y = (-7).dp).padding(end = 4.dp).fillMaxWidth(),
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
                days.forEach { iso ->
                    DayColumn(
                        iso = iso,
                        events = events.filter { !it.allDay && it.dayISO == iso },
                        isToday = iso == data.today,
                        nowColor = now,
                        gridColor = gridColor,
                        onEventClick = onEventClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(iso: String, isToday: Boolean, modifier: Modifier) {
    val d = remember(iso) { runCatching { LocalDate.parse(iso) }.getOrNull() }
    Column(
        modifier.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            d?.dayOfWeek?.name?.take(3)?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier.height(26.dp).width(26.dp).clip(CircleShape)
                .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                d?.dayOfMonth?.toString() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AllDayChip(e: CalEventDto, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(parseGridColor(e.color))
            .clickable { onClick() }.padding(horizontal = 5.dp, vertical = 2.dp),
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

@Composable
private fun DayColumn(
    iso: String,
    events: List<CalEventDto>,
    isToday: Boolean,
    nowColor: String,
    gridColor: Color,
    onEventClick: (CalEventDto) -> Unit,
    modifier: Modifier,
    compact: Boolean = false,
) {
    val placed = remember(events) { placeEvents(events) }
    val nowMin = if (isToday) deviceNowMinutes() else -1
    val nowC = remember(nowColor) { parseGridColor(nowColor) }
    val todayTint = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent

    BoxWithConstraints(
        modifier
            .height(HOUR_H * HOURS)
            .background(todayTint)
            .drawBehind {
                for (h in 0..HOURS) {
                    val y = size.height * (h.toFloat() / HOURS)
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                drawLine(gridColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
            },
    ) {
        val colW = maxWidth
        placed.forEach { p ->
            val e = p.e
            val top = HOUR_H * (e.startMin / 60f)
            val h = (HOUR_H * ((e.endMin - e.startMin) / 60f)).coerceAtLeast(22.dp)
            val w = colW / p.cols
            val x = w * p.col
            Box(
                Modifier
                    .offset(x = x + 1.dp, y = top)
                    .width(w - 2.dp)
                    .height(h)
                    .clip(RoundedCornerShape(4.dp))
                    .background(parseGridColor(e.color).copy(alpha = 0.9f))
                    .clickable { onEventClick(e) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    e.title,
                    style = if (compact) {
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                        )
                    } else {
                        MaterialTheme.typography.labelSmall
                    },
                    color = Color.White,
                    maxLines = when {
                        compact && h > 48.dp -> 3
                        h > 34.dp -> 2
                        else -> 1
                    },
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (nowMin >= 0) {
            val y = HOUR_H * (nowMin / 60f)
            Box(Modifier.offset(y = y).fillMaxWidth().height(2.dp).background(nowC))
            Box(Modifier.offset(x = (-3).dp, y = y - 3.dp).height(7.dp).width(7.dp).clip(CircleShape).background(nowC))
        }
    }
}

/** Minutes-from-midnight right now in the device's timezone. Events are localised
 *  to device time before they reach the grid, so the now-line matches them. */
private fun deviceNowMinutes(): Int {
    val t = ZonedDateTime.now(ZoneId.systemDefault())
    return t.hour * 60 + t.minute
}

/** Frozen left column of the day view: the snapped day's Mon/date on top (which
 *  updates as you page), then the hour labels that scroll with the day columns. */
@Composable
fun DayAxisColumn(
    dateISO: String,
    today: String,
    scroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    val d = remember(dateISO) { runCatching { LocalDate.parse(dateISO) }.getOrNull() }
    val isToday = dateISO == today
    val gridColor = MaterialTheme.colorScheme.outline
    Column(
        modifier.width(GUTTER).drawBehind {
            drawLine(gridColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1f)
        },
    ) {
        Column(
            Modifier.height(HEADER_H).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                d?.dayOfWeek?.name?.take(3)?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                d?.dayOfMonth?.toString() ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(gridColor))
        Box(Modifier.weight(1f).verticalScroll(scroll)) {
            Column(Modifier.height(HOUR_H * HOURS)) {
                for (h in 0 until HOURS) {
                    Box(Modifier.height(HOUR_H).fillMaxWidth()) {
                        if (h > 0) {
                            Text(
                                hourLabel(h),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.offset(y = (-7).dp).padding(end = 5.dp).fillMaxWidth(),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One day's page in the day pager: a frozen header (all-day events / "No events"
 *  / blank) above the scrolling hour column. [events] are already localised. */
@Composable
fun DayGridPage(
    events: List<CalEventDto>,
    iso: String,
    today: String,
    nowColor: String,
    loading: Boolean,
    scroll: androidx.compose.foundation.ScrollState,
    onEventClick: (CalEventDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outline
    val dayEvents = events.filter { it.dayISO == iso }
    val allDay = dayEvents.filter { it.allDay }
    Column(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().height(HEADER_H).padding(horizontal = 4.dp, vertical = 3.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            when {
                loading -> {}
                allDay.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    allDay.take(2).forEach { e -> AllDayChip(e) { onEventClick(e) } }
                }
                dayEvents.isEmpty() -> Text(
                    "No events",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {}
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(gridColor))
        Box(Modifier.weight(1f).verticalScroll(scroll)) {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(HOUR_H * HOURS), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else {
                DayColumn(
                    iso = iso,
                    events = dayEvents.filter { !it.allDay },
                    isToday = iso == today,
                    nowColor = nowColor,
                    gridColor = gridColor,
                    onEventClick = onEventClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Frozen left column for the week view: a blank header (the 7 day headers live
 *  in the sliding pages) then the hour labels, with the column line up the side. */
@Composable
fun WeekAxisColumn(
    scroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outline
    Column(
        modifier.width(GUTTER).drawBehind {
            drawLine(gridColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1f)
        },
    ) {
        Spacer(Modifier.height(WEEK_HEADER_H))
        Box(Modifier.fillMaxWidth().height(1.dp).background(gridColor))
        Box(Modifier.weight(1f).verticalScroll(scroll)) {
            Column(Modifier.height(HOUR_H * HOURS)) {
                for (h in 0 until HOURS) {
                    Box(Modifier.height(HOUR_H).fillMaxWidth()) {
                        if (h > 0) {
                            Text(
                                hourLabel(h),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.offset(y = (-7).dp).padding(end = 5.dp).fillMaxWidth(),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One week's page: a frozen header (7 day headers + all-day strip, fixed height)
 *  above the 7-column hour grid that scrolls with the axis. [events] localised. */
@Composable
fun WeekGridPage(
    days: List<String>,
    events: List<CalEventDto>,
    today: String,
    nowColor: String,
    scroll: androidx.compose.foundation.ScrollState,
    onEventClick: (CalEventDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outline
    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().height(WEEK_HEADER_H)) {
            Row(Modifier.fillMaxWidth()) {
                days.forEach { iso -> DayHeader(iso, isToday = iso == today, modifier = Modifier.weight(1f)) }
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                days.forEach { iso ->
                    Column(
                        Modifier.weight(1f).padding(horizontal = 1.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        events.filter { it.allDay && it.dayISO == iso }.take(2).forEach { e ->
                            AllDayChip(e) { onEventClick(e) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(gridColor))
        Box(Modifier.weight(1f).verticalScroll(scroll)) {
            Row(Modifier.fillMaxWidth().height(HOUR_H * HOURS)) {
                days.forEach { iso ->
                    DayColumn(
                        iso = iso,
                        events = events.filter { !it.allDay && it.dayISO == iso },
                        isToday = iso == today,
                        nowColor = nowColor,
                        gridColor = gridColor,
                        onEventClick = onEventClick,
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                }
            }
        }
    }
}

/** Data for one day column in the 3-day grid (already localized). */
data class ThreeDayData(
    val events: List<CalEventDto>,
    val today: String,
    val nowColor: String,
)

/**
 * 3-day view as a single two-way-scrolling grid: frozen hour axis (left) and
 * frozen day headers (top), a horizontally-snapping LazyRow of day columns for
 * day-by-day paging, and — crucially — ONE shared vertical scroll for all three
 * columns so they move in unison (the old per-column scrollers drifted apart on
 * fast flings and at the edges). Headers slide with the horizontal scroll offset.
 */
@Composable
fun ThreeDayGrid(
    listState: LazyListState,
    snapFling: FlingBehavior,
    scroll: androidx.compose.foundation.ScrollState,
    itemCount: Int,
    dateFor: (Int) -> String,
    dayData: (String) -> ThreeDayData?,
    onEventClick: (CalEventDto) -> Unit,
) {
    val gridColor = MaterialTheme.colorScheme.outline
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dayWidth = (maxWidth - GUTTER) / 3
        Row(Modifier.fillMaxSize()) {
            WeekAxisColumn(scroll)
            Column(Modifier.weight(1f)) {
                // Frozen day headers, sliding with the horizontal scroll offset.
                Row(Modifier.fillMaxWidth().height(WEEK_HEADER_H)) {
                    Box(Modifier.fillMaxSize().clipToBounds()) {
                        val headerFirst by remember { derivedStateOf { listState.firstVisibleItemIndex } }
                        Row(
                            Modifier
                                .fillMaxHeight()
                                .offset { IntOffset(-listState.firstVisibleItemScrollOffset, 0) },
                        ) {
                            for (i in headerFirst until headerFirst + 4) {
                                val iso = dateFor(i)
                                val pd = dayData(iso)
                                Column(Modifier.width(dayWidth).fillMaxHeight()) {
                                    DayHeader(iso, isToday = pd != null && pd.today == iso, Modifier.fillMaxWidth())
                                    Column(
                                        Modifier.fillMaxWidth().weight(1f).padding(horizontal = 1.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        pd?.events?.filter { it.allDay && it.dayISO == iso }?.take(2)?.forEach { e ->
                                            AllDayChip(e) { onEventClick(e) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(gridColor))
                // One vertical scroll for the whole grid; LazyRow pages days inside it.
                Box(Modifier.weight(1f).verticalScroll(scroll)) {
                    LazyRow(
                        state = listState,
                        flingBehavior = snapFling,
                        modifier = Modifier.height(HOUR_H * HOURS),
                    ) {
                        items(itemCount) { index ->
                            val iso = dateFor(index)
                            val pd = dayData(iso)
                            if (pd != null) {
                                DayColumn(
                                    iso = iso,
                                    events = pd.events.filter { !it.allDay && it.dayISO == iso },
                                    isToday = pd.today == iso,
                                    nowColor = pd.nowColor,
                                    gridColor = gridColor,
                                    onEventClick = onEventClick,
                                    modifier = Modifier.width(dayWidth),
                                )
                            } else {
                                Box(Modifier.width(dayWidth).height(HOUR_H * HOURS), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun hourLabel(h: Int): String = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

private fun parseGridColor(hex: String?): Color {
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
