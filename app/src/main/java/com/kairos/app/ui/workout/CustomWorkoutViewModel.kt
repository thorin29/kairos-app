package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.CustomLogRequest
import com.kairos.app.data.remote.dto.LogCategoryDto
import com.kairos.app.data.remote.dto.MetricOptionDto
import com.kairos.app.data.remote.dto.PoolExerciseDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomUiState(
    val loading: Boolean = true,
    val categories: List<LogCategoryDto> = emptyList(),
    val exercises: List<PoolExerciseDto> = emptyList(),
    val categoryKey: String = "",
    val metricKey: String = "",
    val exerciseId: String = "",
    val value: String = "",
    val load: String = "",
    val notes: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    val category: LogCategoryDto? get() = categories.firstOrNull { it.key == categoryKey }
    val metric: MetricOptionDto? get() = category?.metrics?.firstOrNull { it.key == metricKey }
    val exercisesForCategory: List<PoolExerciseDto>
        get() = exercises.filter { it.category == categoryKey }
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
                val first = pool.categories.firstOrNull()
                _ui.update {
                    it.copy(
                        loading = false,
                        categories = pool.categories,
                        exercises = pool.exercises,
                        categoryKey = first?.key ?: "",
                        metricKey = first?.metrics?.firstOrNull()?.key ?: "",
                        exerciseId = pool.exercises.firstOrNull { e -> e.category == first?.key }?.id ?: "",
                    )
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, error = e.error.message) }
            }
        }
    }

    fun onCategory(key: String) {
        _ui.update { s ->
            val cat = s.categories.firstOrNull { it.key == key }
            s.copy(
                categoryKey = key,
                metricKey = cat?.metrics?.firstOrNull()?.key ?: "",
                exerciseId = s.exercises.firstOrNull { it.category == key }?.id ?: "",
                error = null,
            )
        }
    }

    fun onMetric(key: String) = _ui.update { it.copy(metricKey = key, error = null) }
    fun onExercise(id: String) = _ui.update { it.copy(exerciseId = id, error = null) }
    fun onValue(v: String) = _ui.update { it.copy(value = v, error = null) }
    fun onLoad(v: String) = _ui.update { it.copy(load = v, error = null) }
    fun onNotes(v: String) = _ui.update { it.copy(notes = v) }

    fun submit() {
        val s = _ui.value
        val cat = s.category ?: return
        val metric = s.metric ?: return
        val v = s.value.trim().toDoubleOrNull()
        if (v == null || v <= 0) {
            _ui.update { it.copy(error = "Enter a value.") }
            return
        }
        if (cat.isPool && s.exerciseId.isBlank()) {
            _ui.update { it.copy(error = "Pick an exercise.") }
            return
        }
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                session.logCustom(
                    CustomLogRequest(
                        date = date,
                        category = if (cat.isPool) null else cat.key,
                        poolExerciseId = if (cat.isPool) s.exerciseId else null,
                        metric = metric.key,
                        value = v,
                        unit = metric.unit,
                        load = if (cat.load) s.load.trim().toDoubleOrNull() else null,
                        notes = s.notes.trim().ifBlank { null },
                    ),
                )
                _ui.update { it.copy(saving = false, done = true) }
            } catch (e: ApiException) {
                _ui.update { it.copy(saving = false, error = e.error.message) }
            }
        }
    }
}
