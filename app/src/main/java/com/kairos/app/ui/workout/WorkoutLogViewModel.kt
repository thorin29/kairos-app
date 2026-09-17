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
            try {
                val plan = freshPlan()
                date = plan.date
                val src: List<WorkoutBlockDto> = when {
                    plan.workouts.isNotEmpty() -> plan.workouts
                    plan.plannedWorkoutId != null -> listOf(
                        WorkoutBlockDto(plan.plannedWorkoutId, plan.name ?: "Workout", plan.exercises),
                    )
                    else -> emptyList()
                }
                val blocks = src.map { b ->
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
                    )
                }
                val planName = if (blocks.isEmpty()) null else blocks.joinToString(" \u00b7 ") { it.name }
                _ui.update {
                    it.copy(loading = false, loggable = plan.loggable, date = plan.date, blocks = blocks, planName = planName)
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
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

    private suspend fun freshPlan(): WorkoutPlanDto =
        applyPending(session.loadWorkout(requestedDate), session.pendingWrites())

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

    fun onValue(planId: String, exId: String, v: String) {
        _ui.update { s ->
            s.copy(
                blocks = s.blocks.map { b ->
                    if (b.plannedWorkoutId != planId) b
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
    fun setBlockSkipped(planId: String, skipped: Boolean) {
        _ui.update { s ->
            s.copy(
                blocks = s.blocks.map { b ->
                    if (b.plannedWorkoutId != planId) b
                    else b.copy(inputs = b.inputs.map {
                        it.copy(skipped = skipped, value = if (skipped) "" else it.value)
                    })
                },
            )
        }
    }

    /** Log one block's entered (non-skipped) movements. Other blocks stay open. */
    fun saveBlock(planId: String, replace: Boolean = false) {
        val d = date ?: return
        val block = _ui.value.blocks.find { it.plannedWorkoutId == planId } ?: return
        _ui.update { s -> s.copy(blocks = s.blocks.map { if (it.plannedWorkoutId == planId) it.copy(saving = true) else it }, actionError = null) }
        viewModelScope.launch {
            try {
                val entries = block.inputs.filter { !it.skipped }.mapNotNull { m ->
                    m.value.trim().toDoubleOrNull()?.let { v ->
                        PlannedEntryDto(m.poolExerciseId, m.metric, v, m.unit)
                    }
                }
                val ack = session.logWorkout(d, planId, entries, replace = replace, detectConflict = true)
                if (ack.status == "conflict" && ack.conflict != null) {
                    _ui.update { s ->
                        s.copy(
                            blocks = s.blocks.map { if (it.plannedWorkoutId == planId) it.copy(saving = false) else it },
                            conflict = ack.conflict, conflictPlanId = planId,
                        )
                    }
                } else {
                    _ui.update { s ->
                        s.copy(
                            blocks = s.blocks.map { if (it.plannedWorkoutId == planId) it.copy(saving = false, logged = true) else it },
                            savedTick = s.savedTick + 1,
                        )
                    }
                }
            } catch (e: ApiException) {
                _ui.update { s -> s.copy(blocks = s.blocks.map { if (it.plannedWorkoutId == planId) it.copy(saving = false) else it }, actionError = e.error.message) }
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
            } catch (e: ApiException) {
                _ui.update { it.copy(expiring = false, actionError = e.error.message) }
            }
        }
    }

    private fun fmt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
