package com.kairos.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    /** Color theme, per device (a ThemeScheme name); defaults to TEAL. */
    val themeScheme: Flow<String> = dataStore.data.map { it[KEY_THEME] ?: "TEAL" }

    suspend fun setThemeScheme(v: String) {
        dataStore.edit { it[KEY_THEME] = v }
    }

    /** Dark mode, per device; off by default. */
    val darkMode: Flow<Boolean> = dataStore.data.map { it[KEY_DARK] ?: false }

    suspend fun setDarkMode(on: Boolean) {
        dataStore.edit { it[KEY_DARK] = on }
    }

    /** 24-hour (military) clock on the calendar, per device; off (12-hour) by default. */
    val militaryTime: Flow<Boolean> = dataStore.data.map { it[KEY_MILITARY] ?: false }

    suspend fun setMilitaryTime(on: Boolean) {
        dataStore.edit { it[KEY_MILITARY] = on }
    }

    /** The last custom (non-palette) color picked in the profile, per device, so
     *  it can be re-offered as a swatch. */
    val lastCustomColor: Flow<String?> = dataStore.data.map { it[KEY_LAST_COLOR] }

    suspend fun setLastCustomColor(hex: String) {
        dataStore.edit { it[KEY_LAST_COLOR] = hex }
    }

    /** Per-device calendar notification preferences (JSON). All off by default. */
    val notifPrefs: Flow<com.kairos.app.data.notifications.NotifPrefs> =
        dataStore.data.map { com.kairos.app.data.notifications.NotifPrefs.decode(it[KEY_NOTIF]) }

    suspend fun currentNotifPrefs(): com.kairos.app.data.notifications.NotifPrefs =
        notifPrefs.first()

    suspend fun setNotifPrefs(p: com.kairos.app.data.notifications.NotifPrefs) {
        dataStore.edit { it[KEY_NOTIF] = com.kairos.app.data.notifications.NotifPrefs.encode(p) }
    }

    /** Request codes of the alarms currently scheduled, so stale ones can be
     *  cancelled when events change. Stored as a comma-separated list. */
    suspend fun currentScheduledCodes(): Set<Int> =
        (dataStore.data.first()[KEY_CODES] ?: "")
            .split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    suspend fun setScheduledCodes(codes: Set<Int>) {
        dataStore.edit { it[KEY_CODES] = codes.joinToString(",") }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_CAL_DEFAULT_VIEW = stringPreferencesKey("cal_default_view")
        val KEY_CAL_LAST_VIEW = stringPreferencesKey("cal_last_view")
        val KEY_THEME = stringPreferencesKey("theme_scheme")
        val KEY_DARK = booleanPreferencesKey("dark_mode")
        val KEY_MILITARY = booleanPreferencesKey("military_time")
        val KEY_LAST_COLOR = stringPreferencesKey("profile.lastCustomColor")
        val KEY_NOTIF = stringPreferencesKey("notif.prefs")
        val KEY_CODES = stringPreferencesKey("notif.scheduledCodes")
    }
}
