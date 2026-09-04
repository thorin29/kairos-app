package com.kairos.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes for the /api/v1 surface, matching docs/API.md exactly. Success
 * responses are the raw object (no envelope); only errors are wrapped. The
 * client tolerates unknown fields (configured on the Json instance), so the
 * server adding fields within v1 never breaks us.
 */

@Serializable
data class MetaDto(
    val apiVersion: Int,
    val appVersion: String,
    val minClient: Int,
)

@Serializable
data class PersonDto(
    val id: String,
    val name: String,
    val shortName: String,
    val avatarUrl: String? = null,
    val avatarPosition: String? = null,
    val avatarIcon: String? = null,
    val color: String? = null,
    val role: String,
    val kind: String,
)

@Serializable
data class EnrollRequest(
    val code: String,
    val deviceName: String? = null,
    val loginToken: String? = null,
)

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val loginToken: String,
    val person: PersonDto? = null,
)

@Serializable
data class ReauthRequest(
    val password: String,
)

@Serializable
data class ReauthResponse(
    val person: PersonDto? = null,
)

@Serializable
data class DeviceDto(
    val id: String,
    val name: String? = null,
    val enrolledAt: String,
    val lastSeenAt: String? = null,
    val status: String,
    val current: Boolean = false,
)

@Serializable
data class DevicesResponse(
    val devices: List<DeviceDto> = emptyList(),
)

@Serializable
data class RevokeDeviceResponse(
    val id: String,
    val revoked: Boolean = false,
)

@Serializable
data class EnrollResponse(
    val token: String,
    val expiresAt: String,
    val person: PersonDto,
)

@Serializable
data class TokenResponse(
    val token: String,
    val expiresAt: String,
)

@Serializable
data class RevokeResponse(
    val revoked: Boolean,
)

// --- Dashboard (GET /dashboard) ---

@Serializable
data class DashboardDto(
    val date: String,
    val percent: Int? = null,
    val categories: List<CategoryBarDto> = emptyList(),
    val overdue: List<TaskDto> = emptyList(),
    val groups: List<TaskGroupDto> = emptyList(),
    val personalReading: PersonalReadingDto? = null,
    val upForGrabs: List<UpForGrabsDto> = emptyList(),
    val alwaysOpen: List<AlwaysOpenDashDto> = emptyList(),
)

@Serializable
data class UpForGrabsDto(
    val id: String = "",
    val title: String = "",
    val isShared: Boolean = false,
    val releasedByName: String = "",
    val isOverdue: Boolean = false,
    val dueDate: String = "",
)

@Serializable
data class AlwaysOpenDashDto(
    val id: String = "",
    val title: String = "",
    val readyAtMs: Long? = null,
    val myCount: Int = 0,
)

@Serializable
data class PersonalReadingDto(
    val passage: String = "",
    val read: Boolean = false,
)

@Serializable
data class CategoryBarDto(
    val category: String,
    val label: String,
    val total: Int,
    val complete: Int,
    val overdue: Int,
    val percent: Int,
)

@Serializable
data class TaskGroupDto(
    val category: String,
    val label: String,
    val items: List<TaskDto> = emptyList(),
)

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val category: String,
    val status: String,
    val dueDate: String,
    val subtitle: String? = null,
    val isOverdue: Boolean = false,
    val stale: Boolean = false,
    val locked: Boolean = false,
    val isWorkout: Boolean = false,
    val completable: Boolean = false,
    val test: TaskTestDto? = null,
)

@Serializable
data class TaskTestDto(
    val score: Int? = null,
    val scoreMax: Int = 100,
)

/** Response of the task complete/uncomplete endpoints. */
@Serializable
data class TaskStatusDto(
    val id: String = "",
    val status: String = "",
)

/** Body for the day-level workout endpoints. */
@Serializable
data class WorkoutDateRequest(
    val date: String,
)

/** Response of the workout complete/uncomplete/rest endpoints. */
@Serializable
data class WorkoutAckDto(
    val date: String,
    val status: String,
)

