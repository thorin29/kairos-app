package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.WorkoutHistoryDto
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.data.local.PayloadCacheStore
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

class RecentWorkoutsViewModel(private val session: SessionRepository, private val cache: PayloadCacheStore) : ViewModel() {

    private val _ui = MutableStateFlow(RecentUiState())
    val ui: StateFlow<RecentUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = it.history.isEmpty(), error = null) }
        viewModelScope.launch {
            val pid = session.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD
            if (_ui.value.history.isEmpty()) {
                runCatching { cache.readAs("workout-progress", "main", pid, com.kairos.app.data.remote.dto.WorkoutProgressDto.serializer()) }
                    .getOrNull()?.let { p -> _ui.update { if (it.history.isEmpty()) it.copy(history = p.history, loading = false) else it } }
            }
            try {
                val p = session.loadWorkoutProgress()
                _ui.update { it.copy(loading = false, history = p.history) }
                launch { runCatching { cache.writeAs("workout-progress", "main", pid, com.kairos.app.data.remote.dto.WorkoutProgressDto.serializer(), p) } }
            } catch (e: ApiException) {
                _ui.update {
                    if (it.history.isEmpty()) it.copy(loading = false, error = e.error.message)
                    else it.copy(loading = false)
                }
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
                // Re-fetch authoritative progress and refresh the durable cache so
                // the deleted item can't resurface from Room on a cold start.
                launch {
                    runCatching { session.loadWorkoutProgress() }.getOrNull()?.let { p ->
                        _ui.update { it.copy(history = p.history) }
                        runCatching { cache.writeAs("workout-progress", "main", session.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD, com.kairos.app.data.remote.dto.WorkoutProgressDto.serializer(), p) }
                    }
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(deletingIds = it.deletingIds - id, error = e.error.message) }
            }
        }
    }
}
