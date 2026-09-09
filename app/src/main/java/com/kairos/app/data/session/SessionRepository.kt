package com.kairos.app.data.session

import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.ApiError
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.ApiService
import com.kairos.app.data.remote.apiCall
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
    private val httpCache: okhttp3.Cache? = null,
    private val networkMonitor: com.kairos.app.data.remote.NetworkMonitor? = null,
    private val writeQueue: com.kairos.app.data.remote.WriteQueue? = null,
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
        service = ApiClient.create(rawBase, httpCache, networkMonitor, writeQueue) { tokens.current() }
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
                    clearOfflineWrites()
                    _state.value = SessionState.NeedsEnroll
                }
                else ->
                    // Transient network/server issue on a known-good token.
                    _state.value = SessionState.NeedsEnroll
            }
        }
    }

    suspend fun loadNotifMeta(): com.kairos.app.data.remote.dto.NotifMetaDto =
        runAuthed { requireService().notifMeta() }

    suspend fun loadUpcoming(): com.kairos.app.data.remote.dto.UpcomingDto =
        runAuthed { requireService().upcoming() }

    /** Set my color (ring + calendar + everywhere it's used). Syncs to the web. */
    suspend fun setMyColor(color: String) {
        runAuthed { requireService().setColor(com.kairos.app.data.remote.dto.ColorRequest(color)) }
    }

    /** Upload a new avatar photo and/or re-frame it. Pass null bytes to only
     *  re-frame the existing photo. Syncs to the web. */
    suspend fun setAvatar(imageBytes: ByteArray?, mime: String?, position: String) {
        val posBody = position.toRequestBody("text/plain".toMediaType())
        val part = if (imageBytes != null && mime != null) {
            val ext = when (mime) {
                "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg"
            }
            okhttp3.MultipartBody.Part.createFormData(
                "image", "avatar.$ext", imageBytes.toRequestBody(mime.toMediaType()),
            )
        } else {
            null
        }
        runAuthed { requireService().setAvatar(part, posBody) }
    }

    /** Re-fetch the enrolled person after a profile change so the drawer + ring
     *  reflect it. No-op if we're not in the Ready state or offline. */
    suspend fun refreshPerson() {
        val svc = service ?: return
        try {
            val person = apiCall { svc.me() }
            if (_state.value is SessionState.Ready) _state.value = SessionState.Ready(person)
        } catch (_: ApiException) {
        }
    }

    /** Validate a candidate server with the /meta handshake, and adopt it on
     *  success. Throws [ApiException] if it can't be reached or is too new. */
    suspend fun configureServer(rawBase: String) {
        val candidate = ApiClient.create(rawBase, httpCache, networkMonitor, writeQueue) { tokens.current() }
        val meta = apiCall { candidate.meta() }
        if (meta.minClient > CLIENT_BUILD) {
            throw ApiException(
                ApiError.Conflict("This server needs a newer app (build ${meta.minClient})."),
            )
        }
        settings.setBaseUrl(rawBase)
        rebuildService(rawBase)
        clearOfflineWrites() // writes queued against the old server must not replay here
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
        runCatching { httpCache?.evictAll() } // fresh device: no prior user's cached data
        clearOfflineWrites() // ...nor a prior user's queued writes
        _state.value = SessionState.Ready(res.person)
    }

    /** Sign out: best-effort server revoke, then wipe the local token. */
    suspend fun signOut() {
        val svc = service
        if (svc != null) {
            runCatching { apiCall { svc.revoke() } }
        }
        tokens.clear()
        clearOfflineWrites()
        runCatching { httpCache?.evictAll() }
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
        clearOfflineWrites()
        runCatching { httpCache?.evictAll() }
        settings.clearBaseUrl()
        service = null
        baseUrlRaw = null
        loginToken = null
        _state.value = SessionState.NeedsSetup
    }

    /** Load the day. A 401 here means the token died server-side, so we drop it
     *  and fall back to enrollment; the error is rethrown for the caller too. */
    suspend fun sportConfirm(eventId: String, dateISO: String?) {
        runAuthed { requireService().sportConfirm(com.kairos.app.data.remote.dto.SportAnswerRequest(eventId, dateISO)) }
    }

    suspend fun sportDecline(eventId: String, dateISO: String?) {
        runAuthed { requireService().sportDecline(com.kairos.app.data.remote.dto.SportAnswerRequest(eventId, dateISO)) }
    }

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

    suspend fun loadWorkoutBuilder(): com.kairos.app.data.remote.dto.WorkoutBuilderDto =
        runAuthed { requireService().workoutBuilder() }

    suspend fun createPersonalWorkout(body: com.kairos.app.data.remote.dto.CreatePersonalWorkoutRequest) {
        runAuthed { requireService().createPersonalWorkout(body) }
    }

    suspend fun updatePersonalWorkout(body: com.kairos.app.data.remote.dto.UpdatePersonalWorkoutRequest) {
        runAuthed { requireService().updatePersonalWorkout(body) }
    }

    suspend fun shareWorkout(workoutId: String, targetUserId: String) {
        runAuthed { requireService().shareWorkout(com.kairos.app.data.remote.dto.ShareWorkoutRequest(workoutId, targetUserId)) }
    }

    suspend fun deletePersonalWorkout(workoutId: String) {
        runAuthed { requireService().deletePersonalWorkout(com.kairos.app.data.remote.dto.WorkoutIdRequest(workoutId)) }
    }

    suspend fun addMovement(category: String, name: String): String? =
        runAuthed { requireService().addMovement(com.kairos.app.data.remote.dto.AddMovementRequest(category, name)) }.id

    suspend fun renameMovement(movementId: String, name: String) {
        runAuthed { requireService().renameMovement(com.kairos.app.data.remote.dto.RenameMovementRequest(movementId, name)) }
    }

    suspend fun deleteMovement(movementId: String) {
        runAuthed { requireService().deleteMovement(com.kairos.app.data.remote.dto.MovementIdRequest(movementId)) }
    }

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

    suspend fun saveCalendarPrefs(
        body: com.kairos.app.data.remote.dto.CalendarPrefsRequest,
    ) {
        runAuthed { requireService().saveCalendarPrefs(body) }
    }

    suspend fun createCalendarEvent(
        body: com.kairos.app.data.remote.dto.CreateEventRequest,
    ) {
        runAuthed { requireService().createEvent(body) }
    }

    suspend fun loadMoney(user: String?): com.kairos.app.data.remote.dto.MoneyDto =
        runAuthed { requireService().money(user) }

    suspend fun addMoneyEntry(body: com.kairos.app.data.remote.dto.AddMoneyRequest) {
        runAuthed { requireService().addMoneyEntry(body) }
    }

    suspend fun approveRewardMonth(periodKey: String) {
        runAuthed {
            requireService().approveRewardMonth(
                com.kairos.app.data.remote.dto.RewardApproveMonthRequest(periodKey),
            )
        }
    }

    suspend fun approveRewardBase(userId: String, periodKey: String) {
        runAuthed {
            requireService().approveRewardBase(
                com.kairos.app.data.remote.dto.RewardApproveBaseRequest(userId, periodKey),
            )
        }
    }

    suspend fun approveMoney(id: String) {
        runAuthed { requireService().approveMoney(com.kairos.app.data.remote.dto.MoneyIdRequest(id)) }
    }

    suspend fun unapproveMoney(id: String) {
        runAuthed { requireService().unapproveMoney(com.kairos.app.data.remote.dto.MoneyIdRequest(id)) }
    }

    suspend fun approveAllMoney() {
        runAuthed { requireService().approveAllMoney() }
    }

    suspend fun updateMoney(body: com.kairos.app.data.remote.dto.UpdateMoneyRequest) {
        runAuthed { requireService().updateMoney(body) }
    }

    suspend fun deleteMoney(id: String) {
        runAuthed { requireService().deleteMoney(com.kairos.app.data.remote.dto.MoneyIdRequest(id)) }
    }

    suspend fun setStartingFunds(body: com.kairos.app.data.remote.dto.StartingFundsRequest) {
        runAuthed { requireService().setStartingFunds(body) }
    }

    suspend fun loadBooks(): com.kairos.app.data.remote.dto.BooksDto =
        runAuthed { requireService().books() }

    suspend fun addBook(body: com.kairos.app.data.remote.dto.AddBookRequest) {
        runAuthed { requireService().addBook(body) }
    }

    suspend fun logBook(id: String, page: Int) {
        runAuthed { requireService().logBook(com.kairos.app.data.remote.dto.LogBookRequest(id, page)) }
    }

    suspend fun updateBook(body: com.kairos.app.data.remote.dto.UpdateBookRequest) {
        runAuthed { requireService().updateBook(body) }
    }

    suspend fun finishBook(id: String, finished: Boolean) {
        runAuthed { requireService().finishBook(com.kairos.app.data.remote.dto.BookFinishRequest(id, finished)) }
    }

    suspend fun shelfBook(id: String, shelved: Boolean) {
        runAuthed { requireService().shelfBook(com.kairos.app.data.remote.dto.BookShelfRequest(id, shelved)) }
    }

    suspend fun deleteBook(id: String) {
        runAuthed { requireService().deleteBook(com.kairos.app.data.remote.dto.BookIdRequest(id)) }
    }

    // ---- Groceries (shared family list) ----
    suspend fun loadGroceries(): com.kairos.app.data.remote.dto.GroceriesDto =
        runAuthed { requireService().groceries() }

    suspend fun addGrocery(name: String, storeId: String, note: String? = null) {
        runAuthed { requireService().addGrocery(com.kairos.app.data.remote.dto.AddGroceryRequest(name, storeId, note)) }
    }

    suspend fun addGroceryFromCatalog(catalogId: String, storeId: String? = null) {
        runAuthed { requireService().addGroceryFromCatalog(com.kairos.app.data.remote.dto.AddCatalogRequest(catalogId, storeId)) }
    }

    suspend fun removeGrocery(id: String) {
        runAuthed { requireService().removeGrocery(com.kairos.app.data.remote.dto.GroceryIdRequest(id)) }
    }

    suspend fun setGroceryPurchased(id: String, purchased: Boolean) {
        runAuthed { requireService().setGroceryPurchased(com.kairos.app.data.remote.dto.GroceryPurchasedRequest(id, purchased)) }
    }

    suspend fun startGroceryTrip(storeId: String, shopperId: String? = null): com.kairos.app.data.remote.dto.StartTripDto =
        runAuthed { requireService().startGroceryTrip(com.kairos.app.data.remote.dto.StartTripRequest(storeId, shopperId)) }

    suspend fun completeGroceryTrip(tripId: String) {
        runAuthed { requireService().completeGroceryTrip(com.kairos.app.data.remote.dto.CompleteTripRequest(tripId)) }
    }

    suspend fun moveGrocery(id: String, storeId: String) {
        runAuthed { requireService().moveGrocery(com.kairos.app.data.remote.dto.MoveGroceryRequest(id, storeId)) }
    }

    suspend fun loadCharacter(): com.kairos.app.data.remote.dto.CharacterDto =
        runAuthed { requireService().character() }

    suspend fun hatchCompanion(mode: String): com.kairos.app.data.remote.dto.HatchResultDto =
        runAuthed { requireService().hatchCompanion(com.kairos.app.data.remote.dto.HatchRequest(mode)) }

    suspend fun loadCollection(): com.kairos.app.data.remote.dto.CollectionDto =
        runAuthed { requireService().collection() }

    suspend fun loadCoop(): com.kairos.app.data.remote.dto.CoopDto =
        runAuthed { requireService().coop() }
    suspend fun proposeCoop(title: String, detail: String) {
        runAuthed { requireService().proposeCoop(com.kairos.app.data.remote.dto.ProposeCoopRequest(title, detail)) }
    }
    suspend fun voteCoop(proposalId: String) {
        runAuthed { requireService().voteCoop(com.kairos.app.data.remote.dto.CoopProposalIdRequest(proposalId)) }
    }
    suspend fun selectCoop(proposalId: String) {
        runAuthed { requireService().selectCoop(com.kairos.app.data.remote.dto.CoopProposalIdRequest(proposalId)) }
    }
    suspend fun grantCoop(proposalId: String) {
        runAuthed { requireService().grantCoop(com.kairos.app.data.remote.dto.CoopProposalIdRequest(proposalId)) }
    }
    suspend fun removeCoop(proposalId: String) {
        runAuthed { requireService().removeCoop(com.kairos.app.data.remote.dto.CoopProposalIdRequest(proposalId)) }
    }

    suspend fun loadSchool(term: String?): com.kairos.app.data.remote.dto.SchoolDto =
        runAuthed { requireService().school(term) }
    suspend fun addSchool(userId: String, title: String, type: String, dueDate: String, subject: String?, classId: String?) {
        runAuthed { requireService().addSchool(com.kairos.app.data.remote.dto.AddSchoolRequest(userId, title, type, dueDate, subject, classId)) }
    }
    suspend fun deleteSchool(taskId: String) {
        runAuthed { requireService().deleteSchool(com.kairos.app.data.remote.dto.SchoolTaskIdRequest(taskId)) }
    }
    suspend fun renameSchool(taskId: String, title: String) {
        runAuthed { requireService().renameSchool(com.kairos.app.data.remote.dto.SchoolRenameRequest(taskId, title)) }
    }
    suspend fun loadTasksList(): com.kairos.app.data.remote.dto.TasksListDto =
        runAuthed { requireService().tasksList() }
    suspend fun addTask(userId: String, title: String, dueDate: String?) {
        runAuthed { requireService().addTask(com.kairos.app.data.remote.dto.AddTaskRequest(userId, title, dueDate)) }
    }

    suspend fun deleteCalendarEvent(eventId: String, scope: String?, occurrenceISO: String?) {
        runAuthed {
            requireService().deleteEvent(
                com.kairos.app.data.remote.dto.DeleteEventRequest(eventId, scope, occurrenceISO),
            )
        }
    }

    suspend fun updateCalendarEvent(
        body: com.kairos.app.data.remote.dto.UpdateEventRequest,
    ) {
        runAuthed { requireService().updateEvent(body) }
    }

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

    suspend fun previewReadingPlan(
        body: com.kairos.app.data.remote.dto.PersonalPlanRequest,
    ): com.kairos.app.data.remote.dto.PlanPreviewDto =
        runAuthed { requireService().previewReadingPlan(body) }

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

    /** Live connectivity, so optimistic UI can skip the reload (which would read
     *  the stale offline cache and undo the optimistic change) while offline. */
    fun isOnline(): Boolean = networkMonitor?.isOnline() ?: true

    /** The writes still waiting to sync, so a screen can re-apply them on top of a
     *  (possibly cached) load and keep offline changes visible across navigation. */
    suspend fun pendingWrites(): List<com.kairos.app.data.remote.PendingWrite> =
        writeQueue?.snapshot() ?: emptyList()

    /** Discard any queued offline writes. Called whenever identity or server
     *  changes (sign-out, re-enroll, server switch, dead token) so one
     *  person's/server's pending writes can never replay under another. */
    private suspend fun clearOfflineWrites() {
        runCatching { writeQueue?.clear() }
    }

    /** The enrolled person's id, or null — so a screen can tell which queued
     *  writes belong to "me" (e.g. a task added for this device's person). */
    fun currentPersonId(): String? = (_state.value as? SessionState.Ready)?.person?.id

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
                    clearOfflineWrites()
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
                clearOfflineWrites()
                _state.value = SessionState.NeedsEnroll
            }
        }
    }

    private companion object {
        /** This client's build number; compared against the server's minClient. */
        const val CLIENT_BUILD = 184
    }
}
