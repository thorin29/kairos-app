package com.kairos.app.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.dto.CollectionDto
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.data.local.PayloadCacheStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: CollectionDto? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class CollectionViewModel(private val session: SessionRepository, private val cache: PayloadCacheStore) : ViewModel() {
    private val _ui = MutableStateFlow(CollectionUiState())
    val ui: StateFlow<CollectionUiState> = _ui.asStateFlow()

    init { load() }

    /**
     * Bring an owned companion out of the gallery and make it the active one.
     *
     * Reloads afterwards rather than patching the list: activating also retires
     * whoever was in the slot, so two entries change and the server is the only
     * thing that knows both.
     */
    fun activate(species: String) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val r = session.activateCompanion(species)
                _ui.update { it.copy(busy = false, message = r.activated?.let { n -> "$n is with you now." }) }
                load()
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, message = e.message ?: "Couldn't do that.") }
            }
        }
    }

    fun clearMessage() { _ui.update { it.copy(message = null) } }

    fun load() {
        _ui.update { it.copy(loading = it.data == null, error = null) }
        viewModelScope.launch {
            val pid = session.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD
            if (_ui.value.data == null) {
                runCatching { cache.readAs("collection", "main", pid, com.kairos.app.data.remote.dto.CollectionDto.serializer()) }
                    .getOrNull()?.let { d -> _ui.update { if (it.data == null) it.copy(data = d, loading = false) else it } }
            }
            try {
                val data = session.loadCollection()
                _ui.update { it.copy(loading = false, data = data) }
                launch { runCatching { cache.writeAs("collection", "main", pid, com.kairos.app.data.remote.dto.CollectionDto.serializer(), data) } }
            } catch (e: Exception) {
                _ui.update {
                    if (it.data == null) it.copy(loading = false, error = e.message ?: "Couldn't load your gallery.")
                    else it.copy(loading = false)
                }
            }
        }
    }
}
