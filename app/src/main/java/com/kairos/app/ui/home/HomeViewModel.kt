package com.kairos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.dto.AddSchoolRequest
import com.kairos.app.data.remote.dto.AddTaskRequest
import com.kairos.app.data.remote.dto.TaskGroupDto
import java.time.LocalDate
import java.util.UUID
import com.kairos.app.data.remote.dto.AlwaysOpenRequest
import com.kairos.app.data.remote.dto.ClaimChoreRequest
import com.kairos.app.data.remote.dto.CreateEventRequest
import com.kairos.app.data.remote.dto.ScheduleItemDto
import com.kairos.app.data.remote.dto.DashboardDto
import com.kairos.app.data.remote.dto.MarkReadingRequest
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
                path == "school/add" -> {
                    val body = w.body ?: continue
                    val req = runCatching {
                        ApiClient.json.decodeFromString(AddSchoolRequest.serializer(), body)
                    }.getOrNull() ?: continue
                    if (req.userId == me) d = insertDashSchool(d, req.title, req.dueDate)
                }
                path.startsWith("tasks/") && path.endsWith("/complete") ->
                    d = mutateTaskStatus(d, path.removePrefix("tasks/").removeSuffix("/complete"), "COMPLETE")
                path.startsWith("tasks/") && path.endsWith("/uncomplete") ->
                    d = mutateTaskStatus(d, path.removePrefix("tasks/").removeSuffix("/uncomplete"), "PENDING")
                path == "chores/claim" -> {
                    val req = w.body?.let {
                        runCatching { ApiClient.json.decodeFromString(ClaimChoreRequest.serializer(), it) }.getOrNull()
                    } ?: continue
                    d = d.copy(upForGrabs = d.upForGrabs.filterNot { it.id == req.taskId })
                }
                path == "chores/always-open" -> {
                    val req = w.body?.let {
                        runCatching { ApiClient.json.decodeFromString(AlwaysOpenRequest.serializer(), it) }.getOrNull()
                    } ?: continue
                    d = bumpAlwaysOpen(d, req.choreId)
                }
                path == "reading/mark" -> {
                    val req = w.body?.let {
                        runCatching { ApiClient.json.decodeFromString(MarkReadingRequest.serializer(), it) }.getOrNull()
                    } ?: continue
                    d = d.copy(
                        personalReading = d.personalReading?.let {
                            if (it.passage == req.passage) it.copy(read = req.read) else it
                        },
                    )
                }
                path == "calendar/event" -> {
                    val req = w.body?.let {
                        runCatching { ApiClient.json.decodeFromString(CreateEventRequest.serializer(), it) }.getOrNull()
                    } ?: continue
                    val mine = req.isFamily == true || req.participants == null || (me != null && req.participants!!.contains(me))
                    if (req.date == d.date && mine) d = insertDashSchedule(d, req)
                }
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
        return insertDashItem(dash, "OTHER", "Tasks", task)
    }

    /** Drop a queued-but-unsynced assignment into the dashboard's School group. */
    private fun insertDashSchool(dash: DashboardDto, title: String, dueDate: String): DashboardDto {
        val overdue = try { LocalDate.parse(dueDate).isBefore(LocalDate.now()) } catch (_: Exception) { false }
        val task = TaskDto(
            id = "temp-${UUID.randomUUID()}",
            title = title,
            category = "SCHOOL",
            status = "PENDING",
            dueDate = dueDate,
            completable = true,
            isOverdue = overdue,
        )
        return insertDashItem(dash, "SCHOOL", "School", task)
    }

    private fun insertDashItem(dash: DashboardDto, category: String, label: String, task: TaskDto): DashboardDto {
        val groups = dash.groups.toMutableList()
        val idx = groups.indexOfFirst { it.category == category }
        if (idx >= 0) {
            groups[idx] = groups[idx].copy(items = groups[idx].items + task)
        } else {
            groups.add(TaskGroupDto(category = category, label = label, items = listOf(task)))
        }
        return dash.copy(groups = groups)
    }

    /** Drop a queued-but-unsynced calendar event onto today's agenda. */
    private fun insertDashSchedule(dash: DashboardDto, req: CreateEventRequest): DashboardDto {
        val startMin = parseHHMM(req.start) ?: 0
        val item = ScheduleItemDto(
            title = req.title,
            allDay = req.allDay,
            timeLabel = if (req.allDay) "All day" else fmtTime(startMin),
            startMin = startMin,
            color = "#64748b",
            location = req.location,
        )
        return dash.copy(schedule = (dash.schedule + item).sortedBy { it.startMin })
    }

    private fun parseHHMM(s: String?): Int? {
        val parts = s?.split(":") ?: return null
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun fmtTime(min: Int): String {
        val h = min / 60
        val m = min % 60
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = ((h + 11) % 12) + 1
        return if (m == 0) "$h12 $ampm" else "%d:%02d %s".format(h12, m, ampm)
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
        val before = _ui.value.dashboard
        _ui.update {
            it.copy(
                busyIds = it.busyIds + key,
                actionError = null,
                dashboard = it.dashboard?.let { d -> d.copy(upForGrabs = d.upForGrabs.filterNot { g -> g.id == taskId }) },
            )
        }
        viewModelScope.launch {
            try {
                session.claimChore(taskId)
                if (session.isOnline()) {
                    _ui.update { it.copy(dashboard = freshDashboard(), busyIds = it.busyIds - key) }
                } else {
                    _ui.update { it.copy(busyIds = it.busyIds - key) }
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(dashboard = before, busyIds = it.busyIds - key, actionError = e.error.message) }
            }
        }
    }

    /** Tap an always-open chore done for yourself, then reload. */
    fun completeAlwaysOpen(choreId: String) {
        val key = "always-$choreId"
        if (_ui.value.busyIds.contains(key)) return
        val before = _ui.value.dashboard
        _ui.update {
            it.copy(
                busyIds = it.busyIds + key,
                actionError = null,
                dashboard = it.dashboard?.let { d -> bumpAlwaysOpen(d, choreId) },
            )
        }
        viewModelScope.launch {
            try {
                session.completeAlwaysOpen(choreId)
                if (session.isOnline()) {
                    _ui.update { it.copy(dashboard = freshDashboard(), busyIds = it.busyIds - key) }
                } else {
                    _ui.update { it.copy(busyIds = it.busyIds - key) }
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(dashboard = before, busyIds = it.busyIds - key, actionError = e.error.message) }
            }
        }
    }

    private fun bumpAlwaysOpen(dash: DashboardDto, choreId: String): DashboardDto =
        dash.copy(alwaysOpen = dash.alwaysOpen.map { if (it.id == choreId) it.copy(myCount = it.myCount + 1) else it })

    fun clearActionError() {
        _ui.update { it.copy(actionError = null) }
    }

    /** Tick/untick the day's personal reading, then reload. Uses a sentinel busy
     *  id since it isn't a task row. */
    fun togglePersonalReading(passage: String, currentlyRead: Boolean) {
        val key = "personal-reading"
        if (_ui.value.busyIds.contains(key)) return
        val before = _ui.value.dashboard
        val newRead = !currentlyRead
        _ui.update {
            it.copy(
                busyIds = it.busyIds + key,
                actionError = null,
                dashboard = it.dashboard?.let { d -> d.copy(personalReading = d.personalReading?.copy(read = newRead)) },
            )
        }
        viewModelScope.launch {
            try {
                session.markReading(passage, newRead)
                if (session.isOnline()) {
                    _ui.update { it.copy(dashboard = freshDashboard(), busyIds = it.busyIds - key) }
                } else {
                    _ui.update { it.copy(busyIds = it.busyIds - key) }
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(dashboard = before, busyIds = it.busyIds - key, actionError = e.error.message) }
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
