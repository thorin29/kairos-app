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
data class LoginRequest(
    val identifier: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    // Legacy proof field. The app never reads it; kept optional so the server can
    // stop sending it without breaking this client's response parsing.
    val loginToken: String? = null,
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
data class JoinCheckRequest(val token: String)

@Serializable
data class JoinCheckResponse(
    val valid: Boolean = false,
    val hasPassword: Boolean = false,
    val name: String = "",
    val purpose: String = "join",
)

@Serializable
data class ForgotRequest(val identifier: String)

@Serializable
data class JoinRequest(
    val token: String,
    val password: String,
    val deviceName: String? = null,
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
    val money: DashboardMoneyDto? = null,
    val categories: List<CategoryBarDto> = emptyList(),
    val overdue: List<TaskDto> = emptyList(),
    val groups: List<TaskGroupDto> = emptyList(),
    val personalReading: PersonalReadingDto? = null,
    val upForGrabs: List<UpForGrabsDto> = emptyList(),
    val alwaysOpen: List<AlwaysOpenDashDto> = emptyList(),
    val schedule: List<ScheduleItemDto> = emptyList(),
    val sportPrompts: List<SportPromptDto> = emptyList(),
)

@Serializable
data class DashboardMoneyDto(
    val pendingApprovals: Int = 0,
    val rewardMonths: Int = 0,
)

@Serializable
data class SportPromptDto(
    val eventId: String = "",
    val title: String = "",
)

@Serializable
data class SportAnswerRequest(
    val eventId: String,
    val dateISO: String? = null,
)

@Serializable
data class ScheduleItemDto(
    val title: String = "",
    val allDay: Boolean = false,
    val timeLabel: String = "",
    val startMin: Int = 0,
    val color: String = "#64748b",
    val ownerName: String = "",
    val location: String? = null,
    val notes: String? = null,
    val didNotAttend: Boolean = false,
    val attendees: List<AttendeeDto> = emptyList(),
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
    val date: String = "",
    val status: String = "",
    val conflict: WorkoutConflictDto? = null,
)

@Serializable
data class WorkoutConflictDto(
    val name: String,
    val summary: String,
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
    val replace: Boolean = false,
    val detectConflict: Boolean = false,
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

// --- Personal workout builder + mutations ---

@Serializable
data class WorkoutTypeOptionDto(val key: String, val label: String)

@Serializable
data class BuilderMovementDto(val id: String, val name: String)

@Serializable
data class SharePersonDto(val id: String, val name: String)

@Serializable
data class MyWorkoutMovementDto(
    val poolExerciseId: String,
    val reps: Int? = null,
    val distance: Double? = null,
    val weight: Double? = null,
)

@Serializable
data class MyWorkoutDto(
    val id: String,
    val name: String,
    val type: String = "",
    val capSec: Int? = null,
    val notes: String? = null,
    val movements: List<MyWorkoutMovementDto> = emptyList(),
)

@Serializable
data class WorkoutBuilderDto(
    val types: List<WorkoutTypeOptionDto> = emptyList(),
    val movements: List<BuilderMovementDto> = emptyList(),
    val people: List<SharePersonDto> = emptyList(),
    val myWorkouts: List<MyWorkoutDto> = emptyList(),
    val myExercises: List<BuilderMovementDto> = emptyList(),
)

@Serializable
data class PersonalMovementReq(
    val poolExerciseId: String,
    val reps: Int? = null,
    val distance: Double? = null,
    val weight: Double? = null,
)

@Serializable
data class CreatePersonalWorkoutRequest(
    val name: String,
    val type: String,
    val capSec: Int? = null,
    val notes: String? = null,
    val movements: List<PersonalMovementReq> = emptyList(),
)

@Serializable
data class UpdatePersonalWorkoutRequest(
    val workoutId: String,
    val name: String,
    val type: String,
    val capSec: Int? = null,
    val notes: String? = null,
    val movements: List<PersonalMovementReq> = emptyList(),
)

@Serializable
data class ShareWorkoutRequest(val workoutId: String, val targetUserId: String)

@Serializable
data class WorkoutIdRequest(val workoutId: String)

@Serializable
data class AddMovementRequest(val category: String, val name: String)

@Serializable
data class RenameMovementRequest(val movementId: String, val name: String)

@Serializable
data class MovementIdRequest(val movementId: String)

@Serializable
data class AddMovementResponse(val status: String = "", val id: String? = null)

@Serializable
data class OkStatusDto(val status: String = "")

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
    val hiitWorkouts: List<HiitLogOptionDto> = emptyList(),
    val muscleGroups: List<MuscleGroupDto> = emptyList(),
)

@Serializable
data class HiitLogOptionDto(
    val id: String,
    val name: String,
    val hero: Boolean = false,
    val resultMetric: String = "REPS",
    val resultLabel: String = "Result",
    val resultUnit: String = "",
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
    val muscleGroup: String? = null,
)

@Serializable
data class CustomLogRequest(
    val date: String,
    val category: String? = null,
    val poolExerciseId: String? = null,
    val hiitWorkoutId: String? = null,
    val metric: String,
    val value: Double,
    val unit: String = "",
    val load: Double? = null,
    val notes: String? = null,
    val replace: Boolean = false,
    val detectConflict: Boolean = false,
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
    val havePlan: Boolean = false,
    val cards: List<ReadingCardDto> = emptyList(),
    val todayIndex: Int = 0,
    val remaining: Int = 0,
    val lastDayISO: String? = null,
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
    val endISO: String? = null,
)

@Serializable
data class PlanPreviewDto(
    val dayCount: Int = 0,
    val totalChapters: Int = 0,
    val startISO: String? = null,
    val endISO: String? = null,
    val days: List<PlanPreviewDayDto> = emptyList(),
)

@Serializable
data class PlanPreviewDayDto(
    val iso: String = "",
    val passage: String = "",
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
data class AttendeeDto(val name: String = "", val state: String = "")

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
    val whoLabel: String = "",
    val notes: String? = null,
    val didNotAttend: Boolean = false,
    val attendees: List<AttendeeDto> = emptyList(),
    val ownerId: String? = null,
    val eventTypeId: String? = null,
    val reminders: List<Int> = emptyList(),
    val reminderUserIds: List<String> = emptyList(),
    val memberIds: List<String> = emptyList(),
    val calendarName: String? = null,
    val recurring: Boolean = false,
    val recurLabel: String? = null,
    val external: Boolean = false,
    val schoolType: String? = null,
    val schoolClassName: String? = null,
)

@Serializable
data class CalOptionPersonDto(
    val id: String = "",
    val name: String = "",
    val color: String = "#64748b",
)

@Serializable
data class CalOptionSubDto(
    val id: String = "",
    val name: String = "",
    val ownerName: String? = null,
    val color: String = "#64748b",
)

@Serializable
data class CalEventTypeDto(
    val id: String = "",
    val name: String = "",
    val color: String = "#64748b",
    val defaultMinutes: Int? = null,
    val defaultReminder: Int? = null,
)

@Serializable
data class CalOptionsDto(
    val people: List<CalOptionPersonDto> = emptyList(),
    val subscriptions: List<CalOptionSubDto> = emptyList(),
    val shownPeople: List<String> = emptyList(),
    val shownSubs: List<String> = emptyList(),
    val showFamily: Boolean = false,
    val showSchoolWork: Boolean = false,
    val canManageFamily: Boolean = false,
    val eventTypes: List<CalEventTypeDto> = emptyList(),
    val colorPrefs: ColorPrefsDto = ColorPrefsDto(),
    val meColor: String = "#2563eb",
    val holidaySystemColor: String = "#0f5c63",
    val familySystemColor: String = "#0f5c63",
    val nowSystemColor: String = "#dc2626",
)

@Serializable
data class ColorPrefsDto(
    val personalizeColors: Boolean = false,
    val othersMode: String = "own",
    val othersColor: String? = null,
    val holidayColor: String? = null,
    val familyColor: String? = null,
    val nowColor: String? = null,
    val kindColors: Map<String, String> = emptyMap(),
    val eventTypeColors: Map<String, String> = emptyMap(),
    val subColors: Map<String, String> = emptyMap(),
)

@Serializable
data class CalendarDto(
    val today: String = "",
    val view: String = "agenda",
    val date: String = "",
    val heading: String = "",
    val timezone: String = "UTC",
    val rangeDays: List<String> = emptyList(),
    val prevDate: String = "",
    val nextDate: String = "",
    val events: List<CalEventDto> = emptyList(),
    val nowColor: String = "#ef4444",
    val monthDays: List<String> = emptyList(),
    val monthDots: Map<String, List<String>> = emptyMap(),
    val options: CalOptionsDto = CalOptionsDto(),
)

@Serializable
data class CalendarPrefsRequest(
    val shownPeople: List<String>? = null,
    val shownSubs: List<String>? = null,
    val showFamily: Boolean? = null,
    val showSchoolWork: Boolean? = null,
    val view: String? = null,
    val personalizeColors: Boolean? = null,
    val othersMode: String? = null,
    val othersColor: String? = null,
    val holidayColor: String? = null,
    val familyColor: String? = null,
    val nowColor: String? = null,
    val kindColors: Map<String, String>? = null,
    val eventTypeColors: Map<String, String>? = null,
    val subColors: Map<String, String>? = null,
)

@Serializable
data class CreateEventRequest(
    val title: String,
    val allDay: Boolean,
    val date: String,
    val start: String? = null,
    val end: String? = null,
    val endDate: String? = null,
    val location: String? = null,
    val timezone: String? = null,
    val repeat: String? = null,
    val isFamily: Boolean? = null,
    val kind: String? = null,
    val eventTypeId: String? = null,
    val participants: List<String>? = null,
    val reminders: List<Int>? = null,
    val reminderUserIds: List<String>? = null,
)

@Serializable
data class DeleteEventRequest(
    val eventId: String,
    val scope: String? = null,
    val occurrenceISO: String? = null,
    val isFamily: Boolean? = null,
    val kind: String? = null,
    val eventTypeId: String? = null,
    val participants: List<String>? = null,
)

@Serializable
data class UpdateEventRequest(
    val eventId: String,
    val title: String,
    val allDay: Boolean,
    val date: String,
    val start: String? = null,
    val end: String? = null,
    val endDate: String? = null,
    val location: String? = null,
    val timezone: String? = null,
    val scope: String? = null,
    val occurrenceISO: String? = null,
    val isFamily: Boolean? = null,
    val kind: String? = null,
    val eventTypeId: String? = null,
    val participants: List<String>? = null,
    val reminders: List<Int>? = null,
    val reminderUserIds: List<String>? = null,
)

// ---- Money (GET /money, POST /money/entry, reward approvals) ----

@Serializable
data class MoneyDto(
    val today: String = "",
    val participants: List<MoneyParticipantDto> = emptyList(),
    val selectedId: String? = null,
    val rows: List<MoneyRowDto> = emptyList(),
    val roster: List<PersonDto> = emptyList(),
    val frequentPayments: List<String> = emptyList(),
    val isAdmin: Boolean = false,
    val canApproveRewards: Boolean = false,
    val rewardMonths: List<MoneyRewardMonthDto> = emptyList(),
    val pendingApprovals: List<MoneyPendingDto> = emptyList(),
)

@Serializable
data class MoneyPendingDto(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val date: String = "",
    val direction: String = "DEPOSIT",
    val category: String? = null,
    val detail: String? = null,
    val amountCents: Long = 0,
    val status: String = "PENDING",
    val kind: String = "MANUAL",
)

@Serializable
data class MoneyParticipantDto(
    val person: PersonDto? = null,
    val balanceCents: Long = 0,
)

@Serializable
data class MoneyRowDto(
    val id: String = "",
    val date: String = "",
    val direction: String = "DEPOSIT",
    val category: String? = null,
    val detail: String? = null,
    val amountCents: Long = 0,
    val status: String = "PENDING",
    val kind: String = "MANUAL",
)

@Serializable
data class MoneyRewardMonthDto(
    val periodKey: String = "",
    val label: String = "",
    val bonusAvailable: Boolean = false,
    val bonusCents: Long = 0,
    val completers: List<MoneyRewardCompleterDto> = emptyList(),
)

@Serializable
data class MoneyRewardCompleterDto(
    val userId: String = "",
    val name: String = "",
    val baseCents: Long = 0,
    val needsBase: Boolean = false,
)

@Serializable
data class AddMoneyRequest(
    val userId: String,
    val direction: String,
    val amountCents: Long,
    val category: String? = null,
    val detail: String? = null,
    val date: String? = null,
)

@Serializable
data class RewardApproveMonthRequest(
    val periodKey: String,
)

@Serializable
data class RewardApproveBaseRequest(
    val userId: String,
    val periodKey: String,
)

@Serializable
data class MoneyIdRequest(
    val id: String,
)

@Serializable
data class UpdateMoneyRequest(
    val id: String,
    val direction: String,
    val amountCents: Long,
    val category: String? = null,
    val detail: String? = null,
    val date: String? = null,
)

@Serializable
data class StartingFundsRequest(
    val userId: String,
    val amountCents: Long,
    val date: String? = null,
)

// ---- Reading (leisure book-tracker; self-only) ----

@Serializable
data class BooksDto(
    val books: List<BookDto> = emptyList(),
)

@Serializable
data class BookDto(
    val id: String = "",
    val title: String = "",
    val author: String? = null,
    val unit: String = "PAGES",
    val length: Int = 0,
    val pages: Int? = null,
    val chapters: Int? = null,
    val position: Int = 0,
    val read: Int = 0,
    val finished: Boolean = false,
    val shelved: Boolean = false,
)

@Serializable
data class AddBookRequest(
    val title: String,
    val author: String? = null,
    val pages: Int? = null,
    val chapters: Int? = null,
)

@Serializable
data class LogBookRequest(
    val id: String,
    val page: Int,
)

@Serializable
data class UpdateBookRequest(
    val id: String,
    val title: String? = null,
    val author: String? = null,
    val pages: Int? = null,
    val chapters: Int? = null,
    val position: Int? = null,
)

@Serializable
data class BookFinishRequest(
    val id: String,
    val finished: Boolean,
)

@Serializable
data class BookShelfRequest(
    val id: String,
    val shelved: Boolean,
)

@Serializable
data class BookIdRequest(
    val id: String,
)

// ---- Groceries (shared family shopping list) ----

@Serializable
data class GroceryStoreDto(
    val id: String = "",
    val name: String = "",
    val icon: String = "",
)

@Serializable
data class GroceryPersonDto(
    val id: String = "",
    val name: String = "",
    val color: String? = null,
    val avatarPath: String? = null,
    val avatarPosition: String? = null,
)

@Serializable
data class GroceryLineDto(
    val id: String = "",
    val name: String = "",
    val icon: String = "",
    val storeId: String = "",
    val note: String? = null,
    val purchased: Boolean = false,
    val assignee: GroceryPersonDto? = null,
)

@Serializable
data class GroceryTripDto(
    val id: String = "",
    val storeId: String = "",
    val shopper: GroceryPersonDto = GroceryPersonDto(),
    val items: List<GroceryLineDto> = emptyList(),
    val total: Int = 0,
    val got: Int = 0,
)

@Serializable
data class GroceryCatalogDto(
    val id: String = "",
    val name: String = "",
    val icon: String = "",
    val defaultStoreId: String? = null,
)

@Serializable
data class GroceriesDto(
    val stores: List<GroceryStoreDto> = emptyList(),
    val saved: List<GroceryLineDto> = emptyList(),
    val trips: List<GroceryTripDto> = emptyList(),
    val catalog: List<GroceryCatalogDto> = emptyList(),
    val roster: List<GroceryPersonDto> = emptyList(),
)

@Serializable
data class AddGroceryRequest(
    val name: String,
    val storeId: String,
    val note: String? = null,
)

@Serializable
data class AddCatalogRequest(
    val catalogId: String,
    val storeId: String? = null,
)

@Serializable
data class GroceryIdRequest(
    val id: String,
)

@Serializable
data class GroceryPurchasedRequest(
    val id: String,
    val purchased: Boolean,
)

@Serializable
data class StartTripRequest(
    val storeId: String,
    val shopperId: String? = null,
)

@Serializable
data class StartTripDto(
    val ok: Boolean = false,
    val reason: String? = null,
)

@Serializable
data class CompleteTripRequest(
    val tripId: String,
)

@Serializable
data class MoveGroceryRequest(
    val id: String,
    val storeId: String,
)

@Serializable
data class CharacterDto(
    val seasonName: String = "",
    val familyGoal: FamilyGoalDto = FamilyGoalDto(),
    val className: String = "",
    val level: CharLevelDto = CharLevelDto(),
    val season: CharSeasonDto = CharSeasonDto(),
    val stats: List<CharStatDto> = emptyList(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val milestones: List<Int> = emptyList(),
    val perfectWeeks: Int = 0,
    val bestWeekPct: Double? = null,
    val masteries: List<CharMasteryDto> = emptyList(),
    val companion: CharCompanionDto = CharCompanionDto(),
)

@Serializable
data class CharLevelDto(val level: Int = 1, val pct: Double = 0.0, val toNext: Int = 0)

@Serializable
data class CharSeasonDto(val tier: Int = 0, val maxTier: Int = 10, val pct: Double = 0.0, val complete: Boolean = false)

@Serializable
data class CharStatDto(val label: String = "", val level: Int = 0, val pct: Double = 0.0)

@Serializable
data class CharMasteryDto(val chore: String = "", val title: String = "", val count: Int = 0)

@Serializable
data class CharCompanionDto(
    val active: Boolean = false,
    val speciesName: String? = null,
    val stageName: String? = null,
    val shiny: Boolean = false,
    val incubationPct: Int = 0,
    val eggReady: Boolean = false,
    val image: String = "",
    val color: String = "#94a3b8",
    val xpCells: List<String> = emptyList(),
)

@Serializable
data class FamilyGoalDto(val text: String = "", val kids: String? = null)

@Serializable
data class HatchRequest(val mode: String = "new")

@Serializable
data class HatchResultDto(val hatched: String? = null)

@Serializable
data class CollectionDto(val eras: List<EraDto> = emptyList())

@Serializable
data class EraDto(
    val key: String = "",
    val label: String = "",
    val total: Int = 0,
    val unlocked: Int = 0,
    val species: List<CollectSpeciesDto> = emptyList(),
)

@Serializable
data class CollectSpeciesDto(
    val id: String = "",
    val rarity: String = "common",
    val owned: Boolean = false,
    val name: String? = null,
    val image: String? = null,
)

@Serializable
data class CoopDto(
    val seasonLabel: String = "",
    val floor: Int = 0,
    val childrenMeeting: Int = 0,
    val childrenTotal: Int = 0,
    val gateMet: Boolean = false,
    val meId: String = "",
    val isAdmin: Boolean = false,
    val children: List<CoopChildDto> = emptyList(),
    val proposals: List<CoopProposalDto> = emptyList(),
)

@Serializable
data class CoopChildDto(val name: String = "", val color: String? = null, val tier: Int = 0, val meets: Boolean = false)

@Serializable
data class CoopProposalDto(
    val id: String = "",
    val title: String = "",
    val detail: String? = null,
    val proposedByName: String = "",
    val status: String = "PROPOSED",
    val votes: Int = 0,
    val iVoted: Boolean = false,
)

@Serializable
data class ProposeCoopRequest(val title: String, val detail: String)

@Serializable
data class CoopProposalIdRequest(val proposalId: String)

@Serializable
data class SchoolDto(
    val meId: String = "",
    val seasonHint: String = "",
    val terms: List<SchoolTermDto> = emptyList(),
    val selectedTermId: String? = null,
    val subjects: List<String> = emptyList(),
    val types: List<SchoolTypeDto> = emptyList(),
    val canActFor: List<SchoolActorDto> = emptyList(),
    val classOptionsByUser: Map<String, List<ClassOptionDto>> = emptyMap(),
    val people: List<SchoolPersonDto> = emptyList(),
    val progress: List<SchoolProgressDto> = emptyList(),
)

@Serializable
data class SchoolTermDto(val id: String = "", val name: String = "")

@Serializable
data class SchoolTypeDto(val key: String = "", val label: String = "")

@Serializable
data class SchoolActorDto(val id: String = "", val name: String = "")

@Serializable
data class ClassOptionDto(val id: String = "", val name: String = "", val color: String? = null)

@Serializable
data class SchoolPersonDto(
    val id: String = "",
    val name: String = "",
    val color: String? = null,
    val pending: Int = 0,
    val overdue: Int = 0,
    val classes: List<SchoolClassDto> = emptyList(),
    val items: List<SchoolItemDto> = emptyList(),
)

@Serializable
data class SchoolClassDto(val id: String = "", val name: String = "", val color: String? = null, val meeting: String? = null)

@Serializable
data class SchoolItemDto(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val typeLabel: String = "",
    val className: String? = null,
    val classColor: String? = null,
    val dueISO: String = "",
    val overdue: Boolean = false,
)

@Serializable
data class SchoolProgressDto(
    val id: String = "",
    val name: String = "",
    val color: String? = null,
    val pct: Int = 0,
    val completed: Int = 0,
    val total: Int = 0,
    val onTime: Int = 0,
    val overdue: Int = 0,
    val byClass: List<SchoolByClassDto> = emptyList(),
)

@Serializable
data class SchoolByClassDto(val key: String = "", val color: String? = null, val completed: Int = 0, val total: Int = 0)

@Serializable
data class AddSchoolRequest(
    val userId: String,
    val title: String,
    val type: String,
    val dueDate: String,
    val subject: String? = null,
    val classId: String? = null,
)

@Serializable
data class SchoolTaskIdRequest(val taskId: String)

@Serializable
data class SchoolRenameRequest(val taskId: String, val title: String)

@Serializable
data class TasksListDto(
    val meId: String = "",
    val isParent: Boolean = false,
    val canActFor: List<TaskActorDto> = emptyList(),
    val groups: List<TaskUserGroupDto> = emptyList(),
)

@Serializable
data class TaskActorDto(val id: String = "", val name: String = "")

@Serializable
data class TaskUserGroupDto(
    val userId: String = "",
    val name: String = "",
    val color: String? = null,
    val open: List<TaskOpenDto> = emptyList(),
    val done: List<TaskDoneDto> = emptyList(),
)

@Serializable
data class TaskOpenDto(val id: String = "", val title: String = "", val dueISO: String = "", val overdue: Boolean = false, val recurring: Boolean = false)

@Serializable
data class TaskDoneDto(val id: String = "", val title: String = "", val dueISO: String = "", val recurring: Boolean = false)

@Serializable
data class RecurRequest(
    val freq: String = "WEEKLY",
    val interval: Int = 1,
    val byday: List<String> = emptyList(),
    val startDate: String = "",
    val endMode: String = "NEVER",
    val maxCount: Int? = null,
    val until: String = "",
    val notifyMinutes: Int? = null,
)

@Serializable
data class AddTaskRequest(
    val userId: String,
    val title: String,
    val dueDate: String? = null,
    val recur: RecurRequest? = null,
    val notifyMinutes: Int? = null,
)

@Serializable
data class TaskEditDataDto(
    val taskId: String = "",
    val userId: String = "",
    val title: String = "",
    val recurring: Boolean = false,
    val dueDate: String? = null,
    val notifyMinutes: Int? = null,
    val freq: String = "WEEKLY",
    val interval: Int = 1,
    val byday: List<String> = emptyList(),
    val startDate: String = "",
    val endMode: String = "NEVER",
    val maxCount: Int? = null,
    val until: String = "",
)

@Serializable
data class ColorRequest(val color: String)

@Serializable
data class ColorAckDto(val color: String = "")

@Serializable
data class AvatarAckDto(val avatarPosition: String = "0 0 1")

@Serializable
data class NotifTypeDto(val id: String = "", val name: String = "", val color: String = "#64748b")

@Serializable
data class NotifMetaDto(val eventTypes: List<NotifTypeDto> = emptyList())

@Serializable
data class UpcomingEventDto(
    val id: String = "",
    val title: String = "",
    val startMs: Long = 0,
    val reminders: List<Int> = emptyList(),
    val location: String? = null,
)

@Serializable
data class UpcomingTaskDto(
    val id: String = "",
    val title: String = "",
    val dueISO: String = "",
    val minute: Int = 0,
)

@Serializable
data class UpcomingDto(
    val events: List<UpcomingEventDto> = emptyList(),
    val tasks: List<UpcomingTaskDto> = emptyList(),
)

@Serializable
data class SavedAddressDto(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val navByName: Boolean = true,
)

@Serializable
data class AddressesResponse(
    val addresses: List<SavedAddressDto> = emptyList(),
)

@Serializable
data class SubmitAddressRequest(
    val name: String,
    val address: String,
    val navByName: Boolean = true,
    val force: Boolean = false,
)

@Serializable
data class AddressDuplicateDto(
    val id: String = "",
    val name: String = "",
    val address: String = "",
)

@Serializable
data class SubmitAddressResponse(
    val ok: Boolean = false,
    val id: String = "",
    val status: String = "",
    val duplicate: AddressDuplicateDto? = null,
)

// --- Class creation (mirrors the web's class form) ---


@Serializable
data class SchoolApprovalsDto(
    val subjects: List<PendingSubjectDto> = emptyList(),
    val terms: List<PendingTermDto> = emptyList(),
    val allSubjects: List<ClassOptionDto> = emptyList(),
    val allTerms: List<ClassOptionDto> = emptyList(),
)

@Serializable
data class PendingSubjectDto(
    val id: String = "",
    val name: String = "",
    val proposedBy: String? = null,
)

@Serializable
data class PendingTermDto(
    val id: String = "",
    val name: String = "",
    val startISO: String = "",
    val endISO: String = "",
    val proposedBy: String? = null,
)

@Serializable
data class ApprovalActionRequest(
    val kind: String,
    val op: String,
    val id: String,
    val name: String? = null,
    val start: String? = null,
    val end: String? = null,
    val targetId: String? = null,
)

@Serializable
data class ClassFormDto(
    val canMakeClass: Boolean = false,
    val isAdmin: Boolean = false,
    val meId: String = "",
    val meName: String? = null,
    val subjects: List<ClassOptionDto> = emptyList(),
    val classTypes: List<ClassOptionDto> = emptyList(),
    val terms: List<ClassOptionDto> = emptyList(),
    val students: List<ClassOptionDto> = emptyList(),
)

@Serializable
data class CreateClassRequest(
    val replaceEventId: String? = null,
    val newSubject: String? = null,
    val newTermName: String? = null,
    val newTermStart: String? = null,
    val newTermEnd: String? = null,
    val userId: String? = null,
    val classTypeId: String? = null,
    val termId: String? = null,
    val color: String? = null,
    val start: String? = null,
    val end: String? = null,
    val byday: String? = null,
    val sharedWith: String? = null,
    val meetingStartDate: String? = null,
    val meetingEndDate: String? = null,
    val location: String? = null,
    val promptHomework: Boolean = true,
    val reminders: List<Int> = emptyList(),
    val reminderBell: List<String> = emptyList(),
)
