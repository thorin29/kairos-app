package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.PendingWrite
import com.kairos.app.data.remote.dto.WorkoutDateRequest
import com.kairos.app.data.remote.dto.WorkoutConflictDto
import com.kairos.app.data.remote.dto.WorkoutLogRequest
import com.kairos.app.data.remote.dto.WorkoutPlanDto
import com.kairos.app.data.remote.dto.WorkoutBlockDto
import com.kairos.app.data.remote.dto.PlannedEntryDto
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.data.local.PayloadCacheStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.kairos.app.data.remote.dto.PoolExerciseDto
import kotlinx.coroutines.launch

/** One editable movement row: a value typed by the movement's metric, plus a
 *  skipped flag (the user is doing part of the workout and skipping this one). */
data class MovementInput(
    val poolExerciseId: String,
    val name: String,
    val metric: String,
    val unit: String,
    val value: String,
    /** Seconds for a DURATION movement; `value` holds the minutes. The server
     *  stores duration in seconds, and a single "time" box could not say
     *  whether 2 meant minutes or seconds — the web read it one way and this
     *  read it the other, so the same plank logged twice differed by 60x. */
    val seconds: String = "",
    /** Reps for the top set, WEIGHT movements only. Optional: blank logs exactly
     *  as it always did, and the weight remains the record either way. */
    val reps: String = "",
    val skipped: Boolean = false,
    /** Set when this row has been swapped for a variation TODAY ONLY — holds the
     *  planned movement's name so the row can say what it replaced and offer to
     *  put it back. The weekly plan is never touched. */
    val swappedFromName: String? = null,
    /** The planned movement's id, kept so an undo restores exactly it. */
    val plannedExerciseId: String? = null,
    /** Record / best reps / last, straight from the server. Appended last. */
    val stats: com.kairos.app.data.remote.dto.MovementStatsDto? = null,
    /** This movement's own Log button is mid-save. Per movement, not per block:
     *  a card holding two movements used to put BOTH buttons into the spinner
     *  when either was pressed. */
    val saving: Boolean = false,
    /** Already recorded for the day — from the server on load, or from a save
     *  made here. Turns this movement's button into "edit weight". */
    val logged: Boolean = false,
    /** Bumped by a save made on this screen, so the button can flash "logged"
     *  briefly afterwards. Deliberately separate from [logged]: a movement that
     *  arrives already logged should not flash on every page open. */
    val savedAt: Int = 0,
    /** False for a movement the plan lists without asking a number for. It
     *  never holds up the plan's tick. */
    val tracked: Boolean = true,
    /** Sets logged for the day, and the day's sets / last time's in a few
     *  words. Appended last; read by the set-logger rows. */
    val setCount: Int = 0,
    val summary: String = "",
    val last: String = "",
)

/** One planned workout for the day (e.g. Core, Arms). A day can have several. */
data class WorkoutBlock(
    val plannedWorkoutId: String,
    val name: String,
    /** Plans sharing this are drawn on one card (two chest workouts together). */
    val muscleGroup: String? = null,
    val inputs: List<MovementInput>,
    val saving: Boolean = false,
    val logged: Boolean = false,
    // Unique row id (plannedWorkoutId, or plannedWorkoutId@date for an overdue
    // day, since a past day can share a weekday's plan with today).
    val key: String = plannedWorkoutId,
    // The day this block logs to (null = the screen's date).
    val date: String? = null,
    val isOverdue: Boolean = false,
    /** The plan's category; strength plans open each movement's set logger. */
    val category: String? = null,
)

