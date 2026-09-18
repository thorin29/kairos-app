package com.kairos.app.data.diag

import android.util.Log
import com.kairos.app.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A tiny, always-on UI breadcrumb trail: the last few navigation, dialog, and
 * session events, kept in memory AND persisted to DataStore. After a "white
 * screen" the user can close and reopen the app (which recovers) and still read
 * what led up to it under Settings -> Diagnostics — no logcat access needed.
 *
 * Records only the TYPE of screen/dialog/action, never user data, so it is safe
 * to keep on in release and to show on a diagnostics screen.
 */
object Breadcrumbs {
    private const val MAX = 30
    private val ring = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var store: SettingsStore? = null
    private var scope: CoroutineScope? = null

    /** Wire persistence and load the previous session's crumbs. Call once at
     *  startup (from AppContainer), before the UI comes up. */
    fun init(store: SettingsStore, scope: CoroutineScope) {
        this.store = store
        this.scope = scope
        scope.launch {
            val saved = runCatching { store.uiBreadcrumbs() }.getOrNull()
                ?.lineSequence()?.filter { it.isNotBlank() }?.toList()
                ?: return@launch
            synchronized(ring) {
                // Put the previous session's crumbs before anything logged so far
                // this session (unlikely to exist yet, but keep order if it does).
                saved.asReversed().forEach { ring.addFirst(it) }
                while (ring.size > MAX) ring.removeFirst()
            }
        }
    }

    /** Record one breadcrumb (e.g. "nav -> HomeSchoolWork", "open ScheduleDetail").
     *  Cheap and safe to call from any thread or composable. */
    fun drop(msg: String) {
        Log.i("KairosUI", msg)
        val line = "${fmt.format(Date())}  $msg"
        val snapshot: String
        synchronized(ring) {
            ring.addLast(line)
            while (ring.size > MAX) ring.removeFirst()
            snapshot = ring.joinToString("\n")
        }
        val s = store ?: return
        scope?.launch { runCatching { s.setUiBreadcrumbs(snapshot) } }
    }

    /** The current trail, oldest first, for the diagnostics screen. */
    fun snapshot(): List<String> = synchronized(ring) { ring.toList() }
}
