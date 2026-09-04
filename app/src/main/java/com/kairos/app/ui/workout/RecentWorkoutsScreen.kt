package com.kairos.app.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.WorkoutHistoryDto
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentWorkoutsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: RecentWorkoutsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RecentWorkoutsViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editMode by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WorkoutHistoryDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recent workouts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (ui.history.isNotEmpty()) {
                        TextButton(onClick = {
                            editMode = !editMode
                            pendingDelete = null
                        }) {
                            Text(if (editMode) "Done" else "Edit")
                        }
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ui.error != null && ui.history.isEmpty() -> Text(
                    ui.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                ui.history.isEmpty() -> Text(
                    "No workouts logged yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.history, key = { it.id }) { h ->
                        RecentRow(h, editMode, ui.deletingIds.contains(h.id)) { pendingDelete = h }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this workout?") },
            text = { Text("${target.label}${if (target.result.isNotBlank()) " · ${target.result}" else ""} on ${shortDate(target.date)}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(target.id)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RecentRow(
    h: WorkoutHistoryDto,
    editMode: Boolean,
    deleting: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            shortDate(h.date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = 56.dp, height = 20.dp),
        )
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(h.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (h.result.isNotBlank()) {
                Text(
                    h.result,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            deleting -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            editMode -> IconButton(onClick = onDelete) {
                Icon(
                    KairosIcons.Trash,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun shortDate(iso: String): String = try {
    val p = iso.split("-")
    "${p[1].toInt()}/${p[2].toInt()}"
} catch (e: Exception) {
    iso
}
