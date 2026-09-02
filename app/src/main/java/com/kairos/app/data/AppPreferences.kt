package com.kairos.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The single process-wide DataStore. Only one instance may exist per file name,
 * so the delegate lives here and both SettingsStore and TokenStore share it.
 * Non-secret settings (base URL) sit here in the clear; the device token is
 * stored here too but only ever as Keystore-encrypted ciphertext.
 */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "kairos_prefs")
