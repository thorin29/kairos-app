package com.kairos.app.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One write the app couldn't send because it was offline. Persisted so it
 *  survives an app restart and replays when connectivity returns.
 *
 *  [url] is a **server-relative path** (e.g. "/api/v1/tasks/add"), never an
 *  absolute URL: it is resolved against the *current* configured server at
 *  replay time, so a queued write can never be sent to a stale or different
 *  host than the one this device is signed into. */
@Serializable
data class PendingWrite(
    val id: String,
    val method: String,
    /** Server-relative path (+ query), resolved against the current base on replay. */
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
    private val listSerializer = ListSerializer(PendingWrite.serializer())

    private fun decode(raw: String?): List<PendingWrite> =
        raw?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    val items: Flow<List<PendingWrite>> = dataStore.data.map { decode(it[key]) }

    suspend fun enqueue(write: PendingWrite) {
        dataStore.edit { prefs ->
            // Cap the queue so a long outage (or a write that keeps failing to
            // replay) can't grow it without bound; keep the most recent entries.
            val next = (decode(prefs[key]) + write).takeLast(MAX_QUEUE)
            prefs[key] = json.encodeToString(listSerializer, next)
        }
    }

    suspend fun remove(id: String) {
        dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(listSerializer, decode(prefs[key]).filterNot { it.id == id })
        }
    }

    /** Drop every pending write. Called on sign-out, server change, or a dead
     *  token, so one identity's queued writes can never replay under another. */
    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(key) }
    }

    suspend fun snapshot(): List<PendingWrite> = items.first()
}

/** Upper bound on queued writes (normally the queue is empty or near-empty). */
private const val MAX_QUEUE = 500
