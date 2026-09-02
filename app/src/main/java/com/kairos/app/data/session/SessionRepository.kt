package com.kairos.app.data.session

import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.ApiError
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.ApiService
import com.kairos.app.data.remote.apiCall
import com.kairos.app.data.remote.dto.DashboardDto
import com.kairos.app.data.remote.dto.EnrollRequest
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.data.remote.dto.TaskStatusDto
import com.kairos.app.data.remote.dto.WorkoutAckDto
import com.kairos.app.data.remote.dto.WorkoutDateRequest
import retrofit2.Response
import com.kairos.app.data.secure.TokenStore
import com.kairos.app.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Top-level app state derived from stored config + token validity. */
sealed interface SessionState {
    data object Loading : SessionState
    /** No server URL configured yet — first launch. */
    data object NeedsSetup : SessionState
    /** Server known, but no valid device token — needs to redeem a code. */
    data object NeedsEnroll : SessionState
    /** Enrolled; [person] came from /me. */
    data class Ready(val person: PersonDto) : SessionState
}

/**
 * Owns identity for the whole app: the configured server, the device token, and
 * the enrolled person. It's the single place that decides which top-level graph
 * shows. The OkHttp service is rebuilt only when the base URL changes; token
 * changes are picked up through the in-memory [TokenStore] the interceptor reads.
 */
class SessionRepository(
    private val settings: SettingsStore,
    private val tokens: TokenStore,
    private val appScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Raw configured origin (no /api/v1), for resolving avatar URLs later. */
    @Volatile
    var baseUrlRaw: String? = null
        private set

    private var service: ApiService? = null

    init {
        appScope.launch { bootstrap() }
    }

    private fun rebuildService(rawBase: String) {
        baseUrlRaw = rawBase
        service = ApiClient.create(rawBase) { tokens.current() }
    }

    /** Decide the start destination on launch. */
    suspend fun bootstrap() {
        val url = settings.currentBaseUrl()
        if (url.isNullOrBlank()) {
            _state.value = SessionState.NeedsSetup
            return
        }
        rebuildService(url)
        val token = tokens.load()
        if (token.isNullOrBlank()) {
            _state.value = SessionState.NeedsEnroll
            return
        }
        // Validate the stored token by fetching the person.
        try {
            val person = apiCall { service!!.me() }
            _state.value = SessionState.Ready(person)
        } catch (e: ApiException) {
            if (e.error is ApiError.Unauthenticated) {
                tokens.clear()
                _state.value = SessionState.NeedsEnroll
            } else {
                // Network/server hiccup on a known-good token: stay enrolled in
                // spirit but let the caller retry. We fall back to NeedsEnroll
                // only for auth failures, not transient ones.
                _state.value = SessionState.NeedsEnroll
            }
        }
    }

    /** Validate a candidate server with the /meta handshake, and adopt it on
     *  success. Throws [ApiException] if it can't be reached or is too new. */
    suspend fun configureServer(rawBase: String) {
        val candidate = ApiClient.create(rawBase) { tokens.current() }
        val meta = apiCall { candidate.meta() }
        if (meta.minClient > CLIENT_BUILD) {
            throw ApiException(
                ApiError.Conflict("This server needs a newer app (build ${meta.minClient})."),
            )
        }
        settings.setBaseUrl(rawBase)
        rebuildService(rawBase)
        _state.value = SessionState.NeedsEnroll
    }

    /** Redeem an enrollment code for a device token, store it, and go Ready. */
    suspend fun enroll(code: String, deviceName: String?) {
        val svc = service ?: throw ApiException(ApiError.Unknown("No server configured."))
        val res = apiCall { svc.enroll(EnrollRequest(code = code.trim(), deviceName = deviceName?.trim())) }
        tokens.save(res.token)
        _state.value = SessionState.Ready(res.person)
    }

    /** Sign out: best-effort server revoke, then wipe the local token. */
    suspend fun signOut() {
        val svc = service
        if (svc != null) {
            runCatching { apiCall { svc.revoke() } }
        }
        tokens.clear()
        _state.value = SessionState.NeedsEnroll
    }

    /** Change server entirely: revoke where possible, drop the token, and go
     *  back to setup so the user re-enrolls against the new host. */
    suspend fun changeServer() {
        val svc = service
        if (svc != null) {
            runCatching { apiCall { svc.revoke() } }
        }
        tokens.clear()
        settings.clearBaseUrl()
        service = null
        baseUrlRaw = null
        _state.value = SessionState.NeedsSetup
    }

    /** Load the day. A 401 here means the token died server-side, so we drop it
     *  and fall back to enrollment; the error is rethrown for the caller too. */
    suspend fun loadDashboard(date: String? = null): DashboardDto =
        runAuthed { requireService().dashboard(date) }

    suspend fun completeTask(id: String): TaskStatusDto =
        runAuthed { requireService().completeTask(id) }

    suspend fun uncompleteTask(id: String): TaskStatusDto =
        runAuthed { requireService().uncompleteTask(id) }

    suspend fun workoutComplete(date: String): WorkoutAckDto =
        runAuthed { requireService().workoutComplete(WorkoutDateRequest(date)) }

    suspend fun workoutUncomplete(date: String): WorkoutAckDto =
        runAuthed { requireService().workoutUncomplete(WorkoutDateRequest(date)) }

    suspend fun workoutRest(date: String): WorkoutAckDto =
        runAuthed { requireService().workoutRest(WorkoutDateRequest(date)) }

    private fun requireService() =
        service ?: throw ApiException(ApiError.Unknown("No server configured."))

    private suspend fun <T> runAuthed(block: suspend () -> Response<T>): T {
        try {
            return apiCall(block)
        } catch (e: ApiException) {
            if (e.error is ApiError.Unauthenticated) {
                tokens.clear()
                _state.value = SessionState.NeedsEnroll
            }
            throw e
        }
    }

    suspend fun refreshMe() {
        val svc = service ?: return
        try {
            val person = apiCall { svc.me() }
            _state.value = SessionState.Ready(person)
        } catch (e: ApiException) {
            if (e.error is ApiError.Unauthenticated) {
                tokens.clear()
                _state.value = SessionState.NeedsEnroll
            }
        }
    }

    private companion object {
        /** This client's build number; compared against the server's minClient. */
        const val CLIENT_BUILD = 3
    }
}
