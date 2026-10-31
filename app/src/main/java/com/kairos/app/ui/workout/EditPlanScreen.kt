package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.PlanDayDto
import com.kairos.app.data.remote.dto.PlanWorkoutDto
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import java.time.LocalDate

private val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlanScreen(onBack: () -> Unit, onOpenRotation: () -> Unit) {
    val container = rememberContainer()
    val vm: EditPlanViewModel = viewModel(
        factory = viewModelFactory { initializer { EditPlanViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()
    val todayDow = remember { LocalDate.now().dayOfWeek.value % 7 }
    var addingDay by remember { mutableStateOf<Int?>(null) }
    var showStart by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenRotation) { Text("Rotation") }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            if (showStart) {
                PlanDatePick(
                    iso = ui.weeklyStart,
                    onPick = { vm.setStartDate(it); showStart = false },
                    onDismiss = { showStart = false },
                )
            }
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ui.error != null && ui.days.isEmpty() -> Text(
                    ui.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "starts-on") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Starts on",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { showStart = true },
                                    enabled = !ui.busy,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Text(if (ui.weeklyStart.isNotEmpty()) ui.weeklyStart else "Active now")
                                }
                                if (ui.weeklyStart.isNotEmpty()) {
                                    TextButton(onClick = { vm.setStartDate("") }, enabled = !ui.busy) {
                                        Text("Clear")
                                    }
                                }
                            }
                            Text(
                                "Pick a future day to start this plan; blank starts now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { vm.setWeeklyActive(!ui.weeklyActive) },
                                enabled = !ui.busy,
                            ) {
                                Text(if (ui.weeklyActive) "Pause weekly plan" else "Resume weekly plan")
                            }
                            if (!ui.weeklyActive) {
                                Text(
                                    "Paused \u2014 no weekly prompts. A rotation, if any, still runs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item(key = "plan-file") {
                        PlanFileActions(vm = vm, ui = ui)
                    }
                    ui.orders?.let { orders ->
                        item(key = "order") {
                            EveryDayOrder(order = orders.everyDay, onSave = { vm.saveOrder(day = null, order = it) })
                        }
                    }
                    items(ui.days, key = { it.day }) { d ->
                        DayCard(
                            order = ui.orders?.let { o -> o.days[d.day.toString()] },
                            everyDay = ui.orders?.everyDay,
                            onOrder = { vm.saveOrder(day = d.day, order = it) },
                            schemes = ui.options?.schemes.orEmpty(),
                            onScheme = { pw, ex, sc -> vm.setScheme(plannedWorkoutId = pw, poolExerciseId = ex, scheme = sc) },
                            day = d,
                            isToday = d.day == todayDow,
                            others = ui.days.filter { it.day != d.day && it.workouts.isNotEmpty() },
                            onCopyFrom = { from -> vm.copyDay(from, d.day) },
                            onRemove = { id -> vm.remove(id) },
                            onMarkRest = { vm.markRest(d.day) },
                            onAdd = { addingDay = d.day },
                        )
                    }
                }
            }

            val opts = ui.options
            val ad = addingDay
            if (ad != null && opts != null) {
                AddWorkoutSheet(
                    day = ad,
                    options = opts,
                    busy = ui.busy,
                    onAddPool = { vm.addPool(it); addingDay = null },
                    onAddHiit = { hid -> vm.addHiit(ad, hid); addingDay = null },
                    onMarkRest = { vm.markRest(ad); addingDay = null },
                    onDismiss = { addingDay = null },
                )
            }
        }
    }
}

