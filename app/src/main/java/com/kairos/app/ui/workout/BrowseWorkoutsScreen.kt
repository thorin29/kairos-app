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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.BrowseWorkoutDto
import com.kairos.app.ui.common.rememberContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseWorkoutsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: BrowseWorkoutsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BrowseWorkoutsViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    var heroOnly by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<BrowseWorkoutDto?>(null) }
    var shareFor by remember { mutableStateOf<BrowseWorkoutDto?>(null) }
    var deleteFor by remember { mutableStateOf<BrowseWorkoutDto?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Browse workouts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                ui.error != null -> Text(
                    ui.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> {
                    val shared = ui.items.filter { it.heroWod == heroOnly && !it.personal }
                    val personal = if (heroOnly) emptyList() else ui.items.filter { it.personal }
                    LazyColumn(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                FilterPill("Workouts", !heroOnly, Modifier.weight(1f)) { heroOnly = false }
                                FilterPill("Hero WODs", heroOnly, Modifier.weight(1f)) { heroOnly = true }
                            }
                        }
                        if (shared.isEmpty() && personal.isEmpty()) {
                            item {
                                Text(
                                    if (heroOnly) "No Hero WODs yet." else "No named workouts yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        if (shared.isNotEmpty()) {
                            items(shared, key = { it.id }) { w -> WorkoutCard(w) }
                        }
                        if (personal.isNotEmpty()) {
                            item { SectionHeader("Personal") }
                            items(personal, key = { it.id }) { w ->
                                WorkoutCard(w) { menuFor = w }
                            }
                        }
                    }
                }
            }
        }
    }

    // Tapping a personal workout: Share / Delete.
    menuFor?.let { w ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            confirmButton = {},
            title = { Text(w.name) },
            text = {
                Column {
                    TextButton(onClick = { menuFor = null; shareFor = w }, modifier = Modifier.fillMaxWidth()) {
                        Text("Share with someone", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { menuFor = null; deleteFor = w }, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
        )
    }

    // People picker for sharing.
    shareFor?.let { w ->
        var target by remember(w.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { shareFor = null },
            confirmButton = {
                TextButton(
                    enabled = target != null,
                    onClick = { target?.let { t -> vm.share(w.id, t) { shareFor = null } } },
                ) { Text("Share") }
            },
            dismissButton = { TextButton(onClick = { shareFor = null }) { Text("Cancel") } },
            title = { Text("Share \"${w.name}\"") },
            text = {
                if (ui.people.isEmpty()) {
                    Text("No one else to share with yet.")
                } else {
                    Column {
                        ui.people.forEach { p ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { target = p.id },
                            ) {
                                RadioButton(selected = target == p.id, onClick = { target = p.id })
                                Text(p.name)
                            }
                        }
                    }
                }
            },
        )
    }

    // Delete confirm.
    deleteFor?.let { w ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            confirmButton = {
                TextButton(onClick = { vm.delete(w.id); deleteFor = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Cancel") } },
            title = { Text("Delete \"${w.name}\"?") },
            text = { Text("This removes it from your workouts. Copies you've shared are unaffected.") },
        )
    }

    if (ui.shared) {
        AlertDialog(
            onDismissRequest = { vm.clearShared() },
            confirmButton = { TextButton(onClick = { vm.clearShared() }) { Text("OK") } },
            title = { Text("Shared") },
            text = { Text("They now have their own copy.") },
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun FilterPill(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                1.dp,
                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun WorkoutCard(w: BrowseWorkoutDto, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(w.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (w.typeLabel.isNotBlank()) {
                Text(
                    w.typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        if (w.detail.isNotBlank()) {
            Text(
                w.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
