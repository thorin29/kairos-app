package com.kairos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.dto.AddTaskRequest
import com.kairos.app.data.remote.dto.TaskGroupDto
import java.time.LocalDate
import java.util.UUID
import com.kairos.app.data.remote.dto.DashboardDto
import com.kairos.app.data.remote.dto.TaskDto
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
    /** The workout prompt whose action sheet is open, if any. */
    val workoutSheet: TaskDto? = null,
    val signingOut: Boolean = false,
)

class HomeViewModel(private val session: SessionRepository) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    fun load() {
        _ui.update { it.copy(loading = it.dashboard == null, loadError = null) }
        viewModelScope.launch {
            try {
                val data = freshDashboard()
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

    /** Load the dashboard and re-apply any still-unsynced task completions, so a
     *  tick made offline stays put even after navigating away and back. */
    private suspend fun freshDashboard(): DashboardDto {
        var d = session.loadDashboard()
        val me = session.currentPersonId()
        for (w in session.pendingWrites()) {
            val path = w.url.substringAfter("/api/v1/", "")
            when {
                path == "tasks/add" -> {
                    val body = w.body ?: continue
                    val req = runCatching {
                        ApiClient.json.decodeFromString(AddTaskRequest.serializer(), body)
                    }.getOrNull() ?: continue
                    if (req.userId == me) d = insertDashTask(d, req.title, req.dueDate)
                }
                path.startsWith("tasks/") && path.endsWith("/complete") ->
                    d = mutateTaskStatus(d, path.removePrefix("tasks/").removeSuffix("/complete"), "COMPLETE")
                path.startsWith("tasks/") && path.endsWith("/uncomplete") ->
                    d = mutateTaskStatus(d, path.removePrefix("tasks/").removeSuffix("/uncomplete"), "PENDING")
            }
        }
        return d
    }

    /** Drop a queued-but-unsynced task into the dashboard's Tasks (OTHER) group. */
    private fun insertDashTask(dash: DashboardDto, title: String, dueDate: String?): DashboardDto {
        val due = dueDate ?: LocalDate.now().toString()
        val overdue = try { LocalDate.parse(due).isBefore(LocalDate.now()) } catch (_: Exception) { false }
        val task = TaskDto(
            id = "temp-${UUID.randomUUID()}",
            title = title,
            category = "OTHER",
            status = "PENDING",
            dueDate = due,
            completable = true,
            isOverdue = overdue,
        )
        val groups = dash.groups.toMutableList()
        val idx = groups.indexOfFirst { it.category == "OTHER" }
        if (idx >= 0) {
            groups[idx] = groups[idx].copy(items = groups[idx].items + task)
        } else {
            groups.add(TaskGroupDto(category = "OTHER", label = "Tasks", items = listOf(task)))
        }
        return dash.copy(groups = groups)
    }

    fun refresh() {
        _ui.update { it.copy(refreshing = true) }
        load()
    }

    /** Toggle a task with an optimistic flip: the row updates immediately, then
     *  we reload (online) to refresh the server-derived bars/percent, or keep the
     *  optimistic state (offline) until it syncs. Reverts on a real failure. */
    fun toggle(taskId: String, currentlyComplete: Boolean) {
        if (_ui.value.busyIds.contains(taskId)) return
        val before = _ui.value.dashboard
        val newStatus = if (currentlyComplete) "PENDING" else "COMPLETE"
        _ui.update {
            it.copy(
                busyIds = it.busyIds + taskId,
                actionError = null,
                dashboard = it.dashboard?.let { d -> mutateTaskStatus(d, taskId, newStatus) },
            )
        }
        viewModelScope.launch {
            try {
                if (currentlyComplete) session.uncompleteTask(taskId)
                else session.completeTask(taskId)
                if (session.isOnline()) {
                    val data = freshDashboard()
                    _ui.update { it.copy(dashboard = data, busyIds = it.busyIds - taskId) }
                } else {
                    _ui.update { it.copy(busyIds = it.busyIds - taskId) }
                }
            } catch (e: ApiException) {
                _ui.update {
                    it.copy(dashboard = before, busyIds = it.busyIds - taskId, actionError = e.error.message)
                }
            }
        }
    }

    private fun mutateTaskStatus(dash: DashboardDto, id: String, status: String): DashboardDto =
        dash.copy(
            overdue = dash.overdue.map { if (it.id == id) it.copy(status = status) else it },
            groups = dash.groups.map { g ->
                g.copy(items = g.items.map { if (it.id == id) it.copy(status = status) else it })
            },
        )

    /** Answer a "Did you do X?" sport prompt (yes/no), then reload. */
    fun answerSport(eventId: String, done: Boolean) {
        val key = "sport-$eventId"
        if (_ui.value.busyIds.contains(key)) return
        _ui.update { it.copy(busyIds = it.busyIds + key, actionError = null) }
        val date = _ui.value.dashboard?.date
        viewModelScope.launch {
            try {
                if (done) session.sportConfirm(eventId, date) else session.sportDecline(eventId, date)
                val data = freshDashboard()
                _ui.update { it.copy(dashboard = data, busyIds = it.busyIds - key) }
            } catch (e: ApiException) {
                _ui.update { it.copy(busyIds = it.busyIds - key, actionError = e.error.message) }
            }
        }
    }

    /** Take an up-for-grabs chore for yourself, then reload. */
    fun claimChore(taskId: String) {
        val key = "claim-$taskId"
        if (_ui.value.busyIds.contains(key)) return
        _ui.update { it.copy(busyIds = it.busyIds + key, actionError = null) }
        viewModelScope.launch {
            try {
                session.claimChore(taskId)
                val data = freshDashboard()
                _ui.update { it.copy(dashboard = data, busyIds = it.busyIds - key) }
            } catch (e: ApiException) {
                _ui.update { it.copy(busyIds = it.busyIds - key, actionError = e.error.message) }
            }
        }
    }

    /** Tap an always-open chore done for yourself, then reload. */
    fun completeAlwaysOpen(choreId: String) {
        val key = "always-$choreId"
        if (_ui.value.busyIds.contains(key)) return
        _ui.update { it.copy(busyIds = it.busyIds + key, actionError = null) }
        viewModelScope.launch {
            try {
                session.completeAlwaysOpen(choreId)
                val data = freshDashboard()
                _ui.update { it.copy(dashboard = data, busyIds = it.busyIds - key) }
            } catch (e: ApiException) {
                _ui.update { it.copy(busyIds = it.busyIds - key, actionError = e.error.message) }
            }
        }
    }

    fun clearActionError() {
        _ui.update { it.copy(actionError = null) }
    }

    /** Tick/untick the day's personal reading, then reload. Uses a sentinel busy
     *  id since it isn't a task row. */
    fun togglePersonalReading(passage: String, currentlyRead: Boolean) {
        val key = "personal-reading"
        if (_ui.value.busyIds.contains(key)) return
        _ui.update { it.copy(busyIds = it.busyIds + key, actionError = null) }
        viewModelScope.launch {
            try {
                session.markReading(passage, !currentlyRead)
                val data = freshDashboard()
                _ui.update { it.copy(dashboard = data, busyIds = it.busyIds - key) }
            } catch (e: ApiException) {
                _ui.update { it.copy(busyIds = it.busyIds - key, actionError = e.error.message) }
            }
        }
    }

    // --- Workout prompts: a small action sheet instead of a plain checkbox ---

    fun openWorkout(task: TaskDto) {
        _ui.update { it.copy(workoutSheet = task) }
    }

    fun dismissWorkout() {
        _ui.update { it.copy(workoutSheet = null) }
    }

    fun markWorkoutDone(task: TaskDto) = workoutOp(task) { session.workoutComplete(task.dueDate) }

    fun undoWorkout(task: TaskDto) = workoutOp(task) { session.workoutUncomplete(task.dueDate) }

    fun restDay(task: TaskDto) = workoutOp(task) { session.workoutRest(task.dueDate) }

    private fun workoutOp(task: TaskDto, block: suspend () -> Unit) {
        _ui.update {
            it.copy(workoutSheet = null, busyIds = it.busyIds + task.id, actionError = null)
        }
        viewModelScope.launch {
            try {
                block()
                val data = freshDashboard()
                _ui.update { it.copy(dashboard = data, busyIds = it.busyIds - task.id) }
            } catch (e: ApiException) {
                _ui.update { it.copy(busyIds = it.busyIds - task.id, actionError = e.error.message) }
            }
        }
    }

    fun signOut() {
        _ui.update { it.copy(signingOut = true) }
        viewModelScope.launch { session.signOut() }
    }
}
