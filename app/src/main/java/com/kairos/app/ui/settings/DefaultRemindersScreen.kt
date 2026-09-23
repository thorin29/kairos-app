package com.kairos.app.ui.settings
import com.kairos.app.ui.nav.KairosIcons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.data.settings.ReminderDefaults
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultRemindersScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val defaults by container.settingsStore.reminderDefaults.collectAsState(initial = emptyMap())
    val scope = rememberCoroutineScope()
    var editingKind by remember { mutableStateOf<String?>(null) }
    var readingLead by remember { mutableStateOf(0) }
    var editingReading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            readingLead = container.sessionRepository.loadReadingReminder().leadDays
        } catch (_: Exception) {}
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Default reminders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "New events start with these reminders based on their type. Existing events aren't changed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            ReminderDefaults.KINDS.forEach { (kind, label) ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val value = ReminderDefaults.effective(defaults, kind)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingKind = kind }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        ReminderDefaults.label(value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        KairosIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { editingReading = true }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Reading goals", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    readingLeadLabel(readingLead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    KairosIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editingKind?.let { kind ->
        val current = ReminderDefaults.effective(defaults, kind)
        val kindLabel = ReminderDefaults.KINDS.firstOrNull { it.first == kind }?.second ?: "Reminder"
        AlertDialog(
            onDismissRequest = { editingKind = null },
            title = { Text(kindLabel) },
            text = {
                Column {
                    ReminderDefaults.PRESETS.forEach { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { container.settingsStore.setReminderDefault(kind, m) }
                                    editingKind = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current == m, onClick = null)
                            Text(
                                ReminderDefaults.label(m),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingKind = null }) { Text("Close") }
            },
        )
    }


    if (editingReading) {
        val presets = listOf(0, 1, 3, 7, 14, 30, 60)
        AlertDialog(
            onDismissRequest = { editingReading = false },
            title = { Text("Reading goals") },
            text = {
                Column {
                    Text(
                        "How far ahead the next reading goal appears on the reading button. Off shows only the current goal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    presets.forEach { d ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        try {
                                            readingLead = container.sessionRepository.setReadingReminder(d).leadDays
                                        } catch (_: Exception) {}
                                    }
                                    editingReading = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = readingLead == d, onClick = null)
                            Text(
                                readingLeadLabel(d),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingReading = false }) { Text("Close") }
            },
        )
    }
}

private fun readingLeadLabel(days: Int): String = when {
    days <= 0 -> "Off"
    days % 30 == 0 -> "${days / 30} month${if (days / 30 > 1) "s" else ""} before"
    days % 7 == 0 -> "${days / 7} week${if (days / 7 > 1) "s" else ""} before"
    days == 1 -> "1 day before"
    else -> "$days days before"
}