data class WorkoutLogUiState(
    val loading: Boolean = true,
    val loggable: Boolean = false,
    val date: String? = null,
    val planName: String? = null,
    val saving: Boolean = false,
    val blocks: List<WorkoutBlock> = emptyList(),
    val loadError: String? = null,
    val actionError: String? = null,
    val done: Boolean = false,
    val conflict: WorkoutConflictDto? = null,
    val conflictPlanId: String? = null,
    /** Which movement's save hit the conflict, so "Update" retries exactly that
     *  one rather than re-sending every movement on the card. */
    val conflictExerciseId: String? = null,
    val savedTick: Int = 0,
    val expiring: Boolean = false,
    /** Movement pool for the swap picker. Loaded on first use, not at startup —
     *  most logging never opens it. */
    val pool: List<PoolExerciseDto> = emptyList(),
    val poolLoading: Boolean = false,
    /** "Chest · Legs": the server's day title (blank on an older server). */
    val title: String = "",
    /** The last 8 days, for the week strip. */
    val strip: com.kairos.app.data.remote.dto.WorkoutStripDto? = null,
    /** "Strength waves · Week 2 of 4", when a program is running. */
    val programLine: String = "",
)

/**
 * Logging state for a day's planned workouts — every planned block (Core, Arms)
 * with one value per movement. [initialDate] null means "today"; the server
 * resolves it and returns the concrete date used for writes.
 */
