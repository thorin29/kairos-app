package com.kairos.app.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.dto.CollectionDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: CollectionDto? = null,
)

class CollectionViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(CollectionUiState())
    val ui: StateFlow<CollectionUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val data = session.loadCollection()
                _ui.update { it.copy(loading = false, data = data) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Couldn't load your gallery.") }
            }
        }
    }
}
