package com.kairos.app.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.AddMoneyRequest
import com.kairos.app.data.remote.dto.MoneyDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MoneyUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val data: MoneyDto? = null,
    val adding: Boolean = false,
    val addError: String? = null,
    val approving: Boolean = false,
    val approveError: String? = null,
)

/**
 * Owns the Money surface: one aggregate read (GET /money) scoped to the enrolled
 * person, add-a-transaction (POST /money/entry), and — for an admin device — the
 * Bible-reward approvals. Mutations reload the current person's ledger so the UI
 * refreshes without re-navigating (the app's standard act-then-reload pattern).
 * Switching person re-reads with ?user=.
 */
class MoneyViewModel(
    private val session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(MoneyUiState())
    val ui: StateFlow<MoneyUiState> = _ui.asStateFlow()

    // The person whose ledger is shown; null lets the server pick the first.
    private var currentUser: String? = null

    init {
        loadInternal(null)
    }

    fun load() = loadInternal(currentUser)

    fun select(userId: String) {
        if (userId != currentUser) loadInternal(userId)
    }

    private fun loadInternal(user: String?) {
        currentUser = user
        _ui.update { it.copy(loading = it.data == null, loadError = null) }
        viewModelScope.launch {
            try {
                val data = session.loadMoney(user)
                currentUser = data.selectedId
                _ui.update { it.copy(loading = false, data = data) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
            }
        }
    }

    fun clearAddError() = _ui.update { it.copy(addError = null) }

    fun addEntry(req: AddMoneyRequest, onDone: () -> Unit) {
        if (_ui.value.adding) return
        _ui.update { it.copy(adding = true, addError = null) }
        viewModelScope.launch {
            try {
                session.addMoneyEntry(req)
                _ui.update { it.copy(adding = false) }
                onDone()
                loadInternal(currentUser)
            } catch (e: ApiException) {
                _ui.update { it.copy(adding = false, addError = e.error.message) }
            }
        }
    }

    fun approveRewardMonth(periodKey: String, onDone: () -> Unit) {
        if (_ui.value.approving) return
        _ui.update { it.copy(approving = true, approveError = null) }
        viewModelScope.launch {
            try {
                session.approveRewardMonth(periodKey)
                _ui.update { it.copy(approving = false) }
                onDone()
                loadInternal(currentUser)
            } catch (e: ApiException) {
                _ui.update { it.copy(approving = false, approveError = e.error.message) }
            }
        }
    }

    fun approveRewardBase(userId: String, periodKey: String, onDone: () -> Unit) {
        if (_ui.value.approving) return
        _ui.update { it.copy(approving = true, approveError = null) }
        viewModelScope.launch {
            try {
                session.approveRewardBase(userId, periodKey)
                _ui.update { it.copy(approving = false) }
                onDone()
                loadInternal(currentUser)
            } catch (e: ApiException) {
                _ui.update { it.copy(approving = false, approveError = e.error.message) }
            }
        }
    }

    // ---- Admin transaction management ----

    private fun mutate(onDone: () -> Unit, block: suspend () -> Unit) {
        if (_ui.value.approving) return
        _ui.update { it.copy(approving = true, approveError = null) }
        viewModelScope.launch {
            try {
                block()
                _ui.update { it.copy(approving = false) }
                onDone()
                loadInternal(currentUser)
            } catch (e: ApiException) {
                _ui.update { it.copy(approving = false, approveError = e.error.message) }
            }
        }
    }

    fun approve(id: String, onDone: () -> Unit = {}) = mutate(onDone) { session.approveMoney(id) }
    fun unapprove(id: String, onDone: () -> Unit = {}) = mutate(onDone) { session.unapproveMoney(id) }
    fun approveAll(onDone: () -> Unit = {}) = mutate(onDone) { session.approveAllMoney() }
    fun deleteEntry(id: String, onDone: () -> Unit = {}) = mutate(onDone) { session.deleteMoney(id) }

    fun updateEntry(req: com.kairos.app.data.remote.dto.UpdateMoneyRequest, onDone: () -> Unit) {
        if (_ui.value.adding) return
        _ui.update { it.copy(adding = true, addError = null) }
        viewModelScope.launch {
            try {
                session.updateMoney(req)
                _ui.update { it.copy(adding = false) }
                onDone()
                loadInternal(currentUser)
            } catch (e: ApiException) {
                _ui.update { it.copy(adding = false, addError = e.error.message) }
            }
        }
    }

    fun setStarting(req: com.kairos.app.data.remote.dto.StartingFundsRequest, onDone: () -> Unit) {
        if (_ui.value.adding) return
        _ui.update { it.copy(adding = true, addError = null) }
        viewModelScope.launch {
            try {
                session.setStartingFunds(req)
                _ui.update { it.copy(adding = false) }
                onDone()
                loadInternal(currentUser)
            } catch (e: ApiException) {
                _ui.update { it.copy(adding = false, addError = e.error.message) }
            }
        }
    }
}
