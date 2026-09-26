package com.kairos.app.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.dto.CharacterDto
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.data.local.PayloadCacheStore
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

class CharacterViewModel(private val session: SessionRepository, private val cache: PayloadCacheStore) : ViewModel() {
    private val _ui = MutableStateFlow(CharacterUiState())
    val ui: StateFlow<CharacterUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = it.data == null, error = null) }
        viewModelScope.launch {
            val pid = session.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD
            if (_ui.value.data == null) {
                runCatching { cache.readAs("character", "main", pid, com.kairos.app.data.remote.dto.CharacterDto.serializer()) }
                    .getOrNull()?.let { d -> _ui.update { if (it.data == null) it.copy(data = d, loading = false) else it } }
            }
            try {
                val data = session.loadCharacter()
                _ui.update { it.copy(loading = false, data = data) }
                launch { runCatching { cache.writeAs("character", "main", pid, com.kairos.app.data.remote.dto.CharacterDto.serializer(), data) } }
            } catch (e: Exception) {
                _ui.update {
                    if (it.data == null) it.copy(loading = false, error = e.message ?: "Couldn't load your character.")
                    else it.copy(loading = false)
                }
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
