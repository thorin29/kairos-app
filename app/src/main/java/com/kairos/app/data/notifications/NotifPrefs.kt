package com.kairos.app.data.notifications

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Who a device wants notifications about. */
enum class NotifScope { MINE, ALL, FAMILY }

/** Common lead times (minutes before the event) offered in the UI. */
val LEAD_OPTIONS = listOf(0, 15, 60, 1440)

fun leadLabel(minutes: Int): String = when (minutes) {
    0 -> "At start"
    15 -> "15 minutes before"
    60 -> "1 hour before"
    1440 -> "1 day before"
    else -> if (minutes % 1440 == 0) "${minutes / 1440} days before"
    else if (minutes % 60 == 0) "${minutes / 60} hours before"
    else "$minutes minutes before"
}

@Serializable
data class TypePref(
    val enabled: Boolean = false,
    val lead: Int = 15,
)

/**
 * Per-device calendar notification preferences. Everything defaults off so a
 * fresh install is silent until the person opts in.
 */
@Serializable
data class NotifPrefs(
    val enabled: Boolean = false,
    val scope: NotifScope = NotifScope.MINE,
    val types: Map<String, TypePref> = emptyMap(),
    val birthdayEnabled: Boolean = false,
    val birthdayLead: Int = 1440,
) {
    fun typePref(id: String): TypePref = types[id] ?: TypePref()

    fun withType(id: String, pref: TypePref): NotifPrefs =
        copy(types = types + (id to pref))

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(raw: String?): NotifPrefs =
            if (raw.isNullOrBlank()) NotifPrefs()
            else runCatching { json.decodeFromString<NotifPrefs>(raw) }.getOrDefault(NotifPrefs())

        fun encode(p: NotifPrefs): String = json.encodeToString(p)
    }
}
