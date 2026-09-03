package com.kairos.app.ui.reauth

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

data class ReauthUiState(
    val password: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
)

class ReauthViewModel(private val session: SessionRepository) : ViewModel() {

    private val _ui = MutableStateFlow(ReauthUiState())
    val ui: StateFlow<ReauthUiState> = _ui.asStateFlow()

    fun onPassword(v: String) = _ui.update { it.copy(password = v, error = null) }

    fun submit() {
        val pw = _ui.value.password
        if (pw.isBlank()) {
            _ui.update { it.copy(error = "Enter your password.") }
            return
        }
        _ui.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                session.reauth(pw)
                // Success flips session state to Ready; the app swaps to Home.
            } catch (e: ApiException) {
                _ui.update { it.copy(submitting = false, error = friendly(e)) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    private fun friendly(e: ApiException): String = when (e.error) {
        is ApiError.Unauthenticated -> "Wrong password."
        is ApiError.RateLimited -> "Too many tries. Wait a moment and try again."
        is ApiError.Network -> "Couldn't reach the server. Check your connection."
        else -> e.error.message
    }
}
