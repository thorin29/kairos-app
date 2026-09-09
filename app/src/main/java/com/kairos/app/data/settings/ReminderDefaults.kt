package com.kairos.app.data.settings

/**
 * Per-event-type default reminders for NEW events, configured per device in
 * Settings. A value is minutes-before-start; [NONE] means "no default reminder".
 * These only seed a new event's reminder — existing events are never touched,
 * and custom event types carry their own default from the server.
 */
object ReminderDefaults {
    const val NONE = -1

    /** Starting defaults before the person customizes them. */
    val INITIAL: Map<String, Int> = mapOf(
        "APPOINTMENT" to 30,
        "CLASS" to 30,
        "WORK" to 15,
        "BIRTHDAY" to NONE,
        "OTHER" to NONE,
    )

    /** Built-in kinds shown on the settings screen, in order. */
    val KINDS: List<Pair<String, String>> = listOf(
        "APPOINTMENT" to "Appointment",
        "CLASS" to "Class",
        "WORK" to "Work shift",
        "BIRTHDAY" to "Birthday",
        "OTHER" to "Other",
    )

    /** Selectable values on the picker (minutes), plus NONE. */
    val PRESETS: List<Int> = listOf(NONE, 0, 10, 15, 30, 60, 120, 1440, 10080)

    /** The effective default for [kind]: the person's override, else the initial. */
    fun effective(overrides: Map<String, Int>, kind: String): Int =
        overrides[kind] ?: INITIAL[kind] ?: NONE

    /** A short human label for a reminder value. */
    fun label(minutes: Int): String = when {
        minutes < 0 -> "None"
        minutes == 0 -> "At start time"
        minutes % 10080 == 0 -> "${minutes / 10080} week" + if (minutes / 10080 > 1) "s before" else " before"
        minutes % 1440 == 0 -> "${minutes / 1440} day" + if (minutes / 1440 > 1) "s before" else " before"
        minutes % 60 == 0 -> "${minutes / 60} hour" + if (minutes / 60 > 1) "s before" else " before"
        else -> "$minutes minutes before"
    }
}
