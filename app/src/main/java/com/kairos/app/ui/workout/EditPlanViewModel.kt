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
                _ui.update { it.copy(loading = false, busy = false, days = session.loadPlan(), error = null) }
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

    fun markRest(day: Int) = act { session.planMarkRest(day) }
    fun copyDay(from: Int, to: Int) = act { session.planCopyDay(from, to) }
    fun remove(id: String) = act { session.planRemove(id) }
}
