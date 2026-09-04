package com.kairos.app.ui.bible

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.PersonalPlanRequest
import com.kairos.app.data.remote.dto.ReadingDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BibleTab { FAMILY, PERSONAL }

data class BibleUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val data: ReadingDto? = null,
    val tab: BibleTab = BibleTab.FAMILY,
    val busy: Boolean = false,
    val actionError: String? = null,
    /** Bumps after each successful write, so a snackbar can react. */
    val savedTick: Int = 0,
)

/**
 * Owns the Bible screen: one aggregate read (GET /reading) into [BibleUiState],
 * and the writes (plan create/delete, day mark, book chapters, bulk books) which
 * run then reload, so the coverage bars and lists refresh without re-navigating.
 * Mirrors the web /bible page on a signed-in personal device.
 */
class BibleViewModel(
    private val session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(BibleUiState())
    val ui: StateFlow<BibleUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = it.data == null, loadError = null) }
        viewModelScope.launch {
            try {
                val data = session.loadReading()
                _ui.update { it.copy(loading = false, data = data) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
            }
        }
    }

    fun setTab(tab: BibleTab) = _ui.update { it.copy(tab = tab) }

    fun clearActionError() = _ui.update { it.copy(actionError = null) }

    /** Create (replacing) the personal plan. Returns true on success; on a
     *  validation error the message lands in [BibleUiState.actionError] and the
     *  creator stays open. */
    fun createPlan(
        name: String,
        bookNames: List<String>,
        startISO: String,
        chaptersPerDay: Int,
        onDone: () -> Unit,
    ) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            try {
                session.createReadingPlan(
                    PersonalPlanRequest(name, bookNames, startISO, chaptersPerDay),
                )
                _ui.update { it.copy(busy = false, savedTick = it.savedTick + 1) }
                onDone()
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(busy = false, actionError = e.error.message) }
            }
        }
    }

    fun deletePlan() = act { session.deleteReadingPlan() }

    fun markDay(passage: String, read: Boolean) =
        act { session.markReading(passage, read) }

    fun saveBook(bookName: String, chapters: List<Int>) =
        act { session.saveReadingBook(bookName, chapters) }

    fun bulkBooks(bookNames: List<String>, read: Boolean) =
        act { session.saveReadingBooks(bookNames, read) }

    private fun act(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            try {
                block()
                _ui.update { it.copy(busy = false, savedTick = it.savedTick + 1) }
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(busy = false, actionError = e.error.message) }
            }
        }
    }
}
