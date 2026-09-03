package com.kairos.app.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.DeviceDto
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevicesUiState(
    val loading: Boolean = true,
    val devices: List<DeviceDto> = emptyList(),
    val error: String? = null,
    val busyIds: Set<String> = emptySet(),
)

class DevicesViewModel(private val session: SessionRepository) : ViewModel() {

    private val _ui = MutableStateFlow(DevicesUiState())
    val ui: StateFlow<DevicesUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = it.devices.isEmpty(), error = null) }
        viewModelScope.launch {
            try {
                val list = session.listDevices()
                _ui.update { it.copy(loading = false, devices = list, error = null) }
            } catch (e: ApiException) {
                _ui.update {
                    it.copy(loading = false, error = if (it.devices.isEmpty()) e.error.message else it.error)
                }
            }
        }
    }

    fun revoke(id: String) {
        if (_ui.value.busyIds.contains(id)) return
        _ui.update { it.copy(busyIds = it.busyIds + id, error = null) }
        viewModelScope.launch {
            try {
                session.revokeDevice(id)
                val list = session.listDevices()
                _ui.update { it.copy(devices = list, busyIds = it.busyIds - id) }
            } catch (e: ApiException) {
                _ui.update { it.copy(busyIds = it.busyIds - id, error = e.error.message) }
            }
        }
    }
}
