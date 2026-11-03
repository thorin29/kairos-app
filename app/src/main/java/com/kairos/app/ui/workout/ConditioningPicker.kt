package com.kairos.app.ui.workout

import com.kairos.app.ui.common.AccentOutlinedButton
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.BenchmarkDto
import com.kairos.app.data.remote.dto.BuildResponseDto
import com.kairos.app.data.remote.dto.ConditioningChoiceDto
import com.kairos.app.data.remote.dto.ConditioningChoicesDto
import com.kairos.app.data.remote.dto.ConditioningMoveDto
import com.kairos.app.data.remote.dto.ConditioningPlanDto
import com.kairos.app.data.remote.dto.PickPlanDto

/** Lengths for Pick one's filter: key, label, fits. */
private val TIME_FILTERS: List<Triple<String, String, (Int?) -> Boolean>> = listOf(
    Triple("any", "Any length", { _: Int? -> true }),
    Triple("short", "10 min or less", { m: Int? -> m != null && m <= 10 }),
    Triple("mid", "10–20 min", { m: Int? -> m != null && m > 10 && m <= 20 }),
    Triple("long", "20+ min", { m: Int? -> m != null && m > 20 }),
)

/** Formats for Pick one's filter: key, label, workout types. */
private val FORMAT_FILTERS: List<Triple<String, String, Set<String>>> = listOf(
    Triple("any", "Any format", emptySet()),
    Triple("AMRAP", "AMRAP", setOf("AMRAP")),
    Triple("EMOM", "EMOM", setOf("EMOM")),
    Triple("TABATA", "Tabata", setOf("TABATA")),
    Triple("FOR_TIME", "For time", setOf("FOR_TIME", "PYRAMID", "FOR_REPS")),
    Triple("STATIONS", "Stations", setOf("TIMED_STATIONS", "STATIONS")),
)

private val BUILD_LENGTHS = listOf(8, 12, 15, 20)
private val BUILD_FORMATS = listOf("AMRAP" to "AMRAP", "EMOM" to "EMOM", "TABATA" to "Tabata", "FOR_TIME" to "For time", "CHIPPER" to "Chipper")
private val BUILD_FOCUSES = listOf(
    "ANY" to "Any",
    "KETTLEBELL" to "Kettlebell",
    "CLUBS" to "Clubs",
    "BALL" to "Slam / med ball",
    "BODYWEIGHT" to "Bodyweight",
)

private fun resultLabelFor(type: String): String = when (type) {
    "AMRAP" -> "Rounds"
    "EMOM" -> "Minutes done"
    "FOR_TIME", "PYRAMID" -> "Time"
    else -> "Total reps"
}

private fun choiceMeta(c: ConditioningChoiceDto): List<String> = listOfNotNull(
    c.last?.let { "Your last: $it" },
    c.best?.let { "Household best: ${it.name} ${it.result}" },
)

/**
 * A Conditioning plan with no workout chosen ahead (the web's
 * PickConditioningCard): Pick one, Build one or Write your own on the day.
 * Any conditioning logged that day completes it.
 */
@Composable
internal fun PickConditioningCard(
    plan: PickPlanDto,
    onPick: () -> Unit,
    onBuild: () -> Unit,
    onWriteOwn: () -> Unit,
    onSkip: (Boolean) -> Unit,
    onFeedback: (String?, String?) -> Unit,
) {
    val done = plan.done.isNotEmpty()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (!done) {
                    TextButton(onClick = { onSkip(!plan.skipped) }) { Text(if (plan.skipped) "Skipped" else "Skip") }
                }
            }
            plan.done.forEach { d ->
                Text(
                    d.name + (d.result?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (done) {
                EffortChooser(question = "How did it feel?", effort = plan.effort, hurt = plan.hurt, onChange = onFeedback)
            }
            if (!plan.skipped) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (done) {
                        AccentOutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) { Text("Pick one") }
                    } else {
                        Button(onClick = onPick, modifier = Modifier.weight(1f)) { Text("Pick one") }
                    }
                    AccentOutlinedButton(onClick = onBuild, modifier = Modifier.weight(1f)) { Text("Build one") }
                }
                TextButton(onClick = onWriteOwn) { Text("Write your own") }
            }
        }
    }
}

