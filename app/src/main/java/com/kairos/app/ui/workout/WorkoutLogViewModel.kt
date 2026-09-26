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
import kotlinx.coroutines.launch

/** One editable movement row: a value typed by the movement's metric, plus a
 *  skipped flag (the user is doing part of the workout and skipping this one). */
data class MovementInput(
    val poolExerciseId: String,
    val name: String,
    val metric: String,
    val unit: String,
    val value: String,
    val skipped: Boolean = false,
)

/** One planned workout for the day (e.g. Core, Arms). A day can have several. */
data class WorkoutBlock(
    val plannedWorkoutId: String,
    val name: String,
    val inputs: List<MovementInput>,
    val saving: Boolean = false,
    val logged: Boolean = false,
    // Unique row id (plannedWorkoutId, or plannedWorkoutId@date for an overdue
    // day, since a past day can share a weekday's plan with today).
    val key: String = plannedWorkoutId,
    // The day this block logs to (null = the screen's date).
    val date: String? = null,
    val isOverdue: Boolean = false,
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
    val savedTick: Int = 0,
    val expiring: Boolean = false,
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
                WorkoutBlockDto(plan.plannedWorkoutId, plan.name ?: "Workout", plan.exercises),
            )
            else -> emptyList()
        }
        fun toBlock(b: WorkoutBlockDto, blockKey: String, day: String?, overdue: Boolean) =
            WorkoutBlock(
                plannedWorkoutId = b.plannedWorkoutId,
                name = b.name,
                inputs = b.exercises.map { e ->
                    MovementInput(
                        poolExerciseId = e.poolExerciseId,
                        name = e.name,
                        metric = e.metric,
                        unit = e.unit,
                        value = e.value?.let { fmt(it) } ?: "",
                    )
                },
                key = blockKey,
                date = day,
                isOverdue = overdue,
            )
        val overdueBlocks = plan.overdue.flatMap { od ->
            od.workouts.map { b -> toBlock(b, "${b.plannedWorkoutId}@${od.date}", od.date, true) }
        }
        val todayBlocks = src.map { b -> toBlock(b, b.plannedWorkoutId, null, false) }
        val blocks = overdueBlocks + todayBlocks
        val planName = if (todayBlocks.isEmpty()) null else todayBlocks.joinToString(" \u00b7 ") { it.name }
        _ui.update {
            it.copy(loading = false, loggable = plan.loggable, date = plan.date, blocks = blocks, planName = planName)
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
        for (w in pending) {
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
        return plan.copy(loggable = loggable)
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

    /** Toggle "skip" for one movement — greys it and excludes it from the log. */
    fun toggleSkip(planId: String, exId: String) {
        _ui.update { s ->
            s.copy(
                blocks = s.blocks.map { b ->
                    if (b.plannedWorkoutId != planId) b
                    else b.copy(inputs = b.inputs.map {
                        if (it.poolExerciseId == exId) it.copy(skipped = !it.skipped, value = if (!it.skipped) "" else it.value) else it
                    })
                },
            )
        }
    }

    /** Skip or un-skip a whole block (its Rest/skip button) — sets every movement
     *  in the block to the given skipped state. */
    fun setBlockSkipped(key: String, skipped: Boolean) {
        _ui.update { s ->
            s.copy(
                blocks = s.blocks.map { b ->
                    if (b.key != key) b
                    else b.copy(inputs = b.inputs.map {
                        it.copy(skipped = skipped, value = if (skipped) "" else it.value)
                    })
                },
            )
        }
    }

    /** Log one block's entered (non-skipped) movements. Other blocks stay open. */
    fun saveBlock(key: String, replace: Boolean = false) {
        val block = _ui.value.blocks.find { it.key == key } ?: return
        val d = block.date ?: date ?: return
        _ui.update { s -> s.copy(blocks = s.blocks.map { if (it.key == key) it.copy(saving = true) else it }, actionError = null) }
        viewModelScope.launch {
            try {
                val entries = block.inputs.filter { !it.skipped }.mapNotNull { m ->
                    m.value.trim().toDoubleOrNull()?.let { v ->
                        PlannedEntryDto(m.poolExerciseId, m.metric, v, m.unit)
                    }
                }
                val ack = session.logWorkout(d, block.plannedWorkoutId, entries, replace = replace, detectConflict = true)
                if (ack.status == "conflict" && ack.conflict != null) {
                    _ui.update { s ->
                        s.copy(
                            blocks = s.blocks.map { if (it.key == key) it.copy(saving = false) else it },
                            conflict = ack.conflict, conflictPlanId = key,
                        )
                    }
                } else {
                    _ui.update { s ->
                        s.copy(
                            blocks = s.blocks.map { if (it.key == key) it.copy(saving = false, logged = true) else it },
                            savedTick = s.savedTick + 1,
                        )
                    }
                    cacheCurrentPlan()
                }
            } catch (e: ApiException) {
                _ui.update { s -> s.copy(blocks = s.blocks.map { if (it.key == key) it.copy(saving = false) else it }, actionError = e.error.message) }
            }
        }
    }

    /** "Update" on the already-logged prompt: overwrite the existing entry. */
    fun confirmReplace() {
        val planId = _ui.value.conflictPlanId ?: return
        _ui.update { it.copy(conflict = null, conflictPlanId = null) }
        saveBlock(planId, replace = true)
    }

    fun dismissConflict() = _ui.update { it.copy(conflict = null, conflictPlanId = null) }

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

    fun restDay() {
        val d = date ?: return
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

    private fun fmt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
