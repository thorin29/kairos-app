package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.BrowseWorkoutDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseUiState(
    val loading: Boolean = true,
    val items: List<BrowseWorkoutDto> = emptyList(),
    val error: String? = null,
)

class BrowseWorkoutsViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(BrowseUiState())
    val ui: StateFlow<BrowseUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _ui.update { it.copy(loading = false, items = session.loadBrowse()) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, error = e.error.message) }
            }
        }
    }
}
