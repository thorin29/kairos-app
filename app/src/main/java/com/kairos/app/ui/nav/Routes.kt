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
    data class WorkoutLog(val date: String) : Route
}