// --- Detailed workout logging (planned workouts) ---

@Serializable
data class WorkoutPlanDto(
    val date: String,
    val loggable: Boolean = false,
    val plannedWorkoutId: String? = null,
    val name: String? = null,
    val exercises: List<PlannedMovementDto> = emptyList(),
)

@Serializable
data class PlannedMovementDto(
    val poolExerciseId: String,
    val name: String,
    val metric: String,
    val unit: String,
    val value: Double? = null,
)

@Serializable
data class WorkoutLogRequest(
    val date: String,
    val plannedWorkoutId: String,
    val entries: List<PlannedEntryDto>,
)

@Serializable
data class PlannedEntryDto(
    val poolExerciseId: String,
    val metric: String,
    val value: Double,
    val unit: String,
)

// --- Workout history + progress ---

@Serializable
data class WorkoutProgressDto(
    val series: List<ProgressSeriesDto> = emptyList(),
    val defaultId: String? = null,
    val history: List<WorkoutHistoryDto> = emptyList(),
)

@Serializable
data class ProgressSeriesDto(
    val poolExerciseId: String,
    val name: String,
    val unit: String,
    val points: List<GraphPointDto> = emptyList(),
)

@Serializable
data class GraphPointDto(
    val date: String,
    val value: Double,
)


// --- This week + browse ---


// --- Weekly plan (Edit plan) ---


// --- Add-workout picker (plan options) ---


// --- Rotation ---

@Serializable
data class RotationSlotDto(
    val id: String,
    val position: Int = 0,
    val name: String = "",
    val label: String = "",
    val isRest: Boolean = false,
)

@Serializable
data class RotationPreviewDto(
    val date: String,
    val label: String = "",
    val rest: Boolean = false,
)

@Serializable
data class RotationDto(
    val active: Boolean = false,
    val anchorISO: String? = null,
    val restMask: Int = 0,
    val slots: List<RotationSlotDto> = emptyList(),
    val preview: List<RotationPreviewDto> = emptyList(),
)

@Serializable
data class RestDaysRequest(val mask: Int)

@Serializable
data class AddSlotRequest(
    val name: String,
    val category: String? = null,
    val muscleGroup: String? = null,
    val isRest: Boolean = false,
)

@Serializable
data class SlotIdRequest(val slotId: String)

@Serializable
data class MoveSlotRequest(val slotId: String, val dir: Int)

@Serializable
data class PlanMetricDto(val key: String, val label: String)

@Serializable
data class PlanCategoryDto(
    val key: String,
    val label: String,
    val kind: String,
    val metrics: List<PlanMetricDto> = emptyList(),
    val defaultMetric: String = "",
)

@Serializable
data class MuscleGroupDto(val key: String, val label: String)

@Serializable
data class PlanExerciseDto(
    val id: String,
    val name: String,
    val category: String,
    val muscleGroup: String? = null,
)

@Serializable
data class HiitOptionDto(val id: String, val name: String, val personal: Boolean = false)

@Serializable
data class PlanOptionsDto(
    val categories: List<PlanCategoryDto> = emptyList(),
    val muscleGroups: List<MuscleGroupDto> = emptyList(),
    val exercises: List<PlanExerciseDto> = emptyList(),
    val hiitWorkouts: List<HiitOptionDto> = emptyList(),
)

@Serializable
data class AddPoolExercise(
    val poolExerciseId: String,
    val tracked: Boolean = true,
    val metric: String? = null,
)

@Serializable
data class AddPoolRequest(
    val day: Int,
    val category: String,
    val muscleGroup: String? = null,
    val exercises: List<AddPoolExercise> = emptyList(),
)

@Serializable
data class AddHiitRequest(val day: Int, val hiitWorkoutId: String)

@Serializable
data class PlanWorkoutDto(
    val id: String,
    val name: String,
    val isRest: Boolean = false,
    val detail: String = "",
)

@Serializable
data class PlanDayDto(
    val day: Int,
    val workouts: List<PlanWorkoutDto> = emptyList(),
)

