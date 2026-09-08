package com.kairos.app.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.dto.CoopDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoopUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: CoopDto? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class CoopViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(CoopUiState())
    val ui: StateFlow<CoopUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                _ui.update { it.copy(loading = false, data = session.loadCoop()) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Couldn't load the family goal.") }
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
                _ui.update { it.copy(busy = false, data = session.loadCoop()) }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, message = e.message ?: "Something went wrong.") }
            }
        }
    }

    fun propose(title: String, detail: String) = act { session.proposeCoop(title, detail) }
    fun vote(id: String) = act { session.voteCoop(id) }
    fun select(id: String) = act { session.selectCoop(id) }
    fun grant(id: String) = act { session.grantCoop(id) }
    fun remove(id: String) = act { session.removeCoop(id) }

    fun clearMessage() { _ui.update { it.copy(message = null) } }
}
