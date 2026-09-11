package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.CustomLogRequest
import com.kairos.app.data.remote.dto.WorkoutConflictDto
import com.kairos.app.data.remote.dto.HiitLogOptionDto
import com.kairos.app.data.remote.dto.LogCategoryDto
import com.kairos.app.data.remote.dto.MetricOptionDto
import com.kairos.app.data.remote.dto.MuscleGroupDto
import com.kairos.app.data.remote.dto.PoolExerciseDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WizardStep { TYPE, MUSCLE, EXERCISE, WORKOUT, VALUE }

data class CustomUiState(
    val loading: Boolean = true,
    val categories: List<LogCategoryDto> = emptyList(),
    val exercises: List<PoolExerciseDto> = emptyList(),
    val hiitWorkouts: List<HiitLogOptionDto> = emptyList(),
    val muscleGroups: List<MuscleGroupDto> = emptyList(),
    val step: WizardStep = WizardStep.TYPE,
    val categoryKey: String = "",
    val metricKey: String = "",
    val muscleKey: String = "",
    val exerciseId: String = "",
    val hiitWorkoutId: String = "",
    val value: String = "",
    val load: String = "",
    val notes: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    val conflict: WorkoutConflictDto? = null,
) {
    val category: LogCategoryDto? get() = categories.firstOrNull { it.key == categoryKey }
    val metric: MetricOptionDto? get() = category?.metrics?.firstOrNull { it.key == metricKey }
    val isHiit: Boolean get() = categoryKey == "HIIT"
    val isWeights: Boolean get() = categoryKey == "WEIGHTS"
    val selectedHiit: HiitLogOptionDto? get() = hiitWorkouts.firstOrNull { it.id == hiitWorkoutId }

    /** Exercises to choose from at the exercise step: filtered to the category, and
     *  for weights also to the chosen muscle group. */
    val exercisesForStep: List<PoolExerciseDto>
        get() = if (isWeights) exercises.filter { it.category == "WEIGHTS" && it.muscleGroup == muscleKey }
        else exercises.filter { it.category == categoryKey }

    /** Muscle groups that actually have weights exercises. */
    val musclesWithExercises: List<MuscleGroupDto>
        get() {
            val have = exercises.filter { it.category == "WEIGHTS" }.mapNotNull { it.muscleGroup }.toSet()
            return muscleGroups.filter { it.key in have }
        }

    val resultLabel: String get() = if (isHiit) (selectedHiit?.resultLabel ?: "Result") else (metric?.label ?: "Result")
    val resultUnit: String get() = if (isHiit) (selectedHiit?.resultUnit ?: "") else (metric?.unit ?: "")
    val showLoad: Boolean get() = !isHiit && category?.load == true
}

class CustomWorkoutViewModel(
    private val session: SessionRepository,
    private val date: String,
) : ViewModel() {

    private val _ui = MutableStateFlow(CustomUiState())
    val ui: StateFlow<CustomUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val pool = session.loadWorkoutPool()
                _ui.update {
                    it.copy(
                        loading = false,
                        categories = pool.categories,
                        exercises = pool.exercises,
                        hiitWorkouts = pool.hiitWorkouts,
                        muscleGroups = pool.muscleGroups,
                    )
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, error = e.error.message) }
            }
        }
    }

    /** Step 1: pick a type, then jump to the right next step for that type. */
    fun pickType(key: String) {
        _ui.update { s ->
            val cat = s.categories.firstOrNull { it.key == key }
            val next = when {
                key == "WEIGHTS" -> WizardStep.MUSCLE
                key == "HIIT" -> WizardStep.WORKOUT
                cat?.isPool == true -> WizardStep.EXERCISE
                else -> WizardStep.VALUE
            }
            s.copy(
                categoryKey = key,
                metricKey = cat?.metrics?.firstOrNull()?.key ?: "",
                muscleKey = "",
                exerciseId = "",
                hiitWorkoutId = if (key == "HIIT") s.hiitWorkouts.firstOrNull()?.id ?: "" else "",
                value = "",
                load = "",
                step = next,
                error = null,
            )
        }
    }

    fun pickMuscle(key: String) = _ui.update { it.copy(muscleKey = key, exerciseId = "", step = WizardStep.EXERCISE, error = null) }
    fun pickExercise(id: String) = _ui.update { it.copy(exerciseId = id, step = WizardStep.VALUE, error = null) }
    fun pickWorkout(id: String) = _ui.update { it.copy(hiitWorkoutId = id, step = WizardStep.VALUE, error = null) }
    fun pickMetric(key: String) = _ui.update { it.copy(metricKey = key, error = null) }

    fun onValue(v: String) = _ui.update { it.copy(value = v, error = null) }
    fun onLoad(v: String) = _ui.update { it.copy(load = v, error = null) }
    fun onNotes(v: String) = _ui.update { it.copy(notes = v) }

    /** Back one step, following the flow for the chosen type. */
    fun back() {
        _ui.update { s ->
            val prev = when (s.step) {
                WizardStep.VALUE -> when {
                    s.isHiit -> WizardStep.WORKOUT
                    s.isWeights -> WizardStep.EXERCISE
                    s.category?.isPool == true -> WizardStep.EXERCISE
                    else -> WizardStep.TYPE
                }
                WizardStep.EXERCISE -> if (s.isWeights) WizardStep.MUSCLE else WizardStep.TYPE
                WizardStep.MUSCLE, WizardStep.WORKOUT -> WizardStep.TYPE
                WizardStep.TYPE -> WizardStep.TYPE
            }
            s.copy(step = prev, error = null)
        }
    }

    val atFirstStep: Boolean get() = _ui.value.step == WizardStep.TYPE

    fun submit(replace: Boolean = false) {
        val s = _ui.value
        val v = s.value.trim().toDoubleOrNull()
        if (v == null || v <= 0) {
            _ui.update { it.copy(error = "Enter a value.") }
            return
        }
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val req = if (s.isHiit) {
                    val w = s.selectedHiit ?: run {
                        _ui.update { it.copy(saving = false, error = "Pick a workout.") }
                        return@launch
                    }
                    CustomLogRequest(
                        date = date, hiitWorkoutId = w.id, metric = w.resultMetric,
                        value = v, unit = w.resultUnit, notes = s.notes.trim().ifBlank { null },
                        replace = replace, detectConflict = true,
                    )
                } else {
                    val cat = s.category ?: return@launch
                    val metric = s.metric ?: return@launch
                    if (cat.isPool && s.exerciseId.isBlank()) {
                        _ui.update { it.copy(saving = false, error = "Pick an exercise.") }
                        return@launch
                    }
                    CustomLogRequest(
                        date = date,
                        category = if (cat.isPool) null else cat.key,
                        poolExerciseId = if (cat.isPool) s.exerciseId else null,
                        metric = metric.key,
                        value = v,
                        unit = metric.unit,
                        load = if (cat.load) s.load.trim().toDoubleOrNull() else null,
                        notes = s.notes.trim().ifBlank { null },
                        replace = replace, detectConflict = true,
                    )
                }
                val ack = session.logCustom(req)
                if (ack.status == "conflict" && ack.conflict != null) {
                    _ui.update { it.copy(saving = false, conflict = ack.conflict) }
                } else {
                    _ui.update { it.copy(saving = false, done = true) }
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(saving = false, error = e.error.message) }
            }
        }
    }

    /** User chose "Update" on the already-logged prompt: overwrite it. */
    fun confirmReplace() {
        _ui.update { it.copy(conflict = null) }
        submit(replace = true)
    }

    fun dismissConflict() = _ui.update { it.copy(conflict = null) }
}
