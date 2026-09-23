package com.kairos.app.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.dto.CharacterDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CharacterUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: CharacterDto? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class CharacterViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(CharacterUiState())
    val ui: StateFlow<CharacterUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val data = session.loadCharacter()
                _ui.update { it.copy(loading = false, data = data) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Couldn't load your character.") }
            }
        }
    }

    fun hatch(mode: String) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val r = session.hatchCompanion(mode)
                val data = session.loadCharacter()
                _ui.update {
                    it.copy(busy = false, data = data, message = r.hatched?.let { h -> "It's $h!" } ?: "Done!")
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, message = e.message ?: "Couldn't hatch right now.") }
            }
        }
    }

    fun clearMessage() { _ui.update { it.copy(message = null) } }
}
