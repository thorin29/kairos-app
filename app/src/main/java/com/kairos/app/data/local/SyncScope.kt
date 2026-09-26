package com.kairos.app.data.local

import com.kairos.app.data.remote.ApiClient

/**
 * Derives the [PayloadCacheEntity.scopeId] a cached payload belongs to.
 *
 * Scope is the server instance (full origin of the current base URL). Because a row is
 * only ever read back under the same scopeId that wrote it, pointing the app at
 * a different server yields a different scope and the old server's rows simply
 * become unreachable — a stale or foreign payload can never render. Sign-out
 * clears the table outright (see PayloadCacheDao.clearAll).
 *
 * Person-level separation within a server is handled at the key level (see
 * PayloadCacheEntity.personId / viewKey), not here.
 */
object SyncScope {
    fun scopeId(baseUrlRaw: String?): String {
        val origin = ApiClient.baseOrigin(baseUrlRaw ?: "")
        return origin?.takeIf { it.isNotBlank() } ?: "unknown"
    }
}
