package com.kairos.app.data.session

import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.ApiError
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.ApiService
import com.kairos.app.data.remote.apiCall
import com.kairos.app.data.remote.dto.DashboardDto
import com.kairos.app.data.remote.dto.EnrollRequest
import com.kairos.app.data.remote.dto.LoginRequest
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.data.remote.dto.ReauthRequest
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
    /** Device still enrolled, but the account password changed — re-enter it. */
    data class NeedsReauth(val person: PersonDto?) : SessionState
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

    /** Short-lived login proof from /auth/login, held only between sign-in and
     *  the code step of enrollment. Never persisted. */
    @Volatile
    private var loginToken: String? = null

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
            when (e.error) {
                is ApiError.ReauthRequired ->
                    // Device still enrolled; the password changed. Keep the token.
                    _state.value = SessionState.NeedsReauth(null)
                is ApiError.Unauthenticated -> {
                    tokens.clear()
                    _state.value = SessionState.NeedsEnroll
                }
                else ->
                    // Transient network/server issue on a known-good token.
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

    /** Verify a password and hold the returned proof for the code step. */
    suspend fun login(identifier: String, password: String): PersonDto? {
        val svc = requireService()
        val res = apiCall { svc.login(LoginRequest(identifier.trim(), password)) }
        loginToken = res.loginToken
        return res.person
    }

    /** Redeem an enrollment code for a device token, store it, and go Ready. The
     *  held login proof (if any) is sent so password accounts pass the gate;
     *  passwordless children enroll with no proof. */
    suspend fun enroll(code: String, deviceName: String?) {
        val svc = requireService()
        val res = apiCall {
            svc.enroll(
                EnrollRequest(
                    code = code.trim(),
                    deviceName = deviceName?.trim(),
                    loginToken = loginToken,
                ),
            )
        }
        tokens.save(res.token)
        loginToken = null
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
        loginToken = null
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

    suspend fun loadWorkout(date: String? = null): com.kairos.app.data.remote.dto.WorkoutPlanDto =
        runAuthed { requireService().workoutPlan(date) }

    suspend fun loadWorkoutProgress(): com.kairos.app.data.remote.dto.WorkoutProgressDto =
        runAuthed { requireService().workoutProgress() }

    suspend fun deleteWorkoutSession(id: String) {
        runAuthed { requireService().deleteWorkoutSession(id) }
    }

    suspend fun loadWorkoutPool(): com.kairos.app.data.remote.dto.WorkoutPoolDto =
        runAuthed { requireService().workoutPool() }

    suspend fun loadWeek(): List<com.kairos.app.data.remote.dto.WeeklyActivityDto> =
        runAuthed { requireService().workoutWeek() }.items

    suspend fun loadBrowse(): List<com.kairos.app.data.remote.dto.BrowseWorkoutDto> =
        runAuthed { requireService().workoutBrowse() }.items

    suspend fun loadPlan(): List<com.kairos.app.data.remote.dto.PlanDayDto> =
        runAuthed { requireService().workoutPlan() }.days

    suspend fun planMarkRest(day: Int) {
        runAuthed { requireService().planRest(com.kairos.app.data.remote.dto.PlanRestRequest(day)) }
    }

    suspend fun planCopyDay(from: Int, to: Int) {
        runAuthed { requireService().planCopy(com.kairos.app.data.remote.dto.PlanCopyRequest(from, to)) }
    }

    suspend fun planRemove(id: String) {
        runAuthed { requireService().planRemove(id) }
    }

    suspend fun loadPlanOptions(): com.kairos.app.data.remote.dto.PlanOptionsDto =
        runAuthed { requireService().planOptions() }

    suspend fun planAddPool(body: com.kairos.app.data.remote.dto.AddPoolRequest) {
        runAuthed { requireService().planAddPool(body) }
    }

    suspend fun planAddHiit(day: Int, hiitWorkoutId: String) {
        runAuthed { requireService().planAddHiit(com.kairos.app.data.remote.dto.AddHiitRequest(day, hiitWorkoutId)) }
    }

    suspend fun loadRotation(): com.kairos.app.data.remote.dto.RotationDto =
        runAuthed { requireService().rotation() }

    suspend fun rotationStart() { runAuthed { requireService().rotationStart() } }
    suspend fun rotationStop() { runAuthed { requireService().rotationStop() } }
    suspend fun rotationRestDays(mask: Int) {
        runAuthed { requireService().rotationRestDays(com.kairos.app.data.remote.dto.RestDaysRequest(mask)) }
    }
    suspend fun rotationAddSlot(body: com.kairos.app.data.remote.dto.AddSlotRequest) {
        runAuthed { requireService().rotationAddSlot(body) }
    }
    suspend fun rotationRemoveSlot(slotId: String) {
        runAuthed { requireService().rotationRemoveSlot(com.kairos.app.data.remote.dto.SlotIdRequest(slotId)) }
    }
    suspend fun rotationMoveSlot(slotId: String, dir: Int) {
        runAuthed { requireService().rotationMoveSlot(com.kairos.app.data.remote.dto.MoveSlotRequest(slotId, dir)) }
    }

    suspend fun logCustom(body: com.kairos.app.data.remote.dto.CustomLogRequest): WorkoutAckDto =
        runAuthed { requireService().logCustom(body) }

    suspend fun logWorkout(
        date: String,
        plannedWorkoutId: String,
        entries: List<com.kairos.app.data.remote.dto.PlannedEntryDto>,
    ): WorkoutAckDto =
        runAuthed {
            requireService().logWorkout(
                com.kairos.app.data.remote.dto.WorkoutLogRequest(date, plannedWorkoutId, entries),
            )
        }

    suspend fun loadReading(): com.kairos.app.data.remote.dto.ReadingDto =
        runAuthed { requireService().reading() }

    suspend fun loadChores(): com.kairos.app.data.remote.dto.ChoresDto =
        runAuthed { requireService().chores() }

    suspend fun loadCalendar(
        view: String?,
        date: String?,
    ): com.kairos.app.data.remote.dto.CalendarDto =
        runAuthed { requireService().calendar(view, date) }

    suspend fun claimChore(taskId: String) {
        runAuthed {
            requireService().claimChore(
                com.kairos.app.data.remote.dto.ClaimChoreRequest(taskId),
            )
        }
    }

    suspend fun completeAlwaysOpen(choreId: String) {
        runAuthed {
            requireService().completeAlwaysOpen(
                com.kairos.app.data.remote.dto.AlwaysOpenRequest(choreId),
            )
        }
    }

    suspend fun createReadingPlan(
        body: com.kairos.app.data.remote.dto.PersonalPlanRequest,
    ) {
        runAuthed { requireService().createReadingPlan(body) }
    }

    suspend fun deleteReadingPlan() {
        runAuthed { requireService().deleteReadingPlan() }
    }

    suspend fun markReading(passage: String, read: Boolean) {
        runAuthed {
            requireService().markReading(
                com.kairos.app.data.remote.dto.MarkReadingRequest(passage, read),
            )
        }
    }

    suspend fun saveReadingBook(bookName: String, chapters: List<Int>) {
        runAuthed {
            requireService().saveReadingBook(
                com.kairos.app.data.remote.dto.SaveBookRequest(bookName, chapters),
            )
        }
    }

    suspend fun saveReadingBooks(bookNames: List<String>, read: Boolean) {
        runAuthed {
            requireService().saveReadingBooks(
                com.kairos.app.data.remote.dto.SaveBooksRequest(bookNames, read),
            )
        }
    }

    suspend fun listDevices(): List<com.kairos.app.data.remote.dto.DeviceDto> =
        runAuthed { requireService().devices() }.devices

    suspend fun revokeDevice(id: String) {
        runAuthed { requireService().revokeDevice(id) }
    }

    private fun requireService() =
        service ?: throw ApiException(ApiError.Unknown("No server configured."))

    private suspend fun <T> runAuthed(block: suspend () -> Response<T>): T {
        try {
            return apiCall(block)
        } catch (e: ApiException) {
            when (e.error) {
                is ApiError.ReauthRequired ->
                    // Keep the device token; the app shows a password prompt.
                    _state.value = SessionState.NeedsReauth(null)
                is ApiError.Unauthenticated -> {
                    tokens.clear()
                    _state.value = SessionState.NeedsEnroll
                }
                else -> {}
            }
            throw e
        }
    }

    /** Re-confirm the password on an enrolled device whose account password
     *  changed. Keeps the same device token; on success returns to Ready. */
    suspend fun reauth(password: String) {
        val svc = requireService()
        val res = apiCall { svc.reauth(ReauthRequest(password)) }
        val person = res.person ?: apiCall { svc.me() }
        _state.value = SessionState.Ready(person)
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
        const val CLIENT_BUILD = 41
    }
}
