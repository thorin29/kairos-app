package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.BrowseWorkoutDto
import com.kairos.app.data.remote.dto.SharePersonDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseUiState(
    val loading: Boolean = true,
    val items: List<BrowseWorkoutDto> = emptyList(),
    val people: List<SharePersonDto> = emptyList(),
    val error: String? = null,
    val shared: Boolean = false,
)

class BrowseWorkoutsViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(BrowseUiState())
    val ui: StateFlow<BrowseUiState> = _ui.asStateFlow()

    init { reload(first = true) }

    private fun reload(first: Boolean = false) {
        viewModelScope.launch {
            try {
                val items = session.loadBrowse()
                val people = if (first) runCatching { session.loadWorkoutBuilder().people }.getOrDefault(emptyList()) else _ui.value.people
                _ui.update { it.copy(loading = false, items = items, people = people) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, error = e.error.message) }
            }
        }
    }

    fun share(workoutId: String, targetUserId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { session.shareWorkout(workoutId, targetUserId) }
                .onSuccess { _ui.update { it.copy(shared = true) } }
            onDone()
        }
    }

    fun clearShared() = _ui.update { it.copy(shared = false) }

    fun delete(workoutId: String) {
        viewModelScope.launch {
            runCatching { session.deletePersonalWorkout(workoutId) }
            reload()
        }
    }
}
