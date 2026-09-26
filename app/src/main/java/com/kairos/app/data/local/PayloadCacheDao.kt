package com.kairos.app.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [PayloadCacheEntity]. Room 3 requires suspend functions for
 * one-shot access and reserves non-suspend returns for observable types (Flow).
 *
 * personId is part of the key on every read/write/trim so one person's rows can
 * never satisfy another person's lookup.
 */
@Dao
interface PayloadCacheDao {

    /** Insert or replace the row for (scopeId, section, personId, viewKey). */
    @Upsert
    suspend fun upsert(row: PayloadCacheEntity)

    /** The cached payload for a key, or null if nothing has been stored yet. */
    @Query(
        "SELECT * FROM payload_cache " +
            "WHERE scopeId = :scopeId AND section = :section " +
            "AND personId = :personId AND viewKey = :viewKey LIMIT 1",
    )
    suspend fun get(
        scopeId: String,
        section: String,
        personId: String,
        viewKey: String,
    ): PayloadCacheEntity?

    /**
     * Observe a key so a screen re-renders when a background refresh writes a
     * newer payload. Emits null until the first write for that key.
     */
    @Query(
        "SELECT * FROM payload_cache " +
            "WHERE scopeId = :scopeId AND section = :section " +
            "AND personId = :personId AND viewKey = :viewKey LIMIT 1",
    )
    fun observe(
        scopeId: String,
        section: String,
        personId: String,
        viewKey: String,
    ): Flow<PayloadCacheEntity?>

    /**
     * Keep only the [keep] most recently fetched rows in a (scope, section,
     * person), so month-by-month calendar browsing (each date is its own
     * viewKey) can't grow the cache without bound. Trimming is per person so a
     * busy section for one person can't evict a quieter person's rows. Called
     * after every write.
     */
    @Query(
        "DELETE FROM payload_cache " +
            "WHERE scopeId = :scopeId AND section = :section AND personId = :personId " +
            "AND viewKey NOT IN (" +
            "  SELECT viewKey FROM payload_cache " +
            "  WHERE scopeId = :scopeId AND section = :section AND personId = :personId " +
            "  ORDER BY fetchedAt DESC LIMIT :keep" +
            ")",
    )
    suspend fun trimSection(scopeId: String, section: String, personId: String, keep: Int)

    /** Remove every row for one person within a scope (person-switch cleanup). */
    @Query("DELETE FROM payload_cache WHERE scopeId = :scopeId AND personId = :personId")
    suspend fun deletePerson(scopeId: String, personId: String)

    /** Remove every row for a scope (e.g. leaving/switching a server). */
    @Query("DELETE FROM payload_cache WHERE scopeId = :scopeId")
    suspend fun deleteScope(scopeId: String)

    /** Wipe the whole cache (sign-out). */
    @Query("DELETE FROM payload_cache")
    suspend fun clearAll()
}
