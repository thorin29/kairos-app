package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kairos.app.data.remote.dto.DaySportDto
import com.kairos.app.ui.common.AccentOutlinedButton
import com.kairos.app.ui.common.TimeFmt
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.launch

/**
 * A day's workouts as blocks (v0.538): the planned workout and each sport
 * event from the calendar are separate workouts, listed in time order. The
 * list shows while two or more are on the day; each opens its own page.
 */

/** Workout type colours, as on Weekly training. */
internal val PLAN_COLOR = Color(0xFF4F46E5)
internal val SPORT_COLOR = Color(0xFF65A30D)

internal data class DayBlockUi(
    /** "plan", "prog:<id>" for one program's workout (v0.544), or the sport's event id. */
    val key: String,
    /** Minutes from midnight, or null. */
    val time: Int?,
    val sport: DaySportDto? = null,
    val program: com.kairos.app.data.remote.dto.DayProgramBlockDto? = null,
)

/** Timed blocks by time; a block without one after them, in the order given. */
internal fun orderDayBlocks(blocks: List<DayBlockUi>): List<DayBlockUi> =
    blocks.withIndex().sortedWith(
        compareBy<IndexedValue<DayBlockUi>>({ it.value.time == null }, { it.value.time ?: 0 }, { it.index }),
    ).map { it.value }

internal fun timeOfDayText(min: Int): String {
    val h = (min / 60) % 24
    val m = min % 60
    return if (TimeFmt.military) "%02d:%02d".format(h, m)
    else "${(h + 11) % 12 + 1}:${"%02d".format(m)} ${if (h < 12) "AM" else "PM"}"
}

@Composable
private fun DoneChip(label: String, positive: Boolean = true) = StatusChip(label = label, positive = positive)

/** A card with a coloured left edge for its workout type. */
@Composable
private fun BlockCard(color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(color = color, size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height)) }
                .padding(start = 19.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        ) { content() }
    }
}

