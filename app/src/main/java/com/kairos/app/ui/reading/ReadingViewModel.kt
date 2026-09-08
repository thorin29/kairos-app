package com.kairos.app.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.PendingWrite
import com.kairos.app.data.remote.dto.AddBookRequest
import com.kairos.app.data.remote.dto.BookDto
import com.kairos.app.data.remote.dto.BookFinishRequest
import com.kairos.app.data.remote.dto.BookIdRequest
import com.kairos.app.data.remote.dto.BookShelfRequest
import com.kairos.app.data.remote.dto.BooksDto
import com.kairos.app.data.remote.dto.LogBookRequest
import com.kairos.app.data.remote.dto.UpdateBookRequest
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
 * Every change is optimistic and re-applied from the offline queue on load, so it
 * shows immediately, survives navigation/restart offline, and reconciles on sync.
 */
class ReadingViewModel(
    private val session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ReadingUiState())
    val ui: StateFlow<ReadingUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = it.data == null, loadError = null) }
        viewModelScope.launch {
            try {
                _ui.update { it.copy(loading = false, data = freshData()) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
            }
        }
    }

    private suspend fun freshData(): BooksDto =
        applyPending(session.loadBooks(), session.pendingWrites())

    fun clearSaveError() = _ui.update { it.copy(saveError = null) }

    fun add(req: AddBookRequest, onDone: () -> Unit) =
        mutate({ insertBook(it, req) }, form = true, onDone) { session.addBook(req) }
    fun update(req: UpdateBookRequest, onDone: () -> Unit) =
        mutate({ updateBook(it, req) }, form = true, onDone) { session.updateBook(req) }

    fun log(id: String, page: Int) =
        mutate({ setRead(it, id, page) }, form = false, {}) { session.logBook(id, page) }
    fun finish(id: String, finished: Boolean, onDone: () -> Unit = {}) =
        mutate({ setFinished(it, id, finished) }, form = false, onDone) { session.finishBook(id, finished) }
    fun shelf(id: String, shelved: Boolean, onDone: () -> Unit = {}) =
        mutate({ setShelved(it, id, shelved) }, form = false, onDone) { session.shelfBook(id, shelved) }
    fun delete(id: String, onDone: () -> Unit = {}) =
        mutate({ removeBook(it, id) }, form = false, onDone) { session.deleteBook(id) }

    /** Apply the optimistic change, close the sheet/dialog, then write; reload
     *  (online) or keep the optimistic state (offline). Reverts on a real error. */
    private fun mutate(
        change: (BooksDto) -> BooksDto,
        form: Boolean,
        onDone: () -> Unit,
        write: suspend () -> Unit,
    ) {
        if (form && _ui.value.saving) return
        if (!form && _ui.value.busy) return
        val before = _ui.value.data
        _ui.update {
            val d = before?.let(change) ?: it.data
            if (form) it.copy(saving = true, saveError = null, data = d) else it.copy(busy = true, data = d)
        }
        onDone()
        viewModelScope.launch {
            try {
                write()
                val data = if (session.isOnline()) freshData() else _ui.value.data
                _ui.update { if (form) it.copy(saving = false, data = data) else it.copy(busy = false, data = data) }
            } catch (e: ApiException) {
                _ui.update {
                    if (form) it.copy(saving = false, saveError = e.error.message, data = before)
                    else it.copy(busy = false, loadError = e.error.message, data = before)
                }
            }
        }
    }

    // ---- transforms, also used to re-apply the offline queue on load ----

    private fun insertBook(data: BooksDto, req: AddBookRequest): BooksDto {
        val unit = if (req.chapters != null) "CHAPTERS" else "PAGES"
        val length = req.pages ?: req.chapters ?: 0
        val book = BookDto(
            id = "temp-${UUID.randomUUID()}",
            title = req.title,
            author = req.author,
            unit = unit,
            length = length,
            pages = req.pages,
            chapters = req.chapters,
        )
        return data.copy(books = data.books + book)
    }

    private fun updateBook(data: BooksDto, req: UpdateBookRequest): BooksDto =
        data.copy(
            books = data.books.map { b ->
                if (b.id != req.id) b
                else b.copy(
                    title = req.title ?: b.title,
                    author = req.author ?: b.author,
                    pages = req.pages ?: b.pages,
                    chapters = req.chapters ?: b.chapters,
                    length = req.pages ?: req.chapters ?: b.length,
                    position = req.position ?: b.position,
                )
            },
        )

    private fun setRead(data: BooksDto, id: String, page: Int): BooksDto =
        data.copy(books = data.books.map { if (it.id == id) it.copy(read = page) else it })

    private fun setFinished(data: BooksDto, id: String, finished: Boolean): BooksDto =
        data.copy(books = data.books.map { if (it.id == id) it.copy(finished = finished) else it })

    private fun setShelved(data: BooksDto, id: String, shelved: Boolean): BooksDto =
        data.copy(books = data.books.map { if (it.id == id) it.copy(shelved = shelved) else it })

    private fun removeBook(data: BooksDto, id: String): BooksDto =
        data.copy(books = data.books.filterNot { it.id == id })

    private fun <T> parse(body: String?, ser: kotlinx.serialization.KSerializer<T>): T? =
        body?.let { runCatching { ApiClient.json.decodeFromString(ser, it) }.getOrNull() }

    private fun applyPending(data: BooksDto, pending: List<PendingWrite>): BooksDto {
        var d = data
        for (w in pending) {
            when (w.url.substringAfter("/api/v1/", "")) {
                "books/add" -> parse(w.body, AddBookRequest.serializer())?.let { d = insertBook(d, it) }
                "books/update" -> parse(w.body, UpdateBookRequest.serializer())?.let { d = updateBook(d, it) }
                "books/log" -> parse(w.body, LogBookRequest.serializer())?.let { d = setRead(d, it.id, it.page) }
                "books/finish" -> parse(w.body, BookFinishRequest.serializer())?.let { d = setFinished(d, it.id, it.finished) }
                "books/shelf" -> parse(w.body, BookShelfRequest.serializer())?.let { d = setShelved(d, it.id, it.shelved) }
                "books/delete" -> parse(w.body, BookIdRequest.serializer())?.let { d = removeBook(d, it.id) }
            }
        }
        return d
    }
}
