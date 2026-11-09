package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.PlanDayDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EditPlanUiState(
    val loading: Boolean = true,
    val days: List<PlanDayDto> = emptyList(),
    val weeklyStart: String = "",
    val weeklyActive: Boolean = true,
    val options: com.kairos.app.data.remote.dto.PlanOptionsDto? = null,
    /** Section orders: every day's and weekdays' own (server 0.615+; null on an older one). */
    val orders: com.kairos.app.data.remote.dto.DayOrdersDto? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** When the last change was saved (every change saves at once): the
     *  screen shows "Saved" each time it moves (v0.454). */
    val savedAt: Long = 0L,
    /** Household plans, loaded when that sheet opens (server 0.618+). */
    val sharedPlans: List<com.kairos.app.data.remote.dto.SharedPlanDto>? = null,
    /** What the chosen file or household plan would do. */
    val preview: com.kairos.app.data.remote.dto.ImportPreviewDto? = null,
    /** The household plan [preview] is of; null = a file. */
    val previewPlanId: String? = null,
    /** The file chosen to import, as text. */
    val importText: String? = null,
    /** "Done. 4 added to your plan · 1 new workout." / "Sent for approval." */
    val fileMessage: String? = null,
    val fileError: String? = null,
    val fileBusy: Boolean = false,
)

class EditPlanViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(EditPlanUiState())
    val ui: StateFlow<EditPlanUiState> = _ui.asStateFlow()

    init {
        load(initial = true)
    }

    /** Reload after a change made elsewhere (a warm-up's routine, v0.430). */
    fun reload() {
        markSaved()
        load(initial = false)
    }

    private fun markSaved() = _ui.update { it.copy(savedAt = System.currentTimeMillis()) }

    private fun load(initial: Boolean) {
        if (initial) _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val plan = session.loadPlan()
                val opts = if (_ui.value.options == null) runCatching { session.loadPlanOptions() }.getOrNull() else _ui.value.options
                val orders = runCatching { session.dayOrders() }.getOrNull()?.takeIf { it.everyDay.isNotEmpty() } ?: _ui.value.orders
                _ui.update { it.copy(loading = false, busy = false, days = plan.days, weeklyStart = plan.weeklyStart, weeklyActive = plan.weeklyActive, options = opts, orders = orders, error = null) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, busy = false, error = e.error.message) }
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                markSaved()
                load(initial = false)
            } catch (e: ApiException) {
                _ui.update { it.copy(busy = false, error = e.error.message) }
            }
        }
    }

    fun setStartDate(iso: String) = act { session.setWeeklyStart(iso) }
    fun setWeeklyActive(active: Boolean) = act { session.setWeeklyActive(active) }
    fun markRest(day: Int) = act { session.planMarkRest(day) }
    fun copyDay(from: Int, to: Int) = act { session.planCopyDay(from, to) }
    fun remove(id: String) = act { session.planRemove(id) }
    fun addPool(body: com.kairos.app.data.remote.dto.AddPoolRequest) = act { session.planAddPool(body) }
    fun addHiit(day: Int, hiitWorkoutId: String) = act { session.planAddHiit(day, hiitWorkoutId) }
    // --- core days (server 0.637+) -----------------------------------------

    /** Make a Core day on `day`, or change `plannedWorkoutId`'s rotation. */
    fun setCoreDay(plannedWorkoutId: String?, day: Int?, rotation: List<String>, test: Boolean, kind: String = "core") =
        act { session.setCoreDay(plannedWorkoutId = plannedWorkoutId, day = day, rotation = rotation, test = test, kind = kind) }

    /** Save one's own core workout; the options reload so it shows in the list. */
    fun saveCoreWorkout(
        id: String?,
        name: String,
        moves: List<com.kairos.app.data.remote.dto.CoreMoveIn>,
        onSaved: (String) -> Unit,
        onError: (String) -> Unit,
        kind: String = "core",
    ) {
        viewModelScope.launch {
            try {
                val saved = session.saveCoreWorkout(id = id, name = name, moves = moves, kind = kind)
                refreshOptions()
                markSaved()
                if (id != null) load(initial = false)
                onSaved(saved)
            } catch (e: ApiException) {
                onError(e.error.message)
            }
        }
    }

    fun deleteCoreWorkout(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                session.deleteCoreWorkout(id)
                refreshOptions()
                markSaved()
                load(initial = false)
                onDone()
            } catch (e: ApiException) {
                _ui.update { it.copy(error = e.error.message) }
            }
        }
    }

    private suspend fun refreshOptions() {
        val o = runCatching { session.loadPlanOptions() }.getOrNull() ?: return
        _ui.update { it.copy(options = o) }
    }

    fun setScheme(plannedWorkoutId: String, poolExerciseId: String, scheme: String) =
        act { session.setPlanScheme(plannedWorkoutId = plannedWorkoutId, poolExerciseId = poolExerciseId, scheme = scheme) }

    /** Save a section order: every day (`day` null) or one weekday; null order resets. */
    fun saveOrder(day: Int?, order: List<String>?) {
        _ui.update { u ->
            val cur = u.orders ?: return@update u
            u.copy(
                orders = if (day == null) {
                    cur.copy(everyDay = order ?: cur.everyDay)
                } else {
                    cur.copy(days = if (order == null) cur.days - day.toString() else cur.days + (day.toString() to order))
                },
            )
        }
        viewModelScope.launch {
            try {
                val saved = session.saveDayOrder(day = day, order = order)
                _ui.update { it.copy(orders = saved, savedAt = System.currentTimeMillis()) }
            } catch (e: ApiException) {
                _ui.update { it.copy(error = e.error.message) }
            }
        }
    }

    // --- workouts file and household plans (server 0.618+) ---------------

    /** Clear what the file sheets were showing. */
    fun clearFile() {
        _ui.update {
            it.copy(preview = null, previewPlanId = null, importText = null, fileMessage = null, fileError = null, fileBusy = false)
        }
    }

    private fun fileAct(block: suspend () -> Unit) {
        _ui.update { it.copy(fileBusy = true, fileError = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                _ui.update { it.copy(fileError = e.error.message) }
            } catch (e: Exception) {
                _ui.update { it.copy(fileError = "Couldn't read or save that file.") }
            } finally {
                _ui.update { it.copy(fileBusy = false) }
            }
        }
    }

    /** Fetch the workouts file and hand it to [write] (the file the person picked). */
    fun exportTo(history: Boolean, write: (String) -> Unit) = fileAct {
        val text = session.exportWorkouts(history)
        withContext(Dispatchers.IO) { write(text) }
        _ui.update { it.copy(fileMessage = "Saved.") }
    }

    /** Read the picked file and ask the server what it holds. */
    fun readImport(read: () -> String?) = fileAct {
        val text = withContext(Dispatchers.IO) { read() } ?: throw IllegalStateException("unreadable")
        val p = session.previewImport(text)
        _ui.update { it.copy(importText = text, preview = p, previewPlanId = null) }
    }

    fun loadSharedPlans() {
        _ui.update { it.copy(sharedPlans = null) }
        fileAct {
            val plans = session.sharedPlans()
            _ui.update { it.copy(sharedPlans = plans) }
        }
    }

    fun previewSharedPlan(id: String) = fileAct {
        val p = session.previewSharedPlan(id)
        _ui.update { it.copy(preview = p, previewPlanId = id, importText = null) }
    }

    /** Bring the previewed file or household plan in: replace or add. */
    fun applyPreview(mode: String) = fileAct {
        val u = _ui.value
        val id = u.previewPlanId
        val text = u.importText
        val added = when {
            id != null -> session.useSharedPlan(id = id, mode = mode)
            text != null -> session.applyImport(fileText = text, mode = mode)
            else -> null
        }
        val msg = buildList {
            add("Done. ${added?.plans ?: 0} added to your plan")
            added?.workouts?.takeIf { it > 0 }?.let { add("$it new ${if (it == 1) "workout" else "workouts"}") }
            added?.movements?.takeIf { it > 0 }?.let { add("$it new ${if (it == 1) "movement" else "movements"}") }
        }.joinToString(" \u00b7 ") + "."
        _ui.update { it.copy(fileMessage = msg, preview = null) }
        load(initial = false)
    }

    fun sharePlan(name: String) = fileAct {
        val r = session.sharePlan(name)
        // A parent's plan is shared at once (server 0.624+).
        _ui.update { it.copy(fileMessage = if (r.status == "shared") "Shared with the household." else "Sent to a parent to approve.") }
    }

    /** Save the current plan to My plans (server 0.620+). */
    fun savePlan(name: String) = fileAct {
        session.savePlan(name)
        _ui.update { it.copy(fileMessage = "Saved to My plans.") }
    }

    fun withdrawSharedPlan(id: String) = fileAct {
        session.withdrawSharedPlan(id)
        _ui.update { u -> u.copy(sharedPlans = u.sharedPlans?.filter { it.id != id }) }
    }
}
