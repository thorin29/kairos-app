package com.kairos.app.ui.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.kairos.app.ui.common.AnimatedDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.kairos.app.ui.theme.KairosThemeState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kairos.app.data.remote.dto.PersonalPlanDayDto
import com.kairos.app.data.remote.dto.PersonalPlanDto
import com.kairos.app.data.remote.dto.PlanPreviewDto
import com.kairos.app.ui.nav.KairosIcons
import kotlin.math.roundToInt

private enum class PaceMode { CHAPTERS, FINISH }

private fun booksFromDays(days: List<PersonalPlanDayDto>): List<String> {
    val names = BIBLE_BOOKS.map { it.name }.sortedByDescending { it.length }
    val order = LinkedHashSet<String>()
    days.forEach { d -> names.firstOrNull { d.passage.startsWith(it) }?.let { order.add(it) } }
    return order.toList()
}

@Composable
fun PlanWizard(
    vm: BibleViewModel,
    existingPlan: PersonalPlanDto?,
    today: String,
    busy: Boolean,
    onDone: () -> Unit,
) {
    var editing by remember { mutableStateOf(existingPlan == null) }

    if (existingPlan != null && !editing) {
        PlanOverview(vm, existingPlan, busy, onEdit = { editing = true }, onDeleted = onDone)
        return
    }

    var step by remember { mutableStateOf(1) }
    val picked = remember {
        mutableStateListOf<String>().apply { if (existingPlan != null) addAll(booksFromDays(existingPlan.days)) }
    }
    var name by remember { mutableStateOf(existingPlan?.name?.ifBlank { null } ?: "My reading plan") }
    var startISO by remember { mutableStateOf(today) }
    var mode by remember { mutableStateOf(PaceMode.CHAPTERS) }
    var cpd by remember { mutableStateOf(1) }
    var endISO by remember { mutableStateOf(today) }
    val endParam = if (mode == PaceMode.FINISH) endISO else null

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Step $step of 4 \u00b7 " + when (step) {
                1 -> "Books"; 2 -> "Order"; 3 -> "Pace"; else -> "Preview"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        when (step) {
            1 -> BooksStep(picked, name) { name = it }
            2 -> ReorderStep(picked)
            3 -> PaceStep(startISO, { startISO = it }, mode, { mode = it }, cpd, { cpd = it }, endISO, { endISO = it })
            else -> PreviewStep(vm, picked.toList(), startISO, cpd, endParam)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 1) OutlinedButton(onClick = { step-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            if (step < 4) {
                Button(onClick = { step++ }, enabled = step != 1 || picked.isNotEmpty()) { Text("Next") }
            } else {
                Button(
                    onClick = { vm.createPlan(name.trim(), picked.toList(), startISO, cpd, endParam, onDone) },
                    enabled = !busy && picked.isNotEmpty(),
                ) { Text("Create plan") }
            }
        }
    }
}

