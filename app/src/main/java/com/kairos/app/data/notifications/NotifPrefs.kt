package com.kairos.app.data.notifications

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * This phone's alert switches, which the reminder scheduler reads. Since
 * v0.553 they mirror the person's choices saved on the server (Settings →
 * Notifications): calendar = either calendar choice on, tasks = task alerts.
 * Fresh installs start off.
 */
@Serializable
data class NotifPrefs(
    val enabled: Boolean = false,
    val tasksEnabled: Boolean = false,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(raw: String?): NotifPrefs =
            if (raw.isNullOrBlank()) NotifPrefs()
            else runCatching { json.decodeFromString<NotifPrefs>(raw) }.getOrDefault(NotifPrefs())

        fun encode(p: NotifPrefs): String = json.encodeToString(p)
    }
}
