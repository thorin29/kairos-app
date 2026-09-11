package com.kairos.app.ui.school

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.ApprovalActionRequest
import com.kairos.app.data.remote.dto.ClassOptionDto
import com.kairos.app.data.remote.dto.PendingSubjectDto
import com.kairos.app.data.remote.dto.PendingTermDto
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.rememberContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolApprovalsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: SchoolApprovalsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SchoolApprovalsViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Class approvals") },
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
                ui.terms.isEmpty() && ui.subjects.isEmpty() -> Text(
                    "Nothing waiting for approval.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ui.error?.let {
                        item(key = "err") {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (ui.terms.isNotEmpty()) {
                        item(key = "th") { SectionHeader("Semesters") }
                        items(ui.terms, key = { it.id }) { t ->
                            PendingTermCard(t, ui.allTerms, ui.busyId == t.id) { vm.submit(it) }
                        }
                    }
                    if (ui.subjects.isNotEmpty()) {
                        item(key = "sh") { SectionHeader("Subjects") }
                        items(ui.subjects, key = { it.id }) { sub ->
                            PendingSubjectCard(sub, ui.allSubjects, ui.busyId == sub.id) { vm.submit(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun PendingSubjectCard(
    subject: PendingSubjectDto,
    others: List<ClassOptionDto>,
    busy: Boolean,
    onSubmit: (ApprovalActionRequest) -> Unit,
) {
    var name by remember(subject.id) { mutableStateOf(subject.name) }
    var showMerge by remember(subject.id) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            ProposedBy(subject.proposedBy)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(8.dp))
            ActionRow(
                approveEnabled = !busy && name.trim().length >= 2,
                busy = busy,
                onApprove = {
                    onSubmit(ApprovalActionRequest(kind = "subject", op = "approve", id = subject.id, name = name.trim()))
                },
                canMerge = others.isNotEmpty(),
                onMerge = { showMerge = true },
            )
        }
    }
    if (showMerge) {
        MergePicker("Merge into which subject?", others, onDismiss = { showMerge = false }) { targetId ->
            showMerge = false
            onSubmit(ApprovalActionRequest(kind = "subject", op = "merge", id = subject.id, targetId = targetId))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingTermCard(
    term: PendingTermDto,
    others: List<ClassOptionDto>,
    busy: Boolean,
    onSubmit: (ApprovalActionRequest) -> Unit,
) {
    var name by remember(term.id) { mutableStateOf(term.name) }
    var start by remember(term.id) { mutableStateOf(term.startISO) }
    var end by remember(term.id) { mutableStateOf(term.endISO) }
    var pickStart by remember(term.id) { mutableStateOf(false) }
    var pickEnd by remember(term.id) { mutableStateOf(false) }
    var showMerge by remember(term.id) { mutableStateOf(false) }
    val datesOk = isoOk(start) && isoOk(end) && end >= start
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            ProposedBy(term.proposedBy)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.weight(1f)) {
                    Text(start.ifBlank { "Start date" })
                }
                OutlinedButton(onClick = { pickEnd = true }, modifier = Modifier.weight(1f)) {
                    Text(end.ifBlank { "End date" })
                }
            }
            if (!datesOk) {
                Text("Set a start and end date.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.size(8.dp))
            ActionRow(
                approveEnabled = !busy && name.trim().length >= 2 && datesOk,
                busy = busy,
                onApprove = {
                    onSubmit(
                        ApprovalActionRequest(
                            kind = "term", op = "approve", id = term.id,
                            name = name.trim(), start = start, end = end,
                        ),
                    )
                },
                canMerge = others.isNotEmpty(),
                onMerge = { showMerge = true },
            )
        }
    }
    if (pickStart) DatePick(start, { start = it; pickStart = false }, { pickStart = false })
    if (pickEnd) DatePick(end, { end = it; pickEnd = false }, { pickEnd = false })
    if (showMerge) {
        MergePicker("Merge into which semester?", others, onDismiss = { showMerge = false }) { targetId ->
            showMerge = false
            onSubmit(ApprovalActionRequest(kind = "term", op = "merge", id = term.id, targetId = targetId))
        }
    }
}

@Composable
private fun ProposedBy(who: String?) {
    Text(
        "Proposed" + if (!who.isNullOrBlank()) " by $who" else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun ActionRow(
    approveEnabled: Boolean,
    busy: Boolean,
    onApprove: () -> Unit,
    canMerge: Boolean,
    onMerge: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onApprove, enabled = approveEnabled) {
            if (busy) {
                CircularProgressIndicator(Modifier.width(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text("Approve")
        }
        if (canMerge) {
            TextButton(onClick = onMerge, enabled = !busy) { Text("Merge\u2026") }
        }
    }
}

@Composable
private fun MergePicker(
    title: String,
    options: List<ClassOptionDto>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = title,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column {
            options.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(o.id) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(o.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePick(iso: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = isoToMillis(iso))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.selectedDateMillis?.let { millisToIso(it) } ?: iso) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

private fun isoOk(s: String): Boolean = Regex("""\d{4}-\d{2}-\d{2}""").matches(s)

private fun isoToMillis(iso: String): Long =
    try { LocalDate.parse(iso, DateTimeFormatter.ISO_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    catch (_: Exception) { Instant.now().toEpochMilli() }

private fun millisToIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_DATE)
