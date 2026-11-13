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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.AddedLiftRequest
import com.kairos.app.data.remote.dto.CurrentExtrasDto
import com.kairos.app.data.remote.dto.ImportPreviewDto
import com.kairos.app.data.remote.dto.ProgramChoiceDto
import com.kairos.app.data.remote.dto.ProgramInfoDto
import com.kairos.app.data.remote.dto.ProgramsDto
import com.kairos.app.data.remote.dto.StartProgramRequest
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.kairos.app.ui.common.AnimatedDialog

private val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val SETUP_STEPS = listOf("Days", "Lifts", "Extras", "Maxes")

/** A companion lift's set choices (the web's SCHEME_CHOICES without the wave). */
private val COMPANION_SCHEMES = listOf(
    "" to "Any sets",
    "same:3x5" to "3 × 5",
    "same:5x5" to "5 × 5",
    "range:3x8-12" to "3 × 8–12",
    "range:3x10-15" to "3 × 10–15",
)

/** "same:5x15" → "5 × 15", "range:5x10-20" → "5 × 10–20". */
private fun schemeText(key: String): String {
    Regex("^same:(\\d+)x(\\d+)$").find(key)?.let { return "${it.groupValues[1]} × ${it.groupValues[2]}" }
    Regex("^range:(\\d+)x(\\d+)-(\\d+)$").find(key)?.let {
        return "${it.groupValues[1]} × ${it.groupValues[2]}–${it.groupValues[3]}"
    }
    return if (key.isBlank()) "Any sets" else key
}

/** A main lift's tag: 5/3/1, or Waves for wave loading. */
private fun mainTag(programKey: String): String = when {
    programKey.startsWith("wendler") -> "5/3/1"
    programKey == "wave-loading" -> "Waves"
    else -> "Main"
}

