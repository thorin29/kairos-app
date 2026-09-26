package com.kairos.app.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The app-facing API over [PayloadCacheDao]. It injects the current [SyncScope]
 * so callers (ViewModels/repositories) work in terms of (section, personId,
 * viewKey) without threading a scope id through everything.
 *
 * [personId] is a required argument on every call — there is no default — so a
 * caller must decide explicitly whether a payload is household-wide
 * ([HOUSEHOLD]) or belongs to a person (their id). That, plus personId being in
 * the DB primary key, makes a cross-person leak structurally impossible rather
 * than a convention to remember.
 *
 * Nothing in the app reads or writes this yet — it's wired lazily in
 * AppContainer and lit up screen-by-screen (calendar first).
 */
class PayloadCacheStore(
    private val dao: PayloadCacheDao,
    private val scopeProvider: () -> String,
) {
    /** The last stored payload for a key, or null. */
    suspend fun read(section: String, viewKey: String, personId: String): String? =
        dao.get(scopeProvider(), section, personId, viewKey)?.payloadJson

    /** Observe a key; emits the payload string (or null) and re-emits on refresh. */
    fun observe(section: String, viewKey: String, personId: String): Flow<String?> =
        dao.observe(scopeProvider(), section, personId, viewKey).map { it?.payloadJson }

    /**
     * Store a freshly fetched payload and trim the (section, person) to [keep]
     * newest entries. Pass [HOUSEHOLD] for shared data, or the owning person's
     * id for person-scoped data.
     */
    suspend fun write(
        section: String,
        viewKey: String,
        personId: String,
        json: String,
        keep: Int = DEFAULT_KEEP,
    ) {
        val scope = scopeProvider()
        dao.upsert(
            PayloadCacheEntity(
                scopeId = scope,
                section = section,
                personId = personId,
                viewKey = viewKey,
                payloadJson = json,
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        dao.trimSection(scope, section, personId, keep)
    }

    /** Drop one person's rows in the current scope (person-switch cleanup). */
    suspend fun clearPerson(personId: String) = dao.deletePerson(scopeProvider(), personId)

    /** Drop the current scope's rows (leaving/switching a server). */
    suspend fun clearScope() = dao.deleteScope(scopeProvider())

    /** Typed read: decode the cached payload for a key with [deser], or null. */
    suspend fun <T> readAs(
        section: String,
        viewKey: String,
        personId: String,
        deser: kotlinx.serialization.DeserializationStrategy<T>,
    ): T? = read(section, viewKey, personId)?.let {
        runCatching { com.kairos.app.data.remote.ApiClient.json.decodeFromString(deser, it) }.getOrNull()
    }

    /** Typed write: serialize [value] with [ser] and store it under the key. */
    suspend fun <T> writeAs(
        section: String,
        viewKey: String,
        personId: String,
        ser: kotlinx.serialization.SerializationStrategy<T>,
        value: T,
        keep: Int = DEFAULT_KEEP,
    ) = write(section, viewKey, personId, com.kairos.app.data.remote.ApiClient.json.encodeToString(ser, value), keep)

    /** Wipe everything (sign-out). */
    suspend fun clearAll() = dao.clearAll()

    companion object {
        /** personId value for household-wide (non-person-scoped) payloads. */
        const val HOUSEHOLD = ""

        /** Enough for a couple of months of calendar browsing per section. */
        const val DEFAULT_KEEP = 24
    }
}
