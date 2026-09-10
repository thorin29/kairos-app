package com.kairos.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.PendingWrite
import com.kairos.app.data.remote.dto.CalEventDto
import com.kairos.app.data.remote.dto.CalendarDto
import com.kairos.app.data.remote.dto.CreateEventRequest
import com.kairos.app.data.remote.dto.DeleteEventRequest
import com.kairos.app.data.remote.dto.UpdateEventRequest
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
    /** Approved saved addresses, for showing a place's friendly name on the
     *  event card. Fetched once; empty until then. */
    val savedAddresses: List<com.kairos.app.data.remote.dto.SavedAddressDto> = emptyList(),
)

/**
 * Read-only calendar (Phases 1-4): each view/date change re-fetches GET /calendar.
 * The view the calendar opens to is a device preference ("last" = most recent, or
 * a pinned view); the current view is remembered as the last-used one.
 */
class CalendarViewModel(
    private val session: SessionRepository,
    private val settings: SettingsStore,
    private val appContext: android.content.Context,
) : ViewModel() {

    /** Re-run the reminder scheduler after any event change, so a new/edited/
     *  deleted event's alarms are (re)set right away — not only after the next
     *  app launch or the 2-hour worker. Online event writes don't touch the
     *  offline sync queue, so this is the trigger that covers them. */
    private fun rescheduleReminders() {
        com.kairos.app.data.notifications.NotificationWorker.enqueueOnce(appContext)
    }

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
                val dto = loadCal("day", iso)
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
                val dto = loadCal("month", monthStartIso)
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
                val dto = loadCal("week", weekStartIso)
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
        if (_ui.value.savedAddresses.isEmpty()) {
            viewModelScope.launch {
                runCatching { session.listSavedAddresses() }.getOrNull()?.let { r ->
                    _ui.update { it.copy(savedAddresses = r.addresses) }
                }
            }
        }
        viewModelScope.launch {
            try {
                val data = loadCal(s.tab.serverValue, s.date)
                _ui.update { it.copy(loading = false, data = data, date = data.date) }
                if (s.tab == CalTab.DAY) _pages.update { it + (data.date to data) }
                if (s.tab == CalTab.AGENDA) _pages.update { it + (data.date to data) }
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
        // Switching views always returns to today, rather than carrying over the
        // date the previous view had been paged to.
        val today = _ui.value.data?.today
        clearPageCaches()
        _ui.update { it.copy(tab = tab, date = today, navNonce = it.navNonce + 1) }
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
                rescheduleReminders()
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
                rescheduleReminders()
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
                rescheduleReminders()
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

    // ---- offline queue-apply: every load re-applies pending calendar writes ----

    private suspend fun loadCal(view: String, date: String?): CalendarDto =
        applyPending(session.loadCalendar(view, date), session.pendingWrites())

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

    private fun timeLabel(allDay: Boolean, startMin: Int, endMin: Int): String =
        if (allDay) "All day"
        else if (endMin > startMin) "${fmtTime(startMin)} \u2013 ${fmtTime(endMin)}"
        else fmtTime(startMin)

    private fun insertEvent(data: CalendarDto, req: CreateEventRequest, id: String = "temp-${UUID.randomUUID()}"): CalendarDto {
        if (req.date !in data.rangeDays) return data
        val startMin = parseHHMM(req.start) ?: 0
        val endMin = parseHHMM(req.end) ?: startMin
        val ev = CalEventDto(
            id = id, eventId = id, title = req.title, location = req.location,
            dayISO = req.date, allDay = req.allDay, startMin = startMin, endMin = endMin,
            timeLabel = timeLabel(req.allDay, startMin, endMin),
            isFamily = req.isFamily ?: false, kind = req.kind ?: "",
            recurring = !req.repeat.isNullOrBlank() && req.repeat != "none",
        )
        val events = (data.events + ev).sortedWith(compareBy({ it.dayISO }, { it.startMin }))
        val dots = if (data.monthDays.contains(req.date)) {
            data.monthDots + (req.date to ((data.monthDots[req.date] ?: emptyList()) + ev.color))
        } else {
            data.monthDots
        }
        return data.copy(events = events, monthDots = dots)
    }

    private fun updateEventIn(data: CalendarDto, req: UpdateEventRequest): CalendarDto {
        val startMin = parseHHMM(req.start) ?: 0
        val endMin = parseHHMM(req.end) ?: startMin
        return data.copy(
            events = data.events.map {
                if (it.eventId != req.eventId) it
                else it.copy(
                    title = req.title, allDay = req.allDay, dayISO = req.date,
                    startMin = startMin, endMin = endMin,
                    timeLabel = timeLabel(req.allDay, startMin, endMin),
                    location = req.location, isFamily = req.isFamily ?: it.isFamily,
                )
            },
        )
    }

    private fun removeEvent(data: CalendarDto, eventId: String, scope: String?, occurrenceISO: String?): CalendarDto {
        val events = if (scope == "one" && occurrenceISO != null) {
            data.events.filterNot { it.eventId == eventId && it.dayISO == occurrenceISO }
        } else {
            data.events.filterNot { it.eventId == eventId }
        }
        return data.copy(events = events)
    }

    private fun <T> parse(body: String?, ser: kotlinx.serialization.KSerializer<T>): T? =
        body?.let { runCatching { ApiClient.json.decodeFromString(ser, it) }.getOrNull() }

    private fun applyPending(data: CalendarDto, pending: List<PendingWrite>): CalendarDto {
        var d = data
        for (w in pending) {
            when (w.url.substringAfter("/api/v1/", "")) {
                "calendar/event" -> parse(w.body, CreateEventRequest.serializer())?.let { d = insertEvent(d, it, "temp-${w.id}") }
                "calendar/event/update" -> parse(w.body, UpdateEventRequest.serializer())?.let { d = updateEventIn(d, it) }
                "calendar/event/delete" -> parse(w.body, DeleteEventRequest.serializer())?.let { d = removeEvent(d, it.eventId, it.scope, it.occurrenceISO) }
            }
        }
        return d
    }
}