@Composable
private fun DayCard(
    /** This day's own order, or null for every day's; [everyDay] null = an older server. */
    order: List<String>?,
    everyDay: List<String>?,
    onOrder: (List<String>?) -> Unit,
    schemes: List<com.kairos.app.data.remote.dto.SchemeChoiceDto>,
    onScheme: (String, String, String) -> Unit,
    day: PlanDayDto,
    isToday: Boolean,
    others: List<PlanDayDto>,
    onCopyFrom: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onMarkRest: () -> Unit,
    onAdd: () -> Unit,
) {
    val hasRest = day.workouts.any { it.isRest }
    var pendingRemove by remember { mutableStateOf<Pair<String, String>?>(null) }
    var ordering by remember { mutableStateOf(false) }
    val dayOrder = order ?: everyDay ?: PLAN_KINDS
    // Listed the way the day shows them: by section, in this day's order.
    val listed = day.workouts.sortedBy { w -> if (w.isRest) -1 else dayOrder.indexOf(w.kind.ifBlank { "STRENGTH" }) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(DAY_NAMES[day.day], style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (isToday) {
                Text(
                    "  today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box(Modifier.weight(1f))
            if (everyDay != null && day.workouts.any { !it.isRest }) {
                Text(
                    if (order != null) "Own order" else "Order",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { ordering = !ordering }.padding(4.dp),
                )
            }
            if (others.isNotEmpty()) {
                CopyFromMenu(others, onCopyFrom)
            }
        }

        if (ordering && everyDay != null) {
            Text(
                "${DAY_NAMES[day.day].uppercase()} ONLY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OrderList(order = dayOrder, onSave = { onOrder(it) })
            if (order != null) {
                TextButton(onClick = { onOrder(null) }) { Text("Use the order of every day") }
            }
        }

        listed.forEach { w ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val kindLabel = KIND_LABELS[w.kind]?.takeIf { w.kind != "STRENGTH" && it != w.name }
                    Text(
                        if (kindLabel != null) "${w.name}  ·  $kindLabel" else w.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val withSchemes = schemes.isNotEmpty() && w.exercises.any { it.weights }
                    if (withSchemes) {
                        w.exercises.forEach { e ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (e.tracked) e.name else "${e.name} (no log)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (e.weights) {
                                    SchemeMenu(
                                        schemes = schemes,
                                        selected = e.scheme,
                                        onPick = { onScheme(w.id, e.poolExerciseId, it) },
                                    )
                                }
                            }
                        }
                    } else if (w.detail.isNotBlank()) {
                        Text(w.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { pendingRemove = w.id to w.name }) {
                    Icon(KairosIcons.Trash, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onAdd) { Text("Add workout") }
            if (!hasRest) {
                TextButton(onClick = onMarkRest) { Text("Mark rest") }
            }
        }
    }

    pendingRemove?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove workout?") },
            text = { Text("Remove \"$name\" from ${DAY_NAMES[day.day]}?") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(id)
                    pendingRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Cancel") }
            },
        )
    }
}

/** The sections a day can hold, in the default order (the web's blocks.ts). */
private val PLAN_KINDS = listOf("WARMUP", "CARDIO", "STRENGTH", "CONDITIONING", "STRETCH", "SPORT")

private val KIND_LABELS = mapOf(
    "WARMUP" to "Warm-up",
    "CARDIO" to "Cardio",
    "STRENGTH" to "Strength (each muscle group)",
    "CONDITIONING" to "Conditioning",
    "STRETCH" to "Stretching",
    "SPORT" to "Sport",
)

/** "Order of the day": every day's section order, opened to change it. */
@Composable
private fun EveryDayOrder(order: List<String>, onSave: (List<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.fillMaxWidth().clickable { open = !open }) {
            Text("Order of the day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                order.joinToString(" → ") { (KIND_LABELS[it] ?: it).removeSuffix(" (each muscle group)") },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) OrderList(order = order, onSave = onSave)
    }
}

/** The sections in order, each with up and down. */
@Composable
private fun OrderList(order: List<String>, onSave: (List<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        order.forEachIndexed { i, k ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    KIND_LABELS[k] ?: k,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                TextButton(
                    onClick = { onSave(order.toMutableList().also { it[i] = order[i - 1]; it[i - 1] = k }) },
                    enabled = i > 0,
                ) { Text("↑") }
                TextButton(
                    onClick = { onSave(order.toMutableList().also { it[i] = order[i + 1]; it[i + 1] = k }) },
                    enabled = i < order.size - 1,
                ) { Text("↓") }
            }
        }
    }
}

/** A weights movement's set structure: Any sets, 3 × 5, 5/3/1 wave… */
@Composable
private fun SchemeMenu(
    schemes: List<com.kairos.app.data.remote.dto.SchemeChoiceDto>,
    selected: String,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            (schemes.firstOrNull { it.key == selected }?.label ?: "Any sets") + "  \u203A",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { open = true }.padding(horizontal = 6.dp, vertical = 4.dp),
        )
        if (open) {
            com.kairos.app.ui.common.OptionPickerOverlay(
                title = "Sets",
                options = schemes.map { it.key to it.label },
                selectedKey = selected,
                onPick = onPick,
                onClose = { open = false },
            )
        }
    }
}

@Composable
private fun CopyFromMenu(others: List<PlanDayDto>, onCopyFrom: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "Copy from…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { open = true }.padding(4.dp),
        )
        if (open) {
            com.kairos.app.ui.common.OptionPickerOverlay(
                title = "Copy from",
                options = others.map { it.day.toString() to DAY_NAMES[it.day] },
                selectedKey = null,
                onPick = { onCopyFrom(it.toInt()) },
                onClose = { open = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDatePick(iso: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = planIsoToMillis(iso))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(state.selectedDateMillis?.let { planMillisToIso(it) } ?: iso)
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

private fun planIsoToMillis(iso: String): Long =
    try {
        LocalDate.parse(iso, DateTimeFormatter.ISO_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (_: Exception) {
        Instant.now().toEpochMilli()
    }

private fun planMillisToIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_DATE)

