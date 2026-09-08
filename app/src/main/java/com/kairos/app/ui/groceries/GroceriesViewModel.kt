package com.kairos.app.ui.groceries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.PendingWrite
import com.kairos.app.data.remote.dto.AddCatalogRequest
import com.kairos.app.data.remote.dto.AddGroceryRequest
import com.kairos.app.data.remote.dto.CompleteTripRequest
import com.kairos.app.data.remote.dto.GroceriesDto
import com.kairos.app.data.remote.dto.GroceryIdRequest
import com.kairos.app.data.remote.dto.GroceryLineDto
import com.kairos.app.data.remote.dto.GroceryPurchasedRequest
import com.kairos.app.data.remote.dto.MoveGroceryRequest
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class GroceriesUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val data: GroceriesDto? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * The shared family grocery list. One read (GET /groceries) returns the whole
 * board. Adds, removes, moves and tick-offs are optimistic and re-applied from the
 * offline queue on load, so they show at once, survive navigation/restart offline,
 * and reconcile on sync. Not self-only: anyone can add, shop, and tick things off.
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
                _ui.update { it.copy(loading = false, data = freshData()) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, loadError = e.error.message) }
            }
        }
    }

    private suspend fun freshData(): GroceriesDto =
        applyPending(session.loadGroceries(), session.pendingWrites())

    fun clearMessage() = _ui.update { it.copy(message = null) }

    fun add(name: String, storeId: String) =
        optimistic({ insertLine(it, name, storeId, "") }) { session.addGrocery(name, storeId, null) }
    fun addFromCatalog(catalogId: String, storeId: String?) =
        optimistic({ insertFromCatalog(it, catalogId, storeId) }) { session.addGroceryFromCatalog(catalogId, storeId) }
    fun remove(id: String) = optimistic({ removeLine(it, id) }) { session.removeGrocery(id) }
    fun move(id: String, storeId: String) = optimistic({ moveLine(it, id, storeId) }) { session.moveGrocery(id, storeId) }
    fun setPurchased(id: String, purchased: Boolean) =
        optimistic({ setPurchasedLine(it, id, purchased) }) { session.setGroceryPurchased(id, purchased) }
    fun completeTrip(tripId: String) =
        optimistic({ it.copy(trips = it.trips.filterNot { t -> t.id == tripId }) }) { session.completeGroceryTrip(tripId) }

    /** Start a run for a store: creates a trip server-side and navigates, so it's
     *  not optimistic — reloads to show the trip. */
    fun startTrip(storeId: String) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val res = session.startGroceryTrip(storeId, null)
                val msg = if (!res.ok && res.reason == "in-progress") "Someone's already shopping that store." else null
                _ui.update { it.copy(busy = false, message = msg) }
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(busy = false, loadError = e.error.message) }
            }
        }
    }

    private fun optimistic(mutate: (GroceriesDto) -> GroceriesDto, write: suspend () -> Unit) {
        if (_ui.value.busy) return
        val before = _ui.value.data ?: return
        _ui.update { it.copy(busy = true, message = null, data = mutate(before)) }
        viewModelScope.launch {
            try {
                write()
                val data = if (session.isOnline()) freshData() else _ui.value.data
                _ui.update { it.copy(busy = false, data = data) }
            } catch (e: ApiException) {
                _ui.update { it.copy(busy = false, data = before, loadError = e.error.message) }
            }
        }
    }

    // ---- transforms, also used to re-apply the offline queue on load ----

    private fun insertLine(data: GroceriesDto, name: String, storeId: String, icon: String): GroceriesDto =
        data.copy(saved = data.saved + GroceryLineDto(id = "temp-${UUID.randomUUID()}", name = name, icon = icon, storeId = storeId))

    private fun insertFromCatalog(data: GroceriesDto, catalogId: String, storeId: String?): GroceriesDto {
        val c = data.catalog.firstOrNull { it.id == catalogId } ?: return data
        val sid = storeId ?: c.defaultStoreId ?: ""
        return data.copy(
            saved = data.saved + GroceryLineDto(id = "temp-${UUID.randomUUID()}", name = c.name, icon = c.icon, storeId = sid),
        )
    }

    private fun removeLine(data: GroceriesDto, id: String): GroceriesDto =
        data.copy(
            saved = data.saved.filterNot { it.id == id },
            trips = data.trips.map { t -> t.copy(items = t.items.filterNot { it.id == id }) },
        )

    private fun moveLine(data: GroceriesDto, id: String, storeId: String): GroceriesDto =
        data.copy(saved = data.saved.map { if (it.id == id) it.copy(storeId = storeId) else it })

    private fun setPurchasedLine(data: GroceriesDto, id: String, purchased: Boolean): GroceriesDto =
        data.copy(
            saved = data.saved.map { if (it.id == id) it.copy(purchased = purchased) else it },
            trips = data.trips.map { t -> t.copy(items = t.items.map { if (it.id == id) it.copy(purchased = purchased) else it }) },
        )

    private fun <T> parse(body: String?, ser: kotlinx.serialization.KSerializer<T>): T? =
        body?.let { runCatching { ApiClient.json.decodeFromString(ser, it) }.getOrNull() }

    private fun applyPending(data: GroceriesDto, pending: List<PendingWrite>): GroceriesDto {
        var d = data
        for (w in pending) {
            when (w.url.substringAfter("/api/v1/", "")) {
                "groceries/add" -> parse(w.body, AddGroceryRequest.serializer())?.let { d = insertLine(d, it.name, it.storeId, "") }
                "groceries/add-catalog" -> parse(w.body, AddCatalogRequest.serializer())?.let { d = insertFromCatalog(d, it.catalogId, it.storeId) }
                "groceries/remove" -> parse(w.body, GroceryIdRequest.serializer())?.let { d = removeLine(d, it.id) }
                "groceries/move" -> parse(w.body, MoveGroceryRequest.serializer())?.let { d = moveLine(d, it.id, it.storeId) }
                "groceries/purchased" -> parse(w.body, GroceryPurchasedRequest.serializer())?.let { d = setPurchasedLine(d, it.id, it.purchased) }
                "groceries/trip/complete" -> parse(w.body, CompleteTripRequest.serializer())?.let { req -> d = d.copy(trips = d.trips.filterNot { it.id == req.tripId }) }
            }
        }
        return d
    }
}
