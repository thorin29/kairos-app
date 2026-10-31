package com.kairos.app.ui.nav

import kotlinx.serialization.Serializable

/**
 * Type-safe routes for the authenticated area. The NavHost that uses these grows
 * as read screens land (dashboard, chores, …); today it holds only Home. The
 * auth gate itself (setup/enroll) is not a route — it's decided by SessionState.
 */
sealed interface Route {
    @Serializable
    data object Home : Route

    @Serializable
    data object Devices : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object SchoolApprovals : Route

    @Serializable
    data object SettingsAppearance : Route

    @Serializable
    data object SettingsProfile : Route

    @Serializable
    data object SettingsNotifications : Route

    @Serializable
    data object SettingsUpdate : Route

    @Serializable
    data object SettingsReminders : Route

    @Serializable
    data object SettingsDiagnostics : Route

    @Serializable
    data object SettingsScoring : Route

    @Serializable
    data object SettingsPassword : Route

    /** Connect the person's Concept2 Logbook. */
    @Serializable
    data object SettingsConcept2 : Route

    @Serializable
    data class AddClass(
        val replaceEventId: String? = null,
        val subject: String = "",
        val startMin: Int = -1,
        val endMin: Int = -1,
        val day: String? = null,
        val location: String = "",
        val sharedWith: String = "",
    ) : Route

    @Serializable
    data class WorkoutLog(val date: String) : Route

    /** One planned movement's sets for a day (every-set logging). */
    @Serializable
    data class MovementSets(
        val date: String,
        val plannedWorkoutId: String,
        val slotId: String,
        val name: String,
        val metric: String,
        val unit: String,
        /** The day is being logged late (counts for [date], done today). */
        val late: Boolean = false,
    ) : Route

    @Serializable
    data object RecentWorkouts : Route

    /** Workout programs: the running one, or the list to start one. */
    @Serializable
    data object Programs : Route

    @Serializable
    data class WeightCalculator(
        val forLog: Boolean = false,
        /** The use button's label ("Use for set 3"); blank = the default. */
        val useLabel: String = "",
    ) : Route

    @Serializable
    data object BrowseWorkouts : Route

    @Serializable
    data object CreatePersonalWorkout : Route

    @Serializable
    data object EditPlan : Route

    @Serializable
    data object AddGrocery : Route

    @Serializable
    data object Gallery : Route

    @Serializable
    data object Coop : Route

    @Serializable
    data object ReadingGoals : Route

    @Serializable
    data object AddSchool : Route

    @Serializable
    data object HomeChores : Route

    @Serializable
    data object HomeSchoolWork : Route

    @Serializable
    data object AssignTask : Route

    @Serializable
    data class EditTask(val taskId: String) : Route

    @Serializable
    data object Rotation : Route

    /** A not-yet-built section, shown as a placeholder. `key` matches an entry
     *  in AppSections. Real sections graduate to their own route/screen. */
    @Serializable
    data class Section(val key: String) : Route
}
