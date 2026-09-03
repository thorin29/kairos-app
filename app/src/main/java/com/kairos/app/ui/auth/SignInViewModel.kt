package com.kairos.app.ui.auth

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

data class SignInUiState(
    val identifier: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    /** Flips true once the proof is held; the screen then advances to the code. */
    val succeeded: Boolean = false,
)

class SignInViewModel(private val session: SessionRepository) : ViewModel() {

    private val _ui = MutableStateFlow(SignInUiState())
    val ui: StateFlow<SignInUiState> = _ui.asStateFlow()

    fun onIdentifier(v: String) = _ui.update { it.copy(identifier = v, error = null) }
    fun onPassword(v: String) = _ui.update { it.copy(password = v, error = null) }

    fun submit() {
        val id = _ui.value.identifier.trim()
        val pw = _ui.value.password
        if (id.isBlank() || pw.isBlank()) {
            _ui.update { it.copy(error = "Enter your username and password.") }
            return
        }
        _ui.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                session.login(id, pw)
                _ui.update { it.copy(submitting = false, succeeded = true) }
            } catch (e: ApiException) {
                _ui.update { it.copy(submitting = false, error = friendly(e)) }
            }
        }
    }

    private fun friendly(e: ApiException): String = when (e.error) {
        is ApiError.Unauthenticated -> "Wrong username or password."
        is ApiError.RateLimited -> "Too many tries. Wait a moment and try again."
        is ApiError.Network -> "Couldn't reach the server. Check your connection."
        else -> e.error.message
    }
}
