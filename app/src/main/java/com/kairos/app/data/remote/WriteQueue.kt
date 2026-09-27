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
    /** For an offline *create*, the durable client identity of the new item
     *  (the bare uuid of its `temp-<uuid>` id). Null for non-creates. Lets a
     *  create be correlated with the follow-up ops that reference its temp id,
     *  and rebuilt with the same id by applyPending across restarts. */
    val clientId: String? = null,
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

    /** Remove a queued write by its queue id. Returns true only if an entry
     *  actually matched and was dropped, so a caller can tell a real cancel from
     *  a no-op instead of assuming success. */
    suspend fun remove(id: String): Boolean {
        var removed = false
        dataStore.edit { prefs ->
            val before = decode(prefs[key])
            val after = before.filterNot { it.id == id }
            removed = after.size != before.size
            prefs[key] = json.encodeToString(listSerializer, after)
        }
        return removed
    }

    /** Remove a queued *create* by its client identity (the temp uuid). Returns
     *  true only if one actually matched, so the interceptor can tell a real
     *  add-then-delete collapse from a delete with nothing to cancel. */
    suspend fun removeByClientId(clientId: String): Boolean {
        var removed = false
        dataStore.edit { prefs ->
            val before = decode(prefs[key])
            val after = before.filterNot { it.clientId == clientId }
            removed = after.size != before.size
            prefs[key] = json.encodeToString(listSerializer, after)
        }
        return removed
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
