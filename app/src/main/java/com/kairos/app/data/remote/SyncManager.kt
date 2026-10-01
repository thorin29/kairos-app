package com.kairos.app.data.remote

import android.util.Log
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Drains the offline [WriteQueue] whenever connectivity returns: replays each
 * queued write in order against the *current* server.
 *
 * Each pending write stores a server-relative path; it is resolved against the
 * live base URL here, so a write can never be sent to a stale/other host. The
 * response decides its fate:
 *  - **2xx** — landed; remove it.
 *  - **retryable** (401/403 auth, 408/429 throttle/timeout, 5xx, network) — stop
 *    the pass and try the whole queue again later; the write is kept.
 *  - **other 4xx** (400/404/409/410/422…) — the server rejected it and a retry
 *    would only be rejected again, so drop it, but record it (see
 *    [droppedCount] / logs) rather than losing it silently.
 * After a pass that changed anything, evict the read cache and bump [revision]
 * so screens reload the true state.
 */
class SyncManager(
    private val queue: WriteQueue,
    /** Device connectivity. Taken as a flow + probe rather than the monitor
     *  itself so the replay triggers can be exercised without a Context. */
    private val online: kotlinx.coroutines.flow.Flow<Boolean>,
    private val isOnline: () -> Boolean,
    private val cache: Cache?,
    tokenProvider: () -> String?,
    private val baseUrlProvider: () -> String?,
    scope: CoroutineScope,
) {
    private val jsonType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .addInterceptor(
            AuthInterceptor(tokenProvider) { ApiClient.baseHost(baseUrlProvider() ?: "") },
        )
        .build()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _revision = MutableStateFlow(0)
    /** Increments after each drain so the UI can refresh once writes land. */
    val revision: StateFlow<Int> = _revision

    private val _droppedCount = MutableStateFlow(0)
    /** How many writes the server rejected outright (surfaced, not silent). */
    val droppedCount: StateFlow<Int> = _droppedCount

    val pendingCount: StateFlow<Int> =
        queue.items.map { it.size }.stateIn(scope, SharingStarted.Eagerly, 0)

    private val mutex = Mutex()

    init {
        // 0. One pass at startup: the app may have been killed with writes still
        //    queued. Explicit and once, so the collectors below can be strictly
        //    edge-triggered rather than each firing on their current value.
        scope.launch(kotlinx.coroutines.Dispatchers.IO) { replayAll() }
        // 1. Device connectivity returned (edge only: false -> true).
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var wasOffline = false
            online.collect { up ->
                if (up && wasOffline) replayAll()
                wasOffline = !up
            }
        }
        // 2. The SERVER returned. The outage this app is most often in is Kairos
        //    being down while the phone keeps full signal, so there is no
        //    connectivity transition to ride: a write queued at 502 would sit
        //    there until the radio happened to flap. ServerStatusTracker flips
        //    back to reachable on the first live success, which is the signal.
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var wasUnavailable = false
            ServerStatusTracker.unavailable.collect { unavailable ->
                if (wasUnavailable && !unavailable) replayAll()
                wasUnavailable = unavailable
            }
        }
        // 3. Nothing is watching. If every write is queued and the user isn't
        //    loading screens, no GET ever notices the server came back, so poll
        //    while — and only while — something is actually waiting. A pass
        //    against a still-dead server costs one request and keeps the queue.
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(RETRY_INTERVAL_MS)
                if (isOnline() && queue.snapshot().isNotEmpty()) replayAll()
            }
        }
    }

    private enum class Outcome { DONE, RETRY, DROP }

    /** Resolve a stored path against the current base. Returns null when it
     *  can't be resolved to the current host (e.g. a legacy absolute URL from a
     *  different server), which the caller treats as "drop, don't send". */
    private fun resolveReplayUrl(rawBase: String?, stored: String): String? {
        val base = rawBase?.takeIf { it.isNotBlank() } ?: return null
        if (stored.startsWith("http://") || stored.startsWith("https://")) {
            // Legacy absolute URL: only replay if it still matches the current
            // host, otherwise drop it rather than risk hitting another server.
            val baseHost = ApiClient.baseHost(base) ?: return null
            val storedHost = stored.toHttpUrlOrNull()?.host ?: return null
            return if (storedHost.equals(baseHost, ignoreCase = true)) stored else null
        }
        val origin = base.trim().removeSuffix("/").removeSuffix("/api/v1")
        return origin + stored
    }

    suspend fun replayAll() {
        mutex.withLock {
            val items = queue.snapshot()
            if (items.isEmpty() || !isOnline()) return
            _syncing.value = true
            var changedAny = false
            // As each queued create replays and returns its real id, record
            // "temp-<clientId>" -> realId so later ops in this same pass that were
            // queued offline against the temp id can be rewritten onto the real one.
            val idMap = HashMap<String, String>()
            try {
                val base = baseUrlProvider()
                for (w in items) {
                    if (!isOnline()) break

                    val rw = OfflineSync.rewrite(w, idMap)
                    val target = resolveReplayUrl(base, rw.url)
                    if (target == null) {
                        // Can't be resolved to the current server — a stale write
                        // from a different host. Drop it rather than missend it.
                        Log.w(TAG, "Dropping unresolvable queued write ${w.method} ${w.url}")
                        queue.remove(w.id)
                        _droppedCount.value += 1
                        changedAny = true
                        continue
                    }

                    val req = Request.Builder()
                        .url(target)
                        .method(w.method, (rw.body ?: "").toRequestBody(jsonType))
                        .build()

                    var createdRealId: String? = null
                    val outcome = try {
                        client.newCall(req).execute().use { resp ->
                            when {
                                resp.isSuccessful -> {
                                    // A create returns the new row's id; capture it so
                                    // later ops in this pass can be remapped onto it.
                                    if (w.clientId != null) {
                                        createdRealId = resp.body?.string()?.let {
                                            Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1)
                                        }
                                    }
                                    Outcome.DONE
                                }
                                resp.code in RETRYABLE -> Outcome.RETRY
                                resp.code in 500..599 -> Outcome.RETRY
                                resp.code in 400..499 -> {
                                    Log.w(
                                        TAG,
                                        "Server rejected queued write ${w.method} ${w.url} (HTTP ${resp.code}) — dropping",
                                    )
                                    Outcome.DROP
                                }
                                else -> Outcome.RETRY
                            }
                        }
                    } catch (_: IOException) {
                        Outcome.RETRY // transient (offline again) — stop and retry later
                    }

                    when (outcome) {
                        Outcome.DONE -> {
                            val real = createdRealId
                            if (w.clientId != null && real != null) {
                                val tempId = "temp-${w.clientId}"
                                // Keep the mapping for the rest of THIS pass — the
                                // in-memory snapshot still holds the temp id — and
                                // durably rewrite the queue so a later pass (after a
                                // dropped connection or a restart) no longer depends
                                // on it. Removing the create without this rewrite is
                                // what stranded dependent ops in 0.311.
                                idMap[tempId] = real
                                queue.resolveCreate(w.id, tempId, real)
                            } else {
                                queue.remove(w.id)
                            }
                            changedAny = true
                        }
                        Outcome.DROP -> {
                            queue.remove(w.id)
                            _droppedCount.value += 1
                            changedAny = true
                        }
                        Outcome.RETRY -> break
                    }
                }
            } finally {
                _syncing.value = false
                if (changedAny) {
                    runCatching { cache?.evictAll() }
                    _revision.value = _revision.value + 1
                }
            }
        }
    }

    private companion object {
        const val TAG = "SyncManager"
        // Codes worth retrying rather than dropping: auth (may recover after a
        // re-auth), request timeout, and rate-limit/throttle.
        val RETRYABLE = setOf(401, 403, 408, 425, 429)

        /** How often to re-attempt a non-empty queue when nothing else has
         *  signalled recovery. */
        const val RETRY_INTERVAL_MS = 60_000L
    }
}
