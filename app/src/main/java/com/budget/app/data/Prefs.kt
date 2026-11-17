package com.budget.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** One preferences file for the app. Excluded from every kind of backup. */
val Context.budgetStore: DataStore<Preferences> by preferencesDataStore(name = "budget")

/** Small string settings: server, username, device name, update-check time, appearance. */
class Settings(private val store: DataStore<Preferences>) {
    suspend fun get(key: String): String? = store.data.first()[stringPreferencesKey(key)]

    fun observe(key: String): Flow<String?> = store.data.map { it[stringPreferencesKey(key)] }

    suspend fun set(key: String, value: String?) {
        store.edit { prefs ->
            val k = stringPreferencesKey(key)
            if (value == null) prefs.remove(k) else prefs[k] = value
        }
    }

    companion object {
        const val SERVER = "server"
        const val USERNAME = "username"
        const val DEVICE_NAME = "device_name"
        const val LAST_UPDATE_CHECK = "last_update_check"
        /** The newest release seen, as JSON, so badges survive restarts and failed checks. */
        const val UPDATE_LATEST = "update_latest"
        /** The last versionCode a notification was posted for; each version notifies once. */
        const val UPDATE_NOTIFIED = "update_notified"
        const val ASKED_NOTIFICATIONS = "asked_notifications"
        /** Bill reminders: off, 0, 1 or 2 days before. */
        const val REMINDER_LEAD = "reminder_lead"
        /** The last day reminders ran, so each day reminds once. */
        const val REMINDER_LAST_DAY = "reminder_last_day"
        /** Card payment reminders: off, 0, 1, 3 or 5 days before (plus the due date itself). */
        const val CARD_REMINDER_LEAD = "card_reminder_lead"
        const val CARD_REMINDER_LAST_DAY = "card_reminder_last_day"
        const val THEME_MODE = "theme_mode"
        const val ACCENT = "accent"
    }
}
