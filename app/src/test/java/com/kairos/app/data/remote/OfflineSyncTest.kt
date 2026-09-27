package com.kairos.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Deterministic coverage of the offline temp-id correlation logic (0.311): how a
 * create's clientId is read from its body, and how a resolved create's real id is
 * substituted into the follow-up ops queued against its temp id.
 */
class OfflineSyncTest {
    private fun write(url: String, body: String?) =
        PendingWrite(id = "q1", method = "POST", url = url, body = body, createdAt = 0L)

    // ---- clientIdOf: the tag the interceptor stamps on a queued create ----

    @Test fun clientId_parsed_from_create_body() {
        assertEquals("abc-123", OfflineSync.clientIdOf("""{"title":"x","clientId":"abc-123"}"""))
    }

    @Test fun clientId_null_when_absent_or_empty() {
        assertNull(OfflineSync.clientIdOf("""{"title":"x"}"""))
        assertNull(OfflineSync.clientIdOf(null))
        assertNull(OfflineSync.clientIdOf(""))
    }

    @Test fun clientId_tolerates_whitespace() {
        assertEquals("id9", OfflineSync.clientIdOf("""{ "clientId" : "id9" }"""))
    }

    // ---- rewrite: temp -> real across a follow-up op's path and body ----

    @Test fun rewrite_empty_map_returns_same_instance() {
        val w = write("/tasks/temp-A/complete", null)
        assertSame(w, OfflineSync.rewrite(w, emptyMap()))
    }

    @Test fun rewrite_no_match_returns_same_instance() {
        val w = write("/tasks/temp-A/complete", """{"id":"temp-A"}""")
        assertSame(w, OfflineSync.rewrite(w, mapOf("temp-B" to "real-B")))
    }

    @Test fun rewrite_substitutes_in_url_and_body() {
        val w = write("/grocery/temp-A/move", """{"id":"temp-A","storeId":"s2"}""")
        val r = OfflineSync.rewrite(w, mapOf("temp-A" to "real-A"))
        assertEquals("/grocery/real-A/move", r.url)
        assertEquals("""{"id":"real-A","storeId":"s2"}""", r.body)
    }

    @Test fun rewrite_applies_only_matching_mappings() {
        val w = write("/x/temp-A?ref=temp-B", """{"a":"temp-A","c":"temp-C"}""")
        val r = OfflineSync.rewrite(w, mapOf("temp-A" to "RA", "temp-B" to "RB"))
        assertEquals("/x/RA?ref=RB", r.url)
        // temp-C had no mapping this pass, so it is left untouched.
        assertEquals("""{"a":"RA","c":"temp-C"}""", r.body)
    }
}
