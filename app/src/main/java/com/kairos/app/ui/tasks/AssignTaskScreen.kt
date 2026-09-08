package com.kairos.app.ui.tasks

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import com.kairos.app.ui.common.RollPicker
import com.kairos.app.ui.common.rememberContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
private val NICE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignTaskScreen(parentEntry: NavBackStackEntry?, onClose: () -> Unit) {
    val container = rememberContainer()
    val owner = parentEntry ?: LocalViewModelStoreOwner.current!!
    val vm: TasksViewModel = viewModel(
        viewModelStoreOwner = owner,
        factory = viewModelFactory { initializer { TasksViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()
    val data = ui.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assign a task") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { inner ->
        if (data == null) {
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        var personId by remember(data.canActFor) { mutableStateOf(data.canActFor.firstOrNull()?.id ?: data.meId) }
        var title by remember { mutableStateOf("") }
        var hasDue by remember { mutableStateOf(false) }
        var dueDate by remember { mutableStateOf(LocalDate.now().toString()) }
        var showDate by remember { mutableStateOf(false) }

        val personName = data.canActFor.firstOrNull { it.id == personId }?.name ?: ""
        val dueLabel = try { LocalDate.parse(dueDate).format(NICE) } catch (_: Exception) { dueDate }

        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (data.canActFor.size > 1) {
                        RollPicker("For", personName, data.canActFor.map { it.id to it.name }, { personId = it }, !ui.busy, Modifier.fillMaxWidth())
                    }
                    Labeled("Task") { Field(title, { title = it.take(120) }, "e.g. Wash the car") }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Due date", style = MaterialTheme.typography.bodyLarge)
                            Text("Optional \u2014 otherwise it's due today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = hasDue, onCheckedChange = { hasDue = it })
                    }
                    if (hasDue) {
                        Box(
                            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                .clickable { showDate = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(dueLabel, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Button(
                onClick = { vm.add(personId, title.trim(), if (hasDue) dueDate else null) { onClose() } },
                enabled = !ui.busy && personId.isNotBlank() && title.trim().length >= 2,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Assign") }
        }

        if (showDate) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = try {
                    LocalDate.parse(dueDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                },
            )
            DatePickerDialog(
                onDismissRequest = { showDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { ms ->
                            dueDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().format(ISO)
                        }
                        showDate = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
            ) {
                DatePicker(state = state)
            }
        }
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String) {
    Box(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            },
        )
    }
}