@Serializable
data class PlanResponse(val days: List<PlanDayDto> = emptyList())

@Serializable
data class PlanRestRequest(val day: Int)

@Serializable
data class PlanCopyRequest(val from: Int, val to: Int)

@Serializable
data class WeeklyActivityDto(
    val label: String,
    val count: Int = 0,
    val detail: String = "",
)

@Serializable
data class WeekResponse(val items: List<WeeklyActivityDto> = emptyList())

@Serializable
data class BrowseWorkoutDto(
    val id: String,
    val name: String,
    val type: String = "",
    val typeLabel: String = "",
    val personal: Boolean = false,
    val heroWod: Boolean = false,
    val detail: String = "",
)

@Serializable
data class BrowseResponse(val items: List<BrowseWorkoutDto> = emptyList())

@Serializable
data class DeleteAckDto(
    val id: String = "",
    val deleted: Boolean = false,
)


// --- Log a different workout (custom) ---

@Serializable
data class WorkoutPoolDto(
    val categories: List<LogCategoryDto> = emptyList(),
    val exercises: List<PoolExerciseDto> = emptyList(),
)

@Serializable
data class LogCategoryDto(
    val key: String,
    val label: String,
    val isPool: Boolean = false,
    val load: Boolean = false,
    val metrics: List<MetricOptionDto> = emptyList(),
)

@Serializable
data class MetricOptionDto(
    val key: String,
    val label: String,
    val unit: String = "",
)

@Serializable
data class PoolExerciseDto(
    val id: String,
    val name: String,
    val category: String,
)

@Serializable
data class CustomLogRequest(
    val date: String,
    val category: String? = null,
    val poolExerciseId: String? = null,
    val metric: String,
    val value: Double,
    val unit: String = "",
    val load: Double? = null,
    val notes: String? = null,
)

@Serializable
data class WorkoutHistoryDto(
    val id: String,
    val date: String,
    val label: String,
    val result: String = "",
    val isRest: Boolean = false,
)


/** Error envelope: { "error": { code, message, fields? } }. */
@Serializable
data class ApiErrorEnvelope(
    val error: ApiErrorBody,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val fields: Map<String, String>? = null,
)

// --- Bible reading (GET /reading + reading writes) ---

@Serializable
data class ReadingGroupDto(
    val label: String = "",
    val chapters: Int = 0,
    val read: Int = 0,
    val percent: Int = 0,
)

@Serializable
data class ReadingStatsDto(
    val totalChapters: Int = 0,
    val readChapters: Int = 0,
    val wholeBible: Boolean = false,
    val ot: ReadingGroupDto = ReadingGroupDto(),
    val nt: ReadingGroupDto = ReadingGroupDto(),
    val groups: List<ReadingGroupDto> = emptyList(),
)

@Serializable
data class ReadingCardDto(
    val iso: String = "",
    val passage: String = "",
    val label: String = "",
)

@Serializable
data class ReadingFamilyDto(
    val havePlan: Boolean = false,
    val cards: List<ReadingCardDto> = emptyList(),
    val todayIndex: Int = 0,
    val remaining: Int = 0,
    val lastDayISO: String? = null,
    val stats: ReadingStatsDto = ReadingStatsDto(),
)

@Serializable
data class PersonalPlanDayDto(
    val iso: String = "",
    val label: String = "",
    val passage: String = "",
    val read: Boolean = false,
)

@Serializable
data class PersonalPlanDto(
    val id: String = "",
    val name: String = "",
    val remaining: Int = 0,
    val days: List<PersonalPlanDayDto> = emptyList(),
)

@Serializable
data class ReadingPersonalDto(
    val color: String = "#0f5c63",
    val stats: ReadingStatsDto = ReadingStatsDto(),
    val plan: PersonalPlanDto? = null,
    val readKeys: List<String> = emptyList(),
)

@Serializable
data class ReadingDto(
    val today: String = "",
    val family: ReadingFamilyDto = ReadingFamilyDto(),
    val personal: ReadingPersonalDto? = null,
)

