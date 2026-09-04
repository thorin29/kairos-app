package com.kairos.app.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.CategoryBarDto
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.data.remote.dto.TaskDto
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(person: PersonDto, onOpenDrawer: () -> Unit, onLogWorkout: (String) -> Unit) {
    val container = rememberContainer()
    val vm: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Reload the day whenever Home is (re)shown — e.g. returning from logging a
    // workout — so it reflects changes made on other screens.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(ui.actionError) {
        ui.actionError?.let {
            snackbar.showSnackbar(it)
            vm.clearActionError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Box(
            Modifier
                .padding(inner)
                .fillMaxSize(),
        ) {
            when {
                ui.loading && ui.dashboard == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                ui.dashboard == null -> ErrorState(ui.loadError, onRetry = vm::load)
                else -> DashboardContent(person, ui, vm)
            }

            ui.workoutSheet?.let { WorkoutSheet(it, vm, onLogWorkout) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutSheet(task: TaskDto, vm: HomeViewModel, onLogWorkout: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = vm::dismissWorkout, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(task.title, style = MaterialTheme.typography.titleLarge)

            Button(
                onClick = {
                    vm.dismissWorkout()
                    onLogWorkout(task.dueDate)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Log workout") }

            when (task.status) {
                "COMPLETE" -> {
                    OutlinedButton(
                        onClick = { vm.undoWorkout(task) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Mark as not done") }
                }
                "SKIPPED" -> {
                    Text(
                        "Marked as a rest day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { vm.markWorkoutDone(task) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Mark as done") }
                }
                else -> {
                    OutlinedButton(
                        onClick = { vm.markWorkoutDone(task) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Mark as done") }
                    OutlinedButton(
                        onClick = { vm.restDay(task) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Rest day") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(person: PersonDto, ui: HomeUiState, vm: HomeViewModel) {
    val d = ui.dashboard!!
    PullToRefreshBox(
        isRefreshing = ui.refreshing,
        onRefresh = vm::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { HeaderCard(person.name, d.percent) }

            if (d.categories.isNotEmpty()) {
                item(key = "bars") { CategoryBars(d.categories) }
            }

            if (d.overdue.isNotEmpty()) {
                item(key = "h-overdue") { SectionHeader("Overdue") }
                items(d.overdue, key = { it.id }) { task ->
                    TaskRow(task, ui.busyIds.contains(task.id), vm)
                }
            }

            d.groups.forEach { group ->
                item(key = "h-${group.category}") { SectionHeader(group.label) }
                items(group.items, key = { it.id }) { task ->
                    TaskRow(task, ui.busyIds.contains(task.id), vm)
                }
                if (group.category == "BIBLE" && d.personalReading != null) {
                    item(key = "personal-reading") {
                        PersonalReadingRow(d.personalReading, ui.busyIds.contains("personal-reading"), vm)
                    }
                }
            }

            // A personal reading with no family Bible group still gets a Bible section.
            if (d.personalReading != null && d.groups.none { it.category == "BIBLE" }) {
                item(key = "h-BIBLE-personal") { SectionHeader("Bible reading") }
                item(key = "personal-reading") {
                    PersonalReadingRow(d.personalReading, ui.busyIds.contains("personal-reading"), vm)
                }
            }

            if (d.overdue.isEmpty() && d.groups.isEmpty() && d.personalReading == null) {
                item(key = "empty") { EmptyDay() }
            }

            if (d.upForGrabs.isNotEmpty()) {
                item(key = "h-grabs") { SectionHeader("Up for grabs") }
                items(d.upForGrabs, key = { "grab-${it.id}" }) { c ->
                    UpForGrabsRow(c, ui.busyIds.contains("claim-${c.id}"), vm)
                }
            }

            if (d.alwaysOpen.isNotEmpty()) {
                item(key = "h-always") { SectionHeader("Always open") }
                items(d.alwaysOpen, key = { "ao-${it.id}" }) { c ->
                    AlwaysOpenRow(c, ui.busyIds.contains("always-${c.id}"), vm)
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(name: String, percent: Int?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Hi, $name", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))
            if (percent == null) {
                Text(
                    "Nothing scheduled yet today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "$percent% done today",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(8.dp))
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CategoryBars(bars: List<CategoryBarDto>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            bars.forEach { bar ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            bar.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (bar.overdue > 0) {
                            Text(
                                "${bar.overdue} late",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Text(
                            "${bar.complete}/${bar.total}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(6.dp))
                    LinearProgressIndicator(
                        progress = { if (bar.total > 0) bar.complete / bar.total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun TaskRow(task: TaskDto, busy: Boolean, vm: HomeViewModel) {
    val done = task.status == "COMPLETE"
    // Workout prompts open the action sheet; ordinary completable rows toggle.
    val tappable = !busy && (task.isWorkout || task.completable)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tappable) {
                if (task.isWorkout) vm.openWorkout(task) else vm.toggle(task.id, done)
            }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                done -> Icon(
                    Icons.Filled.Check,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.primary,
                )
                task.isWorkout -> HollowMarker()
                task.completable -> Checkbox(checked = false, onCheckedChange = null)
                else -> HollowMarker()
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done || task.stale) TextDecoration.LineThrough else null,
                color = if (done || task.stale) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            val secondary = buildSecondary(task)
            if (secondary != null) {
                Text(
                    secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun UpForGrabsRow(c: com.kairos.app.data.remote.dto.UpForGrabsDto, busy: Boolean, vm: HomeViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(if (c.isShared) "shared chore" else "released from ${c.releasedByName}")
                    if (c.isOverdue) append(" \u00b7 due ${shortDate(c.dueDate)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (c.isOverdue) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = { vm.claimChore(c.id) }, enabled = !busy) {
            Text(if (busy) "Taking\u2026" else "Take it")
        }
    }
}

@Composable
private fun AlwaysOpenRow(c: com.kairos.app.data.remote.dto.AlwaysOpenDashDto, busy: Boolean, vm: HomeViewModel) {
    val onCooldown = c.readyAtMs != null && c.readyAtMs > System.currentTimeMillis()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.bodyLarge)
            if (c.myCount > 0) {
                Text(
                    "done ${c.myCount}\u00d7 today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = { vm.completeAlwaysOpen(c.id) }, enabled = !busy && !onCooldown) {
            Text(if (busy) "\u2026" else if (onCooldown) "Not back yet" else "Done")
        }
    }
}

@Composable
private fun PersonalReadingRow(reading: com.kairos.app.data.remote.dto.PersonalReadingDto, busy: Boolean, vm: HomeViewModel) {
    val done = reading.read
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) { vm.togglePersonalReading(reading.passage, done) }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                done -> Icon(
                    Icons.Filled.Check,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.primary,
                )
                else -> Checkbox(checked = false, onCheckedChange = null)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Personal bible reading",
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                reading.passage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A small hollow circle used where a checkbox would be, for non-toggle rows. */
@Composable
private fun HollowMarker() {
    Box(
        Modifier
            .size(18.dp)
            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

/** Secondary line: school detail, workout state, and/or an overdue due-date. */
private fun buildSecondary(task: TaskDto): String? {
    val parts = mutableListOf<String>()
    task.subtitle?.let { parts += it }
    if (task.stale) parts += "expired"
    if (task.isWorkout) {
        when (task.status) {
            "SKIPPED" -> parts += "rest day"
            "COMPLETE" -> {} // strike-through already conveys done
            else -> parts += "tap to log"
        }
    }
    if (task.isOverdue) parts += "due ${shortDate(task.dueDate)}"
    return parts.joinToString(" · ").ifBlank { null }
}

/** "2026-09-02" -> "9/2". Falls back to the raw string if it can't parse. */
private fun shortDate(iso: String): String {
    val p = iso.split("-")
    return if (p.size == 3) "${p[1].toIntOrNull() ?: p[1]}/${p[2].toIntOrNull() ?: p[2]}" else iso
}

@Composable
private fun EmptyDay() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "All clear — nothing left today.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorState(message: String?, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message ?: "Couldn't load your day.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(16.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}
