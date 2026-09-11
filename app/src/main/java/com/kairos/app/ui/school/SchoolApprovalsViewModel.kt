package com.kairos.app.ui.school

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.ApprovalActionRequest
import com.kairos.app.data.remote.dto.ClassOptionDto
import com.kairos.app.data.remote.dto.PendingSubjectDto
import com.kairos.app.data.remote.dto.PendingTermDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApprovalsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val subjects: List<PendingSubjectDto> = emptyList(),
    val terms: List<PendingTermDto> = emptyList(),
    val allSubjects: List<ClassOptionDto> = emptyList(),
    val allTerms: List<ClassOptionDto> = emptyList(),
    val busyId: String? = null,
)

class SchoolApprovalsViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(ApprovalsUiState())
    val ui: StateFlow<ApprovalsUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val d = session.loadSchoolApprovals()
                _ui.update {
                    it.copy(
                        loading = false,
                        subjects = d.subjects,
                        terms = d.terms,
                        allSubjects = d.allSubjects,
                        allTerms = d.allTerms,
                    )
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, error = e.error.message) }
            }
        }
    }

    fun submit(body: ApprovalActionRequest) {
        _ui.update { it.copy(busyId = body.id, error = null) }
        viewModelScope.launch {
            try {
                session.submitSchoolApproval(body)
                _ui.update { it.copy(busyId = null) }
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(busyId = null, error = e.error.message) }
            }
        }
    }
}
