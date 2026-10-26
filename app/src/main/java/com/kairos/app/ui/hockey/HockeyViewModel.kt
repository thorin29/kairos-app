package com.kairos.app.ui.hockey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.local.PayloadCacheStore
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.HockeyDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HockeyUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val data: HockeyDto? = null,
    val refreshing: Boolean = false,
    /** Showing the up/down arrows to reorder sections. */
    val arranging: Boolean = false,
)

/** The week of ice times, asked of the server (which asks ice-time) each time
 *  the screen opens. The last good week is cached so it shows straight away. */
class HockeyViewModel(private val session: SessionRepository, private val cache: PayloadCacheStore) : ViewModel() {
    private val _ui = MutableStateFlow(HockeyUiState())
    val ui: StateFlow<HockeyUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = it.data == null, loadError = null) }
        viewModelScope.launch {
            val pid = session.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD
            if (_ui.value.data == null) {
                runCatching { cache.readAs("hockey", "main", pid, HockeyDto.serializer()) }
                    .getOrNull()?.let { d -> _ui.update { if (it.data == null) it.copy(data = d, loading = false) else it } }
            }
            try {
                val data = session.loadHockey()
                // An unreachable ice-time keeps the last good week on screen and
                // says so, rather than blanking it.
                if (data.state == "error" && _ui.value.data?.state == "ok") {
                    _ui.update { it.copy(loading = false, refreshing = false, loadError = data.error) }
                } else {
                    _ui.update { it.copy(loading = false, refreshing = false, data = data) }
                    if (data.state == "ok") {
                        launch { runCatching { cache.writeAs("hockey", "main", pid, HockeyDto.serializer(), data) } }
                    }
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, refreshing = false, loadError = e.error.message) }
            }
        }
    }

    fun setArranging(on: Boolean) = _ui.update { it.copy(arranging = on) }

    /** Open or collapse a section; remembered for this person. */
    fun toggle(name: String) = relayout { list -> list.map { if (it.name == name) it.copy(open = !it.open) else it } }

    /** Move a section up (-1) or down (+1); remembered for this person. */
    fun move(index: Int, by: Int) = relayout { list ->
        val j = index + by
        if (index !in list.indices || j !in list.indices) list
        else list.toMutableList().also { val t = it[index]; it[index] = it[j]; it[j] = t }
    }

    /** Apply a layout change on screen at once, keep it in the Room cache so
     *  it holds offline, and save it to the server (queued when offline). */
    private fun relayout(change: (List<com.kairos.app.data.remote.dto.HockeySectionDto>) -> List<com.kairos.app.data.remote.dto.HockeySectionDto>) {
        val cur = _ui.value.data ?: return
        val next = cur.copy(sections = change(cur.sections))
        _ui.update { it.copy(data = next) }
        viewModelScope.launch {
            val pid = session.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD
            runCatching { cache.writeAs("hockey", "main", pid, HockeyDto.serializer(), next) }
            runCatching {
                session.saveHockeyPrefs(
                    order = next.sections.map { it.name },
                    open = next.sections.filter { it.open }.map { it.name },
                )
            }
        }
    }

    fun refresh() {
        _ui.update { it.copy(refreshing = true) }
        load()
    }
}
