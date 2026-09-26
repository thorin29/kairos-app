package com.kairos.app.data.local

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Stand-in for a screen's payload DTO. */
@Serializable
data class CacheTestItem(val v: Int)

/**
 * Pins the invariant the cache-consistency work relies on: once a change is
 * committed to the durable cache (which every ViewModel now does through
 * freshData()/writeAs after a successful mutation), a later read — e.g. a
 * cold-start seed with the server unavailable — returns the NEW state, never the
 * pre-change payload. Exercises the typed PayloadCacheStore layer the VMs use.
 *
 * Instrumented (Room needs a Context): ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class PayloadCacheStoreTest {

    private lateinit var db: KairosDatabase
    private lateinit var store: PayloadCacheStore
    private val ser = CacheTestItem.serializer()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder<KairosDatabase>(ctx)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        // Fixed scope; person is passed per call, as the VMs do.
        store = PayloadCacheStore(db.payloadCacheDao(), scopeProvider = { "host" })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun committed_change_is_what_a_later_read_returns() = runBlocking {
        // Old state cached (initial load).
        store.writeAs("reading", "main", "p1", ser, CacheTestItem(1))
        assertEquals(1, store.readAs("reading", "main", "p1", ser)?.v)

        // A successful mutation commits the new state to the cache.
        store.writeAs("reading", "main", "p1", ser, CacheTestItem(2))

        // A fresh read (the cold-start seed) must return the new state, not the old.
        assertEquals(2, store.readAs("reading", "main", "p1", ser)?.v)
    }

    @Test
    fun person_scope_isolates_typed_rows() = runBlocking {
        store.writeAs("reading", "main", "p1", ser, CacheTestItem(1))
        store.writeAs("reading", "main", "p2", ser, CacheTestItem(2))
        assertEquals(1, store.readAs("reading", "main", "p1", ser)?.v)
        assertEquals(2, store.readAs("reading", "main", "p2", ser)?.v)
        assertNull(store.readAs("reading", "main", "p3", ser))
    }

    @Test
    fun clearAll_drops_committed_state() = runBlocking {
        store.writeAs("reading", "main", "p1", ser, CacheTestItem(1))
        store.clearAll()
        assertNull(store.readAs("reading", "main", "p1", ser))
    }
}
