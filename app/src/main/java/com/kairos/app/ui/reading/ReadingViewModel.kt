package com.kairos.app.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.AddBookRequest
import com.kairos.app.data.remote.dto.BooksDto
import com.kairos.app.data.remote.dto.UpdateBookRequest
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReadingUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val data: BooksDto? = null,
    val saving: Boolean = false,
    val saveError: String? = null,
    val busy: Boolean = false,
)

/**
 * Owns the leisure Reading surface: one self-only read (GET /books), add a book,
 * set the page you're up to, edit, finish/reopen, shelve/un-shelve, delete.
 * Mutations reload so the queue and bookshelf refresh in place.
 */
class ReadingViewModel(
    private val session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ReadingUiState())
    val ui: StateFlow<ReadingUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = it.data == null, loadError = null) }
        viewModelScope.launch {
            try {
                val data = session.loadBooks()
                _ui.update { it.copy(loading = false, data = data) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
            }
        }
    }

    fun clearSaveError() = _ui.update { it.copy(saveError = null) }

    fun add(req: AddBookRequest, onDone: () -> Unit) = form(onDone) { session.addBook(req) }
    fun update(req: UpdateBookRequest, onDone: () -> Unit) = form(onDone) { session.updateBook(req) }

    fun log(id: String, page: Int) = act { session.logBook(id, page) }
    fun finish(id: String, finished: Boolean, onDone: () -> Unit = {}) = act(onDone) { session.finishBook(id, finished) }
    fun shelf(id: String, shelved: Boolean, onDone: () -> Unit = {}) = act(onDone) { session.shelfBook(id, shelved) }
    fun delete(id: String, onDone: () -> Unit = {}) = act(onDone) { session.deleteBook(id) }

    private fun form(onDone: () -> Unit, block: suspend () -> Unit) {
        if (_ui.value.saving) return
        _ui.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                block()
                _ui.update { it.copy(saving = false) }
                onDone()
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(saving = false, saveError = e.error.message) }
            }
        }
    }

    private fun act(onDone: () -> Unit = {}, block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
                _ui.update { it.copy(busy = false) }
                onDone()
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(busy = false, loadError = e.error.message) }
            }
        }
    }
}
