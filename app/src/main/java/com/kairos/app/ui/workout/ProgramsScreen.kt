package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.ProgramChoiceDto
import com.kairos.app.data.remote.dto.ProgramInfoDto
import com.kairos.app.data.remote.dto.ProgramsDto
import com.kairos.app.data.remote.dto.StartProgramRequest
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.launch

private val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/**
 * Programs: the running one (its week, training maxes, the end-of-block
 * choice, Change and End), or the list to start one. Starting asks which days,
 * a movement for each slot and, for main lifts, a recent top weight; it then
 * writes the person's weekly plan (same as the web, server 0.611).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val container = rememberContainer()
    val session = container.sessionRepository
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<ProgramsDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var setupKey by remember { mutableStateOf<String?>(null) }
    var ending by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            try {
                data = session.loadPrograms()
                error = null
            } catch (e: ApiException) {
                error = e.error.message
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun act(block: suspend () -> Unit, after: () -> Unit = { reload() }) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
                after()
            } catch (e: ApiException) {
                error = e.error.message
            } finally {
                busy = false
            }
        }
    }

    val setup = data?.choices?.firstOrNull { it.key == setupKey }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(setup?.name ?: "Programs") },
                navigationIcon = {
                    IconButton(onClick = { if (setup != null) setupKey = null else onBack() }) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val d = data
            when {
                d == null && error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                d == null -> Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                setup != null -> ProgramSetup(
                    program = setup,
                    busy = busy,
                    onStart = { req ->
                        act({ session.startProgram(req) }) {
                            setupKey = null
                            picking = false
                            onDone()
                        }
                    },
                )
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val a = d.active
                    if (a != null && !picking) {
                        ActiveProgramCard(
                            info = a,
                            busy = busy,
                            onRaise = { act({ session.nextProgramBlock(raise = true) }) },
                            onRepeat = { act({ session.nextProgramBlock(raise = false) }) },
                            onSaveMax = { id, w -> act({ session.setTrainingMax(id, w) }) },
                            onChange = { picking = true },
                            onEnd = { ending = true },
                        )
                    } else {
                        if (a != null) {
                            Text("Change program", style = MaterialTheme.typography.titleMedium)
                        }
                        d.choices.forEach { c -> ProgramChoiceCard(c) { setupKey = c.key } }
                        if (a != null) {
                            TextButton(onClick = { picking = false }) { Text("Cancel") }
                        }
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (ending) {
        AlertDialog(
            onDismissRequest = { ending = false },
            title = { Text("End ${data?.active?.name ?: "program"}?") },
            confirmButton = {
                TextButton(onClick = { ending = false; act({ session.endProgram(restore = true) }) { onDone() } }) {
                    Text("Go back to my old plan")
                }
            },
            dismissButton = {
                TextButton(onClick = { ending = false; act({ session.endProgram(restore = false) }) { onDone() } }) {
                    Text("Keep this plan")
                }
            },
        )
    }
}

@Composable
private fun ActiveProgramCard(
    info: ProgramInfoDto,
    busy: Boolean,
    onRaise: () -> Unit,
    onRepeat: () -> Unit,
    onSaveMax: (String, Double) -> Unit,
    onChange: () -> Unit,
    onEnd: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(info.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (info.blockDone) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Block done. What's next?", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgramPill("Raise my maxes", filled = true, enabled = !busy, onClick = onRaise)
                        ProgramPill("Repeat", enabled = !busy, onClick = onRepeat)
                    }
                }
            }
            if (info.maxes.isNotEmpty()) {
                Text(
                    "TRAINING MAXES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                info.maxes.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider()
                    MaxRow(name = m.name, weight = m.weight) { onSaveMax(m.poolExerciseId, it) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Change program",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onChange() }.padding(vertical = 6.dp),
                )
                Text(
                    "End program",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onEnd() }.padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun MaxRow(name: String, weight: Double, onSave: (Double) -> Unit) {
    var v by remember(weight) { mutableStateOf(MovementSetsViewModel.fmt(weight)) }
    val n = v.trim().toDoubleOrNull()
    val changed = n != null && n > 0 && n != weight
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = v,
            onValueChange = { v = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(88.dp),
        )
        if (changed) {
            Text(
                "Save",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { n?.let(onSave) }.padding(6.dp),
            )
        }
    }
}

@Composable
private fun ProgramChoiceCard(c: ProgramChoiceDto, onPick: () -> Unit) {
    OutlinedCard(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${c.days.size} days \u00b7 ${c.weeks} wk",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(c.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Filled (the main choice) or outlined pill, like the set logger's. */
@Composable
internal fun ProgramPill(label: String, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Days, a movement per slot, and recent top weights for the main lifts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramSetup(program: ProgramChoiceDto, busy: Boolean, onStart: (StartProgramRequest) -> Unit) {
    var days by remember(program.key) { mutableStateOf(program.defaultDays.sorted()) }
    val picks = remember(program.key) {
        mutableStateMapOf<String, String?>().apply {
            program.days.forEachIndexed { i, d -> d.roles.forEach { r -> put("$i:${r.key}", r.defaultId) } }
        }
    }
    val tops = remember(program.key) { mutableStateMapOf<String, String>() }
    val ready = days.size == program.days.size

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(program.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Pick ${program.days.size} days", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WEEKDAYS.forEachIndexed { d, label ->
                val on = d in days
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { days = if (on) days - d else (days + d).sorted() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        program.days.forEachIndexed { i, day ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        buildString {
                            append(day.name)
                            if (ready) append(" \u00b7 ").append(WEEKDAYS[days[i]])
                            if (day.row) append(" \u00b7 with rowing")
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    day.roles.forEach { r ->
                        val k = "$i:${r.key}"
                        val selected = r.options.firstOrNull { it.id == picks[k] }?.name ?: "Leave out"
                        Dropdown(
                            label = r.label,
                            selected = selected,
                            options = listOf("" to "Leave out") + r.options.map { it.id to it.name },
                            onSelect = { picks[k] = it.ifBlank { null } },
                        )
                    }
                }
            }
        }

        val mains = program.days.flatMapIndexed { i, d ->
            d.roles.filter { it.main }.mapNotNull { r ->
                val id = picks["$i:${r.key}"] ?: return@mapNotNull null
                r.options.firstOrNull { it.id == id }?.let { id to it.name }
            }
        }.distinctBy { it.first }
        if (mains.isNotEmpty()) {
            Text("Recent top weight", style = MaterialTheme.typography.titleSmall)
            mains.forEach { (id, name) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = tops[id] ?: "",
                        onValueChange = { tops[id] = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(88.dp),
                    )
                }
            }
        }

        Text(
            "This replaces your weekly plan. Ending the program can put it back.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(2.dp))
        ProgramPill(
            label = if (busy) "Starting\u2026" else "Start program",
            filled = true,
            enabled = ready && !busy,
        ) {
            onStart(
                StartProgramRequest(
                    key = program.key,
                    days = days,
                    picks = picks.toMap(),
                    tops = tops.mapNotNull { (k, v) -> v.toDoubleOrNull()?.takeIf { it > 0 }?.let { k to it } }.toMap(),
                ),
            )
        }
    }
}
