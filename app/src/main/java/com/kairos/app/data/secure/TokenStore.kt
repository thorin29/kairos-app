package com.kairos.app.data.secure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Persists the device token as Keystore-encrypted ciphertext in DataStore, and
 * keeps a decrypted copy in memory so the OkHttp interceptor can attach it
 * without a blocking disk read on the network thread. The in-memory copy is the
 * source of truth for [current]; it's primed once at startup via [load].
 */
class TokenStore(private val dataStore: DataStore<Preferences>) {

    @Volatile
    private var cached: String? = null

    /** Synchronous read for the interceptor. Null until [load] runs or a token
     *  is saved. */
    fun current(): String? = cached

    /** Decrypt the stored token into the in-memory cache. Call once on startup.
     *  Returns the token, or null if none is stored / it failed to decrypt. */
    suspend fun load(): String? {
        val blob = dataStore.data.first()[KEY_TOKEN]
        cached = blob?.let { TokenCrypto.decrypt(it) }
        return cached
    }

    suspend fun save(token: String) {
        val blob = TokenCrypto.encrypt(token)
        dataStore.edit { it[KEY_TOKEN] = blob }
        cached = token
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY_TOKEN) }
        cached = null
    }

    private companion object {
        val KEY_TOKEN = stringPreferencesKey("device_token_enc")
    }
}
