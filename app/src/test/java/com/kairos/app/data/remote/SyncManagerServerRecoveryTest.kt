package com.kairos.app.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The outage this app actually hits: the phone keeps full signal while Kairos
 * goes DOWN and later comes back. There is no connectivity transition to ride,
 * so a queued write must still replay — that was the hole in 0.342, where
 * replay only ran on a NetworkMonitor online edge.
 */
class SyncManagerServerRecoveryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var dir: File
    private lateinit var queue: WriteQueue
    private lateinit var server: MockWebServer

    /** What the mock server answers, for as many requests as arrive. */
    @Volatile private var reply: Int = 200
    @Volatile private var replyBody: String = """{"status":"ok"}"""

    private fun serverAnswers(code: Int, body: String) {
        reply = code
        replyBody = body
    }

    @Before fun setUp() {
        // Shared global: start every test from "server reachable" so the
        // unavailable -> reachable edge below is a real transition.
        ServerStatusTracker.markReachable()
        scope = CoroutineScope(Dispatchers.IO + Job())
        dir = Files.createTempDirectory("kairos-sync").toFile()
        val ds = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "q.preferences_pb") }
        queue = WriteQueue(ds, Json { ignoreUnknownKeys = true })
        server = MockWebServer()
        // A standing reply rather than a one-shot queue. SyncManager's init
        // launches a startup replay on Dispatchers.IO, so the number of requests
        // a test receives is not deterministic: with server.enqueue() the
        // startup pass could take the only response and the test's own pass
        // would meet an empty queue, get a client error, and drop the write.
        // Every request gets the current answer; tests set what that is.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(reply).setBody(replyBody)
        }
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun manager(online: MutableStateFlow<Boolean>) = SyncManager(
        queue = queue,
        online = online,
        isOnline = { online.value },
        cache = null,
        tokenProvider = { "token" },
        baseUrlProvider = { server.url("/api/v1").toString() },
        scope = scope,
    )

    private fun queuedWrite() = PendingWrite(
        id = "q-1",
        method = "POST",
        url = "/api/v1/sport/confirm",
        body = """{"eventId":"e1","dateISO":"2026-10-01"}""",
        createdAt = 0L,
        clientId = null,
    )

    /**
     * Phone stays online throughout. The server is unavailable (502), the write
     * is already queued, and then the server returns — with no change to the
     * connectivity flow. The queue must drain.
     */
    @Test fun replays_when_server_returns_without_any_connectivity_change() = runBlocking {
        val online = MutableStateFlow(true)
        // Construct first, with the queue empty: the startup pass then no-ops and
        // can't race this test for a mock response.
        val sync = manager(online)
        queue.enqueue(queuedWrite())

        // Still down: the pass runs, gets a 502, and keeps the write. Any
        // startup pass gets the same 502, so it cannot change the outcome.
        serverAnswers(502, "bad gateway")
        sync.replayAll()
        assertEquals(1, queue.snapshot().size)

        // The server comes back. This is the signal a live GET produces; the
        // phone's connectivity never changed.
        serverAnswers(200, """{"status":"ok"}""")
        ServerStatusTracker.markUnavailable()
        ServerStatusTracker.markReachable()
        sync.replayAll()

        assertEquals(0, queue.snapshot().size)
    }

    /** A 4xx is a real answer, not an outage: drop it instead of retrying forever. */
    @Test fun drops_a_write_the_server_rejects() = runBlocking {
        val online = MutableStateFlow(true)
        val sync = manager(online)
        queue.enqueue(queuedWrite())

        serverAnswers(400, """{"error":"validation"}""")
        sync.replayAll()

        assertEquals(0, queue.snapshot().size)
        assertEquals(1, sync.droppedCount.value)
    }

    /**
     * The actual 0.342 bug: nobody calls replayAll(). The write is queued, the
     * server comes back, and the SyncManager's own collector has to notice —
     * with no connectivity change and no manual drain.
     */
    @Test fun recovery_collector_drains_the_queue_with_no_manual_replay() = runBlocking {
        val online = MutableStateFlow(true)
        manager(online)
        queue.enqueue(queuedWrite())
        serverAnswers(200, """{"status":"ok"}""")

        // What the interceptor does: 502 marks it unavailable, a later live
        // success marks it reachable. The pause matters — StateFlow conflates,
        // and two instant flips would collapse into no observed change.
        ServerStatusTracker.markUnavailable()
        delay(150)
        ServerStatusTracker.markReachable()

        withTimeout(5_000) {
            while (queue.snapshot().isNotEmpty()) delay(50)
        }
        // At least one, not exactly one: the startup replay may also have run.
        // What this test is about is the queue draining with no manual call.
        assertTrue(server.requestCount >= 1)
    }

    /** Offline means nothing is sent at all — the queue is left intact. */
    @Test fun does_not_send_while_the_phone_is_offline() = runBlocking {
        val online = MutableStateFlow(false)
        val sync = manager(online)
        queue.enqueue(queuedWrite())
        sync.replayAll()

        assertEquals(1, queue.snapshot().size)
        assertEquals(0, server.requestCount)
    }
}
