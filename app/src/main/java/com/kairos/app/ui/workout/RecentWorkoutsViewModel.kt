package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.WorkoutHistoryDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecentUiState(
    val loading: Boolean = true,
    val history: List<WorkoutHistoryDto> = emptyList(),
    val error: String? = null,
    val deletingIds: Set<String> = emptySet(),
)

class RecentWorkoutsViewModel(private val session: SessionRepository) : ViewModel() {

    private val _ui = MutableStateFlow(RecentUiState())
    val ui: StateFlow<RecentUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = it.history.isEmpty(), error = null) }
        viewModelScope.launch {
            try {
                val p = session.loadWorkoutProgress()
                _ui.update { it.copy(loading = false, history = p.history) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, error = if (it.history.isEmpty()) e.error.message else it.error) }
            }
        }
    }

    fun delete(id: String) {
        if (_ui.value.deletingIds.contains(id)) return
        _ui.update { it.copy(deletingIds = it.deletingIds + id) }
        viewModelScope.launch {
            try {
                session.deleteWorkoutSession(id)
                _ui.update {
                    it.copy(history = it.history.filterNot { h -> h.id == id }, deletingIds = it.deletingIds - id)
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(deletingIds = it.deletingIds - id, error = e.error.message) }
            }
        }
    }
}
