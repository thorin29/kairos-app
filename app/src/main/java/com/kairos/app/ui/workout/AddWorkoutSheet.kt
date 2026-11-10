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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairos.app.ui.nav.KairosIcons
import com.kairos.app.data.remote.dto.AddPoolExercise
import com.kairos.app.data.remote.dto.AddPoolRequest
import com.kairos.app.data.remote.dto.PlanOptionsDto

private data class Pick(val tracked: Boolean, val metric: String, val scheme: String = "")
private val DAY_LABELS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

/** A movement that sits in no muscle group charts as its own progress block. */
private const val NO_GROUP_LABEL = "No group \u2014 its own chart"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkoutSheet(
    day: Int,
    options: PlanOptionsDto,
    busy: Boolean,
    onAddPool: (AddPoolRequest) -> Unit,
    onAddHiit: (String) -> Unit,
    onMarkRest: () -> Unit,
    onDismiss: () -> Unit,
    /** A Core day: its rotation and the monthly plank test (server 0.637+). */
    /** rotation, plank test, kind ("core" or "arms", v0.462). */
    onAddCore: (List<String>, Boolean, String) -> Unit = { _, _, _ -> },
    coreActions: CoreActions? = null,
) {
    var rest by remember { mutableStateOf(false) }
    var categoryKey by remember { mutableStateOf(options.categories.firstOrNull()?.key ?: "WEIGHTS") }
    var muscleKey by remember { mutableStateOf(options.muscleGroups.firstOrNull()?.key ?: "") }
    var picked by remember { mutableStateOf<Map<String, Pick>>(emptyMap()) }
    var hiitId by remember { mutableStateOf("") }
    // Core (v0.444): a rotation of core workouts, or movements picked by hand.
    var coreRotationMode by remember { mutableStateOf(true) }
    var coreRotation by remember { mutableStateOf<List<String>>(emptyList()) }
    var coreTest by remember { mutableStateOf(true) }
    // Warm-up (v0.445): its routine chosen as it's added; null is a plain check.
    var warmupId by remember { mutableStateOf<String?>(null) }

    val category = options.categories.firstOrNull { it.key == categoryKey }
    val kind = category?.kind ?: "pool"
    val defMetric = category?.defaultMetric ?: ""
    val metrics = category?.metrics.orEmpty()

    // An empty muscle key means "no group": it matches the movements that are
    // filed under none, which chart as their own block rather than a group's.
    val exercises = options.exercises.filter {
        it.category == categoryKey && (kind != "weights" || (it.muscleGroup ?: "") == muscleKey)
    }
    val ownMetrics = exercises.mapNotNull { e -> e.metric?.let { e.id to it } }.toMap()

    // Core and Arms (v0.462) both rotate through workouts.
    val rotKind = if (muscleKey == "ARMS") "arms" else "core"
    val rotWorkouts = if (rotKind == "arms") options.armsWorkouts else options.coreWorkouts
    val coreRot = kind == "weights" && (muscleKey == "CORE" || muscleKey == "ARMS") && coreRotationMode &&
        rotWorkouts.isNotEmpty() && coreActions != null
    val canSave = when {
        rest -> true
        coreRot -> coreRotation.isNotEmpty()
        kind == "hiit" -> hiitId.isNotBlank()
        kind == "metricOnly" -> true
        else -> picked.isNotEmpty()
    }

    fun submit() {
        if (!canSave || busy) return
        when {
            rest -> onMarkRest()
            coreRot -> onAddCore(coreRotation, coreTest && rotKind == "core", rotKind)
            // Conditioning with no workout chosen ahead: picked, built or
            // written on the day (server 0.617+).
            kind == "hiit" && hiitId == PICK_ON_THE_DAY -> onAddPool(
                AddPoolRequest(day = day, category = categoryKey, name = "Conditioning"),
            )
            kind == "hiit" -> onAddHiit(hiitId)
            else -> onAddPool(
                AddPoolRequest(
                    day = day,
                    category = categoryKey,
                    muscleGroup = if (kind == "weights") muscleKey.ifBlank { null } else null,
                    warmup = if (categoryKey == "WARMUP" || categoryKey == "MOBILITY") warmupId else null,
                    exercises = if (kind == "metricOnly") emptyList()
                    else picked.map { (id, p) ->
                        AddPoolExercise(
                            poolExerciseId = id,
                            tracked = p.tracked,
                            metric = p.metric,
                            scheme = if (kind == "weights" && p.tracked && p.metric == "WEIGHT") p.scheme else "",
                        )
                    },
                ),
            )
        }
    }

    // A full screen, not a bottom sheet: scrolling down the movement list
    // closed the sheet (v0.429). Back closes it.
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
            Text("Add to ${DAY_LABELS[day]}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            // Category (Rest + real categories)
            Dropdown(
                label = "Category",
                selected = if (rest) "Rest day" else (category?.label ?: ""),
                options = buildList {
                    add("REST" to "Rest day")
                    options.categories.forEach { add(it.key to it.label) }
                },
                onSelect = { key ->
                    if (key == "REST") rest = true
                    else {
                        rest = false
                        categoryKey = key
                        picked = emptyMap()
                        hiitId = ""
                        warmupId = null
                    }
                },
            )

            when {
                rest -> Note("Marks ${DAY_LABELS[day]} as a planned rest day — no workout expected.")
                kind == "weights" -> {
                    Dropdown(
                        label = "Muscle group",
                        selected = if (muscleKey.isBlank()) NO_GROUP_LABEL
                        else options.muscleGroups.firstOrNull { it.key == muscleKey }?.label ?: "",
                        options = options.muscleGroups.map { it.key to it.label } +
                            listOf("" to NO_GROUP_LABEL),
                        onSelect = { muscleKey = it; picked = emptyMap(); coreRotation = emptyList() },
                    )
                    if ((muscleKey == "CORE" || muscleKey == "ARMS") && rotWorkouts.isNotEmpty() && coreActions != null) {
                        CoreModeSwitch(
                            rotation = coreRotationMode,
                            onRotation = { coreRotationMode = it },
                            label = if (rotKind == "arms") "Arms rotation" else "Core rotation",
                        )
                    }
                    if (coreRot && coreActions != null) {
                        CoreRotationWithEditor(
                            kind = rotKind,
                            workouts = rotWorkouts,
                            exercises = options.exercises,
                            rotation = coreRotation,
                            onRotation = { coreRotation = it },
                            test = coreTest,
                            onTest = { coreTest = it },
                            actions = coreActions,
                        )
                    } else ExerciseList(
                        items = exercises.map { it.id to it.name },
                        picked = picked,
                        metrics = metrics,
                        defMetric = defMetric,
                        own = ownMetrics,
                        about = exercises.associate { it.id to it.about },
                        schemes = options.schemes,
                    ) { picked = it }
                }
                kind == "hiit" -> {
                    Dropdown(
                        label = "Workout",
                        selected = if (hiitId == PICK_ON_THE_DAY) "Pick on the day"
                        else options.hiitWorkouts.firstOrNull { it.id == hiitId }?.name ?: "Pick a workout…",
                        options = listOf(PICK_ON_THE_DAY to "Pick on the day") +
                            options.hiitWorkouts.map { it.id to (it.name + if (it.personal) " (yours)" else "") },
                        onSelect = { hiitId = it },
                    )
                }
                // Warm-up: its routine, chosen like a Core day's workouts (v0.445).
                categoryKey == "WARMUP" -> WarmupChooser(value = warmupId, onChange = { warmupId = it })
                // Mobility (v0.469): the same chooser, its own routines.
                categoryKey == "MOBILITY" -> androidx.compose.runtime.key("mobility") {
                    WarmupChooser(value = warmupId, onChange = { warmupId = it }, kind = "mobility")
                }
                // Cardio: nothing to pick.
                category?.simple == true -> Unit
                kind == "metricOnly" -> Note("Adds a ${category?.label?.lowercase() ?: ""} day — you'll log it on completion.")
                else -> {
                    if (exercises.isEmpty()) Note("No ${category?.label?.lowercase() ?: ""} movements in the pool yet.")
                    else ExerciseList(
                        items = exercises.map { it.id to it.name },
                        picked = picked,
                        metrics = metrics,
                        defMetric = defMetric,
                        own = ownMetrics,
                        about = exercises.associate { it.id to it.about },
                    ) { picked = it }
                }
            }

        }
        Button(
            onClick = { submit() },
            enabled = canSave && !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(if (busy) "Adding…" else if (coreRot) "Add $rotKind day" else "Add workout")
        }
        }
        }
    }
}