/** The planned workout's time, tap to change it for this day. */
@Composable
internal fun TimeChip(time: Int?, onClick: () -> Unit) {
    Text(
        (time?.let { timeOfDayText(it) } ?: "Set a time") + " ✎",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun CardChevronRow(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            KairosIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Today's workouts: one card per workout, in time order. */
@Composable
internal fun DayWorkoutsList(
    blocks: List<DayBlockUi>,
    planTitle: String,
    planParts: List<String>,
    planDone: Int,
    planTotal: Int,
    onOpen: (String) -> Unit,
    onTime: () -> Unit,
    onAnswer: (eventId: String, went: Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { b ->
            val sp = b.sport
            if (sp == null) {
                val pg = b.program
                val parts = pg?.parts ?: planParts
                val done = pg?.doneCount ?: planDone
                val total = pg?.total ?: planTotal
                BlockCard(color = PLAN_COLOR, onClick = { onOpen(b.key) }) {
                    TimeChip(time = b.time, onClick = onTime)
                    Spacer(Modifier.height(4.dp))
                    CardChevronRow(title = pg?.name ?: planTitle)
                    if (parts.isNotEmpty()) {
                        Text(
                            parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (pg?.skipped == true) {
                        DoneChip(label = "Skipped")
                    } else if (pg?.done ?: (planTotal > 0 && planDone >= planTotal)) {
                        DoneChip(label = "Done")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LinearProgressIndicator(
                                progress = { if (total > 0) done / total.toFloat() else 0f },
                                modifier = Modifier.weight(1f),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Text(
                                "$done of $total done",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                BlockCard(color = SPORT_COLOR, onClick = { onOpen(sp.eventId) }) {
                    Text(
                        "${sp.startLabel} – ${sp.endLabel} · from calendar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CardChevronRow(title = sp.title)
                    Spacer(Modifier.height(6.dp))
                    when (sp.state) {
                        "went" -> DoneChip(label = "Went · ${sp.minutes} min")
                        "skipped" -> DoneChip(label = "Didn’t go", positive = false)
                        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Did you go?", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            AccentOutlinedButton(onClick = { onAnswer(sp.eventId, false) }) { Text("No") }
                            androidx.compose.material3.Button(onClick = { onAnswer(sp.eventId, true) }) { Text("Yes") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = androidx.compose.ui.unit.TextUnit(0.08f, androidx.compose.ui.unit.TextUnitType.Em),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, start = 2.dp),
    )
}

/** A sport's own page (v0.538): did you go, its time and how it felt, and
 *  its own warm-up (passed in). */
@Composable
internal fun SportPage(
    sport: DaySportDto,
    onAnswer: (went: Boolean) -> Unit,
    onEdit: () -> Unit,
    warmup: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "${sport.startLabel} – ${sport.endLabel} · from calendar",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(sport.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        BlockSectionLabel(text = "Did you go?")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sport.state == "went") {
                    androidx.compose.material3.Button(onClick = {}) { Text("Yes, I went") }
                } else {
                    AccentOutlinedButton(onClick = { onAnswer(true) }) { Text("Yes, I went") }
                    if (sport.state == "skipped") {
                        androidx.compose.material3.Button(onClick = {}) { Text("No") }
                    } else {
                        AccentOutlinedButton(onClick = { onAnswer(false) }) { Text("No") }
                    }
                }
            }
        }
        if (sport.state == "went" && sport.sessionId != null) {
            BlockSectionLabel(text = "Time and how it felt")
            OutlinedCard(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${sport.minutes} min", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            sport.effort?.lowercase()?.replace('_', ' ') ?: "How did it feel?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        KairosIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        BlockSectionLabel(text = "Warm-up")
        warmup()
    }
}

/** The planned workout's time for this day, or back to the usual one. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutTimeDialog(
    time: Int?,
    usual: Int?,
    onSave: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = time ?: usual ?: 12 * 60
    val state = androidx.compose.material3.rememberTimePickerState(
        initialHour = start / 60,
        initialMinute = start % 60,
        is24Hour = TimeFmt.military,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Workout time", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Today only · " + (usual?.let { "usually ${timeOfDayText(it)}" } ?: "no usual time"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.TimePicker(state = state)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (time != null && time != usual) {
                        TextButton(onClick = { onSave(null) }) { Text("Usual") }
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onSave(state.hour * 60 + state.minute) }) { Text("Save") }
                }
            }
        }
    }
}

/** Settings → Usual workout time (v0.538): one time per weekday. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UsualTimesScreen(onBack: () -> Unit) {
    val container = com.kairos.app.ui.common.rememberContainer()
    val session = container.sessionRepository
    var times by remember { mutableStateOf<List<Int?>?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        times = runCatching { session.usualTimes() }.getOrNull()?.let { l -> List(7) { l.getOrNull(it) } } ?: List(7) { null }
    }
    val save = { next: List<Int?> ->
        times = next
        scope.launch {
            runCatching { session.setUsualTimes(next) }
                .onSuccess { times = it }
                .onFailure { error = "Couldn't save." }
        }
    }
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Usual workout time") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val list = times
            if (list == null) {
                androidx.compose.material3.CircularProgressIndicator()
            } else {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column {
                        WEEKDAY_NAMES.forEachIndexed { i, d ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { editing = i }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(d, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(
                                    list[i]?.let { timeOfDayText(it) } ?: "None",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (list[i] != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    val i = editing
    val list = times
    if (i != null && list != null) {
        UsualTimeDialog(
            day = WEEKDAY_NAMES[i],
            time = list[i],
            onSave = { t ->
                editing = null
                save(list.mapIndexed { j, v -> if (j == i) t else v })
            },
            onDismiss = { editing = null },
        )
    }
}

private val WEEKDAY_NAMES = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun UsualTimeDialog(day: String, time: Int?, onSave: (Int?) -> Unit, onDismiss: () -> Unit) {
    val start = time ?: 12 * 60
    val state = androidx.compose.material3.rememberTimePickerState(
        initialHour = start / 60,
        initialMinute = start % 60,
        is24Hour = TimeFmt.military,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text(day, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.TimePicker(state = state)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (time != null) TextButton(onClick = { onSave(null) }) { Text("None") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onSave(state.hour * 60 + state.minute) }) { Text("Save") }
                }
            }
        }
    }
}
