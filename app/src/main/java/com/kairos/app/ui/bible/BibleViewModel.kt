package com.kairos.app.ui.bible

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.PendingWrite
import com.kairos.app.data.remote.dto.MarkReadingRequest
import com.kairos.app.data.remote.dto.PersonalPlanRequest
import com.kairos.app.data.remote.dto.ReadingDto
import com.kairos.app.data.remote.dto.SaveBookRequest
import com.kairos.app.data.remote.dto.SaveBooksRequest
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
 * Owns the Bible screen: one aggregate read (GET /reading), and the writes (day
 * mark, book chapters, bulk books, plan delete) which apply optimistically and are
 * re-applied from the offline queue on load, so marks show immediately, survive
 * navigation/restart offline, and reconcile on sync. Coverage is derived from
 * readKeys ("Book|Chapter") client-side, so those grids update at once; the
 * summary stat bars come from the server and catch up on the next sync.
 */
class BibleViewModel(
    private val session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(BibleUiState())
    val ui: StateFlow<BibleUiState> = _ui.asStateFlow()

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

    private suspend fun freshData(): ReadingDto =
        applyPending(session.loadReading(), session.pendingWrites())

    fun setTab(tab: BibleTab) = _ui.update { it.copy(tab = tab) }

    fun clearActionError() = _ui.update { it.copy(actionError = null) }

    /** Create (replacing) the personal plan. The plan's days are generated
     *  server-side, so this isn't optimistic — do it online. */
    fun createPlan(
        name: String,
        bookNames: List<String>,
        startISO: String,
        chaptersPerDay: Int,
        endISO: String?,
        onDone: () -> Unit,
    ) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            try {
                session.createReadingPlan(PersonalPlanRequest(name, bookNames, startISO, chaptersPerDay, endISO))
                _ui.update { it.copy(busy = false, savedTick = it.savedTick + 1) }
                onDone()
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(busy = false, actionError = e.error.message) }
            }
        }
    }

    suspend fun previewPlan(
        bookNames: List<String>,
        startISO: String,
        chaptersPerDay: Int,
        endISO: String?,
    ): com.kairos.app.data.remote.dto.PlanPreviewDto? =
        try {
            session.previewReadingPlan(PersonalPlanRequest("", bookNames, startISO, chaptersPerDay, endISO))
        } catch (e: ApiException) {
            _ui.update { it.copy(actionError = e.error.message) }
            null
        }

    fun deletePlan() = optimistic({ clearPlan(it) }) { session.deleteReadingPlan() }
    fun markDay(passage: String, read: Boolean) =
        optimistic({ toggleDay(it, passage, read) }) { session.markReading(passage, read) }
    fun saveBook(bookName: String, chapters: List<Int>) =
        optimistic({ setBookChapters(it, bookName, chapters) }) { session.saveReadingBook(bookName, chapters) }
    fun bulkBooks(bookNames: List<String>, read: Boolean) =
        optimistic({ bulkSetBooks(it, bookNames, read) }) { session.saveReadingBooks(bookNames, read) }

    private fun optimistic(mutate: (ReadingDto) -> ReadingDto, write: suspend () -> Unit) {
        if (_ui.value.busy) return
        val before = _ui.value.data ?: return
        _ui.update { it.copy(busy = true, actionError = null, data = mutate(before)) }
        viewModelScope.launch {
            try {
                write()
                val data = if (session.isOnline()) freshData() else _ui.value.data
                _ui.update { it.copy(busy = false, savedTick = it.savedTick + 1, data = data) }
            } catch (e: ApiException) {
                _ui.update { it.copy(busy = false, data = before, actionError = e.error.message) }
            }
        }
    }

    // ---- transforms, also used to re-apply the offline queue on load ----

    private fun toggleDay(data: ReadingDto, passage: String, read: Boolean): ReadingDto {
        val p = data.personal ?: return data
        val plan = p.plan ?: return data
        val days = plan.days.map { if (it.passage == passage) it.copy(read = read) else it }
        return data.copy(personal = p.copy(plan = plan.copy(days = days)))
    }

    private fun setBookChapters(data: ReadingDto, bookName: String, chapters: List<Int>): ReadingDto {
        val p = data.personal ?: return data
        val kept = p.readKeys.filterNot { it.startsWith("$bookName|") }
        return data.copy(personal = p.copy(readKeys = kept + chapters.map { "$bookName|$it" }))
    }

    private fun bulkSetBooks(data: ReadingDto, bookNames: List<String>, read: Boolean): ReadingDto {
        val p = data.personal ?: return data
        val keys = p.readKeys.toMutableList()
        for (name in bookNames) {
            keys.removeAll { it.startsWith("$name|") }
            if (read) {
                val chs = BIBLE_BOOKS.firstOrNull { it.name == name }?.chapters ?: 0
                for (c in 1..chs) keys.add("$name|$c")
            }
        }
        return data.copy(personal = p.copy(readKeys = keys))
    }

    private fun clearPlan(data: ReadingDto): ReadingDto {
        val p = data.personal ?: return data
        return data.copy(personal = p.copy(plan = null, havePlan = false))
    }

    private fun <T> parse(body: String?, ser: kotlinx.serialization.KSerializer<T>): T? =
        body?.let { runCatching { ApiClient.json.decodeFromString(ser, it) }.getOrNull() }

    private fun applyPending(data: ReadingDto, pending: List<PendingWrite>): ReadingDto {
        var d = data
        for (w in pending) {
            when (w.url.substringAfter("/api/v1/", "")) {
                "reading/mark" -> parse(w.body, MarkReadingRequest.serializer())?.let { d = toggleDay(d, it.passage, it.read) }
                "reading/books" -> parse(w.body, SaveBookRequest.serializer())?.let { d = setBookChapters(d, it.bookName, it.chapters) }
                "reading/books/bulk" -> parse(w.body, SaveBooksRequest.serializer())?.let { d = bulkSetBooks(d, it.bookNames, it.read) }
                "reading/plan/delete" -> d = clearPlan(d)
            }
        }
        return d
    }
}