/** Core rotation or Pick movements, as two halves of one outlined bar. */
@Composable
private fun CoreModeSwitch(rotation: Boolean, onRotation: (Boolean) -> Unit, label: String = "Core rotation") {
    val rotationLabel = label
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(width = 1.dp, color = accent, shape = shape),
    ) {
        listOf(true to rotationLabel, false to "Pick movements").forEach { (k, label) ->
            val on = k == rotation
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) accent else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onRotation(k) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else accent,
                )
            }
        }
    }
}

/** The workout choice for a Conditioning plan picked on the day. */
private const val PICK_ON_THE_DAY = "pick"

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
    )
}

@Composable
private fun ExerciseList(
    items: List<Pair<String, String>>,
    picked: Map<String, Pick>,
    metrics: List<com.kairos.app.data.remote.dto.PlanMetricDto>,
    defMetric: String,
    own: Map<String, String> = emptyMap(),
    about: Map<String, String?> = emptyMap(),
    /** Set structures, offered on weights movements logged by weight. */
    schemes: List<com.kairos.app.data.remote.dto.SchemeChoiceDto> = emptyList(),
    onChange: (Map<String, Pick>) -> Unit,
) {
    var peek by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Exercises", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { (id, name) ->
            val p = picked[id]
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        if (p != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = p != null,
                        onCheckedChange = {
                            onChange(
                                if (p != null) picked - id
                                // The movement's own "Logs as" (a plank is
                                // timed), else the category's default.
                                else picked + (
                                    id to Pick(
                                        tracked = true,
                                        metric = own[id]?.takeIf { m -> metrics.any { it.key == m } } ?: defMetric,
                                    )
                                ),
                            )
                        },
                    )
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    MovementInfoButton(
                        name = name,
                        onClick = { peek = if (peek == id) null else id },
                        tint = if (peek == id) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (peek == id) {
                    MovementAboutInline(name = name, about = about[id])
                }
                if (p != null) {
                    Row(
                        Modifier.padding(start = 12.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = p.tracked,
                            onCheckedChange = { onChange(picked + (id to p.copy(tracked = it))) },
                        )
                        Text("Log a metric", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (p.tracked && metrics.size > 1) {
                            Dropdown(
                                label = "",
                                title = "Log as",
                                selected = metrics.firstOrNull { it.key == p.metric }?.label ?: "",
                                options = metrics.map { it.key to it.label },
                                onSelect = { onChange(picked + (id to p.copy(metric = it))) },
                                compact = true,
                            )
                        } else if (p.tracked && metrics.size == 1) {
                            Text(metrics[0].label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (schemes.isNotEmpty() && p.tracked && p.metric == "WEIGHT") {
                        Row(
                            Modifier.padding(start = 24.dp, top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Sets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Dropdown(
                                label = "",
                                title = "Sets",
                                selected = schemes.firstOrNull { it.key == p.scheme }?.label ?: "Any sets",
                                options = schemes.map { it.key to it.label },
                                onSelect = { onChange(picked + (id to p.copy(scheme = it))) },
                                compact = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Dropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    compact: Boolean = false,
    /** The picker's title; the label when it has one. */
    title: String = label,
) {
    // Opens the app's full-screen picker (the calendar's Type / Calendar
    // style), not a small drop-down menu.
    var open by remember { mutableStateOf(false) }
    Column {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier
                .then(if (compact) Modifier else Modifier.fillMaxWidth())
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected,
                style = MaterialTheme.typography.bodyMedium,
                modifier = if (compact) Modifier else Modifier.weight(1f),
            )
            androidx.compose.material3.Icon(
                com.kairos.app.ui.nav.KairosIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp).size(16.dp),
            )
        }
    }
    if (open) {
        com.kairos.app.ui.common.OptionPickerOverlay(
            title = title.ifBlank { "Choose" },
            options = options,
            selectedKey = options.firstOrNull { it.second == selected }?.first,
            onPick = onSelect,
            onClose = { open = false },
        )
    }
}