/** The monthly benchmark: the offer once, then each month's in its first week. */
@Composable
internal fun BenchmarkCard(
    benchmark: BenchmarkDto,
    onAnswer: (String) -> Unit,
    onDo: (String) -> Unit,
) {
    val w = benchmark.workout
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (benchmark.state == "OFFER") {
                Text("A monthly benchmark?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                w?.let {
                    Text(
                        "First up: ${it.name}" + (it.minutes?.let { m -> " · $m min" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onAnswer("yes") }) { Text("Yes") }
                    AccentOutlinedButton(onClick = { onAnswer("later") }) { Text("Later") }
                    TextButton(onClick = { onAnswer("never") }) { Text("No thanks") }
                }
            } else if (w != null) {
                Text(
                    "This month's benchmark",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    w.name + (w.minutes?.let { " · $it min" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDo(w.hiitWorkoutId) }, modifier = Modifier.weight(1f)) { Text("Do it") }
                    AccentOutlinedButton(onClick = { onAnswer("skip") }) { Text("Not this month") }
                }
            }
        }
    }
}

/**
 * One workout ready to do: how it's done, Rx / Scaled, the movements, the
 * result to beat, Start (the timer) and Log a result.
 */
@Composable
private fun RunWorkout(
    name: String,
    type: String,
    capSec: Int?,
    about: String?,
    rx: List<ConditioningMoveDto>,
    scaled: List<ConditioningMoveDto>?,
    startVersion: String,
    resultLabel: String,
    durationResult: Boolean,
    meta: List<String>,
    onLog: (Int, String?) -> Unit,
    extra: @Composable () -> Unit = {},
) {
    var version by remember(name) { mutableStateOf(if (scaled != null) startVersion else "RX") }
    var timing by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val movements = if (version == "SCALED" && scaled != null) scaled else rx
    val logVersion = if (scaled != null) version else null

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            TYPE_LABEL[type] ?: type,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        about?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (scaled != null) {
            VersionToggle(value = version, onChange = { version = it })
        }
        movements.forEach { m -> Text(m.line, style = MaterialTheme.typography.bodyMedium) }
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (manual) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter { ch -> ch.isDigit() || ch == ':' } },
                label = { Text(if (durationResult) "$resultLabel (mm:ss)" else resultLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val v: Int? = if (durationResult) {
                        Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})$""").find(value.trim())?.let { m ->
                            m.groupValues[1].ifEmpty { "0" }.toInt() * 3600 + m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt()
                        }
                    } else {
                        value.trim().toIntOrNull()?.takeIf { it > 0 }
                    }
                    if (v == null || v <= 0) {
                        error = if (durationResult) "Time as mm:ss." else "Enter the ${resultLabel.lowercase()}."
                    } else {
                        error = null
                        manual = false
                        onLog(v, logVersion)
                    }
                }) { Text("Save") }
                TextButton(onClick = { manual = false }) { Text("Cancel") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { timing = true }, modifier = Modifier.weight(1f)) { Text("Start") }
                AccentOutlinedButton(onClick = { manual = true }) { Text("Log a result") }
            }
            extra()
        }
    }

    if (timing) {
        ConditioningTimer(
            workout = ConditioningPlanDto(
                name = name,
                type = type,
                capSec = capSec,
                movements = movements,
                resultLabel = resultLabel,
                resultMetric = if (durationResult) "DURATION" else "REPS",
            ),
            onLog = { v, _ ->
                timing = false
                onLog(v, logVersion)
            },
            onClose = { timing = false },
        )
    }
}

/** A sheet's title row, with a way back when there is one. */
@Composable
private fun SheetTitle(title: String, onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("‹ Back") }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Pick one: every workout the person can do, filtered by length, format and
 * the equipment on hand; tap one to do it. Opens straight on [openId] when
 * given (this month's benchmark).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PickOneSheet(
    choices: ConditioningChoicesDto?,
    error: String?,
    openId: String?,
    onLoad: () -> Unit,
    onLog: (ConditioningChoiceDto, Int, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    var time by remember { mutableStateOf("any") }
    var format by remember { mutableStateOf("any") }
    var haveOnly by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf(openId) }
    val have = choices?.equipment?.map { it.key }?.toSet().orEmpty()
    val fits = TIME_FILTERS.first { it.first == time }.third
    val types = FORMAT_FILTERS.first { it.first == format }.third
    val list = choices?.choices.orEmpty().filter { c ->
        fits(c.minutes) && (types.isEmpty() || c.type in types) && (!haveOnly || c.equipment.all { it in have })
    }
    val current = picked?.let { id -> choices?.choices?.firstOrNull { it.hiitWorkoutId == id } }

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
            when {
                current != null && choices != null -> {
                    SheetTitle(title = current.name, onBack = if (openId == null) ({ picked = null }) else null)
                    RunWorkout(
                        name = current.name,
                        type = current.type,
                        capSec = current.capSec,
                        about = current.about,
                        rx = current.movements,
                        scaled = current.scaled,
                        startVersion = choices.version,
                        resultLabel = current.resultLabel.ifBlank { resultLabelFor(current.type) },
                        durationResult = current.resultMetric == "DURATION",
                        meta = choiceMeta(current),
                        onLog = { v, version -> onLog(current, v, version) },
                    )
                }
                error != null -> {
                    SheetTitle(title = "Pick one", onBack = null)
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                choices == null -> {
                    SheetTitle(title = "Pick one", onBack = null)
                    CircularProgressIndicator(Modifier.padding(8.dp))
                }
                else -> {
                    SheetTitle(title = "Pick one", onBack = null)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TIME_FILTERS.forEach { (key, label, _) -> ChoicePill(label = label, selected = time == key) { time = key } }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FORMAT_FILTERS.forEach { (key, label, _) -> ChoicePill(label = label, selected = format == key) { format = key } }
                    }
                    ChoicePill(label = "Equipment we have", selected = haveOnly) { haveOnly = !haveOnly }
                    if (list.isEmpty()) {
                        Text(
                            "Nothing matches.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    list.filter { it.kind != "HERO" }.forEach { c -> ChoiceRow(choice = c) { picked = c.hiitWorkoutId } }
                    val heroes = list.filter { it.kind == "HERO" }
                    if (heroes.isNotEmpty()) {
                        Text(
                            "Hero workouts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        heroes.forEach { c -> ChoiceRow(choice = c) { picked = c.hiitWorkoutId } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(choice: ConditioningChoiceDto, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                choice.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                (TYPE_LABEL[choice.type] ?: choice.type) + (choice.minutes?.let { " · $it min" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            choice.movements.joinToString(", ") { it.name },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = choiceMeta(choice)
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Build one: a workout for the length, format and focus from the household's
 * equipment; Another idea, Use this, and Save to our workouts.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun BuildOneSheet(
    built: BuildResponseDto?,
    building: Boolean,
    saved: Boolean,
    startVersion: String,
    onBuild: (Int, String, String, Int) -> Unit,
    onLog: (Int, String?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableIntStateOf(12) }
    var format by remember { mutableStateOf("AMRAP") }
    var focus by remember { mutableStateOf("ANY") }
    var seed by remember { mutableIntStateOf((0..99_999).random()) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(minutes, format, focus, seed) {
        onBuild(minutes, format, focus, seed)
    }
    val w = built?.built

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
            if (ready && w != null && built != null) {
                SheetTitle(title = w.name, onBack = { ready = false })
                RunWorkout(
                    name = w.name,
                    type = w.type,
                    capSec = w.capSec,
                    about = w.about.ifBlank { null },
                    rx = built.movements,
                    scaled = built.scaled.takeIf { s -> s.isNotEmpty() && s.map { it.line } != built.movements.map { it.line } },
                    startVersion = startVersion,
                    resultLabel = resultLabelFor(w.type),
                    durationResult = w.type == "FOR_TIME" || w.type == "PYRAMID",
                    meta = emptyList(),
                    onLog = onLog,
                    extra = {
                        AccentOutlinedButton(onClick = onSave, enabled = !saved) {
                            Text(if (saved) "Saved" else "Save to our workouts")
                        }
                    },
                )
            } else {
                SheetTitle(title = "Build one", onBack = null)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BUILD_LENGTHS.forEach { m -> ChoicePill(label = "$m min", selected = minutes == m) { minutes = m } }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BUILD_FORMATS.forEach { (key, label) -> ChoicePill(label = label, selected = format == key) { format = key } }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BUILD_FOCUSES.forEach { (key, label) -> ChoicePill(label = label, selected = focus == key) { focus = key } }
                }
                when {
                    building && w == null -> CircularProgressIndicator(Modifier.padding(8.dp))
                    w == null -> Text(
                        "Not enough equipment for that.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(w.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            built?.movements?.forEach { m -> Text(m.line, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ready = true }, enabled = w != null && !building, modifier = Modifier.weight(1f)) {
                        Text("Use this")
                    }
                    AccentOutlinedButton(onClick = { seed += 1 }, enabled = !building) { Text("Another idea") }
                }
            }
        }
    }
}
