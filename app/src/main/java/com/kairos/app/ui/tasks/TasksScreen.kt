package com.kairos.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.TaskDoneDto
import com.kairos.app.data.remote.dto.TaskUserGroupDto
import com.kairos.app.data.remote.dto.TaskOpenDto
import com.kairos.app.data.remote.dto.TasksListDto
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(onOpenDrawer: () -> Unit, onOpenAssign: () -> Unit, refreshKey: Int = 0) {
    val container = rememberContainer()
    val vm: TasksViewModel = viewModel(
        factory = viewModelFactory { initializer { TasksViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()

    androidx.compose.runtime.LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
                actions = {
                    TextButton(onClick = { vm.toggleCompleted() }) {
                        Text(if (ui.showCompleted) "Hide done" else "Show done")
                    }
                    IconButton(onClick = onOpenAssign) {
                        Icon(KairosIcons.Plus, "Assign a task", modifier = Modifier.size(22.dp))
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val data = ui.data
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.error ?: "Couldn't load tasks.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> TasksContent(data, ui, vm)
            }
        }
    }
}

@Composable
private fun TasksContent(data: TasksListDto, ui: TasksUiState, vm: TasksViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ui.message?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF047857), modifier = Modifier.fillMaxWidth())
        }

        val anything = data.groups.any { it.open.isNotEmpty() || (ui.showCompleted && it.done.isNotEmpty()) }
        if (!anything) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text(
                    "No tasks right now. Assign one with the + button.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        data.groups.forEach { g ->
            val isOwn = g.userId == data.meId
            val visibleDone = if (ui.showCompleted) g.done else emptyList()
            if (g.open.isEmpty() && visibleDone.isEmpty()) return@forEach
            GroupCard(g, isOwn, visibleDone, ui.busy, { vm.complete(it) }, { vm.uncomplete(it) })
        }
    }
}

@Composable
private fun GroupCard(
    g: TaskUserGroupDto,
    isOwn: Boolean,
    doneShown: List<TaskDoneDto>,
    busy: Boolean,
    onComplete: (String) -> Unit,
    onUncomplete: (String) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(999.dp)).background(parseColor(g.color)))
                Text(g.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${g.open.size} open",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            g.open.forEach { t -> OpenRow(t, isOwn, busy, onComplete) }
            doneShown.forEach { t -> DoneRow(t, isOwn, busy, onUncomplete) }
        }
    }
}

@Composable
private fun OpenRow(t: TaskOpenDto, isOwn: Boolean, busy: Boolean, onComplete: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isOwn) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(999.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                    .clickable(enabled = !busy) { onComplete(t.id) },
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (t.recurring) {
                    Icon(
                        KairosIcons.Repeat,
                        contentDescription = "Repeats",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Text(
                "due ${t.dueISO}",
                style = MaterialTheme.typography.labelSmall,
                color = if (t.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DoneRow(t: TaskDoneDto, isOwn: Boolean, busy: Boolean, onUncomplete: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isOwn) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF10B981))
                    .clickable(enabled = !busy) { onUncomplete(t.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(KairosIcons.Check, contentDescription = "Undo", tint = Color.White, modifier = Modifier.size(13.dp))
            }
        } else {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF10B981)), contentAlignment = Alignment.Center) {
                Icon(KairosIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (t.recurring) {
                Icon(
                    KairosIcons.Repeat,
                    contentDescription = "Repeats",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                t.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
            )
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
