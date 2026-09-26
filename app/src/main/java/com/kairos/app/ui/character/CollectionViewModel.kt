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
)

class CollectionViewModel(private val session: SessionRepository, private val cache: PayloadCacheStore) : ViewModel() {
    private val _ui = MutableStateFlow(CollectionUiState())
    val ui: StateFlow<CollectionUiState> = _ui.asStateFlow()

    init { load() }

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
