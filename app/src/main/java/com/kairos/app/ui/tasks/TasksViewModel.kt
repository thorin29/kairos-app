package com.kairos.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.dto.TasksListDto
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

    private fun act(onDone: (() -> Unit)? = null, block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
                _ui.update { it.copy(busy = false, data = session.loadTasksList()) }
                onDone?.invoke()
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, message = e.message ?: "Something went wrong.") }
            }
        }
    }

    fun complete(id: String) = act { session.completeTask(id) }
    fun uncomplete(id: String) = act { session.uncompleteTask(id) }

    fun add(userId: String, title: String, dueDate: String?, onDone: () -> Unit) =
        act(onDone) { session.addTask(userId, title, dueDate) }

    fun clearMessage() { _ui.update { it.copy(message = null) } }
}
