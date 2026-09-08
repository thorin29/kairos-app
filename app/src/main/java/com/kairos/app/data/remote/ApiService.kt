package com.kairos.app.data.remote

import com.kairos.app.data.remote.dto.DashboardDto
import com.kairos.app.data.remote.dto.DeleteAckDto
import com.kairos.app.data.remote.dto.DeviceDto
import com.kairos.app.data.remote.dto.DevicesResponse
import com.kairos.app.data.remote.dto.EnrollRequest
import com.kairos.app.data.remote.dto.EnrollResponse
import com.kairos.app.data.remote.dto.LoginRequest
import com.kairos.app.data.remote.dto.LoginResponse
import com.kairos.app.data.remote.dto.MetaDto
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.data.remote.dto.ReauthRequest
import com.kairos.app.data.remote.dto.RevokeDeviceResponse
import com.kairos.app.data.remote.dto.ReauthResponse
import com.kairos.app.data.remote.dto.RevokeResponse
import com.kairos.app.data.remote.dto.TaskStatusDto
import com.kairos.app.data.remote.dto.TokenResponse
import com.kairos.app.data.remote.dto.WorkoutAckDto
import com.kairos.app.data.remote.dto.WorkoutLogRequest
import com.kairos.app.data.remote.dto.WorkoutPlanDto
import com.kairos.app.data.remote.dto.WorkoutProgressDto
import com.kairos.app.data.remote.dto.WorkoutPoolDto
import com.kairos.app.data.remote.dto.WeekResponse
import com.kairos.app.data.remote.dto.BrowseResponse
import com.kairos.app.data.remote.dto.PlanResponse
import com.kairos.app.data.remote.dto.PlanRestRequest
import com.kairos.app.data.remote.dto.PlanCopyRequest
import com.kairos.app.data.remote.dto.PlanOptionsDto
import com.kairos.app.data.remote.dto.AddPoolRequest
import com.kairos.app.data.remote.dto.AddHiitRequest
import com.kairos.app.data.remote.dto.RotationDto
import com.kairos.app.data.remote.dto.RestDaysRequest
import com.kairos.app.data.remote.dto.AddSlotRequest
import com.kairos.app.data.remote.dto.SlotIdRequest
import com.kairos.app.data.remote.dto.MoveSlotRequest
import com.kairos.app.data.remote.dto.CustomLogRequest
import com.kairos.app.data.remote.dto.WorkoutDateRequest
import com.kairos.app.data.remote.dto.ReadingDto
import com.kairos.app.data.remote.dto.PersonalPlanRequest
import com.kairos.app.data.remote.dto.MarkReadingRequest
import com.kairos.app.data.remote.dto.SaveBookRequest
import com.kairos.app.data.remote.dto.SaveBooksRequest
import com.kairos.app.data.remote.dto.ChoresDto
import com.kairos.app.data.remote.dto.ClaimChoreRequest
import com.kairos.app.data.remote.dto.AlwaysOpenRequest
import com.kairos.app.data.remote.dto.CalendarDto
import com.kairos.app.data.remote.dto.CalendarPrefsRequest
import com.kairos.app.data.remote.dto.CreateEventRequest
import com.kairos.app.data.remote.dto.DeleteEventRequest
import com.kairos.app.data.remote.dto.UpdateEventRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The built /api/v1 surface (docs/API.md "Auth & identity — built"). Read
 * endpoints (dashboard, chores, …) get added here as each screen lands and its
 * server endpoint is built. Responses are returned as Retrofit [Response] so
 * the calling layer can map status + body to a single [ApiError].
 *
 * The Bearer header is attached by AuthInterceptor, not declared per-method, so
 * there is one place that knows the auth rule.
 */
interface ApiService {

    @GET("meta")
    suspend fun meta(): Response<MetaDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("auth/enroll")
    suspend fun enroll(@Body body: EnrollRequest): Response<EnrollResponse>

    @POST("auth/refresh")
    suspend fun refresh(): Response<TokenResponse>

    @POST("auth/revoke")
    suspend fun revoke(): Response<RevokeResponse>

    @POST("auth/reauth")
    suspend fun reauth(@Body body: ReauthRequest): Response<ReauthResponse>

    @GET("me")
    suspend fun me(): Response<PersonDto>

    @GET("dashboard")
    suspend fun dashboard(@Query("date") date: String? = null): Response<DashboardDto>

    @POST("tasks/{id}/complete")
    suspend fun completeTask(@Path("id") id: String): Response<TaskStatusDto>

    @POST("tasks/{id}/uncomplete")
    suspend fun uncompleteTask(@Path("id") id: String): Response<TaskStatusDto>

