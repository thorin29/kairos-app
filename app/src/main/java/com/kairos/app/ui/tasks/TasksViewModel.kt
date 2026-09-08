package com.kairos.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.dto.TaskDoneDto
import com.kairos.app.data.remote.dto.TaskOpenDto
import com.kairos.app.data.remote.dto.TasksListDto
import java.time.LocalDate
import java.util.UUID
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TasksUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: TasksListDto? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val showCompleted: Boolean = false,
)

class TasksViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(TasksUiState())
    val ui: StateFlow<TasksUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                _ui.update { it.copy(loading = false, data = session.loadTasksList()) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Couldn't load tasks.") }
            }
        }
    }

    fun toggleCompleted() { _ui.update { it.copy(showCompleted = !it.showCompleted) } }

    fun complete(id: String) = optimisticMove(id, toDone = true) { session.completeTask(id) }
    fun uncomplete(id: String) = optimisticMove(id, toDone = false) { session.uncompleteTask(id) }

    /** Move a task between open/done locally right away, then reload (online) or
     *  keep the optimistic state (offline). Reverts on a real failure. */
    private fun optimisticMove(id: String, toDone: Boolean, write: suspend () -> Unit) {
        if (_ui.value.busy) return
        val before = _ui.value.data ?: return
        _ui.update { it.copy(busy = true, message = null, data = moveTask(before, id, toDone)) }
        viewModelScope.launch {
            try {
                write()
                if (session.isOnline()) {
                    _ui.update { it.copy(busy = false, data = session.loadTasksList()) }
                } else {
                    _ui.update { it.copy(busy = false) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, data = before, message = e.message ?: "Something went wrong.") }
            }
        }
    }

    private fun moveTask(data: TasksListDto, id: String, toDone: Boolean): TasksListDto {
        val today = LocalDate.now()
        val groups = data.groups.map { g ->
            if (toDone) {
                val t = g.open.firstOrNull { it.id == id } ?: return@map g
                g.copy(
                    open = g.open.filterNot { it.id == id },
                    done = g.done + TaskDoneDto(t.id, t.title, t.dueISO),
                )
            } else {
                val t = g.done.firstOrNull { it.id == id } ?: return@map g
                val overdue = try { LocalDate.parse(t.dueISO).isBefore(today) } catch (_: Exception) { false }
                g.copy(
                    done = g.done.filterNot { it.id == id },
                    open = g.open + TaskOpenDto(t.id, t.title, t.dueISO, overdue),
                )
            }
        }
        return data.copy(groups = groups)
    }

    fun add(userId: String, title: String, dueDate: String?, onDone: () -> Unit) {
        if (_ui.value.busy) return
        val before = _ui.value.data
        val optimistic = before?.let { insertTask(it, userId, title, dueDate) }
        _ui.update { it.copy(busy = true, message = null, data = optimistic ?: it.data) }
        onDone() // close the wizard right away; the task already shows on the list
        viewModelScope.launch {
            try {
                session.addTask(userId, title, dueDate)
                if (session.isOnline()) {
                    _ui.update { it.copy(busy = false, data = session.loadTasksList()) }
                } else {
                    _ui.update { it.copy(busy = false) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, data = before, message = e.message ?: "Couldn't add the task.") }
            }
        }
    }

    private fun insertTask(data: TasksListDto, userId: String, title: String, dueDate: String?): TasksListDto {
        val due = dueDate ?: LocalDate.now().toString()
        val overdue = try { LocalDate.parse(due).isBefore(LocalDate.now()) } catch (_: Exception) { false }
        val temp = TaskOpenDto("temp-${UUID.randomUUID()}", title, due, overdue)
        val groups = data.groups.map { g -> if (g.userId == userId) g.copy(open = g.open + temp) else g }
        return data.copy(groups = groups)
    }

    fun clearMessage() { _ui.update { it.copy(message = null) } }
}
