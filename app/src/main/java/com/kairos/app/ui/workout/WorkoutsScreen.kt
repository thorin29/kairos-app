package com.kairos.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer

/**
 * The Workouts section page. TODAY (planned-workout logging) + RECENT history.
 * The progress graph lands next; the history + series read already backs it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(onOpenDrawer: () -> Unit) {
    val container = rememberContainer()
    val vm: WorkoutLogViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WorkoutLogViewModel(container.sessionRepository, null) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var history by remember { mutableStateOf<List<WorkoutHistoryDto>>(emptyList()) }
    // Reload history whenever a workout is saved/marked (savedTick) and on first show.
    LaunchedEffect(ui.savedTick) {
        runCatching { container.sessionRepository.loadWorkoutProgress() }
            .getOrNull()?.let { history = it.history }
        if (ui.savedTick > 0) snackbar.showSnackbar("Workout saved")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workouts") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ui.loadError != null -> Text(
                    ui.loadError!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    workoutLogItems(ui, vm)

                    if (history.isNotEmpty()) {
                        item(key = "recentHeader") {
                            Text(
                                "Recent workouts",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                        items(history, key = { "h-${it.id}" }) { h ->
                            HistoryRow(h)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(h: WorkoutHistoryDto) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(h.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (h.result.isNotBlank()) {
                Text(
                    h.result,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            shortDate(h.date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "2026-09-02" -> "9/2". */
private fun shortDate(iso: String): String = try {
    val p = iso.split("-")
    "${p[1].toInt()}/${p[2].toInt()}"
} catch (e: Exception) {
    iso
}
