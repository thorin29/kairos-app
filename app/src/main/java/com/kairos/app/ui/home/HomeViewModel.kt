package com.kairos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val busy: Boolean = false,
)

class HomeViewModel(private val session: SessionRepository) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    fun signOut() {
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch { session.signOut() }
    }

    fun refresh() {
        viewModelScope.launch { session.refreshMe() }
    }
}
