package com.kairos.app.ui.workout

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.data.remote.dto.ProgressSeriesDto
import com.kairos.app.data.remote.dto.WorkoutProgressDto
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.launch

/**
 * The Workouts page (launcher). TODAY = the day's plan + Edit plan / Log workout
 * / Rest·skip, then Browse workouts + Weight calculator, then a "Recent
 * workouts" link to its own page. This Week (sports) lands next;
 * Edit plan / Browse / Weight calculator are stubs for now with the same look.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    onOpenDrawer: () -> Unit,
    onLogWorkout: (String) -> Unit,
    onOpenRecent: () -> Unit,
    onOpenCalculator: () -> Unit,
) {
    val container = rememberContainer()
    val vm: WorkoutLogViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WorkoutLogViewModel(container.sessionRepository, null) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<WorkoutProgressDto?>(null) }

    LaunchedEffect(ui.savedTick) {
        runCatching { container.sessionRepository.loadWorkoutProgress() }
            .getOrNull()?.let { progress = it }
        if (ui.savedTick > 0) snackbar.showSnackbar("Updated")
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
            if (ui.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    progress?.takeIf { it.series.isNotEmpty() }?.let { p ->
                        Text(
                            "Progress",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        WorkoutChart(p.series, p.defaultId)
                    }

                    Text(
                        "TODAY",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            ui.planName ?: "No workout planned",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(
                            KairosIcons.Calendar, "Edit plan", Modifier.weight(1f),
                        ) { scope.launch { snackbar.showSnackbar("Edit plan is coming soon") } }
                        ActionCard(
                            KairosIcons.Dumbbell, "Log workout", Modifier.weight(1f),
                            highlighted = true,
                            enabled = ui.date != null,
                        ) { ui.date?.let(onLogWorkout) }
                        ActionCard(
                            KairosIcons.Moon, "Rest / skip", Modifier.weight(1f),
                            enabled = !ui.saving,
                        ) { vm.restDay() }
                    }

                    WideButton(KairosIcons.Book, "Browse workouts") {
                        scope.launch { snackbar.showSnackbar("Browse workouts is coming soon") }
                    }
                    WideButton(KairosIcons.Dumbbell, "Weight calculator") { onOpenCalculator() }

                    TextButton(onClick = onOpenRecent, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Recent workouts  →")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint =
        if (highlighted) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(88.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun WideButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(0.dp))
        Text("  $label")
    }
}
