package com.kairos.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.CalendarDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The calendar views. `serverValue` is the `view` param the API expects. */
enum class CalTab(val serverValue: String, val label: String) {
    AGENDA("agenda", "Agenda"),
    DAY("day", "Day"),
    THREE_DAY("three_day", "3-day"),
    WEEK("week", "Week"),
    MONTH("month", "Month"),
}

data class CalendarUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val data: CalendarDto? = null,
    val tab: CalTab = CalTab.AGENDA,
    /** Requested date; null lets the server default to today on first load. */
    val date: String? = null,
)

/**
 * Read-only calendar (Phase 1): each view/date change re-fetches GET /calendar,
 * which resolves the range, colours, and paging anchors server-side.
 */
class CalendarViewModel(
    private val session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CalendarUiState())
    val ui: StateFlow<CalendarUiState> = _ui.asStateFlow()

    init {
        load()
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
        load()
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
        load()
    }

    /** Persist a filter/view change, then reload so the grid reflects it. */
    fun savePrefs(
        shownPeople: List<String>? = null,
        shownSubs: List<String>? = null,
        showFamily: Boolean? = null,
        showSchoolWork: Boolean? = null,
    ) {
        viewModelScope.launch {
            try {
                session.saveCalendarPrefs(
                    com.kairos.app.data.remote.dto.CalendarPrefsRequest(
                        shownPeople = shownPeople,
                        shownSubs = shownSubs,
                        showFamily = showFamily,
                        showSchoolWork = showSchoolWork,
                    ),
                )
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(loadError = e.error.message) }
            }
        }
    }
}
