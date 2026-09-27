package com.kairos.app.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Queue integrity + the add-then-delete collapse (0.311). Uses a real
 * file-backed Preferences DataStore (no Android Context needed) so the
 * serialization round-trip of the new clientId field is exercised too.
 */
class WriteQueueTest {
    private lateinit var scope: CoroutineScope
    private lateinit var dir: File
    private lateinit var queue: WriteQueue

    @Before fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + Job())
        dir = Files.createTempDirectory("kairos-queue").toFile()
        val ds = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "q.preferences_pb") }
        queue = WriteQueue(ds, Json { ignoreUnknownKeys = true })
    }

    @After fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun create(clientId: String) = PendingWrite(
        id = "q-$clientId",
        method = "POST",
        url = "/books/add",
        body = """{"title":"t","clientId":"$clientId"}""",
        createdAt = 0L,
        clientId = clientId,
    )

    @Test fun enqueue_then_snapshot_roundtrips_clientId() = runBlocking {
        queue.enqueue(create("A"))
        val items = queue.snapshot()
        assertEquals(1, items.size)
        assertEquals("A", items[0].clientId)
    }

    @Test fun removeByClientId_collapses_only_the_matching_create() = runBlocking {
        queue.enqueue(create("A"))
        queue.enqueue(create("B"))
        assertTrue(queue.removeByClientId("A"))
        val items = queue.snapshot()
        assertEquals(1, items.size)
        assertEquals("B", items[0].clientId)
    }

    @Test fun removeByClientId_false_when_nothing_matches() = runBlocking {
        queue.enqueue(create("A"))
        assertFalse(queue.removeByClientId("Z"))
        assertEquals(1, queue.snapshot().size)
    }

    @Test fun removeByClientId_is_idempotent() = runBlocking {
        queue.enqueue(create("A"))
        assertTrue(queue.removeByClientId("A"))
        assertFalse(queue.removeByClientId("A"))
        assertTrue(queue.snapshot().isEmpty())
    }

    @Test fun remove_by_queue_id_still_works() = runBlocking {
        queue.enqueue(create("A"))
        assertTrue(queue.remove("q-A"))
        assertTrue(queue.snapshot().isEmpty())
    }
}
