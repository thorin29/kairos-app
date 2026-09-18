package com.kairos.app.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.GameTimeResponseDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GamesUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val data: GameTimeResponseDto? = null,
    val refreshing: Boolean = false,
)

class GamesViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(GamesUiState())
    val ui: StateFlow<GamesUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = it.data == null, loadError = null) }
        viewModelScope.launch {
            try {
                _ui.update { it.copy(loading = false, refreshing = false, data = session.loadGameTime()) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, refreshing = false, loadError = e.error.message) }
            }
        }
    }

    fun refresh() {
        _ui.update { it.copy(refreshing = true) }
        load()
    }
}
