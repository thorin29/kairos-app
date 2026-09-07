package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.dto.BuilderMovementDto
import com.kairos.app.data.remote.dto.CreatePersonalWorkoutRequest
import com.kairos.app.data.remote.dto.MyWorkoutDto
import com.kairos.app.data.remote.dto.PersonalMovementReq
import com.kairos.app.data.remote.dto.UpdatePersonalWorkoutRequest
import com.kairos.app.data.remote.dto.WorkoutTypeOptionDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One exercise chosen for the workout, with its editable metrics. */
data class MoveRow(
    val poolExerciseId: String,
    val name: String,
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
    val pool: List<BuilderMovementDto> = emptyList(),
    val myExercises: List<BuilderMovementDto> = emptyList(),
    val myWorkouts: List<MyWorkoutDto> = emptyList(),
    val editingId: String? = null,
    val locked: Boolean = false,
    val name: String = "",
    val typeKey: String = "",
    val cap: String = "",
    val instructions: String = "",
    val rows: List<MoveRow> = emptyList(),
) {
    val canSave: Boolean
        get() = name.trim().length >= 2 && typeKey.isNotBlank() && rows.isNotEmpty()

    /** A time cap only applies to AMRAP (minutes) and Timed stations (seconds). */
    val capLabel: String?
        get() = when (typeKey) {
            "AMRAP" -> "Time cap (min)"
            "TIMED_STATIONS" -> "Station time (sec)"
            else -> null
        }
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
                        pool = b.movements,
                        myExercises = b.myExercises,
                        myWorkouts = b.myWorkouts,
                        typeKey = it.typeKey.ifBlank { b.types.firstOrNull()?.key ?: "" },
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Couldn't load.") }
            }
        }
    }

    private fun capFromSec(type: String, capSec: Int?): String = when {
        capSec == null -> ""
        type == "AMRAP" -> (Math.round(capSec / 60.0)).toString()
        type == "TIMED_STATIONS" -> capSec.toString()
        else -> ""
    }

    private fun capToSec(type: String, cap: String): Int? {
        val n = cap.toIntOrNull() ?: return null
        return when (type) {
            "AMRAP" -> n * 60
            "TIMED_STATIONS" -> n
            else -> null
        }
    }

    /** Load one of the person's existing workouts into the form (read-only until Edit). */
    fun selectExisting(id: String) {
        val w = _ui.value.myWorkouts.firstOrNull { it.id == id } ?: return
        val poolById = _ui.value.pool.associateBy { it.id }
        _ui.update {
            it.copy(
                editingId = w.id,
                locked = true,
                name = w.name,
                typeKey = w.type,
                cap = capFromSec(w.type, w.capSec),
                instructions = w.notes ?: "",
                error = null,
                rows = w.movements.map { m ->
                    MoveRow(
                        poolExerciseId = m.poolExerciseId,
                        name = poolById[m.poolExerciseId]?.name ?: "Exercise",
                        reps = m.reps?.toString() ?: "",
                        weight = m.weight?.toString() ?: "",
                        distance = m.distance?.toString() ?: "",
                    )
                },
            )
        }
    }

    /** Start a fresh workout with a typed name. */
    fun startNew(name: String) {
        _ui.update {
            it.copy(
                editingId = null,
                locked = false,
                name = name,
                typeKey = it.types.firstOrNull()?.key ?: "",
                cap = "",
                instructions = "",
                rows = emptyList(),
                error = null,
            )
        }
    }

    fun enableEdit() = _ui.update { it.copy(locked = false) }

    fun onName(v: String) = _ui.update { it.copy(name = v, error = null) }
    fun onType(v: String) = _ui.update { it.copy(typeKey = v) }
    fun onCap(v: String) = _ui.update { it.copy(cap = v.filter { c -> c.isDigit() }) }
    fun onInstructions(v: String) = _ui.update { it.copy(instructions = v) }

    fun addExercise(poolId: String) {
        val m = _ui.value.pool.firstOrNull { it.id == poolId } ?: return
        _ui.update { it.copy(rows = it.rows + MoveRow(m.id, m.name)) }
    }

    fun removeRow(index: Int) =
        _ui.update { it.copy(rows = it.rows.filterIndexed { i, _ -> i != index }) }

    fun onReps(index: Int, v: String) = editRow(index) { it.copy(reps = v.filter { c -> c.isDigit() }) }
    fun onWeight(index: Int, v: String) = editRow(index) { it.copy(weight = v.filter { c -> c.isDigit() || c == '.' }) }
    fun onDistance(index: Int, v: String) = editRow(index) { it.copy(distance = v.filter { c -> c.isDigit() || c == '.' }) }

    private fun editRow(index: Int, f: (MoveRow) -> MoveRow) =
        _ui.update { s -> s.copy(rows = s.rows.mapIndexed { i, r -> if (i == index) f(r) else r }) }

    /** Add a brand-new exercise to the HIIT pool (shows only in this person's menus) and select it. */
    fun addCustomExercise(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val id = session.addMovement("HIIT", name)
                if (id != null) {
                    val b = session.loadWorkoutBuilder()
                    _ui.update { it.copy(pool = b.movements) }
                    addExercise(id)
                }
                onDone()
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message ?: "Couldn't add the exercise.") }
                onDone()
            }
        }
    }

    fun submit() {
        val s = _ui.value
        if (!s.canSave || s.saving) return
        _ui.update { it.copy(saving = true, error = null) }
        val moves = s.rows.map { r ->
            PersonalMovementReq(
                poolExerciseId = r.poolExerciseId,
                reps = r.reps.toIntOrNull(),
                distance = r.distance.toDoubleOrNull(),
                weight = r.weight.toDoubleOrNull(),
            )
        }
        val capSec = capToSec(s.typeKey, s.cap)
        val notes = s.instructions.trim().ifBlank { null }
        viewModelScope.launch {
            try {
                if (s.editingId != null) {
                    session.updatePersonalWorkout(
                        UpdatePersonalWorkoutRequest(s.editingId, s.name.trim(), s.typeKey, capSec, notes, moves),
                    )
                } else {
                    session.createPersonalWorkout(
                        CreatePersonalWorkoutRequest(s.name.trim(), s.typeKey, capSec, notes, moves),
                    )
                }
                _ui.update { it.copy(saving = false, done = true) }
            } catch (e: Exception) {
                _ui.update { it.copy(saving = false, error = e.message ?: "Couldn't save.") }
            }
        }
    }

    fun deleteWorkout() {
        val id = _ui.value.editingId ?: return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { session.deletePersonalWorkout(id) }
            _ui.update { it.copy(saving = false, done = true) }
        }
    }

    private suspend fun refreshBuilder() {
        val b = session.loadWorkoutBuilder()
        val poolById = b.movements.associateBy { it.id }
        _ui.update { s ->
            s.copy(
                pool = b.movements,
                myExercises = b.myExercises,
                rows = s.rows
                    .filter { poolById.containsKey(it.poolExerciseId) }
                    .map { it.copy(name = poolById[it.poolExerciseId]?.name ?: it.name) },
            )
        }
    }

    fun renameExercise(id: String, name: String) {
        viewModelScope.launch {
            runCatching { session.renameMovement(id, name) }
            refreshBuilder()
        }
    }

    fun deleteExercise(id: String) {
        viewModelScope.launch {
            runCatching { session.deleteMovement(id) }
            refreshBuilder()
        }
    }
}