/**
 * Programs: the running one (its week, training maxes, Change and End, and
 * the end-of-cycle window), or choosing one: Kairos programs, My plans or the
 * household's plans (server 0.620). A program is set up a step at a time —
 * days, lifts, extras, training maxes — and starting it opens the plan editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    startPicking: Boolean = false,
    onEditPlan: () -> Unit = onDone,
    /** A rotation is chosen with the programs, not in the plan editor (v0.432). */
    onOpenRotation: (() -> Unit)? = null,
) {
    val container = rememberContainer()
    val session = container.sessionRepository
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<ProgramsDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(startPicking) }
    var tab by remember { mutableStateOf("programs") }
    var setupKey by remember { mutableStateOf<String?>(null) }
    // A program with the person's own version: Your version or Default first (v0.476).
    var asking by remember { mutableStateOf<String?>(null) }
    var useVersion by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    var cycleOpen by remember { mutableStateOf(false) }
    // "Next cycle, with changes": the next cycle's training maxes, with the
    // setup opened on the plan as it is (v0.434).
    var changing by remember { mutableStateOf<Map<String, Double>?>(null) }

    fun reload() {
        scope.launch {
            try {
                val d = session.loadPrograms()
                data = d
                cycleOpen = d.active?.blockDone == true
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
    val version = setup?.let { s -> data?.versions?.get(s.key) }?.takeIf { useVersion }
    fun pick(key: String) {
        useVersion = false
        if (data?.versions?.containsKey(key) == true) asking = key else setupKey = key
    }
    val nextSetup = data?.setup?.takeIf { changing != null }
    val nextProgram = nextSetup?.let { ns -> data?.choices?.firstOrNull { it.key == ns.key } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(nextProgram?.name ?: setup?.name ?: if (picking && data?.active != null) "Change program" else "Programs")
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                nextProgram != null -> changing = null
                                setup != null -> setupKey = null
                                picking && data?.active != null && !startPicking -> picking = false
                                else -> onBack()
                            }
                        },
                    ) {
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
                nextProgram != null && nextSetup != null -> ProgramSetup(
                    program = nextProgram,
                    current = d.current,
                    warmups = d.warmups,
                    busy = busy,
                    error = error,
                    initial = nextSetup,
                    nextMaxes = changing,
                    onStart = { req ->
                        act(block = { session.startProgram(req.copy(next = true)) }) {
                            changing = null
                            reload()
                        }
                    },
                )
                setup != null -> key(setup.key, version != null) {
                    ProgramSetup(
                        program = setup,
                        current = version?.extras ?: d.current,
                        warmups = d.warmups,
                        busy = busy,
                        error = error,
                        initial = version?.setup,
                        nextMaxes = version?.setup?.maxes,
                        fromVersion = version != null,
                        onStart = { req ->
                            act(block = { session.startProgram(req) }) {
                                setupKey = null
                                picking = false
                                onEditPlan()
                            }
                        },
                    )
                }
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
                            onCycle = { cycleOpen = true },
                            onChange = { picking = true },
                            onEnd = { ending = true },
                        )
                    } else {
                        ChooserTabs(selected = tab, onSelect = { tab = it })
                        when (tab) {
                            "programs" -> {
                                d.choices.forEach { c ->
                                    ProgramChoiceCard(c = c, edited = d.versions.containsKey(c.key), onPick = { pick(c.key) })
                                }
                                onOpenRotation?.let { open ->
                                    OutlinedCard(onClick = open, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Rotation", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "A cycle of workouts that repeats every few days, whatever the weekday.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            else -> PlanLibrary(mine = tab == "mine", onUsed = onDone)
                        }
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    val askingChoice = asking?.let { k -> data?.choices?.firstOrNull { it.key == k } }
    val askingVersion = asking?.let { k -> data?.versions?.get(k) }
    if (askingChoice != null && askingVersion != null) {
        AnimatedDialog(
            onDismissRequest = { asking = null },
            title = askingChoice.name,
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val k = askingChoice.key
                        act(block = { session.forgetProgramVersion(k) }) {
                            useVersion = false
                            setupKey = k
                            asking = null
                            reload()
                        }
                    },
                ) {
                    Text("Forget my version")
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgramPill(
                    label = "Your version · ${shortDate(askingVersion.savedOn)}",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        useVersion = true
                        setupKey = askingChoice.key
                        asking = null
                    },
                )
                ProgramPill(
                    label = "Default",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        useVersion = false
                        setupKey = askingChoice.key
                        asking = null
                    },
                )
            }
        }
    }

    if (ending) {
        AnimatedDialog(
            onDismissRequest = { ending = false },
            title = "End ${data?.active?.name ?: "program"}?",
            confirmButton = {
                TextButton(onClick = { ending = false; act(block = { session.endProgram(restore = true) }) { onDone() } }) {
                    Text("Go back to my old plan")
                }
            },
            dismissButton = {
                TextButton(onClick = { ending = false; act(block = { session.endProgram(restore = false) }) { onDone() } }) {
                    Text("Keep this plan")
                }
            },
        ) {}
    }

    val active = data?.active
    if (cycleOpen && active != null && active.blockDone) {
        CycleEndDialog(
            info = active,
            busy = busy,
            canChange = data?.setup?.let { ns -> data?.choices?.any { it.key == ns.key } } == true,
            onChanges = { raises ->
                cycleOpen = false
                changing = active.maxes.associate { it.poolExerciseId to it.weight + (raises[it.poolExerciseId] ?: 0.0) }
            },
            onClose = { cycleOpen = false },
            onNext = { raises ->
                cycleOpen = false
                act(block = { session.nextProgramBlock(raise = false, raises = raises) })
            },
            onSwitch = {
                cycleOpen = false
                picking = true
            },
        )
    }
}

