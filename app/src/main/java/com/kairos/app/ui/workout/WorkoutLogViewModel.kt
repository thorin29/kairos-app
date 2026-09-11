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
import com.kairos.app.data.remote.dto.PlannedEntryDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One editable movement row: a single value typed by the movement's metric. */
data class MovementInput(
    val poolExerciseId: String,
    val name: String,
    val metric: String,
    val unit: String,
    val value: String,
)

data class WorkoutLogUiState(
    val loading: Boolean = true,
    val loggable: Boolean = false,
    val planName: String? = null,
    val date: String? = null,
    val inputs: List<MovementInput> = emptyList(),
    val loadError: String? = null,
    val saving: Boolean = false,
    val actionError: String? = null,
    val done: Boolean = false,
    val conflict: WorkoutConflictDto? = null,
    val savedTick: Int = 0,
)

/**
 * Logging state for a planned workout (e.g. "Legs") — one value per movement.
 * [initialDate] null means "today"; the server resolves it and returns the
 * concrete date used for writes.
 */
class WorkoutLogViewModel(
    private val session: SessionRepository,
    private val initialDate: String?,
) : ViewModel() {

    private val _ui = MutableStateFlow(WorkoutLogUiState())
    val ui: StateFlow<WorkoutLogUiState> = _ui.asStateFlow()

    private var date: String? = initialDate
    private var requestedDate: String? = initialDate
    private var plannedWorkoutId: String? = null

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = it.inputs.isEmpty(), loadError = null) }
        viewModelScope.launch {
            try {
                val plan = freshPlan()
                date = plan.date
                plannedWorkoutId = plan.plannedWorkoutId
                val inputs = plan.exercises.map { e ->
                    MovementInput(
                        poolExerciseId = e.poolExerciseId,
                        name = e.name,
                        metric = e.metric,
                        unit = e.unit,
                        value = e.value?.let { fmt(it) } ?: "",
                    )
                }
                _ui.update {
                    it.copy(loading = false, loggable = plan.loggable, planName = plan.name, date = plan.date, inputs = inputs)
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

    /** Load today's plan and re-apply any queued complete/rest/log so a workout
     *  marked offline still reads as done (loggable = false) until it syncs. */
    private suspend fun freshPlan(): WorkoutPlanDto =
        applyPending(session.loadWorkout(requestedDate), session.pendingWrites())

    private fun <T> parse(body: String?, ser: kotlinx.serialization.KSerializer<T>): T? =
        body?.let { runCatching { ApiClient.json.decodeFromString(ser, it) }.getOrNull() }

    private fun applyPending(plan: WorkoutPlanDto, pending: List<PendingWrite>): WorkoutPlanDto {
        var loggable = plan.loggable
        for (w in pending) {
            val path = w.url.substringAfter("/api/v1/", "")
            val d = when (path) {
                "workouts/complete", "workouts/uncomplete", "workouts/rest" ->
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

    fun onValue(id: String, v: String) {
        _ui.update { s ->
            s.copy(inputs = s.inputs.map { if (it.poolExerciseId == id) it.copy(value = v) else it }, actionError = null)
        }
    }

    fun save(replace: Boolean = false) {
        val d = date ?: return
        val planId = plannedWorkoutId ?: return
        _ui.update { it.copy(saving = true, actionError = null) }
        viewModelScope.launch {
            try {
                val entries = _ui.value.inputs.mapNotNull { m ->
                    m.value.trim().toDoubleOrNull()?.let { v ->
                        PlannedEntryDto(m.poolExerciseId, m.metric, v, m.unit)
                    }
                }
                val ack = session.logWorkout(
                    d, planId, entries, replace = replace, detectConflict = true,
                )
                if (ack.status == "conflict" && ack.conflict != null) {
                    _ui.update { it.copy(saving = false, conflict = ack.conflict) }
                } else {
                    _ui.update {
                        it.copy(saving = false, done = true, savedTick = it.savedTick + 1)
                    }
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(saving = false, actionError = e.error.message) }
            }
        }
    }

    /** "Update" on the already-logged prompt: overwrite the existing entry. */
    fun confirmReplace() {
        _ui.update { it.copy(conflict = null) }
        save(replace = true)
    }

    fun dismissConflict() = _ui.update { it.copy(conflict = null) }

    fun markDone() = quick { session.workoutComplete(it) }
    fun restDay() = quick { session.workoutRest(it) }

    private fun quick(block: suspend (String) -> Unit) {
        val d = date ?: return
        _ui.update { it.copy(saving = true, actionError = null) }
        viewModelScope.launch {
            try {
                block(d)
                _ui.update { it.copy(saving = false, done = true, savedTick = it.savedTick + 1) }
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(saving = false, actionError = e.error.message) }
            }
        }
    }

    private fun fmt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
