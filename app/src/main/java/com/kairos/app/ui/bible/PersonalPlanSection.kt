package com.kairos.app.ui.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.PersonalPlanDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Composable
fun PersonalPlanSection(
    vm: BibleViewModel,
    plan: PersonalPlanDto?,
    today: String,
    busy: Boolean,
    actionError: String?,
) {
    var replacing by remember { mutableStateOf(false) }

    if (plan != null && !replacing) {
        PlanView(
            vm = vm,
            plan = plan,
            today = today,
            busy = busy,
            onReplace = { replacing = true },
        )
    } else {
        PlanCreator(
            vm = vm,
            today = today,
            busy = busy,
            err = actionError,
            onCancel = if (plan != null) ({ replacing = false }) else null,
            onCreated = { replacing = false },
        )
    }
}

@Composable
private fun PlanView(
    vm: BibleViewModel,
    plan: PersonalPlanDto,
    today: String,
    busy: Boolean,
    onReplace: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(plan.name, fontWeight = FontWeight.Medium)
                    Text(
                        if (plan.remaining > 0) "${plan.remaining} days left" else "Plan complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    TextButton(onClick = onReplace) { Text("New plan") }
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = !busy,
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            plan.days.forEach { d ->
                val isToday = d.iso == today
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .clickable(enabled = !busy) { vm.markDay(d.passage, !d.read) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = d.read,
                        onCheckedChange = { checked -> vm.markDay(d.passage, checked) },
                        enabled = !busy,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.passage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (d.read) FontWeight.Normal else FontWeight.Medium,
                            color = if (d.read) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (d.read) TextDecoration.LineThrough else null,
                        )
                        Text(
                            d.label + if (isToday) " \u00b7 today" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete plan?") },
            text = { Text("Delete this reading plan? Your read chapters stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deletePlan()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlanCreator(
    vm: BibleViewModel,
    today: String,
    busy: Boolean,
    err: String?,
    onCancel: (() -> Unit)?,
    onCreated: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var startISO by remember { mutableStateOf(today) }
    var cpd by remember { mutableStateOf("3") }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDate by remember { mutableStateOf(false) }

    fun toggle(book: String) {
        picked = picked.toMutableSet().apply { if (!add(book)) remove(book) }
    }

    fun pickWhere(pred: (Testament) -> Boolean, on: Boolean) {
        picked = picked.toMutableSet().apply {
            BIBLE_BOOKS.forEach { if (pred(it.testament)) { if (on) add(it.name) else remove(it.name) } }
        }
    }

    val chapters = chapterCountFor(picked)
    val cpdNum = cpd.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val estDays = if (chapters > 0) ceil(chapters.toDouble() / cpdNum).toInt() else 0

    Box(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Create a reading plan", fontWeight = FontWeight.Medium)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Plan name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickChip("Whole Bible") { pickWhere({ true }, true) }
                QuickChip("Old Testament") { pickWhere({ it == Testament.OT }, true) }
                QuickChip("New Testament") { pickWhere({ it == Testament.NT }, true) }
                QuickChip("Clear") { picked = emptySet() }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BIBLE_BOOKS.forEach { b ->
                        val on = b.name in picked
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                .then(
                                    if (on) Modifier
                                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp)),
                                )
                                .clickable { toggle(b.name) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                b.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) androidx.compose.ui.graphics.Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("Start", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .clickable { showDate = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(startISO, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Column {
                    Text("Chapters/day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = cpd,
                        onValueChange = { v -> cpd = v.filter { it.isDigit() }.take(2) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp),
                    )
                }
            }

            if (chapters > 0) {
                Text(
                    "$chapters chapters \u00b7 ~$estDays days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (err != null) {
                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        vm.createPlan(name, picked.toList(), startISO, cpdNum, onCreated)
                    },
                    enabled = !busy,
                ) {
                    Text(if (busy) "Building\u2026" else "Create plan")
                }
                if (onCancel != null) {
                    TextButton(onClick = { vm.clearActionError(); onCancel() }) { Text("Cancel") }
                }
            }
        }
    }

    if (showDate) {
        val initMillis = remember(startISO) { isoToUtcMillis(startISO) }
        val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { startISO = utcMillisToIso(it) }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

private fun isoToUtcMillis(iso: String): Long =
    try {
        LocalDate.parse(iso, ISO).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (_: Exception) {
        Instant.now().toEpochMilli()
    }

private fun utcMillisToIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(ISO)
