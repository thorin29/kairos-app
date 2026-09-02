package com.kairos.app.ui.enroll

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiError
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EnrollUiState(
    val code: String = "",
    val deviceName: String = defaultDeviceName(),
    val enrolling: Boolean = false,
    val error: String? = null,
)

private fun defaultDeviceName(): String {
    val model = Build.MODEL?.trim().orEmpty()
    val maker = Build.MANUFACTURER?.trim().orEmpty().replaceFirstChar { it.uppercase() }
    return listOf(maker, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "My phone" }
}

class EnrollViewModel(private val session: SessionRepository) : ViewModel() {

    private val _ui = MutableStateFlow(EnrollUiState())
    val ui: StateFlow<EnrollUiState> = _ui.asStateFlow()

    fun onCodeChange(value: String) {
        _ui.update { it.copy(code = value, error = null) }
    }

    fun onDeviceNameChange(value: String) {
        _ui.update { it.copy(deviceName = value) }
    }

    fun enroll() {
        val code = _ui.value.code.trim()
        if (code.isBlank()) {
            _ui.update { it.copy(error = "Enter the code from the enrollment screen.") }
            return
        }
        _ui.update { it.copy(enrolling = true, error = null) }
        viewModelScope.launch {
            try {
                session.enroll(code, _ui.value.deviceName)
                // Success flips session state to Ready; the app swaps to Home.
            } catch (e: ApiException) {
                _ui.update { it.copy(enrolling = false, error = friendly(e)) }
            }
        }
    }

    fun changeServer() {
        viewModelScope.launch { session.changeServer() }
    }

    private fun friendly(e: ApiException): String = when (val err = e.error) {
        is ApiError.Forbidden -> "That code is invalid or has expired. Ask for a new one."
        is ApiError.Validation -> "Enter the code from the enrollment screen."
        is ApiError.RateLimited -> {
            val secs = err.retryAfterSec
            if (secs != null) "Too many tries. Wait ${secs}s and try again." else "Too many tries. Wait a moment and try again."
        }
        is ApiError.Network -> "Couldn't reach the server. Check your connection."
        else -> err.message
    }
}
