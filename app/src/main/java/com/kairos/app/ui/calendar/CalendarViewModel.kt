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
    /** Bumped on an external date jump (Today / dropdown) so the pagers animate
     *  to it; the pager's own settle does NOT bump it, avoiding a cancel loop. */
    val navNonce: Int = 0,
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

    // --- Day-view pager cache (Slice 1): pre-fetched neighbour days keyed by ISO
    //     so the finger-follow pager shows populated days as they slide in. ---
    private val _pages = MutableStateFlow<Map<String, CalendarDto>>(emptyMap())
    val pages: StateFlow<Map<String, CalendarDto>> = _pages.asStateFlow()
    private val inFlightDays = mutableSetOf<String>()

    /** Fetch + cache one day for the pager, unless already present or in flight. */
    fun ensureDay(iso: String) {
        if (_pages.value.containsKey(iso) || iso in inFlightDays) return
        inFlightDays.add(iso)
        viewModelScope.launch {
            try {
                val dto = session.loadCalendar("day", iso)
                _pages.update { it + (dto.date to dto) }
            } catch (_: Exception) {
                // Leave uncached; the page shows a spinner and can retry on the next swipe.
            } finally {
                inFlightDays.remove(iso)
            }
        }
    }

    /** The day pager settled on [iso]: sync the anchor + top bar without a reload. */
    fun onDaySettled(iso: String) {
        _pages.value[iso]?.let { cached ->
            _ui.update { it.copy(date = iso, data = cached) }
        } ?: _ui.update { it.copy(date = iso) }
    }

    // --- Month-view pager cache (Slice 2): neighbour months keyed by their
    //     first-of-month ISO. ---
    private val _monthPages = MutableStateFlow<Map<String, CalendarDto>>(emptyMap())
    val monthPages: StateFlow<Map<String, CalendarDto>> = _monthPages.asStateFlow()
    private val inFlightMonths = mutableSetOf<String>()

    fun ensureMonth(monthStartIso: String) {
        if (_monthPages.value.containsKey(monthStartIso) || monthStartIso in inFlightMonths) return
        inFlightMonths.add(monthStartIso)
        viewModelScope.launch {
            try {
                val dto = session.loadCalendar("month", monthStartIso)
                _monthPages.update { it + (monthStartIso to dto) }
            } catch (_: Exception) {
            } finally {
                inFlightMonths.remove(monthStartIso)
            }
        }
    }

    fun onMonthSettled(monthStartIso: String) {
        _monthPages.value[monthStartIso]?.let { cached ->
            _ui.update { it.copy(date = cached.date, data = cached) }
        } ?: _ui.update { it.copy(date = monthStartIso) }
    }

    // --- Week-view pager cache (Slice 2b): neighbour weeks keyed by their
    //     Sunday (week-start) ISO. ---
    private val _weekPages = MutableStateFlow<Map<String, CalendarDto>>(emptyMap())
    val weekPages: StateFlow<Map<String, CalendarDto>> = _weekPages.asStateFlow()
    private val inFlightWeeks = mutableSetOf<String>()

    fun ensureWeek(weekStartIso: String) {
        if (_weekPages.value.containsKey(weekStartIso) || weekStartIso in inFlightWeeks) return
        inFlightWeeks.add(weekStartIso)
        viewModelScope.launch {
            try {
                val dto = session.loadCalendar("week", weekStartIso)
                _weekPages.update { it + (weekStartIso to dto) }
            } catch (_: Exception) {
            } finally {
                inFlightWeeks.remove(weekStartIso)
            }
        }
    }

    fun onWeekSettled(weekStartIso: String) {
        _weekPages.value[weekStartIso]?.let { cached ->
            _ui.update { it.copy(date = cached.date, data = cached) }
        } ?: _ui.update { it.copy(date = weekStartIso) }
    }

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
                if (s.tab == CalTab.DAY) _pages.update { it + (data.date to data) }
                if (s.tab == CalTab.THREE_DAY) {
                    data.rangeDays.forEach { day ->
                        _pages.update { it + (day to data.copy(date = day, rangeDays = listOf(day))) }
                    }
                }
                if (s.tab == CalTab.WEEK) {
                    val ws = runCatching {
                        val d = java.time.LocalDate.parse(data.date)
                        d.minusDays((d.dayOfWeek.value % 7).toLong()).toString()
                    }.getOrNull() ?: data.date
                    _weekPages.update { it + (ws to data) }
                }
                if (s.tab == CalTab.MONTH) {
                    val key = runCatching {
                        java.time.LocalDate.parse(data.date).withDayOfMonth(1).toString()
                    }.getOrNull() ?: data.date
                    _monthPages.update { it + (key to data) }
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
            }
        }
    }

    /** Public retry for the error state. */
    fun reload() = load()

    /** Drop the pager pre-fetch caches (after a mutation) so stale event data
     *  isn't shown; navigation alone keeps them for smooth paging. */
    private fun clearPageCaches() {
        _pages.value = emptyMap()
        _monthPages.value = emptyMap()
        _weekPages.value = emptyMap()
    }

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
                clearPageCaches()
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
                clearPageCaches()
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
                clearPageCaches()
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
    /** Shift the anchor date by whole days (3-day view pages one day at a time). */
    fun shiftDays(n: Int) {
        val cur = _ui.value.date
        val next = runCatching { java.time.LocalDate.parse(cur).plusDays(n.toLong()).toString() }.getOrNull() ?: return
        _ui.update { it.copy(date = next) }
        load()
    }

    fun goToday() {
        _ui.value.data?.let { d -> _ui.update { it.copy(date = d.today, navNonce = it.navNonce + 1) }; load() }
    }

    /** Navigate to a date while keeping the current view (month-dropdown tap). */
    fun goToDate(iso: String) {
        _ui.update { it.copy(date = iso, navNonce = it.navNonce + 1) }
        load()
    }
    /** Jump to the first day of the month n months from the current anchor
     *  (used by the expanded mini-month's left/right swipe). */
    fun goToMonthStart(n: Int) {
        val cur = _ui.value.date
        val next = runCatching {
            java.time.LocalDate.parse(cur).withDayOfMonth(1).plusMonths(n.toLong()).toString()
        }.getOrNull() ?: return
        _ui.update { it.copy(date = next) }
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
        familyColor: String? = null,
        nowColor: String? = null,
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
                        familyColor = familyColor,
                        nowColor = nowColor,
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