@Composable
private fun ChooserTabs(selected: String, onSelect: (String) -> Unit) {
    val tabs = listOf("programs" to "Programs", "mine" to "My plans", "household" to "Household")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActiveProgramCard(
    info: ProgramInfoDto,
    onCycle: () -> Unit,
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
                ProgramPill(label = "Cycle done · what's next?", filled = true, onClick = onCycle)
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
                    MaxRow(name = m.name, weight = m.weight)
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
/** A training max, shown only (v0.495): it holds for the program and changes
 *  at the end of a cycle (Next cycle, or with changes) or by restarting. */
private fun MaxRow(name: String, weight: Double) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(MovementSetsViewModel.fmt(weight), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NumberField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(6)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(88.dp).semantics { contentDescription = label },
    )
}

/** End of a cycle: each lift's raise (+5 upper, +10 lower to start; 0 holds
 *  it), Next cycle, Repeat, or Switch program. */
@Composable
private fun CycleEndDialog(
    info: ProgramInfoDto,
    busy: Boolean,
    /** "Next cycle, with changes" opens the setup on the plan as it is. */
    canChange: Boolean,
    onChanges: (Map<String, Double>) -> Unit,
    onClose: () -> Unit,
    onNext: (Map<String, Double>) -> Unit,
    onSwitch: () -> Unit,
) {
    val by = remember(info) {
        mutableStateMapOf<String, String>().apply { info.maxes.forEach { put(it.poolExerciseId, MovementSetsViewModel.fmt(it.step)) } }
    }
    fun amount(id: String): Double = by[id]?.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0
    AnimatedDialog(
        onDismissRequest = onClose,
        title = "Cycle done",
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = { onNext(info.maxes.associate { it.poolExerciseId to amount(it.poolExerciseId) }) },
            ) { Text("Next cycle") }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = { onNext(info.maxes.associate { it.poolExerciseId to 0.0 }) },
            ) { Text("Repeat") }
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            info.maxes.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${MovementSetsViewModel.fmt(m.weight)} → ${MovementSetsViewModel.fmt(m.weight + amount(m.poolExerciseId))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("+", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NumberField(value = by[m.poolExerciseId] ?: "", label = "Raise ${m.name} by", onChange = { by[m.poolExerciseId] = it })
                }
            }
            if (canChange) {
                TextButton(
                    enabled = !busy,
                    onClick = { onChanges(info.maxes.associate { it.poolExerciseId to amount(it.poolExerciseId) }) },
                ) { Text("Next cycle, with changes") }
            }
            TextButton(onClick = onSwitch) { Text("Switch program") }
        }
    }
}

