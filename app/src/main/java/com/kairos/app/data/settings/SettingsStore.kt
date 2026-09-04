package com.kairos.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Non-secret device settings. Right now that's just the server base URL, which
 * the user enters at setup and can change any time. No default host ships in the
 * app — the repo points at nothing until the user configures it.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val baseUrl: Flow<String?> = dataStore.data.map { it[KEY_BASE_URL] }

    suspend fun currentBaseUrl(): String? = baseUrl.first()

    suspend fun setBaseUrl(url: String) {
        dataStore.edit { it[KEY_BASE_URL] = url.trim() }
    }

    suspend fun clearBaseUrl() {
        dataStore.edit { it.remove(KEY_BASE_URL) }
    }

    /** The calendar view the app opens to. A CalView value, or "last" to use the
     *  most recently used view. */
    val calendarDefaultView: Flow<String> =
        dataStore.data.map { it[KEY_CAL_DEFAULT_VIEW] ?: "last" }

    suspend fun currentCalendarDefaultView(): String = calendarDefaultView.first()

    suspend fun setCalendarDefaultView(v: String) {
        dataStore.edit { it[KEY_CAL_DEFAULT_VIEW] = v }
    }

    /** The most recently used calendar view, for the "last" default. */
    suspend fun currentCalendarLastView(): String =
        dataStore.data.map { it[KEY_CAL_LAST_VIEW] ?: "agenda" }.first()

    suspend fun setCalendarLastView(v: String) {
        dataStore.edit { it[KEY_CAL_LAST_VIEW] = v }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_CAL_DEFAULT_VIEW = stringPreferencesKey("cal_default_view")
        val KEY_CAL_LAST_VIEW = stringPreferencesKey("cal_last_view")
    }
}
