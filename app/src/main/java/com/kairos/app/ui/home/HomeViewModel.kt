package com.kairos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.DashboardDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val dashboard: DashboardDto? = null,
    /** Full-screen load error, shown only when there's nothing to display. */
    val loadError: String? = null,
    /** Transient error from a tap (e.g. a completion failed). */
    val actionError: String? = null,
    /** Task ids with an in-flight complete/uncomplete, for per-row spinners. */
    val busyIds: Set<String> = emptySet(),
    val signingOut: Boolean = false,
)

class HomeViewModel(private val session: SessionRepository) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = it.dashboard == null, loadError = null) }
        viewModelScope.launch {
            try {
                val data = session.loadDashboard()
                _ui.update { it.copy(loading = false, refreshing = false, dashboard = data, loadError = null) }
            } catch (e: ApiException) {
                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadError = if (it.dashboard == null) e.error.message else it.loadError,
                        actionError = if (it.dashboard != null) e.error.message else null,
                    )
                }
            }
        }
    }

    fun refresh() {
        _ui.update { it.copy(refreshing = true) }
        load()
    }

    /** Toggle a task, then reload so the server-derived bars/percent stay right. */
    fun toggle(taskId: String, currentlyComplete: Boolean) {
        if (_ui.value.busyIds.contains(taskId)) return
        _ui.update { it.copy(busyIds = it.busyIds + taskId, actionError = null) }
        viewModelScope.launch {
            try {
                if (currentlyComplete) session.uncompleteTask(taskId)
                else session.completeTask(taskId)
                val data = session.loadDashboard()
                _ui.update { it.copy(dashboard = data, busyIds = it.busyIds - taskId) }
            } catch (e: ApiException) {
                _ui.update { it.copy(busyIds = it.busyIds - taskId, actionError = e.error.message) }
            }
        }
    }

    fun clearActionError() {
        _ui.update { it.copy(actionError = null) }
    }

    fun signOut() {
        _ui.update { it.copy(signingOut = true) }
        viewModelScope.launch { session.signOut() }
    }
}
