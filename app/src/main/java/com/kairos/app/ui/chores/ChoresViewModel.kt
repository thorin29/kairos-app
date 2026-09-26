package com.kairos.app.ui.chores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.ChoresDto
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.data.local.PayloadCacheStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChoresUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val data: ChoresDto? = null,
    val refreshing: Boolean = false,
)

/**
 * Owns the read-only Chores overview: one aggregate read (GET /chores). No
 * writes — completion is on Home (dashboard tasks) and management is web-only,
 * mirroring the web /chores page.
 */
class ChoresViewModel(
    private val session: SessionRepository,
    private val cache: PayloadCacheStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChoresUiState())
    val ui: StateFlow<ChoresUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_ui.value.data == null) com.kairos.app.ui.common.ScreenSnapshots.chores?.let { c -> _ui.update { it.copy(data = c) } }
        _ui.update { it.copy(loading = it.data == null, loadError = null) }
        viewModelScope.launch {
            val pid = session.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD
            if (_ui.value.data == null) {
                runCatching { cache.readAs("chores", "main", pid, com.kairos.app.data.remote.dto.ChoresDto.serializer()) }
                    .getOrNull()?.let { d -> _ui.update { if (it.data == null) it.copy(data = d, loading = false) else it } }
            }
            try {
                val data = session.loadChores()
                _ui.update { it.copy(loading = false, refreshing = false, data = data) }
                com.kairos.app.ui.common.ScreenSnapshots.chores = data
                launch { runCatching { cache.writeAs("chores", "main", pid, com.kairos.app.data.remote.dto.ChoresDto.serializer(), data) } }
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
