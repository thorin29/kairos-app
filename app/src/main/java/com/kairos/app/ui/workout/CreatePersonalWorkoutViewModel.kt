package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.dto.BuilderMovementDto
import com.kairos.app.data.remote.dto.CreatePersonalWorkoutRequest
import com.kairos.app.data.remote.dto.LogCategoryDto
import com.kairos.app.data.remote.dto.PersonalMovementReq
import com.kairos.app.data.remote.dto.WorkoutTypeOptionDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One movement chosen for the workout, with its editable metrics. */
data class MoveRow(
    val poolExerciseId: String,
    val name: String,
    val category: String,
    val reps: String = "",
    val weight: String = "",
    val distance: String = "",
)

data class CreateWorkoutUi(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    val types: List<WorkoutTypeOptionDto> = emptyList(),
    val categories: List<LogCategoryDto> = emptyList(),
    val pool: List<BuilderMovementDto> = emptyList(),
    val name: String = "",
    val typeKey: String = "",
    val capSec: String = "",
    val notes: String = "",
    val rows: List<MoveRow> = emptyList(),
) {
    val canSave: Boolean
        get() = name.trim().length >= 2 && typeKey.isNotBlank() && rows.isNotEmpty()
}

class CreatePersonalWorkoutViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(CreateWorkoutUi())
    val ui: StateFlow<CreateWorkoutUi> = _ui.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            try {
                val b = session.loadWorkoutBuilder()
                _ui.update {
                    it.copy(
                        loading = false,
                        types = b.types,
                        categories = b.categories,
                        pool = b.movements,
                        typeKey = it.typeKey.ifBlank { b.types.firstOrNull()?.key ?: "" },
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Couldn't load.") }
            }
        }
    }

    fun onName(v: String) = _ui.update { it.copy(name = v, error = null) }
    fun onType(v: String) = _ui.update { it.copy(typeKey = v) }
    fun onCap(v: String) = _ui.update { it.copy(capSec = v.filter { c -> c.isDigit() }) }
    fun onNotes(v: String) = _ui.update { it.copy(notes = v) }

    fun addMovement(poolId: String) {
        val m = _ui.value.pool.firstOrNull { it.id == poolId } ?: return
        _ui.update { it.copy(rows = it.rows + MoveRow(m.id, m.name, m.category)) }
    }

    fun removeRow(index: Int) =
        _ui.update { it.copy(rows = it.rows.filterIndexed { i, _ -> i != index }) }

    fun onReps(index: Int, v: String) = editRow(index) { it.copy(reps = v.filter { c -> c.isDigit() }) }
    fun onWeight(index: Int, v: String) = editRow(index) { it.copy(weight = v.filter { c -> c.isDigit() || c == '.' }) }
    fun onDistance(index: Int, v: String) = editRow(index) { it.copy(distance = v.filter { c -> c.isDigit() || c == '.' }) }

    private fun editRow(index: Int, f: (MoveRow) -> MoveRow) =
        _ui.update { s -> s.copy(rows = s.rows.mapIndexed { i, r -> if (i == index) f(r) else r }) }

    /** Add a brand-new movement (shows only in this person's menus) and select it. */
    fun addCustomMovement(category: String, name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val id = session.addMovement(category, name)
                if (id != null) {
                    // reload the pool so the new movement is pickable and add it now
                    val b = session.loadWorkoutBuilder()
                    _ui.update { it.copy(pool = b.movements) }
                    addMovement(id)
                }
                onDone()
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message ?: "Couldn't add the movement.") }
                onDone()
            }
        }
    }

    fun submit() {
        val s = _ui.value
        if (!s.canSave || s.saving) return
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                session.createPersonalWorkout(
                    CreatePersonalWorkoutRequest(
                        name = s.name.trim(),
                        type = s.typeKey,
                        capSec = s.capSec.toIntOrNull(),
                        notes = s.notes.trim().ifBlank { null },
                        movements = s.rows.map { r ->
                            PersonalMovementReq(
                                poolExerciseId = r.poolExerciseId,
                                reps = r.reps.toIntOrNull(),
                                distance = r.distance.toDoubleOrNull(),
                                weight = r.weight.toDoubleOrNull(),
                            )
                        },
                    ),
                )
                _ui.update { it.copy(saving = false, done = true) }
            } catch (e: Exception) {
                _ui.update { it.copy(saving = false, error = e.message ?: "Couldn't save.") }
            }
        }
    }
}
