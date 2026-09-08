package com.kairos.app.ui.school

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.SchoolDto
import com.kairos.app.data.remote.dto.SchoolItemDto
import com.kairos.app.data.remote.dto.SchoolPersonDto
import com.kairos.app.data.remote.dto.SchoolProgressDto
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.RollPicker
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ACCENT = Color(0xFF0F5C63)
private val DUE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d")

private data class DueOption(val iso: String, val label: String)

private fun dueOptions(): List<DueOption> {
    val today = LocalDate.now()
    return (0..44).map { n ->
        val d = today.plusDays(n.toLong())
        val label = when (n) {
            0 -> "Today"
            1 -> "Tomorrow"
            else -> d.format(DUE_FMT)
        }
        DueOption(d.toString(), label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolScreen(onOpenDrawer: () -> Unit) {
    val container = rememberContainer()
    val vm: SchoolViewModel = viewModel(
        factory = viewModelFactory { initializer { SchoolViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("School") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val data = ui.data
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.error ?: "Couldn't load school.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> SchoolContent(data, ui, vm)
            }
        }
    }
}

@Composable
private fun SchoolContent(data: SchoolDto, ui: SchoolUiState, vm: SchoolViewModel) {
    val canActForSet = remember(data) { data.canActFor.map { it.id }.toSet() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AddWorkCard(data, ui.busy) { u, t, ty, d, s, c -> vm.add(u, t, ty, d, s, c) }

        ui.message?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF047857), modifier = Modifier.fillMaxWidth())
        }

        val withWork = data.people.filter { it.items.isNotEmpty() || it.classes.isNotEmpty() }
        if (withWork.isEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text(
                    "Nothing due right now. Assignments and tests show here as they're added.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        withWork.forEach { p ->
            PersonSchoolCard(p, canActForSet.contains(p.id), ui.busy, { vm.complete(it) }, { vm.delete(it) })
        }

        // Progress
        Text("Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val activeTerm = ui.term ?: data.selectedTermId
        TermPills(data, activeTerm) { vm.setTerm(it) }
        if (data.progress.isEmpty()) {
            Text("No completed or due work in this range yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        data.progress.forEach { pr -> ProgressCard(pr) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TermPills(data: SchoolDto, active: String?, onSelect: (String?) -> Unit) {
    if (data.terms.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.terms.forEach { t -> Pill(t.name, active == t.id) { onSelect(t.id) } }
        Pill("All time", active == null) { onSelect("all") }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) ACCENT else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .border(1.dp, if (selected) ACCENT else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun PersonSchoolCard(p: SchoolPersonDto, canAct: Boolean, busy: Boolean, onComplete: (String) -> Unit, onDelete: (String) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(999.dp)).background(parseColor(p.color)))
                Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (p.pending == 0) "all caught up" else "${p.pending} open" + if (p.overdue > 0) " \u00b7 ${p.overdue} late" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (p.overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (p.classes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    p.classes.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(9.dp).clip(RoundedCornerShape(999.dp)).background(parseColor(c.color)))
                            Text(c.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (!c.meeting.isNullOrBlank()) {
                                Text(c.meeting, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }

            if (p.items.isNotEmpty()) {
                val groups = remember(p.items) { p.items.groupBy { it.className ?: "Other work" } }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { (name, items) ->
                        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        items.forEach { it2 -> ItemRow(it2, canAct, busy, onComplete, onDelete) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(it: SchoolItemDto, canAct: Boolean, busy: Boolean, onComplete: (String) -> Unit, onDelete: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canAct) {
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(999.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                    .clickable(enabled = !busy) { onComplete(it.id) },
            )
        }
        Column(Modifier.weight(1f)) {
            Text(it.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "${it.typeLabel} \u00b7 due ${it.dueISO}",
                style = MaterialTheme.typography.labelSmall,
                color = if (it.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canAct) {
            Box(Modifier.clip(RoundedCornerShape(999.dp)).clickable(enabled = !busy) { onDelete(it.id) }.padding(6.dp)) {
                Icon(KairosIcons.Trash, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ProgressCard(pr: SchoolProgressDto) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(999.dp)).background(parseColor(pr.color)))
                Text(pr.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${pr.pct}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }
            Text(
                "${pr.completed} of ${pr.total} done \u00b7 ${pr.onTime} on time" + if (pr.overdue > 0) " \u00b7 ${pr.overdue} overdue" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            pr.byClass.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(parseColor(c.color)))
                    Text(c.key, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${c.completed}/${c.total}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AddWorkCard(data: SchoolDto, busy: Boolean, onAdd: (String, String, String, String, String?, String?) -> Unit) {
    val dates = remember { dueOptions() }
    var personId by remember(data.canActFor) { mutableStateOf(data.canActFor.firstOrNull()?.id ?: "") }
    var title by remember { mutableStateOf("") }
    var typeKey by remember(data.types) { mutableStateOf(data.types.firstOrNull { it.key == "ASSIGNMENT" }?.key ?: data.types.firstOrNull()?.key ?: "ASSIGNMENT") }
    var classId by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var dueIso by remember { mutableStateOf(dates.first().iso) }

    val personName = data.canActFor.firstOrNull { it.id == personId }?.name ?: ""
    val typeLabel = data.types.firstOrNull { it.key == typeKey }?.label ?: ""
    val classes = data.classOptionsByUser[personId] ?: emptyList()
    val classLabel = if (classId.isBlank()) "No class" else classes.firstOrNull { it.id == classId }?.name ?: "No class"
    val dueLabel = dates.firstOrNull { it.iso == dueIso }?.label ?: dueIso

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add school work", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            if (data.canActFor.size > 1) {
                RollPicker("For", personName, data.canActFor.map { it.id to it.name }, { personId = it; classId = "" }, !busy, Modifier.fillMaxWidth())
            }
            Field(title, { title = it.take(120) }, "Title (e.g. Chapter 4 quiz)")
            RollPicker("Type", typeLabel, data.types.map { it.key to it.label }, { typeKey = it }, !busy, Modifier.fillMaxWidth())
            RollPicker(
                "Class",
                classLabel,
                listOf("" to "No class") + classes.map { it.id to it.name },
                { classId = it },
                !busy,
                Modifier.fillMaxWidth(),
            )
            Field(subject, { subject = it.take(60) }, "Subject (optional)")
            RollPicker("Due", dueLabel, dates.map { it.iso to it.label }, { dueIso = it }, !busy, Modifier.fillMaxWidth())

            OutlinedButton(
                onClick = {
                    onAdd(personId, title.trim(), typeKey, dueIso, subject.trim().ifBlank { null }, classId.ifBlank { null })
                    title = ""; subject = ""
                },
                enabled = !busy && personId.isNotBlank() && title.trim().length >= 2,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add") }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            },
        )
    }
}

private fun parseColor(hex: String?): Color {
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
