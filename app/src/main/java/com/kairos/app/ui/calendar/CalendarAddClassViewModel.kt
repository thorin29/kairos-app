package com.kairos.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.CreateClassRequest
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class ClassFormUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val canMakeClass: Boolean = true,
    val isAdmin: Boolean = false,
    val meName: String? = null,
    val subjectNames: List<String> = emptyList(),
    val classTypes: List<Pair<String, String>> = emptyList(),
    val terms: List<Pair<String, String>> = emptyList(),
    val students: List<Pair<String, String>> = emptyList(),
    // field values
    val subject: String = "",
    val studentId: String = "",
    val byday: Set<String> = emptySet(),
    val startMin: Int = 19 * 60,
    val endMin: Int = 20 * 60,
    val runsFrom: String = "",
    val runsUntil: String = "",
    val classTypeId: String = "",
    val termId: String = "",
    val color: String = "",
    val sharedWith: Set<String> = emptySet(),
    val bells: Set<String> = emptySet(),
    val reminders: Set<Int> = emptySet(),
    val promptHomework: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

class CalendarAddClassViewModel(
    private val session: SessionRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ClassFormUiState())
    val ui: StateFlow<ClassFormUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val d = session.loadClassForm()
                _ui.update {
                    it.copy(
                        loading = false,
                        canMakeClass = d.canMakeClass,
                        isAdmin = d.isAdmin,
                        meName = d.meName,
                        subjectNames = d.subjects.map { s -> s.name },
                        classTypes = d.classTypes.map { t -> t.id to t.name },
                        terms = d.terms.map { t -> t.id to t.name },
                        students = d.students.map { s -> s.id to s.name },
                        studentId = if (d.isAdmin) d.students.firstOrNull()?.id ?: "" else "",
                    )
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
            }
        }
    }

    fun setSubject(v: String) = _ui.update { it.copy(subject = v, error = null) }
    fun setStudent(id: String) = _ui.update { it.copy(studentId = id) }
    fun toggleDay(d: String) = _ui.update {
        val n = it.byday.toMutableSet(); if (!n.add(d)) n.remove(d); it.copy(byday = n)
    }
    fun setStart(m: Int) = _ui.update {
        it.copy(startMin = m, endMin = if (it.endMin <= m) (m + 60).coerceAtMost(23 * 60 + 59) else it.endMin)
    }
    fun setEnd(m: Int) = _ui.update { it.copy(endMin = m) }
    fun setRunsFrom(v: String) = _ui.update { it.copy(runsFrom = v) }
    fun setRunsUntil(v: String) = _ui.update { it.copy(runsUntil = v) }
    fun setClassType(id: String) = _ui.update { it.copy(classTypeId = id) }
    fun setTerm(id: String) = _ui.update { it.copy(termId = id) }
    fun setColor(hex: String) = _ui.update { it.copy(color = hex) }
    fun setHomework(b: Boolean) = _ui.update { it.copy(promptHomework = b) }
    fun toggleShared(id: String) = _ui.update {
        val n = it.sharedWith.toMutableSet()
        if (!n.add(id)) n.remove(id)
        it.copy(sharedWith = n, bells = it.bells.filter { b -> b in n }.toSet())
    }
    fun toggleBell(id: String) = _ui.update {
        val n = it.bells.toMutableSet(); if (!n.add(id)) n.remove(id); it.copy(bells = n)
    }
    fun toggleReminder(m: Int) = _ui.update {
        val n = it.reminders.toMutableSet(); if (!n.add(m)) n.remove(m); it.copy(reminders = n)
    }

    fun save() {
        val s = _ui.value
        if (s.subject.trim().length < 2) {
            _ui.update { it.copy(error = "Pick a subject for the class.") }
            return
        }
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val hasMeeting = s.byday.isNotEmpty()
                session.createClass(
                    CreateClassRequest(
                        newSubject = s.subject.trim(),
                        userId = if (s.isAdmin) s.studentId.ifBlank { null } else null,
                        classTypeId = s.classTypeId.ifBlank { null },
                        termId = s.termId.ifBlank { null },
                        color = s.color.ifBlank { null },
                        start = if (hasMeeting) hhmm(s.startMin) else null,
                        end = if (hasMeeting) hhmm(s.endMin) else null,
                        byday = if (hasMeeting) s.byday.joinToString(",") else null,
                        sharedWith = s.sharedWith.joinToString(",").ifBlank { null },
                        meetingStartDate = s.runsFrom.ifBlank { null },
                        meetingEndDate = s.runsUntil.ifBlank { null },
                        location = null,
                        promptHomework = s.promptHomework,
                        reminders = s.reminders.toList(),
                        reminderBell = s.sharedWith.toList(),
                    ),
                )
                _ui.update { it.copy(saving = false, done = true) }
            } catch (e: ApiException) {
                _ui.update { it.copy(saving = false, error = e.error.message) }
            }
        }
    }

    private fun hhmm(m: Int): String = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
}
