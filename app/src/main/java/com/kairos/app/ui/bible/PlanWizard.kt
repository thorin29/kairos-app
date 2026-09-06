package com.kairos.app.ui.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kairos.app.data.remote.dto.PersonalPlanDto
import com.kairos.app.data.remote.dto.PlanPreviewDto
import com.kairos.app.ui.nav.KairosIcons
import kotlin.math.roundToInt

private enum class PaceMode { CHAPTERS, FINISH }

/**
 * A 3-step plan builder: pick + reorder books, choose the pace (chapters/day or
 * finish-by-date) and start date, then preview the generated schedule and create.
 */
@Composable
fun PlanWizard(
    vm: BibleViewModel,
    existingPlan: PersonalPlanDto?,
    today: String,
    busy: Boolean,
    onDone: () -> Unit,
) {
    var step by remember { mutableStateOf(1) }
    val picked = remember { mutableStateListOf<String>() }
    var name by remember { mutableStateOf(existingPlan?.name?.ifBlank { null } ?: "My reading plan") }
    var startISO by remember { mutableStateOf(today) }
    var mode by remember { mutableStateOf(PaceMode.CHAPTERS) }
    var cpd by remember { mutableStateOf(1) }
    var endISO by remember { mutableStateOf(today) }

    val endParam = if (mode == PaceMode.FINISH) endISO else null

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Step $step of 3 \u00b7 " + when (step) {
                1 -> "Books"
                2 -> "Pace"
                else -> "Preview"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        when (step) {
            1 -> BooksStep(picked, name) { name = it }
            2 -> PaceStep(startISO, { startISO = it }, mode, { mode = it }, cpd, { cpd = it }, endISO, { endISO = it }, today)
            else -> PreviewStep(vm, picked.toList(), startISO, cpd, endParam)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 1) {
                OutlinedButton(onClick = { step-- }) { Text("Back") }
            }
            Spacer(Modifier.weight(1f))
            if (step < 3) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BooksStep(picked: MutableList<String>, name: String, onName: (String) -> Unit) {
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text("Plan name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text("Pick books", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BIBLE_BOOKS.forEach { b ->
            val on = b.name in picked
            Text(
                b.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { if (on) picked.remove(b.name) else picked.add(b.name) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    if (picked.isNotEmpty()) {
        Text(
            "Order \u00b7 drag to rearrange",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReorderableBooks(picked)
    }
}

@Composable
private fun ReorderableBooks(books: MutableList<String>) {
    val density = LocalDensity.current
    val rowH = 46.dp
    val rowHpx = with(density) { rowH.toPx() }
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column {
        books.forEachIndexed { index, book ->
            val dragging = index == dragIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowH)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (dragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    KairosIcons.DragHandle,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp).pointerInput(book) {
                        detectDragGestures(
                            onDragStart = { dragIndex = index; dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val target = (dragIndex + (dragOffset / rowHpx).roundToInt())
                                    .coerceIn(0, books.size - 1)
                                if (target != dragIndex && dragIndex in books.indices) {
                                    books.add(target, books.removeAt(dragIndex))
                                    dragOffset -= (target - dragIndex) * rowHpx
                                    dragIndex = target
                                }
                            },
                            onDragEnd = { dragIndex = -1; dragOffset = 0f },
                            onDragCancel = { dragIndex = -1; dragOffset = 0f },
                        )
                    },
                )
                Spacer(Modifier.width(10.dp))
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
    startISO: String,
    onStart: (String) -> Unit,
    mode: PaceMode,
    onMode: (PaceMode) -> Unit,
    cpd: Int,
    onCpd: (Int) -> Unit,
    endISO: String,
    onEnd: (String) -> Unit,
    today: String,
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
    if (mode == PaceMode.FINISH) {
        DateRow("End date", endISO) { pickEnd = true }
    }

    if (pickStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(startISO))
        DatePickerDialog(
            onDismissRequest = { pickStart = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onStart(utcMillisToIso(it)) }
                    pickStart = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickStart = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    if (pickEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(endISO))
        DatePickerDialog(
            onDismissRequest = { pickEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onEnd(utcMillisToIso(it)) }
                    pickEnd = false
                }) { Text("OK") }
            },
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
private fun PreviewStep(
    vm: BibleViewModel,
    books: List<String>,
    startISO: String,
    cpd: Int,
    endISO: String?,
) {
    var loading by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf<PlanPreviewDto?>(null) }

    LaunchedEffect(books, startISO, cpd, endISO) {
        loading = true
        preview = vm.previewPlan(books, startISO, cpd, endISO)
        loading = false
    }

    if (loading) {
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val p = preview ?: run {
        Text("Couldn't build a preview. Go back and check the selection.", color = MaterialTheme.colorScheme.error)
        return
    }

    Text(
        "${p.dayCount} days \u00b7 ${p.totalChapters} chapters",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    p.endISO?.let {
        Text(
            "Runs ${formatShortISO(p.startISO ?: startISO)} \u2192 ${formatShortISO(it)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    LazyColumn(
        Modifier.fillMaxWidth().height(320.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(p.days) { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    formatShortISO(d.iso),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(84.dp),
                )
                Text(d.passage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}
