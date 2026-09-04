package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.RotationDto
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons

private val SHORT_DAY = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: RotationViewModel = viewModel(
        factory = viewModelFactory { initializer { RotationViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rotation") },
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
                ui.rotation?.active != true -> StartRotation(onStart = vm::start, enabled = !ui.busy)
                else -> RotationBody(ui.rotation!!, ui.busy, ui.error, vm)
            }
        }
    }
}

@Composable
private fun StartRotation(onStart: () -> Unit, enabled: Boolean) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Rotation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Put yourself on a repeating cycle of workouts (e.g. Chest, Legs, Push) instead of a fixed weekly plan. Fixed rest weekdays pause the cycle, so a weekend never costs your place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onStart, enabled = enabled) { Text("Start a rotation") }
    }
}

@Composable
private fun RotationBody(r: RotationDto, busy: Boolean, error: String?, vm: RotationViewModel) {
    var name by remember { mutableStateOf("") }
    var rest by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Next 10 days", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            r.preview.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shortDate(p.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(width = 56.dp, height = 18.dp),
                    )
                    Text(
                        p.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (p.rest) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (p.rest) FontWeight.Normal else FontWeight.Medium,
                    )
                }
            }
        }

        Text("Rest weekdays (pause the cycle)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (dow in 0..6) {
                val on = (r.restMask and (1 shl dow)) != 0
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !busy) { vm.toggleRestDay(dow) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        SHORT_DAY[dow],
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text("Cycle", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (r.slots.isEmpty()) {
            Text(
                "No slots yet. Add workouts below to build the cycle.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        r.slots.forEachIndexed { i, s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${i + 1}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(s.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.moveSlot(s.id, -1) }, enabled = i > 0 && !busy) {
                    Icon(KairosIcons.ChevronDown, contentDescription = "Move up", modifier = Modifier.size(18.dp).rotate(180f))
                }
                IconButton(onClick = { vm.moveSlot(s.id, 1) }, enabled = i < r.slots.size - 1 && !busy) {
                    Icon(KairosIcons.ChevronDown, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { vm.removeSlot(s.id) }, enabled = !busy) {
                    Icon(KairosIcons.Trash, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }

        Text("Add to the cycle", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (e.g. Chest, Legs, Push)") },
            singleLine = true,
            enabled = !rest,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rest, onCheckedChange = { rest = it })
            Text("Rest slot (advances the cycle)", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { vm.addSlot(name.trim(), rest); name = ""; rest = false },
            enabled = !busy && (rest || name.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add slot") }

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedButton(onClick = vm::stop, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Stop rotation (back to weekly plan)")
        }
    }
}

private fun shortDate(iso: String): String = try {
    val p = iso.split("-"); "${p[1].toInt()}/${p[2].toInt()}"
} catch (e: Exception) { iso }
