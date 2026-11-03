package com.kairos.app.ui.workout

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.WarmupRoutineDto
import com.kairos.app.data.remote.dto.WarmupStepDto
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "10–15", "30 s each side", "3:00" (the web's stepAmount). */
internal fun stepAmount(s: WarmupStepDto): String {
    val side = if (s.sides) " each side" else ""
    if (s.kind == "TIME") {
        val sec = s.seconds ?: 0
        val t = if (sec >= 60) "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}" else "$sec s"
        return t + side
    }
    val a = s.amount.orEmpty()
    return if (a.isBlank()) side.trim() else a + side
}

private fun clock(s: Int): String = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"

/** A full screen with a back arrow and a title (the app's pattern for a
 *  window you scroll, not a bottom sheet). */
@Composable
private fun FullScreen(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                content()
            }
        }
    }
}

/** A plain count-up timer for a timed step: Start, Stop, and back to 0. */
@Composable
private fun StepTimer(target: Int) {
    var running by remember { mutableStateOf(false) }
    var secs by remember { mutableStateOf(0) }
    var banked by remember { mutableStateOf(0) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = SystemClock.elapsedRealtime()
        while (true) {
            secs = banked + ((SystemClock.elapsedRealtime() - start) / 1000).toInt()
            delay(250)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (running || secs > 0) {
            Text(
                clock(secs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (target > 0 && secs >= target) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        when {
            running -> OutlinedButton(onClick = { banked = secs; running = false }) { Text("Stop") }
            secs > 0 -> OutlinedButton(onClick = { banked = 0; secs = 0 }) { Text("Reset") }
            else -> OutlinedButton(onClick = { running = true }) {
                Icon(KairosIcons.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Start", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** A routine's steps: name and amount, a timer on timed ones, and a tick each. */
@Composable
private fun WarmupSteps(steps: List<WarmupStepDto>) {
    val ticked = remember(steps) { mutableStateListOf<Int>() }
    Column {
        steps.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider()
            val on = i in ticked
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { if (on) ticked.remove(i) else ticked.add(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(KairosIcons.Check, contentDescription = "${s.name} done", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        s.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (on) TextDecoration.LineThrough else null,
                        color = if (on) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(stepAmount(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (s.kind == "TIME") StepTimer(target = s.seconds ?: 0)
            }
        }
    }
}

/**
 * The day's warm-up (the web's WarmupWindow): its routine's steps with timers,
 * Switch (changes that day's plan) and Mark done.
 */
@Composable
internal fun WarmupWindow(
    plannedWorkoutId: String,
    routine: WarmupRoutineDto?,
    done: Boolean,
    onDone: () -> Unit,
    onClose: () -> Unit,
    onChanged: () -> Unit,
) {
    var switching by remember { mutableStateOf(false) }
    FullScreen(title = routine?.name ?: "Warm-up", onClose = onClose) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            if (routine != null) {
                WarmupSteps(steps = routine.steps)
            } else {
                Text("No routine.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { switching = true }) { Text("Switch") }
            Button(onClick = onDone, enabled = !done, modifier = Modifier.weight(1f)) {
                Icon(KairosIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(if (done) "Done" else "Mark done", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
    if (switching) {
        RoutinePicker(
            plannedWorkoutId = plannedWorkoutId,
            current = routine?.id,
            onClose = { switching = false },
            onPicked = {
                switching = false
                onChanged()
            },
        )
    }
}

/**
 * Choosing a Warm-up entry's routine: just a check, the ready-made ones, or
 * your own (edit, and New). Saves it to the plan entry, then [onPicked].
 */
@Composable
internal fun RoutinePicker(
    plannedWorkoutId: String,
    current: String?,
    onClose: () -> Unit,
    onPicked: () -> Unit,
) {
    val session = rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    var routines by remember { mutableStateOf<List<WarmupRoutineDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<WarmupRoutineDto?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            try {
                routines = session.warmupRoutines()
            } catch (e: ApiException) {
                error = e.error.message
            }
        }
    }
    LaunchedEffect(Unit) { load() }
    fun pick(id: String?) {
        scope.launch {
            try {
                session.setPlanWarmup(plannedWorkoutId = plannedWorkoutId, warmup = id)
                onPicked()
            } catch (e: ApiException) {
                error = e.error.message
            }
        }
    }

    FullScreen(title = "Warm-up", onClose = onClose) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoutineRow(name = "Just a check", sub = "", selected = current == null, onPick = { pick(null) }, onEdit = null)
            val list = routines
            if (list == null && error == null) CircularProgressIndicator(Modifier.padding(8.dp))
            list?.forEach { r ->
                RoutineRow(
                    name = r.name,
                    sub = r.steps.joinToString(" · ") { it.name },
                    selected = current == r.id,
                    onPick = { pick(r.id) },
                    onEdit = { editing = r },
                )
            }
            Text(
                "+ New warm-up",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { creating = true }.padding(vertical = 8.dp),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (editing != null || creating) {
        RoutineEditor(
            routine = editing,
            onClose = { editing = null; creating = false },
            onSaved = { id ->
                editing = null
                creating = false
                load()
                if (id != null) pick(id)
            },
        )
    }
}

@Composable
private fun RoutineRow(name: String, sub: String, selected: Boolean, onPick: () -> Unit, onEdit: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { onPick() }
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) Icon(KairosIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp).size(18.dp))
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(KairosIcons.Pencil, contentDescription = "Edit $name", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private data class DraftStep(val k: Int, val step: WarmupStepDto)

/**
 * Writing a routine: a name and its steps (reps or a timed hold, each side).
 * Editing a ready-made one saves your own copy.
 */
@Composable
private fun RoutineEditor(routine: WarmupRoutineDto?, onClose: () -> Unit, onSaved: (String?) -> Unit) {
    val session = rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(routine?.name ?: "") }
    val steps = remember {
        mutableStateListOf<DraftStep>().apply {
            val src = routine?.steps?.takeIf { it.isNotEmpty() } ?: listOf(WarmupStepDto(name = "", kind = "REPS", amount = "10"))
            src.forEachIndexed { i, s -> add(DraftStep(k = i, step = s)) }
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun set(k: Int, change: (WarmupStepDto) -> WarmupStepDto) {
        val i = steps.indexOfFirst { it.k == k }
        if (i >= 0) steps[i] = steps[i].copy(step = change(steps[i].step))
    }
    fun move(k: Int, by: Int) {
        val i = steps.indexOfFirst { it.k == k }
        val j = i + by
        if (i < 0 || j < 0 || j >= steps.size) return
        val a = steps[i]
        steps[i] = steps[j]
        steps[j] = a
    }

    FullScreen(title = if (routine?.mine == true) "Edit warm-up" else "New warm-up", onClose = onClose) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                placeholder = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            steps.forEachIndexed { i, d ->
                val s = d.step
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = s.name,
                            onValueChange = { v -> set(d.k) { it.copy(name = v.take(60)) } },
                            placeholder = { Text("Movement") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { steps.removeAll { it.k == d.k } }) {
                            Icon(KairosIcons.Trash, contentDescription = "Remove step ${i + 1}", modifier = Modifier.size(18.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("REPS" to "Reps", "TIME" to "Time").forEach { (k, label) ->
                            ProgramPill(
                                label = label,
                                filled = s.kind == k,
                                onClick = { set(d.k) { if (k == "TIME") it.copy(kind = k, seconds = it.seconds ?: 30) else it.copy(kind = k) } },
                            )
                        }
                        if (s.kind == "TIME") {
                            OutlinedTextField(
                                value = s.seconds?.toString() ?: "",
                                onValueChange = { v -> set(d.k) { it.copy(seconds = v.filter { c -> c.isDigit() }.take(4).toIntOrNull()) } },
                                singleLine = true,
                                suffix = { Text("sec") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(96.dp),
                            )
                        } else {
                            OutlinedTextField(
                                value = s.amount ?: "",
                                onValueChange = { v -> set(d.k) { it.copy(amount = v.take(30)) } },
                                placeholder = { Text("10–15") },
                                singleLine = true,
                                modifier = Modifier.width(96.dp),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = s.sides, onCheckedChange = { v -> set(d.k) { it.copy(sides = v) } })
                        Text("Each side", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { move(d.k, -1) }) { Text("↑") }
                        TextButton(onClick = { move(d.k, 1) }) { Text("↓") }
                    }
                }
            }
            Text(
                "+ Add a step",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        steps.add(DraftStep(k = (steps.maxOfOrNull { it.k } ?: 0) + 1, step = WarmupStepDto(name = "", kind = "REPS", amount = "10")))
                    }
                    .padding(vertical = 8.dp),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (routine?.mine == true) {
                OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Delete") }
            }
            Button(
                enabled = !busy && name.trim().length >= 2,
                modifier = Modifier.weight(1f),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val id = session.saveWarmupRoutine(
                                id = routine?.id?.takeIf { routine.mine },
                                name = name.trim(),
                                steps = steps.map { it.step },
                            )
                            onSaved(id)
                        } catch (e: ApiException) {
                            error = e.error.message
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Save") }
        }
    }
    if (confirmDelete && routine != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${routine.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            session.deleteWarmupRoutine(routine.id)
                            onSaved(null)
                        } catch (e: ApiException) {
                            error = e.error.message
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** A Warm-up entry in the plan editor: its routine, tapped to choose another. */
@Composable
internal fun WarmupPlanLine(plannedWorkoutId: String, warmup: String?, onChanged: () -> Unit) {
    val session = rememberContainer().sessionRepository
    var open by remember { mutableStateOf(false) }
    var name by remember(warmup) { mutableStateOf<String?>(null) }
    LaunchedEffect(warmup) {
        name = if (warmup == null) null else runCatching { session.warmupRoutines() }.getOrNull()?.firstOrNull { it.id == warmup }?.name
    }
    Text(
        "${name ?: "Just a check"} ›",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { open = true }.padding(vertical = 4.dp),
    )
    if (open) {
        RoutinePicker(
            plannedWorkoutId = plannedWorkoutId,
            current = warmup,
            onClose = { open = false },
            onPicked = {
                open = false
                onChanged()
            },
        )
    }
}
