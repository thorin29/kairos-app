package com.kairos.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.nav.KairosIcons
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class PickMode { DAYS, MONTHS, YEARS }

/**
 * A date picker in the app's own pop-up (AnimatedDialog), laid out like the
 * calendars elsewhere — a month of days, the chosen day filled, today ringed —
 * with the month and the year in the header as buttons: tap the year for a
 * list of years, tap the month for all twelve. Getting to a birth year decades
 * back is two taps instead of hundreds of swipes.
 */
@Composable
fun QuickDatePickerDialog(
    initial: LocalDate?,
    minDate: LocalDate,
    maxDate: LocalDate,
    title: String,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    var picked by remember { mutableStateOf(initial) }
    val start = (initial ?: today).let { if (it.isAfter(maxDate)) maxDate else if (it.isBefore(minDate)) minDate else it }
    var view by remember { mutableStateOf(YearMonth.from(start)) }
    var mode by remember { mutableStateOf(PickMode.DAYS) }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = title,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                onClick = { picked?.let(onPick) },
                enabled = picked != null,
            ) { Text("OK") }
        },
    ) {
        Column(Modifier.width(300.dp)) {
            Text(
                picked?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())) ?: "No date picked",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (picked == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HeaderChip(
                    view.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    on = mode == PickMode.MONTHS,
                ) { mode = if (mode == PickMode.MONTHS) PickMode.DAYS else PickMode.MONTHS }
                Spacer(Modifier.width(4.dp))
                HeaderChip(view.year.toString(), on = mode == PickMode.YEARS) {
                    mode = if (mode == PickMode.YEARS) PickMode.DAYS else PickMode.YEARS
                }
                Spacer(Modifier.weight(1f))
                if (mode == PickMode.DAYS) {
                    val canBack = view.minusMonths(1).atEndOfMonth() >= minDate
                    val canNext = view.plusMonths(1).atDay(1) <= maxDate
                    IconButton(onClick = { view = view.minusMonths(1) }, enabled = canBack) {
                        Icon(KairosIcons.ChevronLeft, contentDescription = "Previous month")
                    }
                    IconButton(onClick = { view = view.plusMonths(1) }, enabled = canNext) {
                        Icon(KairosIcons.ChevronRight, contentDescription = "Next month")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when (mode) {
                PickMode.DAYS -> DayGrid(view, picked, today, minDate, maxDate) { picked = it }
                PickMode.MONTHS -> MonthGrid(view, minDate, maxDate) { m ->
                    view = view.withMonth(m)
                    mode = PickMode.DAYS
                }
                PickMode.YEARS -> YearGrid(view.year, minDate.year, maxDate.year) { y ->
                    view = view.withYear(y)
                    mode = PickMode.MONTHS
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            KairosIcons.ChevronDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DayGrid(
    view: YearMonth,
    picked: LocalDate?,
    today: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    // Sunday-first, like the app's calendar and the web's picker.
    val lead = view.atDay(1).dayOfWeek.value % 7
    val days = view.lengthOfMonth()
    val cells: List<Int?> = List(lead) { null } + (1..days).toList()
    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                Text(
                    d,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val date = view.atDay(day)
                            val off = date.isBefore(minDate) || date.isAfter(maxDate)
                            val isPicked = date == picked
                            val isToday = date == today
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isPicked) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .then(
                                        if (isToday && !isPicked) {
                                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else Modifier,
                                    )
                                    .clickable(enabled = !off) { onPick(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isPicked || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                    color = when {
                                        isPicked -> MaterialTheme.colorScheme.onPrimary
                                        off -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(view: YearMonth, minDate: LocalDate, maxDate: LocalDate, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..12).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { m ->
                    val ym = view.withMonth(m)
                    val off = ym.atEndOfMonth().isBefore(minDate) || ym.atDay(1).isAfter(maxDate)
                    val on = m == view.monthValue
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable(enabled = !off) { onPick(m) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                on -> MaterialTheme.colorScheme.onPrimary
                                off -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearGrid(current: Int, firstYear: Int, lastYear: Int, onPick: (Int) -> Unit) {
    val years = (firstYear..lastYear).toList()
    // Opens with the shown year a couple of rows down, not at 1900.
    val state = rememberLazyGridState(
        initialFirstVisibleItemIndex = (years.indexOf(current) - 8).coerceAtLeast(0),
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = state,
        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(years) { y ->
            val on = y == current
            Box(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onPick(y) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    y.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
