package com.kairos.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val url: String = "https://",
    val connecting: Boolean = false,
    val error: String? = null,
)

class SetupViewModel(private val session: SessionRepository) : ViewModel() {

    private val _ui = MutableStateFlow(SetupUiState())
    val ui: StateFlow<SetupUiState> = _ui.asStateFlow()

    fun onUrlChange(value: String) {
        _ui.update { it.copy(url = value, error = null) }
    }

    fun connect() {
        val raw = _ui.value.url.trim()
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            _ui.update { it.copy(error = "Enter a full URL starting with https://") }
            return
        }
        _ui.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            try {
                // On success the session state flips to NeedsEnroll and the app
                // swaps screens; this ViewModel is discarded.
                session.configureServer(raw)
            } catch (e: ApiException) {
                _ui.update {
                    it.copy(
                        connecting = false,
                        error = friendly(e),
                    )
                }
            }
        }
    }

    private fun friendly(e: ApiException): String = when (e.error) {
        is com.kairos.app.data.remote.ApiError.Network ->
            "Couldn't reach that server. Check the address and that you're on the right network."
        else -> e.error.message
    }
}
