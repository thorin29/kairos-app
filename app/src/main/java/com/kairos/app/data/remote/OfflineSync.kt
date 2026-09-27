package com.kairos.app.data.remote

/**
 * Pure helpers for the offline write queue's temp-id correlation, factored out of
 * the interceptor and the replayer so the state-machine logic can be unit-tested
 * without a live server, a DataStore, or Android.
 *
 * The contract: a create carries a durable `clientId` in its JSON body — the bare
 * uuid of the new item's `temp-<uuid>` id. That id is what the ViewModel shows
 * optimistically, what `applyPending` rebuilds after a restart, and what any
 * follow-up op (move/edit/complete/delete) references while still offline. Once
 * the create replays and the server returns the real id, [rewrite] swaps the temp
 * id for it in every later queued op.
 */
object OfflineSync {
    private val CLIENT_ID = Regex("\"clientId\"\\s*:\\s*\"([^\"]+)\"")

    /** The clientId a create carries in its JSON body, or null if it has none
     *  (i.e. the write isn't a create). */
    fun clientIdOf(body: String?): String? =
        body?.let { CLIENT_ID.find(it)?.groupValues?.get(1) }

    /** Rewrite any temp id in a queued write's path and body onto the real server
     *  id a create has since resolved to. Returns the write unchanged when the map
     *  is empty or nothing matches. */
    fun rewrite(write: PendingWrite, idMap: Map<String, String>): PendingWrite {
        if (idMap.isEmpty()) return write
        var url = write.url
        var body = write.body
        for ((temp, real) in idMap) {
            url = url.replace(temp, real)
            body = body?.replace(temp, real)
        }
        return if (url == write.url && body == write.body) write
        else write.copy(url = url, body = body)
    }
}
