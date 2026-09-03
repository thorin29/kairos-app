package com.kairos.app.data.remote

import com.kairos.app.data.remote.dto.DashboardDto
import com.kairos.app.data.remote.dto.EnrollRequest
import com.kairos.app.data.remote.dto.EnrollResponse
import com.kairos.app.data.remote.dto.LoginRequest
import com.kairos.app.data.remote.dto.LoginResponse
import com.kairos.app.data.remote.dto.MetaDto
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.data.remote.dto.RevokeResponse
import com.kairos.app.data.remote.dto.TaskStatusDto
import com.kairos.app.data.remote.dto.TokenResponse
import com.kairos.app.data.remote.dto.WorkoutAckDto
import com.kairos.app.data.remote.dto.WorkoutDateRequest
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
}
