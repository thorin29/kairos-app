package com.kairos.app.ui.school

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.dto.SchoolDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                val data = session.loadSchool(_ui.value.term)
                _ui.update { it.copy(loading = false, data = data, term = it.term ?: data.selectedTermId) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Couldn't load school.") }
            }
        }
    }

    fun setTerm(term: String?) {
        _ui.update { it.copy(term = term) }
        load()
    }

    private fun act(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
                val data = session.loadSchool(_ui.value.term)
                _ui.update { it.copy(busy = false, data = data) }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, message = e.message ?: "Something went wrong.") }
            }
        }
    }

    fun add(userId: String, title: String, type: String, dueDate: String, subject: String?, classId: String?) =
        act { session.addSchool(userId, title, type, dueDate, subject, classId) }

    fun complete(taskId: String) = act { session.completeTask(taskId) }
    fun delete(taskId: String) = act { session.deleteSchool(taskId) }
    fun rename(taskId: String, title: String) = act { session.renameSchool(taskId, title) }

    /** Apply several name edits at once, then reload. */
    fun applyRenames(edits: Map<String, String>) = act {
        edits.forEach { (id, title) -> session.renameSchool(id, title) }
    }

    fun clearMessage() { _ui.update { it.copy(message = null) } }
}
