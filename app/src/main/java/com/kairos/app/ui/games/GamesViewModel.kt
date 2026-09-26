package com.kairos.app.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.GameTimeResponseDto
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.data.local.PayloadCacheStore
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

class GamesViewModel(private val session: SessionRepository, private val cache: PayloadCacheStore) : ViewModel() {
    private val _ui = MutableStateFlow(GamesUiState())
    val ui: StateFlow<GamesUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = it.data == null, loadError = null) }
        viewModelScope.launch {
            val pid = session.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD
            if (_ui.value.data == null) {
                runCatching { cache.readAs("games", "main", pid, com.kairos.app.data.remote.dto.GameTimeResponseDto.serializer()) }
                    .getOrNull()?.let { d -> _ui.update { if (it.data == null) it.copy(data = d, loading = false) else it } }
            }
            try {
                val data = session.loadGameTime()
                _ui.update { it.copy(loading = false, refreshing = false, data = data) }
                launch { runCatching { cache.writeAs("games", "main", pid, com.kairos.app.data.remote.dto.GameTimeResponseDto.serializer(), data) } }
            } catch (e: ApiException) {
                _ui.update {
                    if (it.data == null) it.copy(loading = false, refreshing = false, loadError = e.error.message)
                    else it.copy(loading = false, refreshing = false)
                }
            }
        }
    }

    fun refresh() {
        _ui.update { it.copy(refreshing = true) }
        load()
    }
}
