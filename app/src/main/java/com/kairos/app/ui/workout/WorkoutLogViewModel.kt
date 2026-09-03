package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
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
    val inputs: List<MovementInput> = emptyList(),
    val loadError: String? = null,
    val saving: Boolean = false,
    val actionError: String? = null,
    val done: Boolean = false,
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
    private var plannedWorkoutId: String? = null

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = it.inputs.isEmpty(), loadError = null) }
        viewModelScope.launch {
            try {
                val plan = session.loadWorkout(initialDate)
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
                    it.copy(loading = false, loggable = plan.loggable, planName = plan.name, inputs = inputs)
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
            }
        }
    }

    fun onValue(id: String, v: String) {
        _ui.update { s ->
            s.copy(inputs = s.inputs.map { if (it.poolExerciseId == id) it.copy(value = v) else it }, actionError = null)
        }
    }

    fun save() {
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
                session.logWorkout(d, planId, entries)
                _ui.update { it.copy(saving = false, done = true, savedTick = it.savedTick + 1) }
            } catch (e: ApiException) {
                _ui.update { it.copy(saving = false, actionError = e.error.message) }
            }
        }
    }

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
