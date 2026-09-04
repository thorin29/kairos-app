package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.PlanDayDto
import com.kairos.app.data.remote.dto.PlanWorkoutDto
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.launch
import java.time.LocalDate

private val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlanScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: EditPlanViewModel = viewModel(
        factory = viewModelFactory { initializer { EditPlanViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val todayDow = remember { LocalDate.now().dayOfWeek.value % 7 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ui.error != null && ui.days.isEmpty() -> Text(
                    ui.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(ui.days, key = { it.day }) { d ->
                        DayCard(
                            day = d,
                            isToday = d.day == todayDow,
                            others = ui.days.filter { it.day != d.day && it.workouts.isNotEmpty() },
                            onCopyFrom = { from -> vm.copyDay(from, d.day) },
                            onRemove = { id -> vm.remove(id) },
                            onMarkRest = { vm.markRest(d.day) },
                            onAdd = { scope.launch { snackbar.showSnackbar("Adding workouts is coming next") } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCard(
    day: PlanDayDto,
    isToday: Boolean,
    others: List<PlanDayDto>,
    onCopyFrom: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onMarkRest: () -> Unit,
    onAdd: () -> Unit,
) {
    val hasRest = day.workouts.any { it.isRest }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(DAY_NAMES[day.day], style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (isToday) {
                Text(
                    "  today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box(Modifier.weight(1f))
            if (others.isNotEmpty()) {
                CopyFromMenu(others, onCopyFrom)
            }
        }

        day.workouts.forEach { w ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(w.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (w.detail.isNotBlank()) {
                        Text(w.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { onRemove(w.id) }) {
                    Icon(KairosIcons.Trash, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onAdd) { Text("Add workout") }
            if (!hasRest) {
                TextButton(onClick = onMarkRest) { Text("Mark rest") }
            }
        }
    }
}

@Composable
private fun CopyFromMenu(others: List<PlanDayDto>, onCopyFrom: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "Copy from…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { open = true }.padding(4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            others.forEach { o ->
                DropdownMenuItem(
                    text = { Text(DAY_NAMES[o.day]) },
                    onClick = { onCopyFrom(o.day); open = false },
                )
            }
        }
    }
}
