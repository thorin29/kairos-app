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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.kairos.app.ui.common.AnimatedDialog

/** The old route (v0.501): the same card on a page of its own. Settings now
 *  opens Notifications & reminders, which holds [DefaultRemindersCard]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultRemindersScreen(onBack: () -> Unit) {
    Scaffold(
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
                .padding(16.dp),
        ) { DefaultRemindersCard() }
    }
}

/**
 * The default reminder for each event type and the reading-goal lead, as one
 * card of rows (v0.501), each opening a small picker.
 */
@Composable
fun DefaultRemindersCard() {
    val container = rememberContainer()
    val defaults by container.settingsStore.reminderDefaults.collectAsState(initial = emptyMap())
    val scope = rememberCoroutineScope()
    var editingKind by remember { mutableStateOf<String?>(null) }
    var readingLead by remember { mutableStateOf(0) }
    var editingReading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Show the last-known value even offline, instead of defaulting to "Off".
        container.settingsStore.readingReminderLead.first().let { if (it >= 0) readingLead = it }
        try {
            val v = container.sessionRepository.loadReadingReminder().leadDays
            readingLead = v
            container.settingsStore.setReadingReminderLead(v)
        } catch (_: Exception) {}
    }

    androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            ReminderDefaults.KINDS.forEachIndexed { i, (kind, label) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
        AnimatedDialog(
            onDismissRequest = { editingKind = null },
            title = kindLabel,
            confirmButton = {
                TextButton(onClick = { editingKind = null }) { Text("Close") }
            },
        ) {
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
        }
    }


    if (editingReading) {
        val presets = listOf(0, 1, 3, 7, 14, 30, 60)
        AnimatedDialog(
            onDismissRequest = { editingReading = false },
            title = "Reading goals",
            confirmButton = {
                TextButton(onClick = { editingReading = false }) { Text("Close") }
            },
        ) {
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
                                        container.settingsStore.setReadingReminderLead(readingLead)
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
        }
    }
}

private fun readingLeadLabel(days: Int): String = when {
    days <= 0 -> "Off"
    days % 30 == 0 -> "${days / 30} month${if (days / 30 > 1) "s" else ""} before"
    days % 7 == 0 -> "${days / 7} week${if (days / 7 > 1) "s" else ""} before"
    days == 1 -> "1 day before"
    else -> "$days days before"
}
