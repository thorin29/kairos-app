package com.kairos.app.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.PendingWrite
import com.kairos.app.data.remote.dto.AddMoneyRequest
import com.kairos.app.data.remote.dto.MoneyDto
import com.kairos.app.data.remote.dto.MoneyIdRequest
import com.kairos.app.data.remote.dto.MoneyRowDto
import com.kairos.app.data.remote.dto.RewardApproveBaseRequest
import com.kairos.app.data.remote.dto.RewardApproveMonthRequest
import com.kairos.app.data.remote.dto.StartingFundsRequest
import com.kairos.app.data.remote.dto.UpdateMoneyRequest
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
 * Owns the Money surface. Transactions and approvals apply optimistically and are
 * re-applied from the offline queue on load (so they show at once, survive
 * navigation/restart offline, and reconcile on sync). Running balances are computed
 * server-side, so the per-person balance figures catch up on the next sync while
 * the ledger rows update immediately. Setting starting funds stays online-only.
 */
class MoneyViewModel(
    private val session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(MoneyUiState())
    val ui: StateFlow<MoneyUiState> = _ui.asStateFlow()

    private var currentUser: String? = null

    init { loadInternal(null) }

    fun load() = loadInternal(currentUser)

    fun select(userId: String) {
        if (userId != currentUser) loadInternal(userId)
    }

    private fun loadInternal(user: String?) {
        currentUser = user
        _ui.update { it.copy(loading = it.data == null, loadError = null) }
        viewModelScope.launch {
            try {
                val data = freshData(user)
                currentUser = data.selectedId
                _ui.update { it.copy(loading = false, data = data) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
            }
        }
    }

    private suspend fun freshData(user: String?): MoneyDto =
        applyPending(session.loadMoney(user), session.pendingWrites())

    fun clearAddError() = _ui.update { it.copy(addError = null) }

    fun addEntry(req: AddMoneyRequest, onDone: () -> Unit) =
        optimistic(form = true, onDone, { insertRow(it, req) }) { session.addMoneyEntry(req) }
    fun updateEntry(req: UpdateMoneyRequest, onDone: () -> Unit) =
        optimistic(form = true, onDone, { updateRow(it, req) }) { session.updateMoney(req) }

    fun approve(id: String, onDone: () -> Unit = {}) =
        optimistic(form = false, onDone, { setApproved(it, id, true) }) { session.approveMoney(id) }
    fun unapprove(id: String, onDone: () -> Unit = {}) =
        optimistic(form = false, onDone, { setApproved(it, id, false) }) { session.unapproveMoney(id) }
    fun approveAll(onDone: () -> Unit = {}) =
        optimistic(form = false, onDone, { approveAllRows(it) }) { session.approveAllMoney() }
    fun deleteEntry(id: String, onDone: () -> Unit = {}) =
        optimistic(form = false, onDone, { removeRow(it, id) }) { session.deleteMoney(id) }

    fun approveRewardMonth(periodKey: String, onDone: () -> Unit) =
        optimistic(form = false, onDone, { setRewardMonth(it, periodKey) }) { session.approveRewardMonth(periodKey) }
    fun approveRewardBase(userId: String, periodKey: String, onDone: () -> Unit) =
        optimistic(form = false, onDone, { setRewardBase(it, userId, periodKey) }) { session.approveRewardBase(userId, periodKey) }

    /** Starting funds change the computed balance, which we can't recompute
     *  offline, so this stays a plain online write. */
    fun setStarting(req: StartingFundsRequest, onDone: () -> Unit) {
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

    private fun optimistic(form: Boolean, onDone: () -> Unit, mutate: (MoneyDto) -> MoneyDto, write: suspend () -> Unit) {
        val busy = if (form) _ui.value.adding else _ui.value.approving
        if (busy) return
        val before = _ui.value.data ?: return
        _ui.update {
            val d = mutate(before)
            if (form) it.copy(adding = true, addError = null, data = d) else it.copy(approving = true, approveError = null, data = d)
        }
        onDone()
        viewModelScope.launch {
            try {
                write()
                val data = if (session.isOnline()) freshData(currentUser) else _ui.value.data
                _ui.update { if (form) it.copy(adding = false, data = data) else it.copy(approving = false, data = data) }
            } catch (e: ApiException) {
                _ui.update {
                    if (form) it.copy(adding = false, addError = e.error.message, data = before)
                    else it.copy(approving = false, approveError = e.error.message, data = before)
                }
            }
        }
    }

    // ---- transforms, also used to re-apply the offline queue on load ----

    private fun insertRow(data: MoneyDto, req: AddMoneyRequest, id: String = "temp-${UUID.randomUUID()}"): MoneyDto {
        if (req.userId != data.selectedId) return data
        val row = MoneyRowDto(
            id = id,
            date = req.date ?: data.today,
            direction = req.direction,
            category = req.category,
            detail = req.detail,
            amountCents = req.amountCents,
            status = "PENDING",
            kind = "MANUAL",
        )
        return data.copy(rows = data.rows + row)
    }

    private fun updateRow(data: MoneyDto, req: UpdateMoneyRequest): MoneyDto =
        data.copy(
            rows = data.rows.map {
                if (it.id != req.id) it
                else it.copy(direction = req.direction, amountCents = req.amountCents, category = req.category, detail = req.detail, date = req.date ?: it.date)
            },
        )

    private fun removeRow(data: MoneyDto, id: String): MoneyDto =
        data.copy(rows = data.rows.filterNot { it.id == id }, pendingApprovals = data.pendingApprovals.filterNot { it.id == id })

    private fun setApproved(data: MoneyDto, id: String, approved: Boolean): MoneyDto =
        data.copy(
            rows = data.rows.map { if (it.id == id) it.copy(status = if (approved) "APPROVED" else "PENDING") else it },
            pendingApprovals = if (approved) data.pendingApprovals.filterNot { it.id == id } else data.pendingApprovals,
        )

    private fun approveAllRows(data: MoneyDto): MoneyDto =
        data.copy(
            rows = data.rows.map { if (it.status == "PENDING") it.copy(status = "APPROVED") else it },
            pendingApprovals = emptyList(),
        )

    private fun setRewardMonth(data: MoneyDto, periodKey: String): MoneyDto =
        data.copy(rewardMonths = data.rewardMonths.map { if (it.periodKey == periodKey) it.copy(bonusAvailable = false) else it })

    private fun setRewardBase(data: MoneyDto, userId: String, periodKey: String): MoneyDto =
        data.copy(
            rewardMonths = data.rewardMonths.map { m ->
                if (m.periodKey != periodKey) m
                else m.copy(completers = m.completers.map { c -> if (c.userId == userId) c.copy(needsBase = false) else c })
            },
        )

    private fun <T> parse(body: String?, ser: kotlinx.serialization.KSerializer<T>): T? =
        body?.let { runCatching { ApiClient.json.decodeFromString(ser, it) }.getOrNull() }

    private fun applyPending(data: MoneyDto, pending: List<PendingWrite>): MoneyDto {
        var d = data
        for (w in pending) {
            when (w.url.substringAfter("/api/v1/", "")) {
                "money/entry" -> parse(w.body, AddMoneyRequest.serializer())?.let { d = insertRow(d, it, "temp-${w.id}") }
                "money/update" -> parse(w.body, UpdateMoneyRequest.serializer())?.let { d = updateRow(d, it) }
                "money/delete" -> parse(w.body, MoneyIdRequest.serializer())?.let { d = removeRow(d, it.id) }
                "money/approve" -> parse(w.body, MoneyIdRequest.serializer())?.let { d = setApproved(d, it.id, true) }
                "money/unapprove" -> parse(w.body, MoneyIdRequest.serializer())?.let { d = setApproved(d, it.id, false) }
                "money/approve-all" -> d = approveAllRows(d)
                "money/rewards/approve-month" -> parse(w.body, RewardApproveMonthRequest.serializer())?.let { d = setRewardMonth(d, it.periodKey) }
                "money/rewards/approve-base" -> parse(w.body, RewardApproveBaseRequest.serializer())?.let { d = setRewardBase(d, it.userId, it.periodKey) }
            }
        }
        return d
    }
}
