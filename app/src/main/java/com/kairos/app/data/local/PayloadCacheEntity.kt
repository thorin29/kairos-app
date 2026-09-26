package com.kairos.app.data.local

import androidx.room3.Entity

/**
 * One cached server-rendered payload, durable across cold starts.
 *
 * Kairos screens are rendered server-side (the calendar view, the home
 * dashboard, a reading list all arrive as ready-to-draw JSON with colors,
 * headings and personal scoping already resolved). So the durable local layer
 * is deliberately NOT a normalized per-screen schema — reproducing the web's
 * view logic on-device would be a large, bug-prone duplication. Instead we keep
 * the last payload verbatim, keyed by where it belongs, and re-render from it
 * instantly on return or on a fresh launch, then refresh from the API in the
 * background. This is the durable twin of the in-memory ScreenSnapshots.
 *
 * Primary key = (scopeId, section, personId, viewKey):
 *  - [scopeId]  which server this belongs to (see [SyncScope]). Rows from a
 *               different server are never read, so switching servers is safe by
 *               construction; sign-out wipes the table outright.
 *  - [section]  the screen family, e.g. "calendar", "home", "reading".
 *  - [personId] the owning person's id for person-scoped data, or the empty
 *               string ([PayloadCacheStore.HOUSEHOLD]) for household-wide data.
 *               It is IN THE KEY, not loose metadata, so two people enrolled on
 *               the same server can never overwrite or read each other's payload
 *               — the isolation is enforced by the schema, not by remembering to
 *               fold the person into [viewKey].
 *  - [viewKey]  the variant within a section, e.g. "week|2026-09-25". Sections
 *               with a single view use a constant like "main".
 *
 * [payloadJson] is the exact response body; [fetchedAt] is epoch millis, used
 * both for staleness display and for trimming a section to its newest rows.
 */
@Entity(
    tableName = "payload_cache",
    primaryKeys = ["scopeId", "section", "personId", "viewKey"],
)
data class PayloadCacheEntity(
    val scopeId: String,
    val section: String,
    val personId: String,
    val viewKey: String,
    val payloadJson: String,
    val fetchedAt: Long,
)
