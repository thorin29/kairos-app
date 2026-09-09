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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.kairos.app.ui.theme.KairosThemeState
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
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import com.kairos.app.ui.common.SentenceCaps


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolScreen(onOpenDrawer: () -> Unit, onOpenAdd: () -> Unit, refreshKey: Int = 0) {
    val container = rememberContainer()
    val vm: SchoolViewModel = viewModel(
        factory = viewModelFactory { initializer { SchoolViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()
    androidx.compose.runtime.LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }

    var editMode by remember { mutableStateOf(false) }
    val edited = remember { mutableStateMapOf<String, String>() }
    var deleting by remember { mutableStateOf<SchoolItemDto?>(null) }

    val data = ui.data
    val myItems = data?.people?.firstOrNull { it.id == data.meId }?.items ?: emptyList()
    val canEdit = myItems.isNotEmpty()
    if (!canEdit && editMode) editMode = false

    fun enterEdit() {
        edited.clear()
        myItems.forEach { edited[it.id] = it.title }
        editMode = true
    }
    fun saveEdits() {
        val changes = myItems
            .mapNotNull { it2 ->
                val n = edited[it2.id]?.trim()
                if (n != null && n.length >= 2 && n != it2.title) it2.id to n else null
            }
            .toMap()
        if (changes.isNotEmpty()) vm.applyRenames(changes)
        editMode = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("School") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
                actions = {
                    if (editMode) {
                        TextButton(onClick = { saveEdits() }) { Text("Done") }
                    } else {
                        if (canEdit) {
                            IconButton(onClick = { enterEdit() }) {
                                Icon(KairosIcons.Pencil, "Edit my work", modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(onClick = onOpenAdd) {
                            Icon(KairosIcons.Plus, "Add school work", modifier = Modifier.size(22.dp))
                        }
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.error ?: "Couldn't load school.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> SchoolContent(data, ui, vm, editMode, edited, onDeleteRequest = { deleting = it })
            }
        }
    }

    deleting?.let { item ->
        AnimatedDialog(
            onDismissRequest = { deleting = null },
            title = "Delete item?",
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(enabled = !ui.busy, onClick = { vm.delete(item.id); deleting = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
        ) {
            Text("Remove \u201c${item.title}\u201d?", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SchoolContent(
    data: SchoolDto,
    ui: SchoolUiState,
    vm: SchoolViewModel,
    editMode: Boolean,
    edited: SnapshotStateMap<String, String>,
    onDeleteRequest: (SchoolItemDto) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ui.message?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF047857), modifier = Modifier.fillMaxWidth())
        }

        val withWork = data.people.filter { it.items.isNotEmpty() || it.classes.isNotEmpty() }
        if (withWork.isEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text(
                    "Nothing due right now. Add work with the + button.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        withWork.forEach { p ->
            val isOwn = p.id == data.meId
            PersonSchoolCard(p, isOwn, editMode && isOwn, edited, ui.busy, { vm.complete(it) }, onDeleteRequest)
        }

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
        color = if (selected) KairosThemeState.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .border(1.dp, if (selected) KairosThemeState.accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun PersonSchoolCard(
    p: SchoolPersonDto,
    isOwn: Boolean,
    editMode: Boolean,
    edited: SnapshotStateMap<String, String>,
    busy: Boolean,
    onComplete: (String) -> Unit,
    onDeleteRequest: (SchoolItemDto) -> Unit,
) {
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
                        items.forEach { it2 ->
                            ItemRow(it2, isOwn, editMode, edited, busy, onComplete, onDeleteRequest)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: SchoolItemDto,
    isOwn: Boolean,
    editMode: Boolean,
    edited: SnapshotStateMap<String, String>,
    busy: Boolean,
    onComplete: (String) -> Unit,
    onDeleteRequest: (SchoolItemDto) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Own work outside edit mode: tap the circle to mark it done.
        if (isOwn && !editMode) {
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(999.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                    .clickable(enabled = !busy) { onComplete(item.id) },
            )
        }
        Column(Modifier.weight(1f)) {
            if (isOwn && editMode) {
                NameField(edited[item.id] ?: item.title) { edited[item.id] = it.take(120) }
            } else {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Text(
                "${item.typeLabel} \u00b7 due ${item.dueISO}",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isOwn && editMode) {
            Box(Modifier.clip(RoundedCornerShape(999.dp)).clickable(enabled = !busy) { onDeleteRequest(item) }.padding(6.dp)) {
                Icon(KairosIcons.Trash, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            keyboardOptions = SentenceCaps,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
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
