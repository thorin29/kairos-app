package com.kairos.app.ui.workout

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kairos.app.data.remote.dto.PoolExerciseDto
import com.kairos.app.ui.common.AnimatedDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.CircularProgressIndicator
import com.kairos.app.ui.nav.KairosIcons
import com.kairos.app.ui.common.AccentOutlinedButton
import com.kairos.app.ui.theme.KairosThemeState
import kotlinx.coroutines.delay
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kairos.app.ui.common.rememberContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.widthIn
import kotlinx.coroutines.launch

/** Three digits wide. Every numeric entry box on this screen uses it, so a
 *  weight, a rep count and a seconds field are never three different sizes. */
private val NUM_FIELD_W = 72.dp

/**
 * The Log workout page. Shows every planned workout for the day (Core, Arms) as
 * its own card with a value per movement and a Log button, a per-movement Skip
 * (do part, skip the rest), and an Expire action to close a missed workout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLogScreen(
    date: String,
    onDone: () -> Unit,
    onOpenCalculator: () -> Unit,
    usedWeight: String?,
    onWeightConsumed: () -> Unit,
    onOpenSets: (com.kairos.app.ui.nav.Route.MovementSets) -> Unit = {},
    /** A Core day's screen (v0.446). */
    onOpenCore: (com.kairos.app.ui.nav.Route.CoreWorkout) -> Unit = {},
    setsSaved: Boolean = false,
    onSetsConsumed: () -> Unit = {},
    /** "Write your own" conditioning: the personal workout builder. */
    onWriteOwn: () -> Unit = {},
    /** "Is the plan too big?" (v0.486): the plan editor, or a smaller program. */
    onEditPlan: () -> Unit = {},
    onOpenPrograms: () -> Unit = {},
) {
    val container = rememberContainer()
    val vm: WorkoutLogViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WorkoutLogViewModel(container.sessionRepository, date, container.payloadCache) }
        },
    )
    val ui by vm.ui.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var editSport by remember { mutableStateOf<com.kairos.app.data.remote.dto.LoggedOtherDto?>(null) }
    // Which card's movement launched the calculator, so its returned weight goes
    // to the right input.
    // Survives navigating to the calculator and back (a plain remember is wiped
    // when this screen is disposed), so the returned weight reaches the card it
    // came from.
    var pendingTarget by rememberSaveable(
        stateSaver = listSaver(
            save = { pair -> pair?.let { listOf(it.first, it.second) } ?: emptyList() },
            restore = { if (it.size == 2) it[0] to it[1] else null },
        ),
    ) { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(ui.done) { if (ui.done) onDone() }
    // Pick one / Build one sheets, and the workout Pick one opens straight on
    // (this month's benchmark). Closed once something is logged.
    var conditioningSheet by remember { mutableStateOf<String?>(null) }
    var addingConditioning by remember { mutableStateOf(false) }
    // "+ Additional" (v0.477): its menu, then a mobility, stretching or isometric picker.
    var addingMenu by remember { mutableStateOf(false) }
    // + Additional → Family WOD (v0.489): set one (a parent) or suggest one.
    var familyForDay by remember { mutableStateOf(false) }
    var otherOpen by remember { mutableStateOf<com.kairos.app.data.remote.dto.LoggedOtherDto?>(null) }
    var extraKind by remember { mutableStateOf<String?>(null) }
    var openWorkoutId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.conditioningTick) { conditioningSheet = null }

    // Back from a movement's set logger: refresh its row.
    LaunchedEffect(setsSaved) {
        if (setsSaved) {
            vm.load()
            onSetsConsumed()
        }
    }

    LaunchedEffect(usedWeight) {
        val w = usedWeight
        val target = pendingTarget
        if (w != null && target != null) {
            vm.onValue(target.first, target.second, w)
            pendingTarget = null
            onWeightConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log workout") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
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
                ui.loadError != null -> Text(
                    ui.loadError!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val overdueBlocks = ui.blocks.filter { it.isOverdue }
                    val todayBlocks = ui.blocks.filter { !it.isOverdue }
                    val openCalc: (String, String) -> Unit = { blockKey, exId ->
                        pendingTarget = blockKey to exId
                        onOpenCalculator()
                    }
                    val today = ui.strip?.today?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()
                    // A day still to come: its plan as a preview only (v0.461).
                    val future = ui.date != null && ui.date!! > today
                    val openSets: (WorkoutBlock, MovementInput) -> Unit = { block, m ->
                        val day = block.date ?: ui.date ?: today
                        onOpenSets(
                            com.kairos.app.ui.nav.Route.MovementSets(
                                date = day,
                                plannedWorkoutId = block.plannedWorkoutId,
                                slotId = m.plannedExerciseId ?: m.poolExerciseId,
                                name = m.swappedFromName ?: m.name,
                                metric = m.metric,
                                unit = m.unit,
                                late = day < today,
                            ),
                        )
                    }
                    val openCore: (WorkoutBlock) -> Unit = { block ->
                        val day = block.date ?: ui.date ?: today
                        onOpenCore(
                            com.kairos.app.ui.nav.Route.CoreWorkout(
                                date = day,
                                plannedWorkoutId = block.plannedWorkoutId,
                                title = block.name,
                                moves = encodeCoreMoves(
                                    block.inputs.filter { it.tracked }.map { m ->
                                        CoreMoveRef(
                                            slotId = m.plannedExerciseId ?: m.poolExerciseId,
                                            name = m.swappedFromName ?: m.name,
                                            metric = m.metric,
                                            unit = m.unit,
                                        )
                                    },
                                ),
                                late = day < today,
                            ),
                        )
                    }

                    ui.date?.let { d ->
                        DayHeader(
                            date = d,
                            today = today,
                            title = ui.title.ifBlank {
                                ui.planName ?: when {
                                    todayBlocks.isNotEmpty() -> "Workout"
                                    // Short labels: a game by its event type ("Hockey game").
                                    ui.loggedOther.isNotEmpty() -> ui.loggedOther.map { it.label ?: it.name }.distinct().joinToString(" \u00b7 ")
                                    else -> "Nothing planned"
                                }
                            },
                            logged = ui.loggedAny,
                            planned = todayBlocks.isNotEmpty() || ui.simple.isNotEmpty() || ui.picks.isNotEmpty(),
                            // A day still to come has nothing done yet: no bar (v0.462).
                            progress = if (future) 0 to 0 else dayProgress(todayBlocks.filter { !it.extra }).let { (d, t) ->
                                // Warm-up and Cardio plans count like any block; what was
                                // added to the day only (v0.477) doesn't.
                                val planned = ui.simple.filter { !it.extra }
                                (d + planned.count { it.done || it.skipped } + ui.picks.count { it.done.isNotEmpty() || it.skipped }) to
                                    (t + planned.size + ui.picks.size)
                            },
                            strip = ui.strip,
                            programLine = ui.programLine,
                            light = ui.light,
                            adjusted = ui.adjusted?.label,
                            onUndoAdjust = { vm.adjust(req = null) },
                            onPickDate = { showDatePicker = true },
                            onPickDay = { vm.setDate(it) },
                            stripPage = { ui.stripPages[it] },
                            onNeedPage = { vm.loadStripPage(it) },
                            onToday = { ui.strip?.today?.let { vm.setDate(it) } },
                        )
                    }
                    // A paused day (v0.488): household or the person's own.
                    ui.paused?.let { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    // A day traded with another weekday this week (v0.487).
                    ui.swappedWith?.let { other ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Swapped with $other this week",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            TextButton(onClick = { vm.undoDaySwap() }, enabled = !ui.saving) { Text("Undo") }
                        }
                    }
                    // Resting or skipping the whole day lives here, with the
                    // day (v0.432; it was on the Workouts page).
                    val canRest = ui.date != null && !future && !ui.loggedAny && !ui.rested && !ui.restPlanned && !ui.loading
                    // Adjust today (v0.468; was Go light): today only, until it's chosen.
                    val canAdjust = ui.date == today && !ui.rested && !ui.restPlanned && !ui.loading && ui.adjusted == null
                    if (canRest || canAdjust) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (canRest) {
                                AccentOutlinedButton(onClick = { vm.restDay() }, enabled = !ui.saving) {
                                    Icon(KairosIcons.Moon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Rest / skip day", modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            if (canAdjust) {
                                AccentOutlinedButton(onClick = { showAdjust = true }, enabled = !ui.saving) {
                                    Text("Adjust today")
                                }
                            }
                        }
                    }
                    editSport?.let { g ->
                        val sp = g.sport
                        if (sp != null) {
                            SportEditDialog(
                                title = g.label ?: g.name,
                                sport = sp,
                                onSave = { minutes, effort, hurt ->
                                    vm.editSport(sessionId = sp.sessionId, minutes = minutes, effort = effort, hurt = hurt)
                                    editSport = null
                                },
                                onDismiss = { editSport = null },
                            )
                        }
                    }
                    if (showAdjust) {
                        AdjustTodayScreen(
                            busy = ui.saving,
                            onShort = { minutes, picks, cardioMin, onAnswer ->
                                vm.adjustShort(minutes = minutes, picks = picks, cardioMin = cardioMin, onAnswer = onAnswer)
                            },
                            onPreview = { mode, how, area, replace, onPreview ->
                                vm.adjustPreview(mode = mode, how = how, area = area, replace = replace, onPreview = onPreview)
                            },
                            sore = ui.sore,
                            onConfirm = { req ->
                                vm.adjust(req = req)
                                showAdjust = false
                            },
                            onRest = {
                                vm.restDay()
                                showAdjust = false
                            },
                            onDismiss = { showAdjust = false },
                        )
                    }

                    ui.suggestion?.takeIf { !ui.restPlanned && !future }?.let { sg ->
                        SuggestionCard(
                            title = sg.title,
                            why = sg.why,
                            actions = sg.actions.map { it.answer to it.label },
                        ) { answer ->
                            vm.answerDaySuggestion(answer)
                            if (answer == "editplan") onEditPlan() else if (answer == "programs") onOpenPrograms()
                        }
                    }

                    ui.benchmark?.takeIf { !ui.restPlanned && !future }?.let { b ->
                        BenchmarkCard(
                            benchmark = b,
                            onAnswer = { vm.answerBenchmark(it) },
                            onDo = { id ->
                                openWorkoutId = id
                                conditioningSheet = "pick"
                            },
                        )
                    }

                    // The day's family WOD (v0.489): on rest and paused days too.
                    ui.familyWod?.let { fw ->
                        FamilyWodDayCard(
                            wod = fw,
                            date = ui.date ?: today,
                            onLog = { c, v, version ->
                                vm.logConditioning(hiitWorkoutId = c.hiitWorkoutId, built = null, value = v, version = version)
                            },
                            onFeedback = { c, e, h -> vm.conditioningFeedback(plannedWorkoutId = c.plannedWorkoutId, effort = e, hurt = h) },
                            onJoin = { on -> vm.joinFamilyWod(id = fw.id, on = on) },
                        )
                    }

                    // Past days still open: the same sections, tinted, each
                    // labelled with its day.
                    overdueBlocks.groupBy { it.date ?: "" }.forEach { (d, blocks) ->
                        daySections(blocks).forEach { section ->
                            DaySectionView(
                                label = "${shortDay(d)} \u00b7 ${section.title}",
                                section = section,
                                overdue = true,
                                vm = vm,
                                onOpenCalculatorFor = openCalc,
                                onOpenSets = openSets,
                                onOpenCore = openCore,
                            )
                        }
                    }

                    val simpleKinds = buildSet {
                        ui.simple.forEach { add(simpleSection(it.category)) }
                        if (ui.cardio.isNotEmpty()) add("CARDIO")
                        // A sport on the day (v0.472): a Warm-up section for its warm-up.
                        if (ui.sportDay != null) add("WARMUP")
                        // Always: + Conditioning sits where the person's order
                        // puts conditioning, planned or not (v0.433).
                        add("CONDITIONING")
                    }
                    // A rest day in the plan has no sections, + Conditioning
                    // included: just what was logged, below (v0.456).
                    val todaySections = if (ui.restPlanned) emptyList() else daySections(todayBlocks, ui.order, simpleKinds)
                    // A skipped day (Rest / skip) keeps its plan in view but faded,
                    // so nothing on it looks open (v0.460).
                    Column(
                        Modifier
                            .alpha(if (ui.rested) 0.4f else if (future) 0.55f else 1f)
                            // A day still to come only previews its plan (v0.461).
                            .then(if (future) Modifier.pointerInput(Unit) { consumeAllTaps() } else Modifier),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        todaySections.forEach { section ->
                            // A planned conditioning workout gets its own card with
                            // the timer; anything else in the section as before.
                            val conds = section.blocks.mapNotNull { b ->
                                ui.conditioning.firstOrNull { it.plannedWorkoutId == b.plannedWorkoutId }
                            }
                            val rest = section.blocks.filter { b -> ui.conditioning.none { it.plannedWorkoutId == b.plannedWorkoutId } }
                            if (rest.isEmpty()) {
                                SectionLabel(section.title)
                            } else {
                                DaySectionView(
                                    label = section.title,
                                    section = section.copy(blocks = rest),
                                    overdue = false,
                                    vm = vm,
                                    onOpenCalculatorFor = openCalc,
                                    onOpenSets = openSets,
                                    onOpenCore = openCore,
                                )
                            }
                            conds.forEach { c ->
                                val block = section.blocks.first { it.plannedWorkoutId == c.plannedWorkoutId }
                                ConditioningCard(
                                    workout = c,
                                    skipped = block.inputs.isNotEmpty() && block.inputs.all { it.skipped },
                                    onLog = { v, version ->
                                        vm.logConditioning(
                                            hiitWorkoutId = c.hiitWorkoutId,
                                            built = null,
                                            value = v,
                                            version = version,
                                        )
                                    },
                                    onSkip = { vm.setBlockSkipped(block.key, it) },
                                    onFeedback = { e, h -> vm.conditioningFeedback(plannedWorkoutId = c.plannedWorkoutId, effort = e, hurt = h) },
                                )
                            }
                            when (section.kind) {
                                "CONDITIONING" -> {
                                    ui.picks.forEach { p ->
                                        PickConditioningCard(
                                            plan = p,
                                            onPick = {
                                                openWorkoutId = null
                                                conditioningSheet = "pick"
                                            },
                                            onBuild = {
                                                // The starting version (Scaled for younger people) comes with the list.
                                                if (ui.choices == null) vm.loadChoices()
                                                conditioningSheet = "build"
                                            },
                                            onWriteOwn = onWriteOwn,
                                            onSkip = { vm.setPickSkipped(plannedWorkoutId = p.plannedWorkoutId, skipped = it) },
                                            onFeedback = { e, h -> vm.pickFeedback(plannedWorkoutId = p.plannedWorkoutId, effort = e, hurt = h) },
                                        )
                                    }
                                    // A one-off conditioning session on any day (v0.427): what's
                                    // been done that no card above shows, then + Conditioning.
                                    if (ui.picks.isEmpty()) {
                                        val planned = ui.conditioning.map { it.name.take(40) }.toSet() + listOfNotNull(ui.familyWod?.name?.take(40))
                                        val extra = ui.conditioningDone.filter { it.name.take(40) !in planned }
                                        if (extra.isNotEmpty()) {
                                            OutlinedCard(Modifier.fillMaxWidth()) {
                                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    extra.forEach { d ->
                                                        Text(
                                                            d.name + (d.result?.let { " \u00b7 $it" } ?: ""),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    AccentOutlinedButton(onClick = { addingMenu = true }) {
                                        Icon(KairosIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("Additional", modifier = Modifier.padding(start = 6.dp))
                                    }
                                }
                                // Mobility (v0.469) shows like a Warm-up: its routine, done with a tap.
                                "WARMUP", "MOBILITY", "STRETCH" -> {
                                  val sd = ui.sportDay
                                  ui.simple.filter { simpleSection(it.category) == section.kind }.forEach { w ->
                                    WarmupCard(
                                        plan = w,
                                        sport = if (w.category == "WARMUP" && sd != null) {
                                            SportChoice(label = sd.label, forSport = sd.forSport, onChange = { vm.setWarmupForSport(it) })
                                        } else {
                                            null
                                        },
                                        onDone = { vm.setWarmup(plannedWorkoutId = w.plannedWorkoutId, done = it) },
                                        onSkip = { vm.setSimpleSkipped(plannedWorkoutId = w.plannedWorkoutId, skipped = it) },
                                        onChanged = { vm.load() },
                                        onRemove = if (w.extra) ({ vm.removeExtra(id = w.plannedWorkoutId) }) else null,
                                    )
                                  }
                                  // The day's sport, optional, when the planned Warm-up isn't for it.
                                  val sr = sd?.routine
                                  if (section.kind == "WARMUP" && sd != null && !sd.forSport && sr != null) {
                                      SportWarmupCard(
                                          label = sd.label,
                                          routine = sr,
                                          state = sd.state,
                                          onState = { vm.setSportWarmupState(state = it) },
                                      )
                                  }
                                }
                                "CARDIO" -> CardioBlock(
                                    name = ui.simple.firstOrNull { it.category == "CARDIO" }?.name ?: "Cardio",
                                    entries = ui.cardio,
                                    concept2 = ui.concept2,
                                    checking = ui.concept2Checking,
                                    saving = ui.cardioSaving,
                                    error = ui.cardioError,
                                    skippedPlan = ui.simple.firstOrNull { it.category == "CARDIO" && it.skipped && ui.cardio.isEmpty() },
                                    onCheck = { vm.checkConcept2(force = true) },
                                    onUndo = { vm.undoConcept2(it) },
                                    onRemove = { vm.removeCardio(it) },
                                    onFeedback = { id, e, h -> vm.cardioFeedback(sessionId = id, effort = e, hurt = h) },
                                    onLog = { a, m, dist, sec, load, done ->
                                        vm.logCardio(activity = a, meters = m, distance = dist, seconds = sec, carried = load, onDone = done)
                                    },
                                    onSkip = ui.simple.firstOrNull { it.category == "CARDIO" }?.let { p ->
                                        { skipped: Boolean -> vm.setSimpleSkipped(plannedWorkoutId = p.plannedWorkoutId, skipped = skipped) }
                                    },
                                )
                            }
                        }
                    }

                    // What was logged outside the day's plans (an older plan, a
                    // one-off), so a past day shows what was done (v0.431).
                    if (ui.loggedOther.isNotEmpty()) {
                        Text(
                            "ADDITIONAL",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        // Name only (v0.482); tap for what was done.
                        ui.loggedOther.forEach { g ->
                            OutlinedCard(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { if (g.sport != null) editSport = g else otherOpen = g },
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(KairosIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(g.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            if (g.lines.size == 1 && g.lines[0].name == g.name) g.lines[0].summary
                                            else "${g.lines.size} " + if (g.lines.size == 1) "movement" else "movements",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    if (ui.actionError != null) {
                        Text(
                            ui.actionError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    ui.date?.let { d ->
                        CustomWorkoutForm(date = d, onLogged = onDone)
                    }
                }
            }
        }
    }

    otherOpen?.let { g ->
        AlertDialog(
            onDismissRequest = { otherOpen = null },
            title = { Text(g.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    g.lines.forEach { l ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(l.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(l.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { otherOpen = null }) { Text("Close") } },
        )
    }

    if (addingMenu) {
        AdditionalMenu(
            onConditioning = {
                addingMenu = false
                addingConditioning = true
            },
            onKind = {
                addingMenu = false
                extraKind = it
            },
            onDismiss = { addingMenu = false },
            onFamilyWod = if (ui.familyWod == null && (ui.date ?: LocalDate.now().toString()) >= LocalDate.now().toString()) {
                {
                    addingMenu = false
                    familyForDay = true
                }
            } else {
                null
            },
        )
    }
    if (familyForDay) {
        FamilyWodForDay(
            date = ui.date ?: LocalDate.now().toString(),
            onDone = {
                familyForDay = false
                vm.load()
            },
            onDismiss = { familyForDay = false },
        )
    }
    extraKind?.let { k ->
        ExtraPickerDialog(
            kind = k,
            onPick = { ref ->
                extraKind = null
                vm.addExtra(kind = k, ref = ref)
            },
            onBack = {
                extraKind = null
                addingMenu = true
            },
            onDismiss = { extraKind = null },
        )
    }

    if (addingConditioning) {
        AlertDialog(
            onDismissRequest = { addingConditioning = false },
            title = { Text("Conditioning") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            addingConditioning = false
                            openWorkoutId = null
                            conditioningSheet = "pick"
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pick one") }
                    AccentOutlinedButton(
                        onClick = {
                            addingConditioning = false
                            if (ui.choices == null) vm.loadChoices()
                            conditioningSheet = "build"
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Build one") }
                    AccentOutlinedButton(
                        onClick = {
                            addingConditioning = false
                            onWriteOwn()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Write your own") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addingConditioning = false }) { Text("Cancel") } },
        )
    }

    when (conditioningSheet) {
        "pick" -> PickOneSheet(
            choices = ui.choices,
            error = ui.choicesError,
            openId = openWorkoutId,
            onLoad = { vm.loadChoices() },
            onLog = { c, v, version -> vm.logConditioning(hiitWorkoutId = c.hiitWorkoutId, built = null, value = v, version = version) },
            onDismiss = { conditioningSheet = null },
        )
        "build" -> BuildOneSheet(
            built = ui.built,
            building = ui.building,
            saved = ui.builtSaved,
            startVersion = ui.choices?.version ?: "RX",
            onBuild = { m, f, fo, seed -> vm.build(minutes = m, format = f, focus = fo, seed = seed) },
            onLog = { v, version -> vm.logConditioning(hiitWorkoutId = null, built = ui.built?.built, value = v, version = version) },
            onSave = { vm.saveBuilt() },
            onDismiss = { conditioningSheet = null },
        )
    }

    val picking = ui.date
    if (showDatePicker && picking != null) {
        WorkoutDatePickerOverlay(
            dateIso = picking,
            onPick = { vm.setDate(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }

    ui.conflict?.let { c ->
        AlertDialog(
            onDismissRequest = { vm.dismissConflict() },
            title = { Text("Already logged") },
            text = {
                Text(
                    "You already logged ${c.name} today: ${c.summary}. " +
                        "Update it with the new value, or cancel?",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmReplace() }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissConflict() }) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun WorkoutActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    filled: Boolean = false,
    compact: Boolean = false,
    /** Overrides the tile's height (the set logger's bottom bar). */
    fixedHeight: androidx.compose.ui.unit.Dp? = null,
    onClick: () -> Unit,
) {
    val tileHeight = fixedHeight ?: if (compact) 56.dp else 84.dp
    // Icon/text color. A filled tile reads white on the system-color fill while
    // it's the primary action, then settles to the theme color once it greys out.
    // An outlined tile is the theme color while actionable and eases to grey after.
    val contentTarget = when {
        filled && highlighted -> MaterialTheme.colorScheme.onPrimary
        filled -> MaterialTheme.colorScheme.primary
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tint by animateColorAsState(contentTarget, animationSpec = tween(700), label = "tileTint")
    if (filled) {
        // "Log weight": starts as the system color with a white icon/text, then
        // slowly fades to a greyed-out button carrying theme-color icon/text once
        // the block is logged. The fill is pinned across the disabled (saving)
        // state too, so the in-progress button doesn't flash grey.
        val containerTarget =
            if (highlighted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
        val container by animateColorAsState(containerTarget, animationSpec = tween(700), label = "tileFill")
        Card(
            onClick = onClick,
            enabled = enabled,
            colors = CardDefaults.cardColors(
                containerColor = container,
                disabledContainerColor = container,
            ),
            modifier = modifier.height(tileHeight),
        ) { WorkoutActionTileBody(icon, label, tint, loading, compact) }
    } else {
        OutlinedCard(onClick = onClick, enabled = enabled, modifier = modifier.height(tileHeight)) {
            WorkoutActionTileBody(icon, label, tint, loading, compact)
        }
    }
}

@Composable
private fun WorkoutActionTileBody(
    icon: ImageVector,
    label: String,
    tint: Color,
    loading: Boolean,
    compact: Boolean = false,
) {
    val iconSize = if (compact) 16.dp else 22.dp
    Column(
        Modifier.fillMaxSize().padding(if (compact) 4.dp else 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(iconSize), strokeWidth = 2.dp, color = tint)
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
        Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
        Text(
            label,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = tint,
        )
    }
}

@Composable
private fun WorkoutBlockCard(
    block: WorkoutBlock,
    vm: WorkoutLogViewModel,
    onOpenCalculatorFor: (String, String) -> Unit,
) {
    // Overdue and today's cards are the same card. The only difference is the
    // background; they used to be two bodies with different button sizes and
    // wording, which made one screen look like two features.
    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors = if (block.isOverdue) {
            CardDefaults.outlinedCardColors(
                containerColor = if (KairosThemeState.dark) Color(0xFF3A1A1E) else Color(0xFFFEF2F2),
            )
        } else {
            CardDefaults.outlinedCardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BlockBody(block, vm, onOpenCalculatorFor)
        }
    }
}

/** One muscle group's movements in the swap picker, under its own heading. */
private data class SwapGroup(
    val key: String,
    val label: String,
    val items: List<PoolExerciseDto>,
)

/** "UPPER_BACK" -> "Upper back". The server sends the enum name. */
private fun muscleLabel(raw: String): String =
    raw.split('_').joinToString(" ") { it.lowercase() }
        .replaceFirstChar { it.uppercase() }

/** The contents of a planned block: name, movements, and its own action row.
 *  Used both as a card of its own and stacked inside a shared muscle card. */
@Composable
private fun BlockBody(
    block: WorkoutBlock,
    vm: WorkoutLogViewModel,
    onOpenCalculatorFor: (String, String) -> Unit,
    hideName: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!hideName) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    block.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (block.logged) {
                    Icon(
                        KairosIcons.Check,
                        contentDescription = "Logged",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
            // No summary list of movement names here: each movement names
            // itself directly above its own entry field, and printing them
            // again under the muscle group said everything twice.
            HorizontalDivider()
            if (block.inputs.isEmpty()) {
                // Nothing to enter, so the buttons belong to the plan itself.
                BlockActions(block, vm, onOpenCalculatorFor)
            } else {
                // Every movement gets its own fields AND its own buttons, under
                // a rule of its own. Two movements added to one plan in a single
                // go now read exactly like two plans sharing a muscle group —
                // how they were added no longer changes how they are logged.
                block.inputs.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider()
                    MovementRow(block.key, m, vm)
                    MovementActions(block, m, vm, onOpenCalculatorFor)
                }
            }
    }
}

/** One section of the day: "Chest", "Cardio"… and what's in it. */
private data class DaySectionUi(
    val key: String,
    val title: String,
    val blocks: List<WorkoutBlock>,
    val kind: String = "STRENGTH",
)

/** The section a done-with-a-tap plan sits in (Stretching routines, v0.473). */
private fun simpleSection(category: String?) = when (category) {
    "WARMUP" -> "WARMUP"
    "MOBILITY" -> "MOBILITY"
    "STRETCHING" -> "STRETCH"
    else -> "CARDIO"
}

/** The default order (blocks.ts DEFAULT_ORDER); the server sends the person's. */
private val DEFAULT_ORDER = listOf("WARMUP", "CARDIO", "STRENGTH", "CONDITIONING", "MOBILITY", "STRETCH", "SPORT")

/** The person's order made whole: known kinds once, missing ones where the
 *  default has them (blocks.ts cleanOrder), then OTHER. */
private fun sectionOrder(order: List<String>): List<String> {
    val out = order.filter { it in DEFAULT_ORDER }.distinct().toMutableList()
    DEFAULT_ORDER.forEachIndexed { at, k ->
        if (k in out) return@forEachIndexed
        val after = out.indexOfLast { DEFAULT_ORDER.indexOf(it) < at }
        out.add(after + 1, k)
    }
    return out + "OTHER"
}

/** Which section a plan belongs in, from its category — blocks.ts blockKind. */
private fun blockKind(b: WorkoutBlock): String = when (b.category) {
    "HIIT" -> "CONDITIONING"
    "CARDIO", "ROWING", "RUNNING", "RUCKING" -> "CARDIO"
    "WARMUP" -> "WARMUP"
    "MOBILITY" -> "MOBILITY"
    "STRETCHING" -> "STRETCH"
    "SPORT" -> "SPORT"
    null, "WEIGHTS", "ISOMETRIC" -> "STRENGTH"
    else -> "OTHER"
}

private val KIND_TITLE = mapOf(
    "WARMUP" to "Warm-up",
    "CARDIO" to "Cardio",
    "CONDITIONING" to "Conditioning",
    "MOBILITY" to "Mobility",
    "STRETCH" to "Stretching",
    "SPORT" to "Sport",
    "OTHER" to "Other",
)

/** The day's sections, in the person's order (blocks.ts daySections): strength
 *  by muscle group, two plans for the same group in one section. Warm-up and
 *  Cardio plans hold no movements, so they come as [simpleKinds]: a section
 *  each, with no blocks (blocks.ts withCardio for cardio done unplanned). */
private fun daySections(
    blocks: List<WorkoutBlock>,
    order: List<String> = DEFAULT_ORDER,
    simpleKinds: Set<String> = emptySet(),
): List<DaySectionUi> {
    val seen = LinkedHashMap<String, Pair<String, DaySectionUi>>()
    blocks.forEach { b ->
        val kind = blockKind(b)
        val mg = b.muscleGroup?.takeIf { it.isNotBlank() }
        // Isometric entries (v0.471) share one "Isometric" section.
        val iso = mg == null && b.category == "ISOMETRIC"
        val key = when {
            kind != "STRENGTH" -> "k:$kind"
            mg != null -> "mg:$mg"
            iso -> "cat:ISOMETRIC"
            else -> "w:${b.key}"
        }
        val title = when {
            kind != "STRENGTH" -> KIND_TITLE[kind] ?: b.name
            mg != null -> muscleLabel(mg)
            iso -> "Isometric"
            else -> b.name
        }
        val cur = seen[key]?.second
        seen[key] = kind to (
            if (cur != null) cur.copy(blocks = cur.blocks + b)
            else DaySectionUi(key = key, title = title, blocks = listOf(b), kind = kind)
            )
    }
    simpleKinds.forEach { kind ->
        val key = "k:$kind"
        if (seen[key] == null) {
            seen[key] = kind to DaySectionUi(key = key, title = KIND_TITLE[kind] ?: kind, blocks = emptyList(), kind = kind)
        }
    }
    return sectionOrder(order).flatMap { k -> seen.values.filter { it.first == k }.map { it.second } }
}

/** A strength plan whose every tracked movement is logged set by set — the
 *  same rule the web day page uses (blocks.ts daySections). */
internal fun opensSetLogger(b: WorkoutBlock): Boolean {
    val tracked = b.inputs.filter { it.tracked }
    return blockKind(b) == "STRENGTH" && tracked.isNotEmpty() &&
        tracked.all { it.metric == "WEIGHT" || it.metric == "REPS" || it.metric == "DURATION" }
}

private fun movementDone(m: MovementInput) = m.setCount > 0 || m.skipped || m.logged

/** Items done of items to do — blocks.ts dayProgress. */
private fun dayProgress(blocks: List<WorkoutBlock>): Pair<Int, Int> {
    var done = 0
    var total = 0
    blocks.forEach { b ->
        if (opensSetLogger(b)) {
            b.inputs.filter { it.tracked }.forEach { m ->
                total++
                if (movementDone(m)) done++
            }
        } else {
            total++
            if (b.logged) done++
        }
    }
    return done to total
}

/** "2026-10-29" -> "Thu 10/29". */
private fun shortDay(iso: String): String = try {
    val d = LocalDate.parse(iso)
    d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) +
        " ${d.monthValue}/${d.dayOfMonth}"
} catch (_: Exception) {
    iso
}

/** "Today", or "Thursday, October 29". */
private fun dayLine(iso: String, today: String): String = if (iso == today) "Today" else try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
} catch (_: Exception) {
    iso
}

/** The page header: the day, what's being trained, how far through it is, and
 *  the last 8 days. Tapping the day opens the date picker. */
@Composable
private fun DayHeader(
    date: String,
    today: String,
    title: String,
    progress: Pair<Int, Int>,
    strip: com.kairos.app.data.remote.dto.WorkoutStripDto?,
    programLine: String = "",
    light: Double? = null,
    /** A Go light day's option title (v0.464), with Undo. */
    adjusted: String? = null,
    onUndoAdjust: () -> Unit = {},
    /** Something was logged that day: a past day isn't "logging late" (v0.431). */
    logged: Boolean = false,
    /** Something was planned that day: a day with nothing planned is never late (v0.435). */
    planned: Boolean = true,
    onPickDate: () -> Unit,
    onPickDay: (String) -> Unit,
    stripPage: (Int) -> com.kairos.app.data.remote.dto.WorkoutStripDto? = { null },
    onNeedPage: (Int) -> Unit = {},
    onToday: () -> Unit = {},
) {
    // The strip scrolls day by day across ~3 months (v0.463). Its state lives
    // here so the reset button can sit in the card's corner.
    val stripDays = remember(today) { stripDayRange(today) }
    val todayIdx = STRIP_DAYS_BACK
    val firstFor = { iso: String ->
        (stripDays.indexOf(iso).takeIf { it >= 0 } ?: todayIdx).minus(STRIP_VISIBLE - 1)
            .coerceIn(0, stripDays.size - STRIP_VISIBLE)
    }
    val list = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = firstFor(date))
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val offToday by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { list.firstVisibleItemIndex != firstFor(today) }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          // The date takes all the room it needs; the reset icon sits at the end
          // (v0.470: a weighted spacer used to take half the row, wrapping
          // long dates like "Saturday, November 7").
          Row(
              Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
                Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPickDate() }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    KairosIcons.Calendar,
                    contentDescription = "Pick a day",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    dayLine(date, today) + if (date < today && planned && !logged) " \u00b7 logging late" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (date < today && planned && !logged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Back to today (v0.463): only when the strip or the day is elsewhere.
            if (strip != null && (offToday || date != today)) {
                androidx.compose.material3.IconButton(
                    onClick = {
                        scope.launch { list.animateScrollToItem(firstFor(today)) }
                        onToday()
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        KairosIcons.Refresh,
                        contentDescription = "Back to today",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
          }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (programLine.isNotBlank()) {
                Text(
                    programLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (adjusted != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        adjusted,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Undo",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onUndoAdjust() }.padding(horizontal = 4.dp),
                    )
                }
            } else if (light != null) {
                Text(
                    "Lighter today \u00b7 ${kotlin.math.round(light * 100).toInt()}% of the usual weights",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val (done, total) = progress
            if (total > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(
                        progress = { done / total.toFloat() },
                        modifier = Modifier.weight(1f),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        "$done of $total done",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            strip?.takeIf { it.days.isNotEmpty() }?.let {
                Spacer(Modifier.height(2.dp))
                ScrollingWeekStrip(
                    list = list,
                    days = stripDays,
                    first = it,
                    pageOf = stripPage,
                    onNeedPage = onNeedPage,
                    selected = date,
                    onPick = onPickDay,
                )
            }
        }
    }
}

/** A section: its label, then a card per movement (strength) or per plan. */
@Composable
private fun DaySectionView(
    label: String,
    section: DaySectionUi,
    overdue: Boolean,
    vm: WorkoutLogViewModel,
    onOpenCalculatorFor: (String, String) -> Unit,
    onOpenSets: (WorkoutBlock, MovementInput) -> Unit,
    onOpenCore: (WorkoutBlock) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.08f, androidx.compose.ui.unit.TextUnitType.Em),
            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
        section.blocks.forEach { block ->
            if (block.core && opensSetLogger(block)) {
                // The month's test is its own card, before the workout (v0.453;
                // Isometric's three holds share one card, v0.471).
                val tests = block.inputs.filter { it.poolExerciseId in block.coreTests }
                val testName = block.coreTestName ?: "Plank test"
                if (tests.isNotEmpty()) {
                    val testBlock = block.copy(name = testName, inputs = tests, coreTest = null, coreTests = emptyList())
                    if (tests.size == 1) {
                        MovementCard(
                            m = tests[0].copy(name = testName, swappedFromName = null),
                            overdue = overdue,
                            onOpen = { onOpenCore(testBlock) },
                        )
                    } else {
                        CoreDayCard(block = testBlock, overdue = overdue, onOpen = { onOpenCore(testBlock) })
                    }
                }
                val workout = if (tests.isNotEmpty()) block.copy(inputs = block.inputs - tests.toSet(), coreTest = null, coreTests = emptyList()) else block
                CoreDayCard(
                    block = workout,
                    overdue = overdue,
                    onOpen = { onOpenCore(workout) },
                    onRemove = if (block.extra) ({ vm.removeExtra(id = block.plannedWorkoutId) }) else null,
                )
            } else if (opensSetLogger(block)) {
                block.inputs.forEach { m ->
                    MovementCard(
                        m = m,
                        overdue = overdue,
                        onOpen = if (m.tracked) ({ onOpenSets(block, m) }) else null,
                    )
                }
            } else {
                WorkoutBlockCard(block, vm, onOpenCalculatorFor)
            }
        }
    }
}

/** A Core day on the day page: one card for the whole workout (v0.446). */
@Composable
private fun CoreDayCard(
    block: WorkoutBlock,
    overdue: Boolean,
    onOpen: () -> Unit,
    /** Added to this day only (v0.477): ✕ takes it off. */
    onRemove: (() -> Unit)? = null,
) {
    val tracked = block.inputs.filter { it.tracked }
    val done = tracked.count { it.setCount > 0 || (it.logged && !it.skipped) }
    val colors = if (overdue) CardDefaults.outlinedCardColors(containerColor = overdueTint())
    else CardDefaults.outlinedCardColors()
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = colors) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    block.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${tracked.size} " + (if (tracked.size == 1) "movement" else "movements") +
                        if (done > 0) " \u00b7 $done logged" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tracked.isNotEmpty() && done == tracked.size) StatusChip("Logged", positive = true)
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(KairosIcons.Close, contentDescription = "Remove ${block.name}", modifier = Modifier.size(18.dp))
                }
            }
            Icon(
                KairosIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun overdueTint(): Color = if (KairosThemeState.dark) Color(0xFF3A1A1E) else Color(0xFFFEF2F2)

/** One movement on the day page: name, today's sets or last time's, a Logged /
 *  Skipped chip, and a chevron into its set logger. */
@Composable
private fun MovementCard(m: MovementInput, overdue: Boolean, onOpen: (() -> Unit)?) {
    val logged = m.setCount > 0 || (m.logged && !m.skipped)
    val skipped = m.skipped && !logged
    val planned = m.swappedFromName ?: m.name
    val detail = when {
        onOpen == null -> "No log needed"
        logged -> (if (m.swappedFromName != null) "${m.name} \u00b7 " else "") + m.summary.ifBlank { "Logged" }
        skipped -> "Skipped today"
        m.last.isNotBlank() -> "Last time ${m.last}"
        else -> "Not logged yet"
    }
    val colors = if (overdue) CardDefaults.outlinedCardColors(containerColor = overdueTint())
    else CardDefaults.outlinedCardColors()
    val body: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                // Two lines before an ellipsis (v0.481): long names fit.
                Text(
                    planned,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (logged) StatusChip("Logged", positive = true)
            if (skipped) StatusChip("Skipped", positive = false)
            if (onOpen != null) {
                Icon(
                    KairosIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    if (onOpen != null) {
        OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = colors) { body() }
    } else {
        OutlinedCard(modifier = Modifier.fillMaxWidth(), colors = colors) { body() }
    }
}

/** Logged (green) / Skipped (grey), as on the web day page. */
@Composable
internal fun StatusChip(label: String, positive: Boolean) {
    val bg = when {
        positive && KairosThemeState.dark -> Color(0xFF0F2E25)
        positive -> Color(0xFFECFDF5)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        positive && KairosThemeState.dark -> Color(0xFF6EE7B7)
        positive -> Color(0xFF047857)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

private const val STRIP_PAGES_BACK = 8
private const val STRIP_PAGES_AHEAD = 4
/** Days the strip reaches (8-day server pages): ~2 months back, ~1 ahead. */
private const val STRIP_DAYS_BACK = STRIP_PAGES_BACK * 8
private const val STRIP_DAYS_AHEAD = STRIP_PAGES_AHEAD * 8
private const val STRIP_VISIBLE = 8

/** Every day the strip can show, oldest first; today is at STRIP_DAYS_BACK. */
private fun stripDayRange(today: String): List<String> {
    val t = runCatching { LocalDate.parse(today) }.getOrElse { LocalDate.now() }
    return (-STRIP_DAYS_BACK..STRIP_DAYS_AHEAD).map { t.plusDays(it.toLong()).toString() }
}

/** The server page (8 days, 0 = up to today) holding day [idx] of the range. */
private fun stripPageOf(idx: Int): Int {
    val off = idx - STRIP_DAYS_BACK
    return if (off > 0) (off + 7) / 8 else -((-off) / 8)
}

/**
 * The week strip, scrolled freely a day or many at a time (v0.463); ‹ ›
 * jump a week. Days are known up front, so a day whose dots haven't loaded
 * still shows its date. The strip owns its swipe (like the Bible cards), so
 * the rest of the page and the side menu behave as everywhere else.
 */
@Composable
private fun ScrollingWeekStrip(
    list: androidx.compose.foundation.lazy.LazyListState,
    days: List<String>,
    first: com.kairos.app.data.remote.dto.WorkoutStripDto,
    pageOf: (Int) -> com.kairos.app.data.remote.dto.WorkoutStripDto?,
    onNeedPage: (Int) -> Unit,
    selected: String?,
    onPick: (String) -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Ask for the pages in view as the strip moves (and after each day load).
    val visiblePages by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            val f = list.firstVisibleItemIndex
            setOf(stripPageOf(f), stripPageOf((f + STRIP_VISIBLE - 1).coerceAtMost(days.size - 1)))
        }
    }
    androidx.compose.runtime.LaunchedEffect(visiblePages, first) {
        visiblePages.filter { it != 0 }.forEach(onNeedPage)
    }
    val byDate = buildMap {
        (STRIP_PAGES_AHEAD downTo -STRIP_PAGES_BACK).forEach { p ->
            val src = if (p == 0) first else pageOf(p)
            src?.days?.forEach { put(it.date, it) }
        }
        first.days.forEach { put(it.date, it) }
    }
    val overdue = first.overdue.toSet()
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(
            onClick = { scope.launch { list.animateScrollToItem((list.firstVisibleItemIndex - 7).coerceAtLeast(0)) } },
            modifier = Modifier.size(32.dp),
        ) { Icon(KairosIcons.ChevronLeft, contentDescription = "Earlier days") }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f)) {
            val cell = maxWidth / STRIP_VISIBLE
            // Snaps to whole days (v0.466), lined up from the left edge (v0.482):
            // the default snaps a day to the centre, and with an even number of
            // days in view that left half a day at each end.
            val snapInfo = androidx.compose.runtime.remember(list) {
                androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider(
                    lazyListState = list,
                    snapPosition = androidx.compose.foundation.gestures.snapping.SnapPosition.Start,
                )
            }
            androidx.compose.foundation.lazy.LazyRow(
                state = list,
                modifier = Modifier.fillMaxWidth(),
                flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(snapLayoutInfoProvider = snapInfo),
            ) {
                items(count = days.size, key = { days[it] }) { i ->
                    val iso = days[i]
                    val d = byDate[iso] ?: com.kairos.app.data.remote.dto.WorkoutStripDayDto(date = iso)
                    StripDayCell(
                        d = d,
                        state = stripState(d, first.today, overdue),
                        selected = iso == selected,
                        onPick = onPick,
                        modifier = Modifier.width(cell),
                    )
                }
            }
        }
        androidx.compose.material3.IconButton(
            onClick = {
                scope.launch {
                    list.animateScrollToItem((list.firstVisibleItemIndex + 7).coerceAtMost(days.size - STRIP_VISIBLE))
                }
            },
            modifier = Modifier.size(32.dp),
        ) { Icon(KairosIcons.ChevronRight, contentDescription = "Later days") }
    }
}

/** The last 8 days: tap one to log it. Done, part-done, rest, open and
 *  expired days read the same as on the web. */
/** One day of the week strip: weekday, date, and its state's look. */
@Composable
private fun StripDayCell(
    d: com.kairos.app.data.remote.dto.WorkoutStripDayDto,
    state: String,
    selected: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSel = selected
    val dow = runCatching {
        LocalDate.parse(d.date).dayOfWeek
            .getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault())
    }.getOrDefault("")
    val num = d.date.takeLast(2).trimStart('0')
    val fill = when (state) {
        "done" -> MaterialTheme.colorScheme.primary
        // A rest day: a grey dot with its date (v0.460, was a moon).
        "rest" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        "partial" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        "open" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    val fg = when (state) {
        "done" -> MaterialTheme.colorScheme.onPrimary
        "expired", "none" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        "open" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .then(if (state != "expired") Modifier.clickable { onPick(d.date) } else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(dow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(fill)
                .border(
                    if (isSel) 1.5.dp else if (state == "planned") 1.dp else 0.dp,
                    when {
                        isSel -> MaterialTheme.colorScheme.primary
                        // A day still to come with a workout (v0.461).
                        state == "planned" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                num,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = if (isSel || state == "today") FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** One strip day's look — mirrors blocks.ts stripState. */
private fun stripState(
    d: com.kairos.app.data.remote.dto.WorkoutStripDayDto,
    today: String,
    overdue: Set<String>,
): String = when {
    // A day still to come (v0.461): what the plan has for it.
    d.date > today -> if (d.restPlanned) "rest" else if (d.planned) "planned" else "none"
    // Rested, but something else got done (a game): done (v0.460).
    d.rested -> if (d.logged) "done" else "rest"
    d.restPlanned && !d.logged -> "rest"
    d.task == "COMPLETE" -> "done"
    d.task == "SKIPPED" -> if (d.logged) "done" else "rest"
    d.task == "PENDING" -> when {
        d.date == today -> if (d.logged) "partial" else "today"
        d.date in overdue -> if (d.logged) "partial" else "open"
        else -> if (d.logged) "partial" else "expired"
    }
    d.logged -> "done"
    d.date == today -> "today"
    else -> "none"
}

/** One movement's three tiles: skip it, open the calculator for it, log it. */
@Composable
private fun MovementActions(
    block: WorkoutBlock,
    m: MovementInput,
    vm: WorkoutLogViewModel,
    onOpenCalculatorFor: (String, String) -> Unit,
) {
    // After a log, flash "logged" briefly, then settle on "edit weight". Keyed
    // on the save counter rather than on `logged`, so a movement that arrives
    // already recorded doesn't flash every time the page opens.
    var justLogged by remember(block.key, m.poolExerciseId) { mutableStateOf(false) }
    LaunchedEffect(m.savedAt) {
        if (m.savedAt > 0) {
            justLogged = true
            delay(2500)
            justLogged = false
        } else {
            // A reload resets the counter; don't leave a half-finished flash on.
            justLogged = false
        }
    }
    val noun = logNoun(listOf(m))
    val logLabel = when {
        justLogged -> "logged"
        m.logged -> "edit $noun"
        else -> "Log $noun"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkoutActionTile(
            icon = KairosIcons.Moon,
            label = if (m.skipped) "Skipped" else "Skip",
            modifier = Modifier.weight(1f),
            highlighted = !m.skipped,
        ) {
            // Skips THIS movement for its day, on the server — overdue days
            // included, since the block carries its own date. Once every
            // movement is logged or skipped the day is done.
            vm.setMovementSkipped(block.key, m.poolExerciseId, !m.skipped)
        }
        WorkoutActionTile(
            icon = KairosIcons.Dumbbell,
            label = "Calculator",
            modifier = Modifier.weight(1f),
            highlighted = true,
        ) {
            // THIS movement, not the card's first one — the calculator used to
            // hand its result back to the top movement whichever button opened
            // it, so a second movement's weight landed in the wrong box.
            onOpenCalculatorFor(block.key, m.poolExerciseId)
        }
        WorkoutActionTile(
            icon = KairosIcons.Dumbbell,
            label = logLabel,
            modifier = Modifier.weight(1f),
            highlighted = !m.logged,
            enabled = !m.saving,
            loading = m.saving,
            filled = true,
        ) { vm.saveBlock(block.key, m.poolExerciseId) }
    }
}

/** The tiles for a plan with no movements to enter — the one shape that still
 *  has nothing to hang per-movement buttons on. */
@Composable
private fun BlockActions(
    block: WorkoutBlock,
    vm: WorkoutLogViewModel,
    onOpenCalculatorFor: (String, String) -> Unit,
) {
    var justLogged by remember(block.key) { mutableStateOf(false) }
    LaunchedEffect(block.logged) {
        if (block.logged) {
            justLogged = true
            delay(2500)
            justLogged = false
        } else {
            justLogged = false
        }
    }
    val blockSkipped = block.inputs.isNotEmpty() && block.inputs.all { it.skipped }
    val logLabel = when {
        block.logged && justLogged -> "logged"
        block.logged -> "edit weight"
        else -> "Log weight"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkoutActionTile(
            icon = KairosIcons.Moon,
            label = if (blockSkipped) "Skipped" else "Skip",
            modifier = Modifier.weight(1f),
            highlighted = !blockSkipped,
        ) {
            vm.setBlockSkipped(block.key, !blockSkipped)
        }
        WorkoutActionTile(
            icon = KairosIcons.Dumbbell,
            label = "Calculator",
            modifier = Modifier.weight(1f),
            highlighted = true,
        ) { onOpenCalculatorFor(block.key, "") }
        WorkoutActionTile(
            icon = KairosIcons.Dumbbell,
            label = logLabel,
            modifier = Modifier.weight(1f),
            highlighted = !block.logged,
            enabled = !block.saving,
            loading = block.saving,
            filled = true,
        ) { vm.saveBlock(block.key) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDatePickerOverlay(
    dateIso: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(dateIso))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(utcMillisToIso(it)) }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

private fun isoToUtcMillis(iso: String): Long =
    try {
        LocalDate.parse(iso, DateTimeFormatter.ISO_DATE)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (_: Exception) {
        Instant.now().toEpochMilli()
    }

private fun utcMillisToIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        .format(DateTimeFormatter.ISO_DATE)

@Composable
private fun MovementRow(planId: String, m: MovementInput, vm: WorkoutLogViewModel) {
    val maxHint = m.metric == "WEIGHT"
    val isTime = m.metric == "DURATION"
    var swapping by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // The movement always names itself, with its swap control on the same
        // line — the muscle group heads the card and no longer repeats these
        // names, so this row is the only place the movement is named.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The name gets a fixed column and the swap pill sits right after
            // it, so the pill lands in the same place on every card. Giving the
            // name weight(1f) instead pushed the pill to the far edge and moved
            // it with every change of movement name.
            Text(
                m.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (m.skipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (m.skipped) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Takes only the width it needs, so Swap sits against the end
                // of the name. A fixed 150dp column parked the button at the
                // same x whatever the name was, leaving it hanging over empty
                // space next to anything short. `fill = false` keeps the bound
                // for a long name — it ellipsizes rather than pushing Swap off.
                modifier = Modifier.weight(1f, fill = false),
            )
            // Swap this movement for a variation TODAY only — front squat for back
            // squat. The weekly plan is untouched; next week comes back as planned.
            if (!m.skipped) {
                if (m.swappedFromName != null) {
                    Text(
                        "Undo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .clickable { vm.undoSwap(planId, m.poolExerciseId) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                } else {
                    // An outlined pill, not a bare word: the first version was a
                    // plain label and nobody could tell it was tappable.
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                            .clickable { vm.loadPool(); swapping = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            KairosIcons.Swap,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "Swap",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }
        if (!m.skipped) {
            if (m.swappedFromName != null) {
                Text(
                    "instead of ${m.swappedFromName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The fields sit in the SAME three weighted cells as the action
            // tiles below, so the weight box lands over "Rest / skip" and reps
            // over "Calculator" instead of drifting to opposite ends of the row.
            // Each box is capped at three digits wide and allowed to shrink
            // below that on a narrow screen rather than overflow its cell.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cell 1 — over "Rest / skip".
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = m.value,
                        onValueChange = { vm.onValue(planId, m.poolExerciseId, it) },
                        placeholder = { Text(if (isTime) "0" else if (maxHint) "wt" else "0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f, fill = false).widthIn(max = NUM_FIELD_W),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isTime) "min" else m.unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Cell 2 — over "Calculator". Seconds for a held movement, reps
                // for a weight, and empty for anything else.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (isTime) {
                        OutlinedTextField(
                            value = m.seconds,
                            onValueChange = { vm.onSeconds(planId, m.poolExerciseId, it) },
                            placeholder = { Text("00") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f, fill = false).widthIn(max = NUM_FIELD_W),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "sec",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (maxHint) {
                        // Reps for the top set. Optional, and only for weights:
                        // without it 185 x 5 and 185 x 12 log identically and the
                        // rep progress between weight jumps never shows up.
                        Text(
                            "\u00d7",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(
                            value = m.reps,
                            onValueChange = { vm.onReps(planId, m.poolExerciseId, it) },
                            placeholder = { Text("reps") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f, fill = false).widthIn(max = NUM_FIELD_W),
                        )
                    }
                }

                // Cell 3 — over the log button. This movement's history, hard
                // right of its own entry fields.
                Box(Modifier.weight(1f)) {
                    m.stats?.let { MovementHistory(it) }
                }
            }
        } else {
            Text(
                "Skipped",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (swapping) {
        SwapMovementDialog(planId, m, vm) { swapping = false }
    }
}

/**
 * Pick a variation for one movement, for today only. Same-category movements
 * first (swapping a bench press for a plank is not the point), the current
 * movement's muscle group floated to the top, and a search box because the pool
 * is long. Nothing is written here — the choice only changes what the Log button
 * sends.
 */
@Composable
private fun SwapMovementDialog(
    planId: String,
    m: MovementInput,
    vm: WorkoutLogViewModel,
    onDismiss: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    var query by remember { mutableStateOf("") }
    var peek by remember { mutableStateOf<String?>(null) }
    val current = ui.pool.firstOrNull { it.id == m.poolExerciseId }
    val q = query.trim().lowercase()

    // Grouped by muscle group, with a heading per group. Floating the current
    // group to the top of a flat list was not enough: everything below it ran
    // together alphabetically across every muscle group, so the list read as
    // one jumble. The movement's own group comes first, then the rest
    // alphabetically, with unassigned movements last under "Other".
    val groups = remember(ui.pool, q, current?.id) {
        ui.pool
            .asSequence()
            .filter { it.id != m.poolExerciseId }
            .filter { current == null || it.category == current.category }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .groupBy { it.muscleGroup }
            .map { (mg, items) ->
                SwapGroup(
                    key = mg ?: "_other",
                    label = if (mg != null) muscleLabel(mg) else "Other",
                    items = items.sortedBy { it.name.lowercase() },
                )
            }
            // Straight alphabetical by muscle group, movements alphabetical
            // inside each. Floating the current group to the top meant the list
            // started somewhere different every time it opened.
            .sortedWith(
                compareBy<SwapGroup> { it.key == "_other" }
                    .thenBy { it.label.lowercase() },
            )
    }
    val total = groups.sumOf { it.items.size }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "Swap ${m.name}",
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Today only \u2014 your plan keeps ${m.swappedFromName ?: m.name}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search movements") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                ui.poolLoading -> Text(
                    "Loading movements\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                total == 0 -> Text(
                    if (ui.pool.isEmpty()) "Couldn't load the movement list." else "No movements match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Column(
                    Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                ) {
                    groups.forEach { g ->
                        Text(
                            g.label.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
                        )
                        g.items.forEach { ex ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    ex.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            vm.swapMovement(planId, m.poolExerciseId, ex)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                )
                                MovementInfoButton(
                                    name = ex.name,
                                    onClick = { peek = if (peek == ex.id) null else ex.id },
                                    tint = if (peek == ex.id) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (peek == ex.id) {
                                MovementAboutInline(name = ex.name, about = ex.about)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Verb-noun for the log button, matching the web (weight/time/distance/reps). */
private fun logNoun(inputs: List<MovementInput>): String {
    val metrics = inputs.map { it.metric }.toSet()
    if (metrics.size != 1) return "workout"
    return when (metrics.first()) {
        "WEIGHT" -> "weight"
        "REPS" -> "reps"
        "DISTANCE" -> "distance"
        "METERS" -> "meters"
        "DURATION" -> "time"
        else -> "workout"
    }
}

/**
 * Three lines of history for one movement, to the right of its entry fields.
 *
 * Always three, with an em dash where there is nothing yet: a line that
 * disappears shifts the other two, and a row that changes shape as you log is
 * harder to read than one with a gap in it.
 *
 * Three different questions, deliberately not collapsed into one:
 *   Best       the heaviest ever lifted, weight only.
 *   Best reps  the MOST reps ever done, with the weight they were done at —
 *              usually a lighter bar than the record, which is the point.
 *   Latest     the last session, even when it is below both records.
 *
 * No dates and no repeated unit: this sits in a third of a phone's width, and
 * the unit is already printed beside the weight box on the same row.
 */
@Composable
private fun MovementHistory(st: com.kairos.app.data.remote.dto.MovementStatsDto) {
    val dash = "\u2014"
    Column(Modifier.fillMaxWidth()) {
        HistoryLine("Best", fmtNum(st.bestWeight))
        HistoryLine(
            "Best reps",
            if (st.bestReps != null && st.bestRepsWeight != null) {
                "${fmtNum(st.bestRepsWeight)} \u00d7 ${st.bestReps}"
            } else {
                dash
            },
        )
        HistoryLine(
            "Latest",
            buildString {
                append(fmtNum(st.lastWeight))
                st.lastReps?.let { reps ->
                    append(" \u00d7 ")
                    append(reps)
                }
            },
        )
    }
}

/**
 * Label hard left, value hard right, so the labels start in one column and the
 * values end in another.
 *
 * The VALUE is the unweighted child: a Row measures those first, so the number
 * always gets the width it needs and the label ellipsizes instead. The other
 * way round, a long label would push the number off the edge of the cell.
 */
@Composable
private fun HistoryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = KairosThemeState.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Trailing ".0" off a whole-number weight: 105, not 105.0. */
private fun fmtNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/** Swallow every touch inside (a planned day's preview can't be logged). */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.consumeAllTaps() {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/** A confirmed game, edited from the Additional card (v0.466): how hard it
 *  was, and how long you stayed when its event type's time varies. */
@Composable
private fun SportEditDialog(
    title: String,
    sport: com.kairos.app.data.remote.dto.SportSessionDto,
    onSave: (minutes: Int?, effort: String?, hurt: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var effort by remember { mutableStateOf(sport.effort) }
    var hurt by remember { mutableStateOf(sport.hurt) }
    var minutes by remember { mutableStateOf(sport.minutes?.toString().orEmpty()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EffortChooser(
                    question = "How hard was $title?",
                    effort = effort,
                    hurt = hurt,
                    onChange = { e, h -> effort = e; hurt = h },
                )
                if (sport.flexTime) {
                    androidx.compose.material3.OutlinedTextField(
                        value = minutes,
                        onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(3) },
                        label = { Text("How long you stayed (min)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onSave(if (sport.flexTime) minutes.toIntOrNull() else null, effort, hurt) },
            ) { Text("Save") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
