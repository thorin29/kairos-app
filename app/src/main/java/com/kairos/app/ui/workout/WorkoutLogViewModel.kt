package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.LogEntryDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One editable exercise row. Weight/reps are held as strings for the fields. */
data class ExerciseInput(
    val exerciseId: String,
    val name: String,
    val unit: String,
    val metric: String,
    val weight: String,
    val reps: String,
)

data class WorkoutLogUiState(
    val loading: Boolean = true,
    val loggable: Boolean = false,
    val inputs: List<ExerciseInput> = emptyList(),
    val loadError: String? = null,
    val saving: Boolean = false,
    val actionError: String? = null,
    /** Set when a save/mark/rest succeeds; a pushed screen pops on this. */
    val done: Boolean = false,
    /** True after the most recent save/mark/rest succeeded (for in-place pages). */
    val savedTick: Int = 0,
)

/**
 * Shared logging state for both the pushed log screen (a specific day) and the
 * Workouts page (today). [initialDate] null means "today" — the server resolves
 * it and returns the concrete date, which is then used for all writes.
 */
class WorkoutLogViewModel(
    private val session: SessionRepository,
    private val initialDate: String?,
) : ViewModel() {

    private val _ui = MutableStateFlow(WorkoutLogUiState())
    val ui: StateFlow<WorkoutLogUiState> = _ui.asStateFlow()

    /** Concrete date the server resolved the plan to; used for all writes. */
    private var date: String? = initialDate

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = it.inputs.isEmpty(), loadError = null) }
        viewModelScope.launch {
            try {
                val plan = session.loadWorkout(initialDate)
                date = plan.date
                val inputs = plan.exercises.map { e ->
                    ExerciseInput(
                        exerciseId = e.exerciseId,
                        name = e.name,
                        unit = e.unit,
                        metric = e.metric,
                        weight = e.logged?.weight?.let { fmt(it) } ?: "",
                        reps = e.logged?.reps?.toString() ?: "",
                    )
                }
                _ui.update {
                    it.copy(loading = false, loggable = plan.loggable, inputs = inputs)
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
            }
        }
    }

    fun onWeight(id: String, v: String) = edit(id) { it.copy(weight = v) }
    fun onReps(id: String, v: String) = edit(id) { it.copy(reps = v) }

    private fun edit(id: String, f: (ExerciseInput) -> ExerciseInput) {
        _ui.update { s ->
            s.copy(inputs = s.inputs.map { if (it.exerciseId == id) f(it) else it }, actionError = null)
        }
    }

    fun save() {
        val d = date ?: return
        _ui.update { it.copy(saving = true, actionError = null) }
        viewModelScope.launch {
            try {
                val entries = _ui.value.inputs.map {
                    LogEntryDto(
                        exerciseId = it.exerciseId,
                        weight = it.weight.trim().toDoubleOrNull(),
                        reps = it.reps.trim().toIntOrNull(),
                    )
                }
                session.logWorkout(d, entries, null)
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
