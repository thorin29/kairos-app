package com.kairos.app.ui.school

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.PendingWrite
import com.kairos.app.data.remote.dto.AddSchoolRequest
import com.kairos.app.data.remote.dto.SchoolDto
import com.kairos.app.data.remote.dto.SchoolItemDto
import com.kairos.app.data.remote.dto.SchoolRenameRequest
import com.kairos.app.data.remote.dto.SchoolTaskIdRequest
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class SchoolUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: SchoolDto? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val term: String? = null, // term id, "all", or null (current)
)

class SchoolViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(SchoolUiState())
    val ui: StateFlow<SchoolUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val data = freshData()
                _ui.update { it.copy(loading = false, data = data, term = it.term ?: data.selectedTermId) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Couldn't load school.") }
            }
        }
    }

    private suspend fun freshData(): SchoolDto =
        applyPending(session.loadSchool(_ui.value.term), session.pendingWrites())

    fun setTerm(term: String?) {
        _ui.update { it.copy(term = term) }
        load()
    }

    /** Apply the optimistic change now, fire the write, then reload (online) or
     *  keep the optimistic state (offline). Reverts on a real failure. */
    private fun optimistic(mutate: (SchoolDto) -> SchoolDto, write: suspend () -> Unit) {
        if (_ui.value.busy) return
        val before = _ui.value.data ?: return
        _ui.update { it.copy(busy = true, message = null, data = mutate(before)) }
        viewModelScope.launch {
            try {
                write()
                if (session.isOnline()) {
                    _ui.update { it.copy(busy = false, data = freshData()) }
                } else {
                    _ui.update { it.copy(busy = false) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, data = before, message = e.message ?: "Something went wrong.") }
            }
        }
    }

    fun add(userId: String, title: String, type: String, dueDate: String, subject: String?, classId: String?) =
        optimistic({ insertItem(it, userId, title, type, dueDate, classId) }) {
            session.addSchool(userId, title, type, dueDate, subject, classId)
        }

    fun complete(taskId: String) = optimistic({ removeItem(it, taskId) }) { session.completeTask(taskId) }
    fun delete(taskId: String) = optimistic({ removeItem(it, taskId) }) { session.deleteSchool(taskId) }
    fun rename(taskId: String, title: String) =
        optimistic({ renameItem(it, taskId, title) }) { session.renameSchool(taskId, title) }

    /** Apply several name edits at once. */
    fun applyRenames(edits: Map<String, String>) = optimistic(
        { data -> edits.entries.fold(data) { acc, (id, title) -> renameItem(acc, id, title) } },
    ) { edits.forEach { (id, title) -> session.renameSchool(id, title) } }

    fun clearMessage() { _ui.update { it.copy(message = null) } }

    // ---- optimistic transforms, also used to re-apply the queue on load ----

    private fun removeItem(data: SchoolDto, taskId: String): SchoolDto {
        val people = data.people.map { p ->
            val item = p.items.firstOrNull { it.id == taskId } ?: return@map p
            p.copy(
                items = p.items.filterNot { it.id == taskId },
                pending = (p.pending - 1).coerceAtLeast(0),
                overdue = (p.overdue - if (item.overdue) 1 else 0).coerceAtLeast(0),
            )
        }
        return data.copy(people = people)
    }

    private fun renameItem(data: SchoolDto, taskId: String, title: String): SchoolDto {
        val people = data.people.map { p ->
            p.copy(items = p.items.map { if (it.id == taskId) it.copy(title = title) else it })
        }
        return data.copy(people = people)
    }

    private fun insertItem(
        data: SchoolDto,
        userId: String,
        title: String,
        type: String,
        dueDate: String,
        classId: String?,
        id: String = "temp-${UUID.randomUUID()}",
    ): SchoolDto {
        val overdue = try { LocalDate.parse(dueDate).isBefore(LocalDate.now()) } catch (_: Exception) { false }
        val typeLabel = data.types.firstOrNull { it.key == type }?.label ?: type
        val cls = if (classId.isNullOrBlank()) null
        else data.classOptionsByUser[userId]?.firstOrNull { it.id == classId }
        val item = SchoolItemDto(
            id = id,
            title = title,
            type = type,
            typeLabel = typeLabel,
            className = cls?.name,
            classColor = cls?.color,
            dueISO = dueDate,
            overdue = overdue,
        )
        val people = data.people.map { p ->
            if (p.id == userId) {
                p.copy(
                    items = p.items + item,
                    pending = p.pending + 1,
                    overdue = p.overdue + if (overdue) 1 else 0,
                )
            } else {
                p
            }
        }
        return data.copy(people = people)
    }

    /** Re-apply still-unsynced school writes so offline changes survive navigation
     *  and app restarts. Completions ride the shared tasks/{id}/complete endpoint. */
    private fun applyPending(data: SchoolDto, pending: List<PendingWrite>): SchoolDto {
        var d = data
        for (w in pending) {
            val path = w.url.substringAfter("/api/v1/", "")
            when {
                path == "school/add" -> {
                    val req = w.body?.let {
                        runCatching { ApiClient.json.decodeFromString(AddSchoolRequest.serializer(), it) }.getOrNull()
                    } ?: continue
                    d = insertItem(d, req.userId, req.title, req.type, req.dueDate, req.classId, "temp-${w.id}")
                }
                path == "school/delete" -> {
                    val req = w.body?.let {
                        runCatching { ApiClient.json.decodeFromString(SchoolTaskIdRequest.serializer(), it) }.getOrNull()
                    } ?: continue
                    d = removeItem(d, req.taskId)
                }
                path == "school/rename" -> {
                    val req = w.body?.let {
                        runCatching { ApiClient.json.decodeFromString(SchoolRenameRequest.serializer(), it) }.getOrNull()
                    } ?: continue
                    d = renameItem(d, req.taskId, req.title)
                }
                path.startsWith("tasks/") && path.endsWith("/complete") ->
                    d = removeItem(d, path.removePrefix("tasks/").removeSuffix("/complete"))
            }
        }
        return d
    }
}
