package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.AddSlotRequest
import com.kairos.app.data.remote.dto.RotationDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RotationUiState(
    val loading: Boolean = true,
    val rotation: RotationDto? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

class RotationViewModel(private val session: SessionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(RotationUiState())
    val ui: StateFlow<RotationUiState> = _ui.asStateFlow()

    init { load(true) }

    private fun load(initial: Boolean) {
        if (initial) _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                _ui.update { it.copy(loading = false, busy = false, rotation = session.loadRotation(), error = null) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, busy = false, error = e.error.message) }
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block(); load(false) }
            catch (e: ApiException) { _ui.update { it.copy(busy = false, error = e.error.message) } }
        }
    }

    fun start() = act { session.rotationStart() }
    fun stop() = act { session.rotationStop() }
    fun toggleRestDay(dow: Int) = act {
        val mask = (_ui.value.rotation?.restMask ?: 0) xor (1 shl dow)
        session.rotationRestDays(mask)
    }
    fun addSlot(name: String, isRest: Boolean) = act {
        session.rotationAddSlot(AddSlotRequest(name = name, isRest = isRest))
    }
    fun removeSlot(id: String) = act { session.rotationRemoveSlot(id) }
    fun moveSlot(id: String, dir: Int) = act { session.rotationMoveSlot(id, dir) }
}
