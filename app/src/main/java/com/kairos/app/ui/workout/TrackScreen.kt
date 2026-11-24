package com.kairos.app.ui.workout

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.data.remote.dto.TrackSaveRequest
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos

internal fun trackIcon(key: String): ImageVector = when (key) {
    "walk" -> KairosIcons.Walk
    "ruck" -> KairosIcons.Backpack
    "hike" -> KairosIcons.Mountain
    else -> KairosIcons.Run
}

/** Go for a… (v0.557): Walk, Run, Ruck, Hike. */
@Composable
internal fun GoForDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "Start a…",
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TRACK_KINDS.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { k ->
                        Column(
                            Modifier
                                .weight(1f)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .clickable { onPick(k.key) }
                                .padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(trackIcon(k.key), contentDescription = null, tint = primary, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(k.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            // A trip over days (v0.563): opens the calendar's New event on the
            // Backpacking type; the event is the trip.
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { onPick("backpack") }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(KairosIcons.Backpack, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
                Column(Modifier.padding(start = 8.dp)) {
                    Text("Backpacking trip", style = MaterialTheme.typography.labelLarge)
                    Text("Plan it on the calendar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun timeOfDay(ms: Long): String =
    DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

private const val PREFS = "track"
private const val RUCK_KEY = "ruck_lb"

private fun lastRuckLb(context: Context): String =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(RUCK_KEY, "") ?: ""

private fun rememberRuckLb(context: Context, lb: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(RUCK_KEY, lb).apply()
}

/**
 * A walk, run, ruck or hike (v0.557): Start (pack weight first for a ruck),
 * then the time, distance, pace and route while it's going, with Pause (it
 * asks) and Finish; then the summary to Save or Discard, or Resume.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(activity: String, onBack: () -> Unit, onSaved: () -> Unit) {
    // A trip is planned on the calendar now (v0.563), not started here.
    if (activity == "backpack" && ActivityTrack.session.value == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val session by ActivityTrack.session.collectAsState()
    val kind = session?.activity ?: activity.ifBlank { "walk" }
    var load by remember { mutableStateOf(lastRuckLb(context)) }
    var askPause by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun begin() {
        val lb = if (kind == "ruck") load.toDoubleOrNull()?.takeIf { it > 0 } else null
        if (kind == "ruck") rememberRuckLb(context, load)
        ActivityTrack.start(context, kind, lb)
    }

    // Location first, then (Android 13+) notifications for the time and
    // Finish while the screen is off; one question at a time.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { begin() }
    fun notifyThenBegin() {
        if (Build.VERSION.SDK_INT >= 33 && !com.kairos.app.data.notifications.Notifications.hasPermission(context)) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            begin()
        }
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) notifyThenBegin() else error = "Location is off for Kairos. Turn it on in the phone's settings to track."
    }
    fun startPressed() {
        error = null
        if (TrackService.hasLocation(context)) notifyThenBegin() else askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    LaunchedEffect(session?.endedAt, session?.pausedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val s = session
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trackLabel(kind)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(KairosIcons.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                s == null -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(trackIcon(kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                    }
                    if (kind == "ruck") {
                        OutlinedTextField(
                            value = load,
                            onValueChange = { v -> load = v.filter { it.isDigit() || it == '.' }.take(5) },
                            label = { Text("Pack weight (lb)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Button(onClick = { startPressed() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Start") }
                }
                s.endedAt == null -> {
                    val moving = s.movingMs(now)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(trackIcon(s.activity), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(
                            trackLabel(s.activity) + (s.load?.let { " · ${fmtLb(it)} lb" } ?: ""),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 6.dp).weight(1f),
                        )
                        Text("Started ${timeOfDay(s.startedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        trackClock(moving),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (s.paused) {
                        Text("Paused", color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Stats(dist = s.distanceM, moving = moving)
                    RouteTrace(s.points)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (s.paused) {
                            OutlinedButton(onClick = { ActivityTrack.resume(context) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Resume") }
                        } else {
                            OutlinedButton(onClick = { askPause = true }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Pause") }
                        }
                        Button(onClick = { ActivityTrack.finish(context) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Finish") }
                    }
                }
                else -> {
                    val moving = s.movingMs(now)
                    Text(
                        trackLabel(s.activity) + (s.load?.let { " · ${fmtLb(it)} lb" } ?: ""),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${timeOfDay(s.startedAt)} – ${timeOfDay(s.endedAt ?: now)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Stats(dist = s.distanceM, moving = moving, time = true)
                    RouteTrace(s.points)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        enabled = !saving,
                        onClick = {
                            saving = true
                            error = null
                            scope.launch {
                                val r = runCatching { container.sessionRepository.saveTrack(requestFor(s)) }
                                saving = false
                                if (r.isSuccess) {
                                    ActivityTrack.clear(context)
                                    onSaved()
                                } else {
                                    error = r.exceptionOrNull()?.message ?: "Couldn't save. Try again."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (saving) "Saving…" else "Save") }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { ActivityTrack.resume(context) }, enabled = !saving, modifier = Modifier.weight(1f)) { Text("Resume") }
                        TextButton(onClick = { askDiscard = true }, enabled = !saving, modifier = Modifier.weight(1f)) {
                            Text("Discard", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (s == null) error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (askPause) {
        AnimatedDialog(
            onDismissRequest = { askPause = false },
            title = "Pause tracking?",
            confirmButton = {
                TextButton(onClick = {
                    askPause = false
                    ActivityTrack.pause(context)
                }) { Text("Pause") }
            },
            dismissButton = { TextButton(onClick = { askPause = false }) { Text("Keep going") } },
            content = {},
        )
    }
    if (askDiscard) {
        AnimatedDialog(
            onDismissRequest = { askDiscard = false },
            title = "Discard this ${trackLabel(kind).lowercase()}?",
            confirmButton = {
                TextButton(onClick = {
                    askDiscard = false
                    ActivityTrack.clear(context)
                    onBack()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Keep") } },
            content = {},
        )
    }
}

private fun fmtLb(lb: Double): String = if (lb % 1.0 == 0.0) lb.toLong().toString() else lb.toString()

@Composable
private fun Stats(dist: Double, moving: Long, time: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        if (time) Stat(trackClock(moving), "time", Modifier.weight(1f))
        Stat(trackMiles(dist), "distance", Modifier.weight(1f))
        Stat(pacePerMile(moving, dist), "min / mi", Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, small: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = if (small) 16.sp else 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The route so far, drawn to fit (no map behind it). */
@Composable
private fun RouteTrace(points: List<TrackPoint>) {
    val color = MaterialTheme.colorScheme.primary
    OutlinedCard(Modifier.fillMaxWidth().height(200.dp)) {
        if (points.size < 2) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (points.isEmpty()) "Finding GPS…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@OutlinedCard
        }
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val k = cos(Math.toRadians(points.first().lat))
            val xs = points.map { it.lng * k }
            val ys = points.map { it.lat }
            val minX = xs.min()
            val minY = ys.min()
            val span = maxOf(xs.max() - minX, ys.max() - minY, 1e-5)
            val scale = minOf(size.width, size.height) / span
            val offX = (size.width - (xs.max() - minX) * scale) / 2
            val offY = (size.height - (ys.max() - minY) * scale) / 2
            fun at(i: Int) = Offset(
                (offX + (xs[i] - minX) * scale).toFloat(),
                (size.height - offY - (ys[i] - minY) * scale).toFloat(),
            )
            val path = Path()
            points.indices.forEach { i ->
                val o = at(i)
                if (i == 0 || points[i].seg != points[i - 1].seg) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, radius = 6.dp.toPx(), center = at(points.lastIndex))
        }
    }
}

private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

/** The save request: the route thinned to what the server keeps. */
internal fun requestFor(s: TrackSession): TrackSaveRequest {
    val end = s.endedAt ?: System.currentTimeMillis()
    val pts = s.points
    val max = 4_000
    val kept = if (pts.size <= max) pts else (0 until max - 1).map { pts[(it.toLong() * pts.size / (max - 1)).toInt()] } + pts.last()
    return TrackSaveRequest(
        clientId = s.clientId,
        activity = s.activity,
        startAt = ISO.format(Instant.ofEpochMilli(s.startedAt)),
        endAt = ISO.format(Instant.ofEpochMilli(end)),
        movingSec = (s.movingMs(end) / 1000).toInt().coerceAtLeast(1),
        distanceM = Math.round(s.distanceM * 10) / 10.0,
        load = s.load,
        path = kept.map { listOf(it.lat, it.lng, it.t.toDouble()) },
    )
}


/**
 * On a backpacking trip today (v0.558): the day and miles so far, "Hiking
 * today?" until it's answered, and the phone's GPS for the trip (v0.559).
 * After a trip, its summary for two weeks. Shown on Workouts.
 */
@Composable
internal fun BackpackCard(
    refreshKey: Int,
    onOpenMap: () -> Unit = {},
    onOpenTrip: (eventId: String, dayISO: String) -> Unit = { _, _ -> },
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<com.kairos.app.data.remote.dto.BackpackDto?>(null) }
    val gps by TripTrack.state.collectAsState()
    var finished by remember { mutableStateOf(TripTrack.finishedToday(context)) }
    LaunchedEffect(refreshKey) {
        data = runCatching { container.sessionRepository.backpackingAll() }.getOrNull()
    }
    fun gpsOn(t: com.kairos.app.data.remote.dto.BackpackTodayDto) {
        TripTrack.start(context, java.time.LocalDate.now().plusDays((t.of - t.day).toLong()).toString())
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val t = data?.today
        if (ok && t != null) gpsOn(t)
    }
    fun startGps(t: com.kairos.app.data.remote.dto.BackpackTodayDto) {
        if (TrackService.hasLocation(context)) gpsOn(t) else askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    fun send(body: com.kairos.app.data.remote.dto.BackpackRequest) {
        scope.launch {
            runCatching { container.sessionRepository.answerBackpacking(body) }
                .onSuccess { today -> data = data?.copy(today = today) ?: com.kairos.app.data.remote.dto.BackpackDto(today = today) }
        }
    }
    fun answer(hiking: Boolean) {
        send(com.kairos.app.data.remote.dto.BackpackRequest(hiking = hiking))
        val t = data?.today
        if (hiking && t != null && gps == null) startGps(t)
    }
    val t = data?.today
    val last = data?.lastTrip
    if (t == null && last == null) return
    if (t != null) {
        // The trip tile (v0.563): the program tile's band on a light shade of
        // the theme color, so it stands out on a trip day. No End: the trip
        // ends with its calendar event, or Finished on the last day.
        val shape = RoundedCornerShape(12.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), shape),
        ) {
            TileHeader(kind = "Trip", title = t.title?.ifBlank { null } ?: "Backpacking", trailing = "Day ${t.day} of ${t.of}")
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val g = gps
                val lastDay = t.day >= t.of
                Text(
                    String.format(Locale.US, "%.1f mi today", t.miles) + " \u00b7 " + when {
                        finished -> "finished"
                        g == null -> "GPS off"
                        g.mode == TripMode.CAMP -> "at camp"
                        g.mode == TripMode.STOPPED -> "GPS on \u00b7 stopped"
                        else -> "GPS recording"
                    } + (if (g != null && g.unsent > 0) " \u00b7 ${g.unsent} to send" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!finished) {
                    when (t.status) {
                        null, "asked" -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Hiking today?", modifier = Modifier.weight(1f))
                            TextButton(onClick = { answer(false) }) { Text("No") }
                            Button(onClick = { answer(true) }) { Text("Yes") }
                        }
                        "skip" -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Not hiking today", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { answer(true) }) { Text("I am") }
                        }
                        else -> {}
                    }
                }
                val buttons = buildList {
                    if (!finished) {
                        when {
                            g == null -> add(TileButton(label = "Track", icon = KairosIcons.Run, onClick = { startGps(t) }))
                            g.mode == TripMode.CAMP -> add(TileButton(label = "Hike", icon = KairosIcons.Run, onClick = { TripTrack.hike(context) }))
                            else -> add(TileButton(label = "Pause", icon = KairosIcons.Pause, onClick = { TripTrack.end(context) }))
                        }
                        if (lastDay) {
                            add(
                                TileButton(label = "Finished", icon = KairosIcons.Flag, onClick = {
                                    TripTrack.markFinished(context)
                                    finished = true
                                    send(com.kairos.app.data.remote.dto.BackpackRequest(finish = true))
                                }),
                            )
                        } else if (g != null && g.mode != TripMode.CAMP) {
                            add(TileButton(label = "Camp", icon = KairosIcons.Tent, onClick = { TripTrack.camp(context) }))
                        }
                    }
                    add(TileButton(label = "Map", icon = KairosIcons.Map, onClick = onOpenMap))
                    t.eventId?.let { id -> add(TileButton(label = "Trip", icon = KairosIcons.Calendar, onClick = { onOpenTrip(id, t.date) })) }
                }
                TileButtons(buttons = buttons)
            }
        }
        return
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (last != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(KairosIcons.Backpack, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        "Backpacking trip \u00b7 ${tripDates(last.from, last.to)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    Stat(String.format(Locale.US, "%.1f", last.miles), "miles", Modifier.weight(1f), small = true)
                    Stat(hoursMinutes(last.minutes), "hiking", Modifier.weight(1f), small = true)
                    Stat(last.gainFt?.let { "%,d".format(Locale.US, it) } ?: "\u2013", "ft up", Modifier.weight(1f), small = true)
                    Stat(last.lossFt?.let { "%,d".format(Locale.US, it) } ?: "\u2013", "ft down", Modifier.weight(1f), small = true)
                }
                Text(
                    "${last.days} ${if (last.days == 1) "day" else "days"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Dec 4 – 7" or "Nov 29 – Dec 2". */
private fun tripDates(from: String, to: String): String = runCatching {
    val a = java.time.LocalDate.parse(from)
    val b = java.time.LocalDate.parse(to)
    val m = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    if (a == b) a.format(m) else if (a.month == b.month) "${a.format(m)} \u2013 ${b.dayOfMonth}" else "${a.format(m)} \u2013 ${b.format(m)}"
}.getOrDefault("$from \u2013 $to")

/** "14 h 10 m". */
private fun hoursMinutes(min: Int): String = if (min >= 60) "${min / 60} h ${min % 60} m" else "$min m"