@Composable
private fun ProgramChoiceCard(c: ProgramChoiceDto, edited: Boolean = false, onPick: () -> Unit) {
    OutlinedCard(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    if (edited) {
                        Text(
                            "EDITED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    "${c.days.size} days · ${c.weeks} wk",
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
internal fun ProgramPill(
    label: String,
    filled: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
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

/** A row of the seven weekdays; `value` holds the ones on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(value: List<Int>, onToggle: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEKDAYS.forEachIndexed { d, label ->
            val on = d in value
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onToggle(d) },
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
}

private fun MutableList<AddedLift>.update(n: Int, change: (AddedLift) -> AddedLift) {
    val i = indexOfFirst { it.n == n }
    if (i >= 0) this[i] = change(this[i])
}

private data class AddedLift(val n: Int, val day: Int, val poolExerciseId: String = "", val scheme: String = "range:3x8-12")

/**
 * Setting a program up, a step at a time (the web's ProgramSetup): a weekday
 * for each program day (two may share one); the lifts — main lifts, then
 * companion lifts with their own sets, swapped, removed or added to; extras
 * (warm-up, cardio and conditioning days, starting from the plan you have);
 * and training maxes (90% of the heaviest you've logged, editable).
 */
@Composable
private fun ProgramSetup(
    program: ProgramChoiceDto,
    current: CurrentExtrasDto,
    warmups: List<com.kairos.app.data.remote.dto.WarmupRoutineDto>,
    busy: Boolean,
    error: String?,
    /** Open on the running program's plan (the next cycle, with changes). */
    initial: com.kairos.app.data.remote.dto.CurrentSetupDto? = null,
    /** The next cycle's training maxes: the Maxes step edits these directly. */
    nextMaxes: Map<String, Double>? = null,
    /** Started from the person's own version (v0.476): its training maxes, and
     *  90% of the best logged for a lift it has none for. */
    fromVersion: Boolean = false,
    onStart: (StartProgramRequest) -> Unit,
) {
    var step by remember(program.key) { mutableStateOf(0) }
    val days = remember(program.key) {
        mutableStateListOf<Int>().apply {
            addAll(initial?.days?.takeIf { it.size == program.days.size } ?: program.defaultDays)
        }
    }
    val picks = remember(program.key) {
        mutableStateMapOf<String, String?>().apply {
            if (initial != null) putAll(initial.picks)
            else program.days.forEachIndexed { i, d -> d.roles.forEach { r -> put("$i:${r.key}", r.defaultId) } }
        }
    }
    val removed = remember(program.key) { mutableStateListOf<String>().apply { addAll(initial?.removed.orEmpty()) } }
    val schemes = remember(program.key) { mutableStateMapOf<String, String>().apply { putAll(initial?.schemes.orEmpty()) } }
    val added = remember(program.key) {
        mutableStateListOf<AddedLift>().apply {
            initial?.added.orEmpty().forEachIndexed { n, a ->
                add(AddedLift(n = n + 1, day = a.day, poolExerciseId = a.poolExerciseId, scheme = a.scheme ?: ""))
            }
        }
    }
    // "none", "" (just a check) or a routine id; starts from the plan's own (v0.430).
    var warmup by remember(program.key) { mutableStateOf(if (current.warmup) current.warmupRoutine ?: "" else "") }
    var cardio by remember(program.key) { mutableStateOf<List<Int>?>(null) }
    var conditioning by remember(program.key) { mutableStateOf(current.conditioningDays) }
    val maxText = remember(program.key) { mutableStateMapOf<String, String>() }
    var tmHelp by remember { mutableStateOf(false) }
    if (tmHelp) {
        AnimatedDialog(
            onDismissRequest = { tmHelp = false },
            title = "Training max",
            confirmButton = { TextButton(onClick = { tmHelp = false }) { Text("OK") } },
        ) {
            Text(
                "The program works from 90% of your max, its training max. Every set is a share of it (65%, 75%, 85%\u2026), " +
                    "which keeps the weights ones you can lift well and leaves room to keep going up. Enter your best; the app does the rest.",
            )
        }
    }

    val allOptions = remember(program.key) {
        program.days.flatMap { d -> d.roles.flatMap { it.options } }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }
    val bestOf = remember(program.key) { allOptions.mapNotNull { o -> o.best?.let { o.id to it } }.toMap() }
    val rowDays = program.days.mapIndexedNotNull { i, d -> if (d.row) days.getOrNull(i) else null }
    val cardioDays = cardio ?: (current.cardioDays + rowDays).distinct().sorted()
    val mains = program.days.flatMapIndexed { i, d ->
        d.roles.filter { it.main && "$i:${it.key}" !in removed }.mapNotNull { r ->
            val id = picks["$i:${r.key}"] ?: return@mapNotNull null
            r.options.firstOrNull { it.id == id }?.let { id to it.name }
        }
    }.distinctBy { it.first }
    // The max asked for is your best (pre-filled from what you've logged); the
    // training max is 90% of it, shown under the field (v0.429).
    // The next cycle: the field is the training max itself, already raised.
    fun maxFor(id: String): String = maxText[id]
        ?: if (nextMaxes != null) {
            nextMaxes[id]?.let { MovementSetsViewModel.fmt(it) }
                ?: bestOf[id]?.takeIf { fromVersion }?.let { MovementSetsViewModel.fmt(((it * 0.9) / 5.0).roundToInt() * 5.0) }
                ?: ""
        }
        else bestOf[id]?.let { MovementSetsViewModel.fmt(it) } ?: ""
    fun tmOf(id: String): Double? = maxFor(id).toDoubleOrNull()?.takeIf { it > 0 }?.let {
        if (nextMaxes != null) it else ((it * 0.9) / 5.0).roundToInt() * 5.0
    }
    fun toggle(list: List<Int>, d: Int): List<Int> = if (d in list) list - d else (list + d).sorted()

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SETUP_STEPS.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Text(
                SETUP_STEPS[step].uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (step) {
                0 -> {
                    Text(program.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    program.days.forEachIndexed { i, d ->
                        Text(d.name, style = MaterialTheme.typography.titleSmall)
                        DayChips(value = listOfNotNull(days.getOrNull(i)), onToggle = { w -> days[i] = w })
                    }
                }
                // In weekday order, whatever order the program lists its days (v0.432).
                1 -> program.days.withIndex().sortedWith(compareBy<IndexedValue<com.kairos.app.data.remote.dto.ProgramDayDto>>({ days[it.index] }, { it.index })).forEach { (i, d) ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${d.name} · ${WEEKDAYS[days[i]]}", style = MaterialTheme.typography.titleSmall)
                            val roles = d.roles.filter { "$i:${it.key}" !in removed }
                            roles.filter { it.main }.forEach { r ->
                                val k = "$i:${r.key}"
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        mainTag(program.key),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                    Box(Modifier.weight(1f)) {
                                        Dropdown(
                                            label = "",
                                            title = r.label,
                                            selected = r.options.firstOrNull { it.id == picks[k] }?.name ?: "${r.label}: choose",
                                            options = r.options.map { it.id to it.name },
                                            onSelect = { picks[k] = it.ifBlank { null } },
                                        )
                                    }
                                    r.options.firstOrNull { it.id == picks[k] }?.let { o -> PickedInfo(name = o.name, about = o.about) }
                                }
                            }
                            val companions = roles.filter { !it.main }
                            val extras = added.filter { it.day == i }
                            if (companions.isNotEmpty() || extras.isNotEmpty()) {
                                Text(
                                    "Companion exercises",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            companions.forEach { r ->
                                val k = "$i:${r.key}"
                                CompanionRow(
                                    label = r.label,
                                    options = r.options.map { it.id to (if (it.add) "${it.name} \u00b7 new" else it.name) },
                                    timedIds = r.options.filter { it.timed }.map { it.id }.toSet(),
                                    abouts = r.options.associate { it.id to (it.name to it.about) },
                                    value = picks[k] ?: "",
                                    scheme = schemes[k] ?: r.scheme,
                                    onPick = { picks[k] = it.ifBlank { null } },
                                    onScheme = { schemes[k] = it },
                                    onRemove = { removed.add(k) },
                                )
                            }
                            extras.forEach { a ->
                                CompanionRow(
                                    label = "Added exercise",
                                    options = allOptions.map { it.id to it.name },
                                    timedIds = allOptions.filter { it.timed }.map { it.id }.toSet(),
                                    abouts = allOptions.associate { it.id to (it.name to it.about) },
                                    value = a.poolExerciseId,
                                    scheme = a.scheme,
                                    onPick = { id -> added.update(n = a.n) { it.copy(poolExerciseId = id) } },
                                    onScheme = { s -> added.update(n = a.n) { it.copy(scheme = s) } },
                                    onRemove = { added.removeAll { it.n == a.n } },
                                )
                            }
                            Text(
                                "+ Add exercise",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { added.add(AddedLift(n = (added.maxOfOrNull { it.n } ?: 0) + 1, day = i)) }
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                }
                2 -> {
                    val warmupOptions = listOf("none" to "None", "" to "Just a check") + warmups.map { it.id to it.name }
                    Dropdown(
                        label = "Warm-up",
                        selected = warmupOptions.firstOrNull { it.first == warmup }?.second ?: "Just a check",
                        options = warmupOptions,
                        onSelect = { warmup = it },
                    )
                    Text("Cardio", style = MaterialTheme.typography.titleSmall)
                    DayChips(value = cardioDays, onToggle = { d -> cardio = toggle(cardioDays, d) })
                    Text("Conditioning", style = MaterialTheme.typography.titleSmall)
                    DayChips(value = conditioning, onToggle = { d -> conditioning = toggle(conditioning, d) })
                }
                else -> {
                    if (mains.isEmpty()) {
                        Text("No main lifts.", style = MaterialTheme.typography.bodyMedium)
                    }
                    mains.forEachIndexed { i, (id, name) ->
                        if (i > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                bestOf[id]?.let {
                                    Text(
                                        "Best ${MovementSetsViewModel.fmt(it)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                NumberField(value = maxFor(id), label = if (nextMaxes != null) "$name training max" else "$name max", onChange = { maxText[id] = it })
                                if (nextMaxes != null) {
                                    Text(
                                        "Training max",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                } else tmOf(id)?.let {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { tmHelp = true }.padding(top = 2.dp),
                                    ) {
                                        Text(
                                            "Training max ${MovementSetsViewModel.fmt(it)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Icon(
                                            KairosIcons.Info,
                                            contentDescription = "What's a training max?",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 4.dp).size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (step > 0) ProgramPill(label = "Back", onClick = { step -= 1 })
            if (step < SETUP_STEPS.size - 1) {
                ProgramPill(label = "Next", filled = true, modifier = Modifier.weight(1f), onClick = { step += 1 })
            } else {
                ProgramPill(
                    label = if (busy) "Starting…" else if (nextMaxes != null && !fromVersion) "Start next cycle" else "Start program",
                    filled = true,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onStart(
                            StartProgramRequest(
                                key = program.key,
                                days = days.toList(),
                                picks = picks.mapValues { (k, v) -> if (k in removed) null else v },
                                tops = emptyMap(),
                                maxes = mains.mapNotNull { (id, _) -> tmOf(id)?.let { id to it } }.toMap(),
                                schemes = schemes.toMap(),
                                added = added.filter { it.poolExerciseId.isNotBlank() }.map {
                                    AddedLiftRequest(day = it.day, poolExerciseId = it.poolExerciseId, scheme = it.scheme.ifBlank { null })
                                },
                                warmup = warmup != "none",
                                warmupRoutine = warmup.takeIf { it != "none" },
                                cardioDays = cardioDays,
                                conditioningDays = conditioning,
                            ),
                        )
                    },
                )
            }
        }
    }
}

/** ⓘ beside a chosen movement: its how-to and "Watch how" (v0.434). */
@Composable
private fun PickedInfo(name: String, about: String?) {
    var open by remember { mutableStateOf(false) }
    MovementInfoButton(name = name, onClick = { open = true })
    if (open) MovementInfoDialog(name = name, about = about, onDismiss = { open = false })
}

/** A companion lift: what kind it is, its movement, its sets (Time for a
 *  timed one), and remove. */
@Composable
private fun CompanionRow(
    label: String,
    options: List<Pair<String, String>>,
    timedIds: Set<String>,
    /** Each option's name and how-to, for the ⓘ beside the chosen one. */
    abouts: Map<String, Pair<String, String?>>,
    value: String,
    scheme: String,
    onPick: (String) -> Unit,
    onScheme: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val schemeOptions = if (COMPANION_SCHEMES.any { it.first == scheme }) COMPANION_SCHEMES else listOf(scheme to schemeText(scheme)) + COMPANION_SCHEMES
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) {
                Dropdown(
                    label = "",
                    title = label,
                    selected = options.firstOrNull { it.first == value }?.second ?: "Choose",
                    options = options,
                    onSelect = onPick,
                )
            }
            abouts[value]?.let { (n, about) -> PickedInfo(name = n, about = about) }
            if (value in timedIds) {
                Text(
                    "Time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            } else {
                Dropdown(
                    label = "",
                    title = "Sets",
                    selected = schemeText(scheme),
                    options = schemeOptions,
                    onSelect = onScheme,
                    compact = true,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(KairosIcons.Close, contentDescription = "Remove $label", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * My plans (saved for later, only yours) or the household's approved plans:
 * a list, and picking one shows what it holds with Replace / Add. My plans
 * also saves the current plan, and renames or deletes a saved one.
 */
@Composable
private fun PlanLibrary(mine: Boolean, onUsed: () -> Unit) {
    val session = rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    var rows by remember(mine) { mutableStateOf<List<Triple<String, String, String>>?>(null) }
    var error by remember(mine) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var preview by remember { mutableStateOf<ImportPreviewDto?>(null) }
    var renaming by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var deleting by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    fun load() {
        scope.launch {
            try {
                rows = if (mine) {
                    session.savedPlans().map { Triple(it.id, it.name, it.summary) }
                } else {
                    session.sharedPlans().filter { it.status == "APPROVED" }
                        .map { Triple(it.id, it.name, listOf(it.by, it.summary).filter { s -> s.isNotBlank() }.joinToString(" · ")) }
                }
                error = null
            } catch (e: ApiException) {
                error = e.error.message
            }
        }
    }
    LaunchedEffect(mine) { load() }

    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (e: ApiException) {
                error = e.error.message
            } finally {
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (mine) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    placeholder = { Text("Name this plan") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                ProgramPill(
                    label = "Save",
                    filled = true,
                    enabled = !busy && name.trim().length >= 2,
                    onClick = { act { session.savePlan(name.trim()); name = ""; load() } },
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        val list = rows
        when {
            list == null && error == null -> CircularProgressIndicator(Modifier.padding(8.dp))
            list != null && list.isEmpty() -> Text(
                if (mine) "No saved plans yet." else "No household plans yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            list != null -> list.forEach { p ->
                val shape = RoundedCornerShape(12.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                        .clickable {
                            picked = p
                            preview = null
                            act { preview = session.previewSharedPlan(p.first) }
                        }
                        .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(p.second, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (p.third.isNotBlank()) {
                            Text(
                                p.third,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (mine) {
                        IconButton(onClick = { renaming = p }) {
                            Icon(KairosIcons.Pencil, contentDescription = "Rename ${p.second}", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { deleting = p }) {
                            Icon(KairosIcons.Trash, contentDescription = "Delete ${p.second}", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    picked?.let { p ->
        val pv = preview
        AnimatedDialog(
            onDismissRequest = { picked = null },
            title = p.second,
            confirmButton = {
                if (pv != null && pv.entries > 0) {
                    TextButton(enabled = !busy, onClick = { act { session.useSharedPlan(p.first, "replace"); picked = null; onUsed() } }) {
                        Text("Replace my plan")
                    }
                }
            },
            dismissButton = {
                if (pv != null) {
                    TextButton(enabled = !busy, onClick = { act { session.useSharedPlan(p.first, "add"); picked = null; onUsed() } }) {
                        Text(if (pv.entries > 0) "Add to my plan" else "Add the workouts")
                    }
                }
            },
        ) {
            if (pv == null) {
                CircularProgressIndicator(Modifier.padding(8.dp))
            } else {
                Text(pv.summary.ifBlank { "No plan, workouts only." }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    renaming?.let { p ->
        var newName by remember(p.first) { mutableStateOf(p.second) }
        AnimatedDialog(
            onDismissRequest = { renaming = null },
            title = "Rename",
            confirmButton = {
                TextButton(
                    enabled = !busy && newName.trim().length >= 2,
                    onClick = { act { session.renameSavedPlan(p.first, newName.trim()); renaming = null; load() } },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        ) {
            OutlinedTextField(value = newName, onValueChange = { newName = it.take(60) }, singleLine = true)
        }
    }

    deleting?.let { p ->
        AnimatedDialog(
            onDismissRequest = { deleting = null },
            title = "Delete ${p.second}?",
            confirmButton = {
                TextButton(enabled = !busy, onClick = { act { session.deleteSavedPlan(p.first); deleting = null; load() } }) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        ) {}
    }
}

/** "2026-11-01" → "Nov 1". */
private fun shortDate(iso: String): String {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3 || parts[1] !in 1..12) return iso
    return "${months[parts[1] - 1]} ${parts[2]}"
}
