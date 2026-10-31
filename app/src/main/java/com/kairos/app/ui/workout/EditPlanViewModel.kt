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
)

class EditPlanViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(EditPlanUiState())
    val ui: StateFlow<EditPlanUiState> = _ui.asStateFlow()

    init {
        load(initial = true)
    }

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
                _ui.update { it.copy(orders = saved) }
            } catch (e: ApiException) {
                _ui.update { it.copy(error = e.error.message) }
            }
        }
    }
}
