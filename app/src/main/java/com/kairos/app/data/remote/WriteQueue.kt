package com.kairos.app.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One write the app couldn't send because it was offline. Persisted so it
 *  survives an app restart and replays when connectivity returns. */
@Serializable
data class PendingWrite(
    val id: String,
    val method: String,
    val url: String,
    val body: String?,
    val createdAt: Long,
)

/**
 * A durable FIFO of offline writes, stored as a JSON blob in DataStore. Kept
 * small and dependency-free (no Room); the queue is normally empty and only
 * holds a handful of items during a brief outage.
 */
class WriteQueue(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    private val key = stringPreferencesKey("offline_write_queue")

    private fun decode(raw: String?): List<PendingWrite> =
        raw?.let { runCatching { json.decodeFromString<List<PendingWrite>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    val items: Flow<List<PendingWrite>> = dataStore.data.map { decode(it[key]) }

    suspend fun enqueue(write: PendingWrite) {
        dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]) + write)
        }
    }

    suspend fun remove(id: String) {
        dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]).filterNot { it.id == id })
        }
    }

    suspend fun snapshot(): List<PendingWrite> = items.first()
}
