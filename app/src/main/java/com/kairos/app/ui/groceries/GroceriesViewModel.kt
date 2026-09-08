package com.kairos.app.ui.groceries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.GroceriesDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroceriesUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val data: GroceriesDto? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * The shared family grocery list. One read (GET /groceries) returns the whole
 * board — stores, the saved list, active trips, and the catalog; mutations
 * reload so every store section refreshes in place. Not self-only: anyone can
 * add, shop, and tick things off.
 */
class GroceriesViewModel(
    private val session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(GroceriesUiState())
    val ui: StateFlow<GroceriesUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = it.data == null, loadError = null) }
        viewModelScope.launch {
            try {
                val data = session.loadGroceries()
                _ui.update { it.copy(loading = false, data = data) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
            }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    fun add(name: String, storeId: String) = act { session.addGrocery(name, storeId, null) }
    fun addFromCatalog(catalogId: String, storeId: String?) = act { session.addGroceryFromCatalog(catalogId, storeId) }
    fun remove(id: String) = act { session.removeGrocery(id) }
    fun move(id: String, storeId: String) = act { session.moveGrocery(id, storeId) }
    fun setPurchased(id: String, purchased: Boolean) = act { session.setGroceryPurchased(id, purchased) }
    fun completeTrip(tripId: String) = act { session.completeGroceryTrip(tripId) }

    /** Start a run for a store (shopper defaults to me). If a run is already
     *  under way the server says so; surface it and reload to show that trip. */
    fun startTrip(storeId: String) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val res = session.startGroceryTrip(storeId, null)
                val msg = if (!res.ok && res.reason == "in-progress") {
                    "Someone's already shopping that store."
                } else {
                    null
                }
                _ui.update { it.copy(busy = false, message = msg) }
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(busy = false, loadError = e.error.message) }
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
                _ui.update { it.copy(busy = false) }
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(busy = false, loadError = e.error.message) }
            }
        }
    }
}
