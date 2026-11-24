package com.kairos.app.ui.workout

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Go for a walk, run, ruck or hike (v0.557): the outing being tracked. The
 * phone's own GPS (TrackService) feeds points in while it runs; the time
 * counts from Start, less any time paused. Kept in a file as it goes, so an
 * app closed by the system mid-run picks up where it was. Saved, it's a cardio
 * workout of its own on the server, its route shown on Family map History.
 */

@Serializable
internal data class TrackPoint(
    val lat: Double,
    val lng: Double,
    /** Wall-clock millis. */
    val t: Long,
    /** Bumped at each resume: no distance is counted across a pause. */
    val seg: Int = 0,
)

@Serializable
internal data class TrackSession(
    val clientId: String,
    /** walk, run, ruck or hike. */
    val activity: String,
    /** Pack weight in lb (ruck). */
    val load: Double? = null,
    val startedAt: Long,
    /** Time spent paused before the current pause. */
    val pausedMs: Long = 0,
    /** Set while paused. */
    val pausedAt: Long? = null,
    /** Set at Finish (the summary shows; Resume clears it). */
    val endedAt: Long? = null,
    val distanceM: Double = 0.0,
    val seg: Int = 0,
    val points: List<TrackPoint> = emptyList(),
) {
    val paused: Boolean get() = pausedAt != null

    /** Time moving, up to now (or Finish). */
    fun movingMs(now: Long = System.currentTimeMillis()): Long {
        val end = endedAt ?: now
        val pausedNow = pausedAt?.let { (end - it).coerceAtLeast(0) } ?: 0
        return (end - startedAt - pausedMs - pausedNow).coerceAtLeast(0)
    }
}

internal data class TrackKind(val key: String, val label: String, val noun: String)

internal val TRACK_KINDS = listOf(
    TrackKind(key = "walk", label = "Walk", noun = "walk"),
    TrackKind(key = "run", label = "Run", noun = "run"),
    TrackKind(key = "ruck", label = "Ruck", noun = "ruck"),
    TrackKind(key = "hike", label = "Hike", noun = "hike"),
)

internal fun trackLabel(key: String): String = TRACK_KINDS.firstOrNull { it.key == key }?.label ?: "Workout"

internal object ActivityTrack {
    private val _session = MutableStateFlow<TrackSession?>(null)
    val session: StateFlow<TrackSession?> = _session.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private var lastWrite = 0L
    private var restored = false

    /** Fixes fuzzier than this are skipped. */
    private const val MAX_ACCURACY_M = 30f
    /** Moves shorter than this are GPS wobble standing still. */
    private const val MIN_STEP_M = 5.0

    private fun file(context: Context) = File(context.filesDir, "track.json")

    /** Faster than this on foot is a GPS jump. */
    private fun maxSpeed(activity: String): Double = if (activity == "run") 9.0 else 4.5

    fun start(context: Context, activity: String, load: Double?) {
        if (_session.value != null) return
        _session.value = TrackSession(
            clientId = UUID.randomUUID().toString(),
            activity = activity,
            load = load,
            startedAt = System.currentTimeMillis(),
        )
        save(context, force = true)
        startService(context)
    }

    fun pause(context: Context) {
        val s = _session.value ?: return
        if (s.paused || s.endedAt != null) return
        _session.value = s.copy(pausedAt = System.currentTimeMillis())
        save(context, force = true)
        TrackService.refresh(context)
    }

    fun resume(context: Context) {
        val s = _session.value ?: return
        val now = System.currentTimeMillis()
        val pausedFor = s.pausedAt?.let { now - it } ?: 0
        val endedFor = if (s.pausedAt == null) s.endedAt?.let { now - it } ?: 0 else 0
        _session.value = s.copy(
            pausedAt = null,
            endedAt = null,
            pausedMs = s.pausedMs + pausedFor + endedFor,
            seg = s.seg + 1,
        )
        save(context, force = true)
        startService(context)
    }

    /** Finish: the summary shows; Save or Discard from there (or Resume). */
    fun finish(context: Context) {
        val s = _session.value ?: return
        if (s.endedAt != null) return
        _session.value = s.copy(endedAt = s.pausedAt ?: System.currentTimeMillis())
        save(context, force = true)
        context.stopService(Intent(context, TrackService::class.java))
    }

    fun clear(context: Context) {
        _session.value = null
        runCatching { file(context).delete() }
        context.stopService(Intent(context, TrackService::class.java))
    }

    /** A new fix from the GPS. Returns true when it was kept. */
    fun onLocation(context: Context, lat: Double, lng: Double, accuracy: Float?, t: Long): Boolean {
        val s = _session.value ?: return false
        if (s.paused || s.endedAt != null) return false
        if (accuracy != null && accuracy > MAX_ACCURACY_M) return false
        val last = s.points.lastOrNull()
        var add = 0.0
        if (last != null) {
            val d = trackMetres(last.lat, last.lng, lat, lng)
            if (last.seg == s.seg) {
                if (d < MIN_STEP_M) return false
                val secs = ((t - last.t) / 1000.0).coerceAtLeast(0.5)
                if (d / secs > maxSpeed(s.activity)) return false
                add = d
            }
        }
        _session.value = s.copy(
            distanceM = s.distanceM + add,
            points = s.points + TrackPoint(lat = lat, lng = lng, t = t, seg = s.seg),
        )
        save(context, force = false)
        return true
    }

    /** Back after the app was closed: the outing in the file, and its GPS
     *  started again if it was still going. */
    fun restore(context: Context) {
        if (restored) return
        restored = true
        if (_session.value != null) return
        val s = runCatching { json.decodeFromString(TrackSession.serializer(), file(context).readText()) }.getOrNull() ?: return
        _session.value = s
        if (!s.paused && s.endedAt == null) startService(context)
    }

    private fun startService(context: Context) {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, TrackService::class.java)) }
    }

    private fun save(context: Context, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastWrite < 15_000) return
        lastWrite = now
        val s = _session.value ?: return
        runCatching { file(context).writeText(json.encodeToString(TrackSession.serializer(), s)) }
    }
}

/** Metres between two points (haversine). */
internal fun trackMetres(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lng2 - lng1)
    val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

internal fun trackMiles(m: Double): String = String.format(java.util.Locale.US, "%.2f mi", m / 1609.344)

/** "17:33 /mi"; "–" until there's a tenth of a mile to go on. */
internal fun pacePerMile(movingMs: Long, m: Double): String {
    if (m < 160.0 || movingMs <= 0) return "–"
    val secPerMile = (movingMs / 1000.0) / (m / 1609.344)
    if (secPerMile > 99 * 60) return "–"
    val total = secPerMile.toLong()
    return String.format(java.util.Locale.US, "%d:%02d", total / 60, total % 60)
}

/** "32:18" or "1:04:09". */
internal fun trackClock(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s) else String.format(java.util.Locale.US, "%d:%02d", m, s)
}
