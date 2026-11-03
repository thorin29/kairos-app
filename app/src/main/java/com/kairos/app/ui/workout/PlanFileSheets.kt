package com.kairos.app.ui.workout

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.ImportPreviewDto
import java.time.LocalDate

/**
 * Household plans, Share this plan, Export and Import (the web's
 * PlanFilePanel), each in its own sheet. Export saves through the system's
 * file picker; Import reads a file the same way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanFileActions(vm: EditPlanViewModel, ui: EditPlanUiState) {
    val context = LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            vm.exportTo(history = history) { text ->
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.readImport {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }
        }
    }
    val show: (String) -> Unit = { which ->
        vm.clearFile()
        open = which
        if (which == "plans") vm.loadSharedPlans()
    }
    val close = {
        open = null
        vm.clearFile()
    }

    // One button at the foot of the plan (v0.432): save, share, export and
    // import in a sheet. Opening a saved or household plan is with the programs.
    OutlinedButton(onClick = { show("menu") }, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Icon(com.kairos.app.ui.nav.KairosIcons.Share, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Save, share or export")
    }

    when (open) {
        "menu" -> FileSheet(title = "This plan", onDismiss = close) {
            listOf(
                Triple("mine", com.kairos.app.ui.nav.KairosIcons.Bookmark, "Save to My plans"),
                Triple("share", com.kairos.app.ui.nav.KairosIcons.Share, "Share with the household"),
                Triple("export", com.kairos.app.ui.nav.KairosIcons.Download, "Export a file"),
                Triple("import", com.kairos.app.ui.nav.KairosIcons.Upload, "Import a file"),
            ).forEach { (key, icon, label) ->
                val shape = RoundedCornerShape(12.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                        .clickable { show(key) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    androidx.compose.material3.Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        "mine" -> FileSheet(title = "Save to My plans", onDismiss = close) {
            var name by remember { mutableStateOf("") }
            if (ui.fileMessage != null) {
                FileStatus(ui = ui)
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FileStatus(ui = ui)
                Button(
                    onClick = { vm.savePlan(name.trim()) },
                    enabled = !ui.fileBusy && name.trim().length >= 2,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }
        }
        "export" -> FileSheet(title = "Export", onDismiss = close) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { history = !history }) {
                Checkbox(checked = history, onCheckedChange = { history = it })
                Text("Include everything I've logged", style = MaterialTheme.typography.bodyMedium)
            }
            FileStatus(ui = ui)
            Button(
                onClick = { saveLauncher.launch("kairos-workouts-${LocalDate.now()}.json") },
                enabled = !ui.fileBusy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save file") }
        }
        "import" -> FileSheet(title = "Import", onDismiss = close) {
            val p = ui.preview
            when {
                ui.fileMessage != null -> FileStatus(ui = ui)
                p != null -> ImportChoice(preview = p, busy = ui.fileBusy, onPick = { vm.applyPreview(it) })
                else -> {
                    FileStatus(ui = ui)
                    Button(
                        onClick = { openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        enabled = !ui.fileBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Choose a file") }
                }
            }
            if (p != null) FileStatus(ui = ui)
        }
        "share" -> FileSheet(title = "Share with the household", onDismiss = close) {
            var name by remember { mutableStateOf("") }
            if (ui.fileMessage != null) {
                FileStatus(ui = ui)
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FileStatus(ui = ui)
                Button(
                    onClick = { vm.sharePlan(name.trim()) },
                    enabled = !ui.fileBusy && name.trim().length >= 2,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Share") }
            }
        }
        "plans" -> {
            val p = ui.preview
            FileSheet(
                title = if (ui.previewPlanId != null) ui.sharedPlans?.firstOrNull { it.id == ui.previewPlanId }?.name ?: "Household plans" else "Household plans",
                onDismiss = close,
            ) {
                when {
                    ui.fileMessage != null -> FileStatus(ui = ui)
                    p != null -> {
                        TextButton(onClick = { vm.clearFile() }) { Text("‹ Back") }
                        ImportChoice(preview = p, busy = ui.fileBusy, onPick = { vm.applyPreview(it) })
                        FileStatus(ui = ui)
                    }
                    ui.sharedPlans == null -> {
                        FileStatus(ui = ui)
                        if (ui.fileError == null) CircularProgressIndicator(Modifier.padding(8.dp))
                    }
                    ui.sharedPlans.isEmpty() -> Text(
                        "No household plans yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        ui.sharedPlans.forEach { sp ->
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                                    .clickable(enabled = sp.status == "APPROVED") { vm.previewSharedPlan(sp.id) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        sp.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        listOf(sp.by, sp.summary).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (sp.status == "PENDING") {
                                    Text(
                                        "Waiting",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(onClick = { vm.withdrawSharedPlan(sp.id) }) { Text("Withdraw") }
                                }
                            }
                        }
                        FileStatus(ui = ui)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun FileStatus(ui: EditPlanUiState) {
    ui.fileMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    ui.fileError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

/** What bringing a plan in would do, and Replace / Add. */
@Composable
private fun ImportChoice(preview: ImportPreviewDto, busy: Boolean, onPick: (String) -> Unit) {
    var confirmReplace by remember { mutableStateOf(false) }
    val adds = listOfNotNull(
        preview.newMovements.size.takeIf { it > 0 }?.let { "$it new ${if (it == 1) "movement" else "movements"}" },
        preview.newWorkouts.size.takeIf { it > 0 }?.let { "$it new ${if (it == 1) "workout" else "workouts"}" },
    )
    Text(
        preview.summary.ifBlank { "No plan, workouts only." },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (adds.isNotEmpty()) {
        Text(
            "Adds ${adds.joinToString(" and ")} of your own.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (preview.newWorkouts.isNotEmpty()) {
        Text(
            preview.newWorkouts.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (preview.entries > 0) {
            Button(onClick = { confirmReplace = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Replace my plan")
            }
        }
        OutlinedButton(onClick = { onPick("add") }, enabled = !busy) {
            Text(if (preview.entries > 0) "Add to my plan" else "Add the workouts")
        }
    }
    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace your plan?") },
            text = { Text("Your whole weekly plan is replaced with this one. A running program ends.") },
            confirmButton = {
                TextButton(onClick = { confirmReplace = false; onPick("replace") }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }
}
