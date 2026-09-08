package com.kairos.app.data.remote

import okhttp3.Cache
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
 * queued write in order against the live server. A 2xx (done) or a 4xx (the
 * write is stale/invalid — dropped rather than retried forever) removes it; a
 * 5xx or network error stops the pass to try again later. After a pass it evicts
 * the read cache and bumps [revision] so screens can reload the true state.
 */
class SyncManager(
    private val queue: WriteQueue,
    private val monitor: NetworkMonitor,
    private val cache: Cache?,
    tokenProvider: () -> String?,
    scope: CoroutineScope,
) {
    private val jsonType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenProvider))
        .build()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _revision = MutableStateFlow(0)
    /** Increments after each drain so the UI can refresh once writes land. */
    val revision: StateFlow<Int> = _revision

    val pendingCount: StateFlow<Int> =
        queue.items.map { it.size }.stateIn(scope, SharingStarted.Eagerly, 0)

    private val mutex = Mutex()

    init {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            monitor.online.collect { online -> if (online) replayAll() }
        }
    }

    suspend fun replayAll() {
        mutex.withLock {
            val items = queue.snapshot()
            if (items.isEmpty() || !monitor.isOnline()) return
            _syncing.value = true
            var replayedAny = false
            try {
                for (w in items) {
                    if (!monitor.isOnline()) break
                    val req = Request.Builder()
                        .url(w.url)
                        .method(w.method, (w.body ?: "").toRequestBody(jsonType))
                        .build()
                    val done = try {
                        client.newCall(req).execute().use { resp ->
                            resp.isSuccessful || resp.code in 400..499
                        }
                    } catch (_: IOException) {
                        false // transient (offline again) — stop and retry later
                    }
                    if (done) {
                        queue.remove(w.id)
                        replayedAny = true
                    } else {
                        break
                    }
                }
            } finally {
                _syncing.value = false
                if (replayedAny) {
                    runCatching { cache?.evictAll() }
                    _revision.value = _revision.value + 1
                }
            }
        }
    }
}
