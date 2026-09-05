package com.kairos.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.kairos.app.data.remote.dto.CalEventDto
import com.kairos.app.data.remote.dto.CalendarDto
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val HOUR_H = 56.dp
private val GUTTER = 44.dp
private const val HOURS = 24

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

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
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
) {
    val placed = remember(events) { placeEvents(events) }
    val nowMin = if (isToday) deviceNowMinutes() else -1
    val nowC = remember(nowColor) { parseGridColor(nowColor) }

    BoxWithConstraints(
        modifier
            .height(HOUR_H * HOURS)
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
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = if (h > 34.dp) 2 else 1,
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

private fun hourLabel(h: Int): String = when {
    h == 0 -> "12a"
    h < 12 -> "${h}a"
    h == 12 -> "12p"
    else -> "${h - 12}p"
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