@Serializable
data class PersonalPlanRequest(
    val name: String,
    val bookNames: List<String>,
    val startISO: String,
    val chaptersPerDay: Int,
)

@Serializable
data class MarkReadingRequest(
    val passage: String,
    val read: Boolean,
)

@Serializable
data class SaveBookRequest(
    val bookName: String,
    val chapters: List<Int>,
)

@Serializable
data class SaveBooksRequest(
    val bookNames: List<String>,
    val read: Boolean,
)

// --- Chores overview (GET /chores, read-only) ---

@Serializable
data class ChorePauseDto(
    val name: String = "",
    val startISO: String = "",
    val endISO: String = "",
)

@Serializable
data class ChoreStatsDto(
    val due: Int = 0,
    val done: Int = 0,
    val open: Int = 0,
    val missed: Int = 0,
)

@Serializable
data class RotationDayDto(
    val dayOfWeek: Int = 0,
    val label: String = "",
    val chore: String = "",
    val complete: Boolean = false,
    val pastDue: Boolean = false,
)

@Serializable
data class ChorePersonDto(
    val person: PersonDto? = null,
    val stats: ChoreStatsDto = ChoreStatsDto(),
    val rotation: List<RotationDayDto> = emptyList(),
)

@Serializable
data class AlwaysOpenChoreDto(
    val id: String = "",
    val title: String = "",
    val today: Int = 0,
    val week: Int = 0,
)

@Serializable
data class PoolChoreDto(
    val id: String = "",
    val title: String = "",
    val intervalDays: Int = 0,
    val isPaused: Boolean = false,
    val nextDueISO: String? = null,
    val outstanding: Boolean = false,
    val claimedByName: String? = null,
    val alwaysOpen: Boolean = false,
    val cooldownMinutes: Int = 0,
    val effort: Int = 0,
    val effortLocked: Boolean = false,
)

@Serializable
data class PoolTallyDto(
    val name: String = "",
    val color: String = "#64748b",
    val count: Int = 0,
)

@Serializable
data class PoolBlockDto(
    val chores: List<PoolChoreDto> = emptyList(),
    val tally: List<PoolTallyDto> = emptyList(),
)

@Serializable
data class ChoresDto(
    val today: String = "",
    val scope: String = "self",
    val pause: ChorePauseDto? = null,
    val people: List<ChorePersonDto> = emptyList(),
    val alwaysOpen: List<AlwaysOpenChoreDto> = emptyList(),
    val pool: PoolBlockDto = PoolBlockDto(),
)

@Serializable
data class ClaimChoreRequest(
    val taskId: String,
)

@Serializable
data class AlwaysOpenRequest(
    val choreId: String,
)

// --- Calendar (GET /calendar, read-only Phase 1) ---

@Serializable
data class CalEventDto(
    val id: String = "",
    val eventId: String = "",
    val title: String = "",
    val location: String? = null,
    val dayISO: String = "",
    val allDay: Boolean = false,
    val startMin: Int = 0,
    val endMin: Int = 0,
    val timeLabel: String = "",
    val color: String = "#64748b",
    val memberColors: List<String> = emptyList(),
    val isFamily: Boolean = false,
    val shade: Boolean = false,
    val kind: String = "",
    val ownerName: String = "",
    val calendarName: String? = null,
    val recurring: Boolean = false,
    val recurLabel: String? = null,
    val external: Boolean = false,
    val schoolType: String? = null,
    val schoolClassName: String? = null,
)

@Serializable
data class CalendarDto(
    val today: String = "",
    val view: String = "agenda",
    val date: String = "",
    val heading: String = "",
    val rangeDays: List<String> = emptyList(),
    val prevDate: String = "",
    val nextDate: String = "",
    val events: List<CalEventDto> = emptyList(),
    val nowColor: String = "#ef4444",
    val monthDays: List<String> = emptyList(),
    val monthDots: Map<String, List<String>> = emptyMap(),
)
