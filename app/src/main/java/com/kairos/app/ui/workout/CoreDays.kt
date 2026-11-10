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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairos.app.data.remote.dto.CoreMoveIn
import com.kairos.app.data.remote.dto.CoreWorkoutDto
import com.kairos.app.data.remote.dto.PlanExerciseDto
import com.kairos.app.data.remote.dto.PlanWorkoutDto
import com.kairos.app.ui.common.AccentOutlinedButton
import com.kairos.app.ui.nav.KairosIcons


/** "Core" / "Arms" / "Isometric" (v0.471). */
internal fun rotationWord(kind: String?) = when (kind) {
    "arms" -> "Arms"
    "iso" -> "Isometric"
    else -> "Core"
}

/**
 * Core days (v0.444): the core workouts a Core day rotates through, making
 * your own, and the plan line for a Core day. The server keeps the day on
 * the workout now due; the phone only picks and shows.
 */

/** What the core screens can do, handed down from the plan editor. */
interface CoreActions {
    /** [kind] "arms" saves an Arms workout (v0.462). */
    fun save(
        id: String?,
        name: String,
        moves: List<CoreMoveIn>,
        onSaved: (String) -> Unit,
        onError: (String) -> Unit,
        kind: String = "core",
    )
    fun delete(id: String, onDone: () -> Unit)
}

private val KINDS = listOf("REPS" to "Reps", "TIME" to "Hold", "WEIGHT" to "Weight")

/** "3 × 8–12", "3 × 0:30–0:45" from a stored scheme; null when it has none. */
internal fun coreSchemeText(scheme: String): String? {
    val clock = { s: Int -> "${s / 60}:" + (s % 60).toString().padStart(2, '0') }
    Regex("^range:(\\d+)x(\\d+)-(\\d+)$").find(scheme)?.let { m ->
        val (a, b, c) = m.destructured
        return "$a × $b–$c"
    }
    Regex("^same:(\\d+)x(\\d+)$").find(scheme)?.let { m ->
        val (a, b) = m.destructured
        return "$a × $b"
    }
    Regex("^hold:(\\d+)x(\\d+)-(\\d+)$").find(scheme)?.let { m ->
        val (a, b, c) = m.destructured
        return if (b == c) "$a × ${clock(b.toInt())}" else "$a × ${clock(b.toInt())}–${clock(c.toInt())}"
    }
    return null
}