class WorkoutLogViewModel(
    private val session: SessionRepository,
    private val initialDate: String?,
    private val cache: PayloadCacheStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(WorkoutLogUiState())
    val ui: StateFlow<WorkoutLogUiState> = _ui.asStateFlow()

    private var date: String? = initialDate
    private var requestedDate: String? = initialDate

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = it.blocks.isEmpty(), loadError = null) }
        viewModelScope.launch {
            val pid = session.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD
            val key = requestedDate ?: "today"
            // Cold-start seed: last plan for this day, with the offline write queue
            // overlaid so an offline edit isn't briefly lost on restart.
            if (_ui.value.blocks.isEmpty()) {
                runCatching { cache.readAs("workout", key, pid, WorkoutPlanDto.serializer()) }.getOrNull()
                    ?.let { applyPlan(applyPending(it, session.pendingWrites())) }
            }
            try {
                val raw = session.loadWorkout(requestedDate)
                applyPlan(applyPending(raw, session.pendingWrites()))
                launch { runCatching { cache.writeAs("workout", key, pid, WorkoutPlanDto.serializer(), raw) } }
            } catch (e: ApiException) {
                _ui.update {
                    if (it.blocks.isEmpty()) it.copy(loading = false, loadError = e.error.message)
                    else it.copy(loading = false)
                }
            }
        }
    }

    /** Build the UI state (blocks/loggable/planName) from a plan. Shared by the
     *  Room seed and the live fetch. */
    private fun applyPlan(plan: WorkoutPlanDto) {
        date = plan.date
        val src: List<WorkoutBlockDto> = when {
            plan.workouts.isNotEmpty() -> plan.workouts
            plan.plannedWorkoutId != null -> listOf(
                // Named arguments on purpose: this is the legacy single-plan
                // payload, and a new field in the middle of the DTO silently
                // reassigns positional ones.
                WorkoutBlockDto(
                    plannedWorkoutId = plan.plannedWorkoutId,
                    name = plan.name ?: "Workout",
                    exercises = plan.exercises,
                ),
            )
            else -> emptyList()
        }
        fun toBlock(b: WorkoutBlockDto, blockKey: String, day: String?, overdue: Boolean) =
            WorkoutBlock(
                plannedWorkoutId = b.plannedWorkoutId,
                name = b.name,
                muscleGroup = b.muscleGroup,
                inputs = b.exercises.map { e ->
                    // A slot already logged with a swapped movement comes back as
                    // that movement, still showing what it replaced — so a reload
                    // after logging matches what was actually done.
                    val swapped = e.loggedAs?.takeIf {
                        it.poolExerciseId.isNotBlank() && it.poolExerciseId != e.poolExerciseId
                    }
                    MovementInput(
                        poolExerciseId = swapped?.poolExerciseId ?: e.poolExerciseId,
                        name = swapped?.name?.takeIf { it.isNotBlank() } ?: e.name,
                        metric = e.metric,
                        unit = e.unit,
                        // The server stores a DURATION in seconds, so split it
                        // back into the minutes and seconds boxes. Dropping the
                        // whole number into the minutes box would reload a
                        // 45-second plank as 45 minutes.
                        value = if (e.metric == "DURATION") {
                            e.value?.let { (it.toInt() / 60).toString() } ?: ""
                        } else {
                            e.value?.let { fmt(it) } ?: ""
                        },
                        seconds = if (e.metric == "DURATION") {
                            e.value?.let { (it.toInt() % 60).toString().padStart(2, '0') } ?: ""
                        } else {
                            ""
                        },
                        // Reps come back with the weight now, so a reopened day
                        // refills both boxes instead of the weight alone.
                        reps = if (e.metric == "WEIGHT") e.reps?.takeIf { it > 0 }?.toString() ?: "" else "",
                        skipped = e.skipped,
                        plannedExerciseId = if (swapped != null) e.poolExerciseId else null,
                        swappedFromName = if (swapped != null) e.name else null,
                        stats = e.stats,
                        tracked = e.tracked,
                        // A value coming back from the server means this slot is
                        // already recorded for the day, so its button offers an
                        // edit rather than a first log — the same reading the
                        // web takes from its prefill.
                        logged = e.value != null || e.setCount > 0,
                        setCount = e.setCount,
                        summary = e.summary,
                        last = e.last,
                    )
                },
                category = b.category,
                key = blockKey,
                date = day,
                isOverdue = overdue,
                // The tick beside the plan name, restored on reload rather than
                // only after a save made in this session.
                // A skipped movement counts as done, the same rule the server
                // uses to decide whether the day is complete.
                logged = b.exercises.filter { it.tracked }.let { t ->
                    t.isNotEmpty() && t.all { it.value != null || it.skipped }
                },
            )
        val overdueBlocks = plan.overdue.flatMap { od ->
            od.workouts.map { b -> toBlock(b, "${b.plannedWorkoutId}@${od.date}", od.date, true) }
        }
        val todayBlocks = src.map { b -> toBlock(b, b.plannedWorkoutId, null, false) }
        val blocks = overdueBlocks + todayBlocks
        // Each name once: two Core plans on a day are two blocks but one name to
        // the reader ("Core \u00b7 Core \u00b7 Legs" said nothing the single "Core" did).
        val planName = if (todayBlocks.isEmpty()) null
        else todayBlocks.map { it.name }.distinctBy { it.lowercase() }.joinToString(" \u00b7 ")
        _ui.update {
            it.copy(
                loading = false,
                loggable = plan.loggable,
                date = plan.date,
                blocks = blocks,
                planName = planName,
                title = plan.title,
                strip = plan.strip,
                programLine = plan.program?.let { "${it.name} \u00b7 ${it.label}" } ?: "",
            )
        }
    }

    /** Clear every still-pending overdue day in one go: the user is declaring
     *  they are not going back to do them. Today is untouched. */
    fun restOverdue() {
        val days = _ui.value.blocks.filter { it.isOverdue }.mapNotNull { it.date }.distinct()
        if (days.isEmpty()) return
        _ui.update { it.copy(saving = true, actionError = null) }
        viewModelScope.launch {
            try {
                days.forEach { session.workoutRest(it) }
                _ui.update { it.copy(saving = false, savedTick = it.savedTick + 1) }
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(saving = false, actionError = e.error.message) }
            }
        }
    }

    /** Switch the day being logged (from the date picker) and reload its plan. */
    fun setDate(iso: String) {
        if (iso == date) return
        requestedDate = iso
        date = iso
        load()
    }

    private fun <T> parse(body: String?, ser: kotlinx.serialization.KSerializer<T>): T? =
        body?.let { runCatching { ApiClient.json.decodeFromString(ser, it) }.getOrNull() }

    private fun applyPending(plan: WorkoutPlanDto, pending: List<PendingWrite>): WorkoutPlanDto {
        var loggable = plan.loggable
        var out = plan
        for (w in pending) {
            if (w.url.substringAfter("/api/v1/", "").substringBefore("?") == "workouts/sets") {
                parse(w.body, com.kairos.app.data.remote.dto.SaveSetsRequest.serializer())?.let { out = withQueuedSets(out, it) }
                continue
            }
            val path = w.url.substringAfter("/api/v1/", "")
            val d = when (path) {
                "workouts/complete", "workouts/uncomplete", "workouts/rest", "workouts/expire" ->
                    parse(w.body, WorkoutDateRequest.serializer())?.date
                "workouts/log" -> parse(w.body, WorkoutLogRequest.serializer())?.date
                else -> null
            }
            if (d != null && d == plan.date) {
                loggable = path == "workouts/uncomplete"
            }
        }
        return out.copy(loggable = loggable)
    }

    /** A set-logger save still queued offline: show its set count on the row. */
    private fun withQueuedSets(
        plan: WorkoutPlanDto,
        q: com.kairos.app.data.remote.dto.SaveSetsRequest,
    ): WorkoutPlanDto {
        fun patch(b: WorkoutBlockDto) =
            if (b.plannedWorkoutId != q.plannedWorkoutId) b
            else b.copy(
                exercises = b.exercises.map { e ->
                    if (e.poolExerciseId != q.slotId) e
                    else e.copy(
                        setCount = q.sets.size,
                        summary = if (q.sets.isEmpty()) "" else if (q.sets.size == 1) "1 set" else "${q.sets.size} sets",
                    )
                },
            )
        return if (q.date == plan.date) plan.copy(workouts = plan.workouts.map { patch(it) })
        else plan.copy(
            overdue = plan.overdue.map { od ->
                if (od.date == q.date) od.copy(workouts = od.workouts.map { patch(it) }) else od
            },
        )
    }

    /** Reps for a movement's top set. Digits only, two of them — nobody logs a
     *  hundred-rep set, and it keeps the box small enough to sit beside the
     *  weight. */
    /** Seconds box for a DURATION movement; minutes live in `onValue`. */
    fun onSeconds(key: String, exId: String, v: String) {
        val clean = v.filter { it.isDigit() }.take(2)
        _ui.update { s ->
            s.copy(
                blocks = s.blocks.map { b ->
                    if (b.key != key) b
                    else b.copy(inputs = b.inputs.map { if (it.poolExerciseId == exId) it.copy(seconds = clean) else it })
                },
                actionError = null,
            )
        }
    }

    fun onReps(key: String, exId: String, v: String) {
        val clean = v.filter { it.isDigit() }.take(2)
        _ui.update { s ->
            s.copy(
                blocks = s.blocks.map { b ->
                    if (b.key != key) b
                    else b.copy(inputs = b.inputs.map { if (it.poolExerciseId == exId) it.copy(reps = clean) else it })
                },
                actionError = null,
            )
        }
    }

    /** Fetch the movement pool once, for the swap picker. */
    fun loadPool() {
        if (_ui.value.pool.isNotEmpty() || _ui.value.poolLoading) return
        _ui.update { it.copy(poolLoading = true) }
        viewModelScope.launch {
            val items = runCatching { session.loadWorkoutPool().exercises }.getOrDefault(emptyList())
            _ui.update { it.copy(pool = items, poolLoading = false) }
        }
    }

    /**
     * Swap a planned movement for a variation for THIS DAY ONLY — back squat for
     * front squat, flat bench for incline. Nothing is written until the block is
     * logged, and the weekly plan is never edited: the log simply carries the
     * chosen movement's id, which the server accepts because it validates the
     * plan's owner, not which movements the plan contains.
     *
     * Typed value and reps are kept: you swapped the movement, not the effort.
     */
    fun swapMovement(key: String, exId: String, to: PoolExerciseDto) {
        _ui.update { s ->
            val block = s.blocks.firstOrNull { it.key == key }
            // Swapping onto a movement the block already has would collapse two
            // rows onto one id and log only one of them.
            if (block != null && block.inputs.any { it.poolExerciseId == to.id && it.poolExerciseId != exId }) {
                return@update s.copy(actionError = "${to.name} is already in this workout.")
            }
            s.copy(
                blocks = s.blocks.map { b ->
                    if (b.key != key) b
                    else b.copy(
                        inputs = b.inputs.map { m ->
                            if (m.poolExerciseId != exId) m
                            else m.copy(
                                poolExerciseId = to.id,
                                name = to.name,
                                plannedExerciseId = m.plannedExerciseId ?: m.poolExerciseId,
                                swappedFromName = m.swappedFromName ?: m.name,
                            )
                        },
                    )
                },
                actionError = null,
            )
        }
    }

    /** Put the planned movement back. */
    fun undoSwap(key: String, exId: String) {
        _ui.update { s ->
            s.copy(
                blocks = s.blocks.map { b ->
                    if (b.key != key) b
                    else b.copy(
                        inputs = b.inputs.map { m ->
                            val planned = m.plannedExerciseId
                            val plannedName = m.swappedFromName
                            if (m.poolExerciseId != exId || planned == null || plannedName == null) m
                            else m.copy(
                                poolExerciseId = planned,
                                name = plannedName,
                                plannedExerciseId = null,
                                swappedFromName = null,
                            )
                        },
                    )
                },
                actionError = null,
            )
        }
    }

    fun onValue(key: String, exId: String, v: String) {
        _ui.update { s ->
            s.copy(
                blocks = s.blocks.map { b ->
                    if (b.key != key) b
                    else b.copy(inputs = b.inputs.map { if (it.poolExerciseId == exId) it.copy(value = v) else it })
                },
                actionError = null,
            )
        }
    }

    /** Skip (or un-skip) one movement for the day. Saved on the server, where a
     *  skip counts as done for that movement: the day completes once every
     *  movement is logged or skipped. Keyed on the block's row key, not its plan
     *  id: an overdue block lives on an earlier date and the two differ there. */
    fun setMovementSkipped(key: String, exId: String, skipped: Boolean) {
        val block = _ui.value.blocks.find { it.key == key } ?: return
        val d = block.date ?: date ?: return
        // A swapped row is skipped under the PLANNED movement — the slot it fills.
        val slot = block.inputs.firstOrNull { it.poolExerciseId == exId }
            ?.let { it.plannedExerciseId ?: it.poolExerciseId } ?: exId
        applySkip(key, exId, skipped)
        viewModelScope.launch {
            try {
                session.workoutSkip(d, block.plannedWorkoutId, slot, skipped)
                _ui.update { it.copy(savedTick = it.savedTick + 1) }
                cacheCurrentPlan()
            } catch (e: ApiException) {
                applySkip(key, exId, !skipped)
                _ui.update { it.copy(actionError = e.error.message) }
            }
        }
    }

    /** Skip or un-skip a block with no movements to enter, as a whole. */
    fun setBlockSkipped(key: String, skipped: Boolean) {
        val block = _ui.value.blocks.find { it.key == key } ?: return
        val d = block.date ?: date ?: return
        applySkip(key, null, skipped)
        viewModelScope.launch {
            try {
                session.workoutSkip(d, block.plannedWorkoutId, "", skipped)
                _ui.update { it.copy(savedTick = it.savedTick + 1) }
                cacheCurrentPlan()
            } catch (e: ApiException) {
                applySkip(key, null, !skipped)
                _ui.update { it.copy(actionError = e.error.message) }
            }
        }
    }

    /** Local half of a skip: flag the movement (or every movement when [exId] is
     *  null) and recompute the plan's tick. Typed values are kept — skipping
     *  doesn't delete anything already logged. */
    private fun applySkip(key: String, exId: String?, skipped: Boolean) {
        _ui.update { s ->
            s.copy(
                blocks = s.blocks.map { b ->
                    if (b.key != key) b
                    else {
                        val inputs = b.inputs.map {
                            if (exId != null && it.poolExerciseId != exId) it
                            else it.copy(skipped = skipped)
                        }
                        b.copy(inputs = inputs, logged = blockDone(inputs, b.logged))
                    }
                },
                actionError = null,
            )
        }
    }

    /** The tick beside a plan's name: every tracked movement logged or skipped.
     *  With nothing tracked, whatever the block already said stands. */
    private fun blockDone(inputs: List<MovementInput>, fallback: Boolean): Boolean {
        val tracked = inputs.filter { it.tracked }
        return if (tracked.isEmpty()) fallback else tracked.all { it.logged || it.skipped }
    }

    /**
     * Mark a block (or one movement in it) as saving / saved. `exId` null means
     * the whole block, which is only the shape with no movements to hang a
     * button on.
     */
    private fun markSaving(key: String, exId: String?, busy: Boolean, saved: Boolean) {
        _ui.update { s ->
            s.copy(
                blocks = s.blocks.map { b ->
                    if (b.key != key) return@map b
                    val inputs = b.inputs.map { m ->
                        if (exId != null && m.poolExerciseId != exId) m
                        else m.copy(
                            saving = busy,
                            logged = if (saved) true else m.logged,
                            savedAt = if (saved) m.savedAt + 1 else m.savedAt,
                            // Logging un-skips on the server; match it here.
                            skipped = if (saved) false else m.skipped,
                        )
                    }
                    // The tick by the plan name means the whole plan is in, so it
                    // waits for the last movement rather than lighting up as soon
                    // as one of several is logged.
                    val allIn =
                        if (inputs.isEmpty()) saved || b.logged
                        else blockDone(inputs, saved || b.logged)
                    b.copy(
                        inputs = inputs,
                        saving = if (exId == null) busy else b.saving,
                        logged = allIn,
                    )
                },
                actionError = null,
            )
        }
    }

    /**
     * Log a block's entered (non-skipped) movements, or just one of them when
     * [exId] is given — which is how every card with movements now saves, since
     * each movement carries its own Log button. The server replaces only the
     * movements a submission covers, so logging the bench press leaves the
     * incline press on the same plan untouched. Other blocks stay open.
     */
    fun saveBlock(key: String, exId: String? = null, replace: Boolean = false) {
        val block = _ui.value.blocks.find { it.key == key } ?: return
        val d = block.date ?: date ?: return
        val targets =
            if (exId == null) block.inputs else block.inputs.filter { it.poolExerciseId == exId }
        // One movement with nothing typed: nothing to send. An empty log used to
        // reach the server and clear every movement already logged on the plan.
        if (exId != null && targets.none { it.hasEntry() }) return
        markSaving(key, exId, busy = true, saved = false)
        viewModelScope.launch {
            try {
                // A movement Logged while marked skipped is being un-skipped by
                // the log itself, so its value is sent like any other.
                val entries = targets.filter { exId != null || !it.skipped }.mapNotNull { m ->
                    val raw = if (m.metric == "DURATION") {
                        val mins = m.value.trim().toDoubleOrNull() ?: 0.0
                        val secs = m.seconds.trim().toDoubleOrNull() ?: 0.0
                        (mins * 60 + secs).takeIf { it > 0 }
                    } else {
                        m.value.trim().toDoubleOrNull()
                    }
                    raw?.let { v ->
                        PlannedEntryDto(
                            m.poolExerciseId,
                            m.metric,
                            v,
                            m.unit,
                            // The planned slot this fills, when swapped for the day.
                            swappedFrom = m.plannedExerciseId,
                            // Reps ride along with a weight; everything else ignores them.
                            reps = if (m.metric == "WEIGHT") m.reps.trim().toIntOrNull()?.takeIf { it > 0 } else null,
                        )
                    }
                }
                // `d` is the day the workout counts for; this is the day it is
                // being logged on. Captured HERE rather than when an offline
                // write is replayed — a log queued on Tuesday and synced on
                // Thursday was still done on Tuesday.
                val loggedOn = java.time.LocalDate.now().toString()
                val ack = session.logWorkout(
                    d,
                    block.plannedWorkoutId,
                    entries,
                    replace = replace,
                    detectConflict = true,
                    completedOn = if (loggedOn != d) loggedOn else null,
                )
                if (ack.status == "conflict" && ack.conflict != null) {
                    markSaving(key, exId, busy = false, saved = false)
                    _ui.update {
                        it.copy(
                            conflict = ack.conflict,
                            conflictPlanId = key,
                            conflictExerciseId = exId,
                        )
                    }
                } else {
                    markSaving(key, exId, busy = false, saved = true)
                    _ui.update { it.copy(savedTick = it.savedTick + 1) }
                    cacheCurrentPlan()
                }
            } catch (e: ApiException) {
                markSaving(key, exId, busy = false, saved = false)
                _ui.update { it.copy(actionError = e.error.message) }
            }
        }
    }

    /** "Update" on the already-logged prompt: overwrite the existing entry. */
    fun confirmReplace() {
        val planId = _ui.value.conflictPlanId ?: return
        val exId = _ui.value.conflictExerciseId
        _ui.update { it.copy(conflict = null, conflictPlanId = null, conflictExerciseId = null) }
        saveBlock(planId, exId, replace = true)
    }

    fun dismissConflict() =
        _ui.update { it.copy(conflict = null, conflictPlanId = null, conflictExerciseId = null) }

    /** Mark the day a rest day (SKIPPED). Used from the workouts overview. */
    /** Re-fetch the raw plan for this date and refresh the "workout" cache, so a
     *  successful log/expire keeps Room current (mirrors what load() does). */
    private fun cacheCurrentPlan() {
        viewModelScope.launch {
            runCatching { session.loadWorkout(requestedDate) }.getOrNull()?.let { raw ->
                runCatching {
                    cache.writeAs("workout", requestedDate ?: "today", session.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD, WorkoutPlanDto.serializer(), raw)
                }
            }
        }
    }

    /**
     * Mark a day rest/skip. [day] names the day to clear; null means the screen's
     * own day. An overdue workout lives on an EARLIER date, so resting it has to
     * send that date — resting "today" left the overdue task PENDING, which is
     * why the late count and the overdue entry survived a skip.
     */
    fun restDay(day: String? = null) {
        val d = day ?: date ?: return
        _ui.update { it.copy(saving = true, actionError = null) }
        viewModelScope.launch {
            try {
                session.workoutRest(d)
                _ui.update { it.copy(saving = false, done = true, savedTick = it.savedTick + 1) }
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(saving = false, actionError = e.error.message) }
            }
        }
    }

    /** Close a missed/overdue workout for the day so it stops showing. */
    fun expire() {
        val d = date ?: return
        _ui.update { it.copy(expiring = true, actionError = null) }
        viewModelScope.launch {
            try {
                session.workoutExpire(d)
                _ui.update { it.copy(expiring = false, done = true, savedTick = it.savedTick + 1) }
                cacheCurrentPlan()
            } catch (e: ApiException) {
                _ui.update { it.copy(expiring = false, actionError = e.error.message) }
            }
        }
    }

    private fun MovementInput.hasEntry(): Boolean =
        if (metric == "DURATION") {
            ((value.trim().toDoubleOrNull() ?: 0.0) * 60 + (seconds.trim().toDoubleOrNull() ?: 0.0)) > 0
        } else {
            (value.trim().toDoubleOrNull() ?: 0.0) > 0
        }

    private fun fmt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