@Composable
private fun PlanOverview(
    vm: BibleViewModel,
    plan: PersonalPlanDto,
    busy: Boolean,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(plan.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            if (plan.remaining > 0) "${plan.remaining} days left" else "Plan complete",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit plan") }
            OutlinedButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirm) {
        AnimatedDialog(
            onDismissRequest = { confirm = false },
            title = "Delete this plan?",
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.deletePlan(); onDeleted() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        ) {
            Text("Your reading plan will be removed. Your logged progress stays.")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BooksStep(picked: MutableList<String>, name: String, onName: (String) -> Unit) {
    fun add(pred: (BibleBook) -> Boolean) {
        BIBLE_BOOKS.filter(pred).forEach { if (it.name !in picked) picked.add(it.name) }
    }

    OutlinedTextField(
        value = name, onValueChange = onName, label = { Text("Plan name") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BulkChip("Whole Bible", enabled = true) { picked.clear(); picked.addAll(BIBLE_BOOKS.map { it.name }) }
        BulkChip("Whole Old Testament", enabled = true) { add { it.testament == Testament.OT } }
        BulkChip("Whole New Testament", enabled = true) { add { it.testament == Testament.NT } }
    }

    BIBLE_GROUPS.forEach { group ->
        val gColor = GROUP_COLOR[group] ?: KairosThemeState.accent
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(gColor))
                Text(group.uppercase(), style = MaterialTheme.typography.labelSmall, color = gColor, fontWeight = FontWeight.SemiBold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BIBLE_BOOKS.filter { it.group == group }.forEach { b ->
                    val on = b.name in picked
                    BookChip(b.name, if (on) BookState.COMPLETE else BookState.NONE, gColor, enabled = true) {
                        if (on) picked.remove(b.name) else picked.add(b.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderStep(books: MutableList<String>) {
    if (books.isEmpty()) {
        Text("Go back and pick some books first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text("Drag the handles to set the reading order.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    val density = LocalDensity.current
    val rowH = 52.dp
    val rowHpx = with(density) { rowH.toPx() }
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column {
        books.forEachIndexed { index, book ->
            val dragging = index == dragIndex
            // Others slide to make room, but the list is only reordered on drop
            // (mutating mid-drag was what got items stuck).
            val shift = if (dragIndex >= 0 && !dragging) {
                val to = (dragIndex + (dragOffset / rowHpx).roundToInt()).coerceIn(0, books.size - 1)
                when {
                    dragIndex < to && index in (dragIndex + 1)..to -> -rowHpx
                    dragIndex > to && index in to until dragIndex -> rowHpx
                    else -> 0f
                }
            } else {
                0f
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowH)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else shift }
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (dragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    KairosIcons.DragHandle,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp).pointerInput(book) {
                        detectDragGestures(
                            onDragStart = { dragIndex = index; dragOffset = 0f },
                            onDrag = { change, amount -> change.consume(); dragOffset += amount.y },
                            onDragEnd = {
                                val to = (dragIndex + (dragOffset / rowHpx).roundToInt()).coerceIn(0, books.size - 1)
                                if (to != dragIndex && dragIndex in books.indices) {
                                    books.add(to, books.removeAt(dragIndex))
                                }
                                dragIndex = -1; dragOffset = 0f
                            },
                            onDragCancel = { dragIndex = -1; dragOffset = 0f },
                        )
                    },
                )
                Spacer(Modifier.width(12.dp))
                Text(book, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { books.remove(book) }) {
                    Icon(KairosIcons.Trash, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaceStep(
    startISO: String, onStart: (String) -> Unit,
    mode: PaceMode, onMode: (PaceMode) -> Unit,
    cpd: Int, onCpd: (Int) -> Unit,
    endISO: String, onEnd: (String) -> Unit,
) {
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    DateRow("Start date", startISO) { pickStart = true }
    Text("Pace", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Row(Modifier.fillMaxWidth().clickable { onMode(PaceMode.CHAPTERS) }, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = mode == PaceMode.CHAPTERS, onClick = { onMode(PaceMode.CHAPTERS) })
        Text("Chapters per day", modifier = Modifier.weight(1f))
    }
    if (mode == PaceMode.CHAPTERS) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onCpd((cpd - 1).coerceAtLeast(1)) }) { Text("\u2212") }
            Text("$cpd", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { onCpd((cpd + 1).coerceAtMost(50)) }) { Text("+") }
            Text("chapters/day", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Row(Modifier.fillMaxWidth().clickable { onMode(PaceMode.FINISH) }, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = mode == PaceMode.FINISH, onClick = { onMode(PaceMode.FINISH) })
        Text("Finish by a date", modifier = Modifier.weight(1f))
    }
    if (mode == PaceMode.FINISH) DateRow("End date", endISO) { pickEnd = true }

    if (pickStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(startISO))
        DatePickerDialog(
            onDismissRequest = { pickStart = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onStart(utcMillisToIso(it)) }; pickStart = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickStart = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    if (pickEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(endISO))
        DatePickerDialog(
            onDismissRequest = { pickEnd = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onEnd(utcMillisToIso(it)) }; pickEnd = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickEnd = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun DateRow(label: String, iso: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(formatShortISO(iso), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PreviewStep(vm: BibleViewModel, books: List<String>, startISO: String, cpd: Int, endISO: String?) {
    var loading by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf<PlanPreviewDto?>(null) }

    LaunchedEffect(books, startISO, cpd, endISO) {
        loading = true
        preview = vm.previewPlan(books, startISO, cpd, endISO)
        loading = false
    }

    if (loading) {
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        return
    }
    val p = preview ?: run {
        Text("Couldn't build a preview. Go back and check the selection.", color = MaterialTheme.colorScheme.error)
        return
    }
    Text("${p.dayCount} days \u00b7 ${p.totalChapters} chapters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    p.endISO?.let {
        Text(
            "Runs ${formatShortISO(p.startISO ?: startISO)} \u2192 ${formatShortISO(it)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    LazyColumn(Modifier.fillMaxWidth().height(320.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(p.days) { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(formatShortISO(d.iso), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(84.dp))
                Text(d.passage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}