/** The workouts in a rotation; tap order is rotation order. */
@Composable
internal fun CoreRotationPicker(
    workouts: List<CoreWorkoutDto>,
    rotation: List<String>,
    onRotation: (List<String>) -> Unit,
    test: Boolean,
    onTest: (Boolean) -> Unit,
    onNew: () -> Unit,
    onEdit: (CoreWorkoutDto) -> Unit,
    kind: String = "core",
) {
    val accent = MaterialTheme.colorScheme.primary
    var peek by remember { mutableStateOf<CoreWorkoutDto?>(null) }
    val toggle = { id: String -> onRotation(if (rotation.contains(id)) rotation - id else rotation + id) }
    Column {
        Text(
            "IN THE ROTATION, IN ORDER",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        workouts.forEachIndexed { i, w ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val n = rotation.indexOf(w.id)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { toggle(w.id) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (n >= 0) accent else Color.Transparent)
                        .border(width = 1.dp, color = accent, shape = RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (n >= 0) {
                        Text(
                            "${n + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(w.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${w.minutes} min · " + if (w.mine) "Yours" else w.gear,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (w.mine) {
                    IconButton(onClick = { onEdit(w) }) {
                        Icon(
                            KairosIcons.Pencil,
                            contentDescription = "Edit ${w.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                IconButton(onClick = { peek = w }) {
                    Icon(
                        KairosIcons.Info,
                        contentDescription = "About ${w.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        AccentOutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("New ${rotationWord(kind).lowercase()} workout")
        }
        // Core has the plank test, Isometric its three holds (v0.471); an Arms day has none.
        if (kind != "arms") {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (kind == "iso") {
                        "Monthly test (wall sit, dead hang, push-up hold), first isometric day each month"
                    } else {
                        "Plank test, first core day each month"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = test, onCheckedChange = onTest)
            }
        }
    }
    peek?.let { w ->
        AlertDialog(
            onDismissRequest = { peek = null },
            title = { Text(w.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    w.moves.forEach { m ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                m.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                m.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { peek = null }) { Text("Close") } },
        )
    }
}

/** A Core day in the plan editor, laid out like a warm-up (v0.453): the
 *  workout planned now as a link that opens the rotation. */
@Composable
internal fun CorePlanLine(w: PlanWorkoutDto, onRotation: () -> Unit) {
    if (w.core == null) return
    Text(
        "${w.name.removePrefix("Core · ").removePrefix("Arms · ").ifBlank { "Pick workouts" }} ›",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onRotation() }.padding(vertical = 4.dp),
    )
}

/** Change a Core day's rotation, full screen like Add workout. */
@Composable
internal fun CoreRotationDialog(
    w: PlanWorkoutDto,
    workouts: List<CoreWorkoutDto>,
    exercises: List<PlanExerciseDto>,
    busy: Boolean,
    actions: CoreActions,
    onSave: (List<String>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var rotation by remember { mutableStateOf(w.core?.rotation.orEmpty()) }
    var test by remember { mutableStateOf(w.core?.test ?: true) }
    val kind = w.core?.kind ?: "core"
    CoreScreen(title = "${rotationWord(kind)} rotation", onDismiss = onDismiss, footer = {
        Button(
            onClick = { onSave(rotation, test) },
            enabled = rotation.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text(if (busy) "Saving…" else "Save") }
    }) {
        CoreRotationWithEditor(
            workouts = workouts,
            exercises = exercises,
            rotation = rotation,
            onRotation = { rotation = it },
            test = test,
            onTest = { test = it },
            actions = actions,
            kind = kind,
        )
    }
}

/** The picker plus the editor it opens for a new or one's own workout. */
@Composable
internal fun CoreRotationWithEditor(
    workouts: List<CoreWorkoutDto>,
    exercises: List<PlanExerciseDto>,
    rotation: List<String>,
    onRotation: (List<String>) -> Unit,
    test: Boolean,
    onTest: (Boolean) -> Unit,
    actions: CoreActions,
    kind: String = "core",
) {
    // null: closed; "": new; else the id being edited.
    var editing by remember { mutableStateOf<String?>(null) }
    CoreRotationPicker(
        workouts = workouts,
        rotation = rotation,
        onRotation = onRotation,
        test = test,
        onTest = onTest,
        onNew = { editing = "" },
        onEdit = { editing = it.id },
        kind = kind,
    )
    editing?.let { id ->
        CoreWorkoutEditor(
            initial = workouts.firstOrNull { it.id == id },
            exercises = exercises,
            actions = actions,
            kind = kind,
            onSaved = { newId ->
                editing = null
                if (!rotation.contains(newId)) onRotation(rotation + newId)
            },
            onDeleted = { gone ->
                editing = null
                onRotation(rotation - gone)
            },
            onDismiss = { editing = null },
        )
    }
}

private class DraftMove(poolExerciseId: String, kind: String, sets: String, min: String, max: String) {
    var poolExerciseId by mutableStateOf(poolExerciseId)
    var kind by mutableStateOf(kind)
    var sets by mutableStateOf(sets)
    var min by mutableStateOf(min)
    var max by mutableStateOf(max)
}

/** Make or change one of your own core workouts. */
@Composable
internal fun CoreWorkoutEditor(
    initial: CoreWorkoutDto?,
    exercises: List<PlanExerciseDto>,
    actions: CoreActions,
    onSaved: (String) -> Unit,
    onDeleted: (String) -> Unit,
    onDismiss: () -> Unit,
    kind: String = "core",
) {
    // Core movements and holds, or arms movements for an Arms workout (v0.462).
    val choices = exercises
        .filter {
            when (kind) {
                "arms" -> it.muscleGroup == "ARMS"
                // Holds (v0.471): isometric movements and any logged as time.
                "iso" -> it.category == "ISOMETRIC" || it.metric == "DURATION"
                else -> it.muscleGroup == "CORE" || it.category == "ISOMETRIC"
            }
        }
        .sortedBy { it.name.lowercase() }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    val rows = remember {
        mutableStateListOf<DraftMove>().apply {
            initial?.moves?.forEach { m ->
                add(DraftMove(m.poolExerciseId.orEmpty(), m.kind, m.sets.toString(), m.min.toString(), m.max.toString()))
            }
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        val moves = rows.filter { it.poolExerciseId.isNotBlank() }.map { r ->
            val lo = r.min.toIntOrNull() ?: 0
            CoreMoveIn(
                poolExerciseId = r.poolExerciseId,
                kind = r.kind,
                sets = r.sets.toIntOrNull() ?: 0,
                min = lo,
                max = r.max.toIntOrNull() ?: lo,
            )
        }
        busy = true
        error = null
        actions.save(
            id = initial?.id,
            name = name,
            moves = moves,
            onSaved = { id -> busy = false; onSaved(id) },
            onError = { msg -> busy = false; error = msg },
            kind = kind,
        )
    }

    CoreScreen(
        title = (if (initial != null) "Edit " else "New ") + "${rotationWord(kind).lowercase()} workout",
        onDismiss = onDismiss,
        footer = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (initial != null) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Box(Modifier.weight(1f))
                Button(onClick = { save() }, enabled = !busy) { Text(if (busy) "Saving…" else "Save") }
            }
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        rows.forEachIndexed { i, r ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Dropdown(
                            label = "",
                            title = "Movement",
                            selected = choices.firstOrNull { it.id == r.poolExerciseId }?.name ?: "Pick a movement…",
                            options = choices.map { it.id to it.name },
                            onSelect = { id ->
                                r.poolExerciseId = id
                                val metric = choices.firstOrNull { it.id == id }?.metric
                                r.kind = when (metric) {
                                    "DURATION" -> "TIME"
                                    "WEIGHT" -> "WEIGHT"
                                    else -> "REPS"
                                }
                                if (r.kind == "TIME") {
                                    r.min = "30"
                                    r.max = "45"
                                }
                            },
                        )
                    }
                    IconButton(onClick = { rows.removeAt(i) }) {
                        Icon(
                            KairosIcons.Trash,
                            contentDescription = "Remove movement",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dropdown(
                        label = "",
                        title = "Logged as",
                        selected = KINDS.firstOrNull { it.first == r.kind }?.second ?: "Reps",
                        options = KINDS,
                        onSelect = { r.kind = it },
                        compact = true,
                    )
                    NumberBox(value = r.sets, onValue = { r.sets = it }, label = "Sets", width = 52)
                    Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NumberBox(value = r.min, onValue = { r.min = it }, label = if (r.kind == "TIME") "Shortest hold, seconds" else "Fewest reps", width = 60)
                    Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NumberBox(value = r.max, onValue = { r.max = it }, label = if (r.kind == "TIME") "Longest hold, seconds" else "Most reps", width = 60)
                    Text(
                        if (r.kind == "TIME") "sec" else "reps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AccentOutlinedButton(
            onClick = { rows.add(if (kind == "iso") DraftMove("", "TIME", "3", "30", "60") else DraftMove("", "REPS", "3", "10", "15")) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) { Text("Add movement") }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${initial.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    busy = true
                    actions.delete(id = initial.id, onDone = { busy = false; onDeleted(initial.id) })
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NumberBox(value: String, onValue: (String) -> Unit, label: String, width: Int) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = { v -> onValue(v.filter { it.isDigit() }.take(3)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .width(width.dp)
            .height(40.dp)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp)
            .semantics { contentDescription = label },
    )
}

/** A full-screen dialog with a back arrow, a scrolling body and a footer. */
@Composable
internal fun CoreScreen(
    title: String,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                ) { body() }
                footer()
            }
        }
    }
}
