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
    // 150 lines (v0.459; it was 30, which a night of widget draws filled).
    private const val MAX = 150
    private val ring = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private val repeat = Regex("""^(\S+ \S+)  (.*?)(?: ×(\d+) since (\S+ \S+))?$""")
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
        val now = fmt.format(Date())
        val snapshot: String
        synchronized(ring) {
            // The same event again straight after (the widget's half-hourly draw
            // overnight) updates one line, "×N since <first>", instead of pushing
            // older lines out (v0.459).
            val last = ring.lastOrNull()?.let { repeat.matchEntire(it) }
            if (last != null && last.groupValues[2] == msg) {
                val n = (last.groupValues[3].toIntOrNull() ?: 1) + 1
                val since = last.groupValues[4].ifBlank { last.groupValues[1] }
                ring.removeLast()
                ring.addLast("$now  $msg ×$n since $since")
            } else {
                ring.addLast("$now  $msg")
            }
            while (ring.size > MAX) ring.removeFirst()
            snapshot = ring.joinToString("\n")
        }
        val s = store ?: return
        scope?.launch { runCatching { s.setUiBreadcrumbs(snapshot) } }
    }

    /** The current trail, oldest first, for the diagnostics screen. */
    fun snapshot(): List<String> = synchronized(ring) { ring.toList() }
}