    @POST("workouts/complete")
    suspend fun workoutComplete(@Body body: WorkoutDateRequest): Response<WorkoutAckDto>

    @POST("workouts/uncomplete")
    suspend fun workoutUncomplete(@Body body: WorkoutDateRequest): Response<WorkoutAckDto>

    @POST("workouts/rest")
    suspend fun workoutRest(@Body body: WorkoutDateRequest): Response<WorkoutAckDto>

    @GET("workouts")
    suspend fun workoutPlan(@Query("date") date: String? = null): Response<WorkoutPlanDto>

    @POST("workouts/log")
    suspend fun logWorkout(@Body body: WorkoutLogRequest): Response<WorkoutAckDto>

    @GET("workouts/progress")
    suspend fun workoutProgress(): Response<WorkoutProgressDto>

    @GET("workouts/pool")
    suspend fun workoutPool(): Response<WorkoutPoolDto>

    @GET("workouts/week")
    suspend fun workoutWeek(): Response<WeekResponse>

    @GET("workouts/browse")
    suspend fun workoutBrowse(): Response<BrowseResponse>

    @GET("workouts/builder")
    suspend fun workoutBuilder(): Response<com.kairos.app.data.remote.dto.WorkoutBuilderDto>

    @POST("workouts/personal")
    suspend fun createPersonalWorkout(@Body body: com.kairos.app.data.remote.dto.CreatePersonalWorkoutRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("workouts/personal/update")
    suspend fun updatePersonalWorkout(@Body body: com.kairos.app.data.remote.dto.UpdatePersonalWorkoutRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("workouts/personal/share")
    suspend fun shareWorkout(@Body body: com.kairos.app.data.remote.dto.ShareWorkoutRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("workouts/personal/delete")
    suspend fun deletePersonalWorkout(@Body body: com.kairos.app.data.remote.dto.WorkoutIdRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("workouts/movement")
    suspend fun addMovement(@Body body: com.kairos.app.data.remote.dto.AddMovementRequest): Response<com.kairos.app.data.remote.dto.AddMovementResponse>

    @POST("workouts/movement/rename")
    suspend fun renameMovement(@Body body: com.kairos.app.data.remote.dto.RenameMovementRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("workouts/movement/delete")
    suspend fun deleteMovement(@Body body: com.kairos.app.data.remote.dto.MovementIdRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @GET("workouts/plan")
    suspend fun workoutPlan(): Response<PlanResponse>

    @POST("workouts/plan/rest")
    suspend fun planRest(@Body body: PlanRestRequest): Response<TaskStatusDto>

    @POST("workouts/plan/copy")
    suspend fun planCopy(@Body body: PlanCopyRequest): Response<TaskStatusDto>

    @POST("workouts/plan/{id}/remove")
    suspend fun planRemove(@Path("id") id: String): Response<DeleteAckDto>

    @GET("workouts/plan/options")
    suspend fun planOptions(): Response<PlanOptionsDto>

    @POST("workouts/plan/add-pool")
    suspend fun planAddPool(@Body body: AddPoolRequest): Response<TaskStatusDto>

    @POST("workouts/plan/add-hiit")
    suspend fun planAddHiit(@Body body: AddHiitRequest): Response<TaskStatusDto>

    @GET("workouts/rotation")
    suspend fun rotation(): Response<RotationDto>

    @POST("workouts/rotation/start")
    suspend fun rotationStart(): Response<TaskStatusDto>

    @POST("workouts/rotation/stop")
    suspend fun rotationStop(): Response<TaskStatusDto>

    @POST("workouts/rotation/rest-days")
    suspend fun rotationRestDays(@Body body: RestDaysRequest): Response<TaskStatusDto>

    @POST("workouts/rotation/add-slot")
    suspend fun rotationAddSlot(@Body body: AddSlotRequest): Response<TaskStatusDto>

    @POST("workouts/rotation/remove-slot")
    suspend fun rotationRemoveSlot(@Body body: SlotIdRequest): Response<TaskStatusDto>

    @POST("workouts/rotation/move-slot")
    suspend fun rotationMoveSlot(@Body body: MoveSlotRequest): Response<TaskStatusDto>

    @POST("workouts/log-custom")
    suspend fun logCustom(@Body body: CustomLogRequest): Response<WorkoutAckDto>

    @POST("workouts/sessions/{id}/delete")
    suspend fun deleteWorkoutSession(@Path("id") id: String): Response<DeleteAckDto>

    @GET("reading")
    suspend fun reading(): Response<ReadingDto>

    @POST("reading/plan")
    suspend fun createReadingPlan(@Body body: PersonalPlanRequest): Response<TaskStatusDto>

    @POST("reading/plan/preview")
    suspend fun previewReadingPlan(@Body body: PersonalPlanRequest): Response<com.kairos.app.data.remote.dto.PlanPreviewDto>

    @POST("reading/plan/delete")
    suspend fun deleteReadingPlan(): Response<TaskStatusDto>

    @POST("reading/mark")
    suspend fun markReading(@Body body: MarkReadingRequest): Response<TaskStatusDto>

    @POST("reading/books")
    suspend fun saveReadingBook(@Body body: SaveBookRequest): Response<TaskStatusDto>

    @POST("reading/books/bulk")
    suspend fun saveReadingBooks(@Body body: SaveBooksRequest): Response<TaskStatusDto>

    @GET("chores")
    suspend fun chores(): Response<ChoresDto>

    @GET("calendar")
    suspend fun calendar(
        @Query("view") view: String?,
        @Query("date") date: String?,
    ): Response<CalendarDto>

    @POST("calendar/prefs")
    suspend fun saveCalendarPrefs(@Body body: CalendarPrefsRequest): Response<TaskStatusDto>

    @POST("calendar/event")
    suspend fun createEvent(@Body body: CreateEventRequest): Response<TaskStatusDto>

    @POST("calendar/event/delete")
    suspend fun deleteEvent(@Body body: DeleteEventRequest): Response<TaskStatusDto>

    @POST("calendar/event/update")
    suspend fun updateEvent(@Body body: UpdateEventRequest): Response<TaskStatusDto>

    @POST("chores/claim")
    suspend fun claimChore(@Body body: ClaimChoreRequest): Response<TaskStatusDto>

    @POST("sport/confirm")
    suspend fun sportConfirm(@Body body: com.kairos.app.data.remote.dto.SportAnswerRequest): Response<Unit>

    @POST("sport/decline")
    suspend fun sportDecline(@Body body: com.kairos.app.data.remote.dto.SportAnswerRequest): Response<Unit>

    @POST("chores/always-open")
    suspend fun completeAlwaysOpen(@Body body: AlwaysOpenRequest): Response<TaskStatusDto>

    @GET("devices")
    suspend fun devices(): Response<DevicesResponse>

    @POST("devices/{id}/revoke")
    suspend fun revokeDevice(@Path("id") id: String): Response<RevokeDeviceResponse>

    @GET("money")
    suspend fun money(@Query("user") user: String? = null): Response<com.kairos.app.data.remote.dto.MoneyDto>

    @POST("money/entry")
    suspend fun addMoneyEntry(@Body body: com.kairos.app.data.remote.dto.AddMoneyRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("money/rewards/approve-month")
    suspend fun approveRewardMonth(@Body body: com.kairos.app.data.remote.dto.RewardApproveMonthRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("money/rewards/approve-base")
    suspend fun approveRewardBase(@Body body: com.kairos.app.data.remote.dto.RewardApproveBaseRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("money/approve")
    suspend fun approveMoney(@Body body: com.kairos.app.data.remote.dto.MoneyIdRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("money/unapprove")
    suspend fun unapproveMoney(@Body body: com.kairos.app.data.remote.dto.MoneyIdRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("money/approve-all")
    suspend fun approveAllMoney(): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("money/update")
    suspend fun updateMoney(@Body body: com.kairos.app.data.remote.dto.UpdateMoneyRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("money/delete")
    suspend fun deleteMoney(@Body body: com.kairos.app.data.remote.dto.MoneyIdRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("money/starting")
    suspend fun setStartingFunds(@Body body: com.kairos.app.data.remote.dto.StartingFundsRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @GET("books")
    suspend fun books(): Response<com.kairos.app.data.remote.dto.BooksDto>

    @POST("books/add")
    suspend fun addBook(@Body body: com.kairos.app.data.remote.dto.AddBookRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("books/log")
    suspend fun logBook(@Body body: com.kairos.app.data.remote.dto.LogBookRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("books/update")
    suspend fun updateBook(@Body body: com.kairos.app.data.remote.dto.UpdateBookRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("books/finish")
    suspend fun finishBook(@Body body: com.kairos.app.data.remote.dto.BookFinishRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("books/shelf")
    suspend fun shelfBook(@Body body: com.kairos.app.data.remote.dto.BookShelfRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>

    @POST("books/delete")
    suspend fun deleteBook(@Body body: com.kairos.app.data.remote.dto.BookIdRequest): Response<com.kairos.app.data.remote.dto.OkStatusDto>
}
