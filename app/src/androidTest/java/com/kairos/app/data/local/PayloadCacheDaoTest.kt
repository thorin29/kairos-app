package com.kairos.app.data.local

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runtime checks for the payload cache DAO on a real (in-memory) Room 3 DB.
 * Runs instrumented (needs a device/emulator) because Room needs a Context:
 *   ./gradlew connectedDebugAndroidTest
 *
 * The primary Step-1 verification is that the module builds and KSP generates
 * the DB; these assertions confirm the DAO's read/write/scope/person/trim
 * behavior.
 */
@RunWith(AndroidJUnit4::class)
class PayloadCacheDaoTest {

    private lateinit var db: KairosDatabase
    private lateinit var dao: PayloadCacheDao

    private val household = PayloadCacheStore.HOUSEHOLD

    private fun row(
        scope: String,
        section: String,
        person: String,
        view: String,
        json: String,
        fetchedAt: Long = 0L,
    ) = PayloadCacheEntity(scope, section, person, view, json, fetchedAt)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder<KairosDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dao = db.payloadCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_then_get_roundtrips_payload() = runBlocking {
        dao.upsert(row("hostA", "calendar", household, "week|2026-09-25", "{\"v\":1}"))
        val got = dao.get("hostA", "calendar", household, "week|2026-09-25")
        assertEquals("{\"v\":1}", got?.payloadJson)
    }

    @Test
    fun upsert_replaces_same_key() = runBlocking {
        dao.upsert(row("hostA", "calendar", household, "week|2026-09-25", "{\"v\":1}", fetchedAt = 1L))
        dao.upsert(row("hostA", "calendar", household, "week|2026-09-25", "{\"v\":2}", fetchedAt = 2L))
        assertEquals("{\"v\":2}", dao.get("hostA", "calendar", household, "week|2026-09-25")?.payloadJson)
    }

    @Test
    fun scope_isolates_rows() = runBlocking {
        dao.upsert(row("hostA", "calendar", household, "main", "{\"from\":\"A\"}"))
        // Same section+view under a different scope must not read back as hostB.
        assertNull(dao.get("hostB", "calendar", household, "main"))
    }

    @Test
    fun person_isolates_rows() = runBlocking {
        // Two people, same server/section/view: distinct rows, no overwrite/leak.
        dao.upsert(row("hostA", "reading", "p1", "main", "for-p1"))
        dao.upsert(row("hostA", "reading", "p2", "main", "for-p2"))
        assertEquals("for-p1", dao.get("hostA", "reading", "p1", "main")?.payloadJson)
        assertEquals("for-p2", dao.get("hostA", "reading", "p2", "main")?.payloadJson)
        // A household lookup for the same key sees neither person's row.
        assertNull(dao.get("hostA", "reading", household, "main"))
    }

    @Test
    fun deletePerson_leaves_other_person() = runBlocking {
        dao.upsert(row("hostA", "reading", "p1", "main", "a"))
        dao.upsert(row("hostA", "reading", "p2", "main", "b"))
        dao.deletePerson("hostA", "p1")
        assertNull(dao.get("hostA", "reading", "p1", "main"))
        assertEquals("b", dao.get("hostA", "reading", "p2", "main")?.payloadJson)
    }

    @Test
    fun deleteScope_leaves_other_scope() = runBlocking {
        dao.upsert(row("hostA", "home", household, "main", "a"))
        dao.upsert(row("hostB", "home", household, "main", "b"))
        dao.deleteScope("hostA")
        assertNull(dao.get("hostA", "home", household, "main"))
        assertEquals("b", dao.get("hostB", "home", household, "main")?.payloadJson)
    }

    @Test
    fun trimSection_keeps_only_newest_per_person() = runBlocking {
        // Three dates for p1; keep the two newest by fetchedAt.
        dao.upsert(row("hostA", "calendar", "p1", "d1", "1", fetchedAt = 10L))
        dao.upsert(row("hostA", "calendar", "p1", "d2", "2", fetchedAt = 20L))
        dao.upsert(row("hostA", "calendar", "p1", "d3", "3", fetchedAt = 30L))
        // A different person's row must be untouched by p1's trim.
        dao.upsert(row("hostA", "calendar", "p2", "d1", "keep-me", fetchedAt = 5L))
        dao.trimSection("hostA", "calendar", "p1", keep = 2)
        assertNull(dao.get("hostA", "calendar", "p1", "d1"))
        assertEquals("2", dao.get("hostA", "calendar", "p1", "d2")?.payloadJson)
        assertEquals("3", dao.get("hostA", "calendar", "p1", "d3")?.payloadJson)
        assertEquals("keep-me", dao.get("hostA", "calendar", "p2", "d1")?.payloadJson)
    }

    @Test
    fun clearAll_empties_cache() = runBlocking {
        dao.upsert(row("hostA", "home", household, "main", "a"))
        dao.upsert(row("hostB", "reading", "p1", "main", "b"))
        dao.clearAll()
        assertNull(dao.get("hostA", "home", household, "main"))
        assertNull(dao.get("hostB", "reading", "p1", "main"))
    }
}
