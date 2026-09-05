package com.kairos.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.CalendarDto
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The calendar views. `serverValue` is the `view` param the API expects. */
enum class CalTab(val serverValue: String, val label: String) {
    AGENDA("agenda", "Agenda"),
    DAY("day", "Day"),
    THREE_DAY("three_day", "3 Days"),
    WEEK("week", "Week"),
    MONTH("month", "Month");

    companion object {
        fun fromServer(v: String?): CalTab =
            entries.firstOrNull { it.serverValue == v } ?: AGENDA
    }
}

data class CalendarUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val data: CalendarDto? = null,
    val tab: CalTab = CalTab.AGENDA,
    /** Requested date; null lets the server default to today on first load. */
    val date: String? = null,
    /** The saved default-view preference: a CalView value or "last". */
    val defaultView: String = "last",
    val creating: Boolean = false,
    val createError: String? = null,
    val deleting: Boolean = false,
    val deleteError: String? = null,
)

/**
 * Read-only calendar (Phases 1-4): each view/date change re-fetches GET /calendar.
 * The view the calendar opens to is a device preference ("last" = most recent, or
 * a pinned view); the current view is remembered as the last-used one.
 */
class CalendarViewModel(
    private val session: SessionRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(CalendarUiState())
    val ui: StateFlow<CalendarUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val def = settings.currentCalendarDefaultView()
            val start = if (def == "last") settings.currentCalendarLastView() else def
            _ui.update { it.copy(tab = CalTab.fromServer(start), defaultView = def) }
            load()
        }
    }

    private fun load() {
        val s = _ui.value
        _ui.update { it.copy(loading = it.data == null, loadError = null) }
        viewModelScope.launch {
            try {
                val data = session.loadCalendar(s.tab.serverValue, s.date)
                _ui.update { it.copy(loading = false, data = data, date = data.date) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
            }
        }
    }

    /** Public retry for the error state. */
    fun reload() = load()

    fun setTab(tab: CalTab) {
        if (tab == _ui.value.tab) return
        _ui.update { it.copy(tab = tab) }
        viewModelScope.launch { settings.setCalendarLastView(tab.serverValue) }
        load()
    }

    fun setDefaultView(v: String) {
        _ui.update { it.copy(defaultView = v) }
        viewModelScope.launch { settings.setCalendarDefaultView(v) }
    }

    fun clearCreateError() = _ui.update { it.copy(createError = null) }

    fun clearDeleteError() = _ui.update { it.copy(deleteError = null) }

    /** Delete an event; on success reload and call [onDone]. Scope + occurrence
     *  matter only for recurring events (this / future / all). */
    fun deleteEvent(eventId: String, scope: String, occurrenceISO: String?, onDone: () -> Unit) {
        if (_ui.value.deleting) return
        _ui.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            try {
                session.deleteCalendarEvent(eventId, scope = scope, occurrenceISO = occurrenceISO)
                _ui.update { it.copy(deleting = false) }
                onDone()
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(deleting = false, deleteError = e.error.message) }
            }
        }
    }

    /** Create a basic event; on success reload the calendar and call [onDone]. */
    fun createEvent(req: com.kairos.app.data.remote.dto.CreateEventRequest, onDone: () -> Unit) {
        if (_ui.value.creating) return
        _ui.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            try {
                session.createCalendarEvent(req)
                _ui.update { it.copy(creating = false) }
                onDone()
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(creating = false, createError = e.error.message) }
            }
        }
    }

    /** Edit an event; on success reload and call [onDone]. Reuses the create
     *  form's busy/error state. */
    fun updateEvent(req: com.kairos.app.data.remote.dto.UpdateEventRequest, onDone: () -> Unit) {
        if (_ui.value.creating) return
        _ui.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            try {
                session.updateCalendarEvent(req)
                _ui.update { it.copy(creating = false) }
                onDone()
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(creating = false, createError = e.error.message) }
            }
        }
    }

    fun goPrev() {
        _ui.value.data?.let { d -> _ui.update { it.copy(date = d.prevDate) }; load() }
    }

    fun goNext() {
        _ui.value.data?.let { d -> _ui.update { it.copy(date = d.nextDate) }; load() }
    }

    fun goToday() {
        _ui.value.data?.let { d -> _ui.update { it.copy(date = d.today) }; load() }
    }

    /** Navigate to a date while keeping the current view (month-dropdown tap). */
    fun goToDate(iso: String) {
        _ui.update { it.copy(date = iso) }
        load()
    }

    /** Tap a month day → open its agenda. */
    fun openDay(iso: String) {
        _ui.update { it.copy(tab = CalTab.AGENDA, date = iso) }
        viewModelScope.launch { settings.setCalendarLastView(CalTab.AGENDA.serverValue) }
        load()
    }

    /** Persist a filter change, then reload so the grid reflects it. */
    fun savePrefs(
        shownPeople: List<String>? = null,
        shownSubs: List<String>? = null,
        showFamily: Boolean? = null,
        showSchoolWork: Boolean? = null,
        personalizeColors: Boolean? = null,
        othersMode: String? = null,
        othersColor: String? = null,
        holidayColor: String? = null,
        kindColors: Map<String, String>? = null,
        eventTypeColors: Map<String, String>? = null,
        subColors: Map<String, String>? = null,
    ) {
        viewModelScope.launch {
            try {
                session.saveCalendarPrefs(
                    com.kairos.app.data.remote.dto.CalendarPrefsRequest(
                        shownPeople = shownPeople,
                        shownSubs = shownSubs,
                        showFamily = showFamily,
                        showSchoolWork = showSchoolWork,
                        personalizeColors = personalizeColors,
                        othersMode = othersMode,
                        othersColor = othersColor,
                        holidayColor = holidayColor,
                        kindColors = kindColors,
                        eventTypeColors = eventTypeColors,
                        subColors = subColors,
                    ),
                )
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(loadError = e.error.message) }
            }
        }
    }
}
