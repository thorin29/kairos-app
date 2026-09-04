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

    @POST("chores/claim")
    suspend fun claimChore(@Body body: ClaimChoreRequest): Response<TaskStatusDto>

    @POST("chores/always-open")
    suspend fun completeAlwaysOpen(@Body body: AlwaysOpenRequest): Response<TaskStatusDto>

    @GET("devices")
    suspend fun devices(): Response<DevicesResponse>

    @POST("devices/{id}/revoke")
    suspend fun revokeDevice(@Path("id") id: String): Response<RevokeDeviceResponse>
}
