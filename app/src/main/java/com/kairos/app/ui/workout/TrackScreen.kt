package com.kairos.app.ui.workout

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
        title = "Go for a…",
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
            // A trip over days (v0.558): tracked from Family map locations,
            // not the phone's GPS; this only says you're going.
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
                Text("Backpacking trip", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
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
    if (activity == "backpack" && ActivityTrack.session.value == null) {
        BackpackStart(onBack = onBack, onStarted = onSaved)
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
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
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
 * Backpacking trip (v0.558): the day you'll be back, then Start trip. Kairos
 * makes the Backpacking event; each day's hiking comes from Family map
 * locations and is logged as that day's workout.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BackpackStart(onBack: () -> Unit, onStarted: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val today = remember { java.time.LocalDate.now() }
    var until by remember { mutableStateOf(today.plusDays(1)) }
    var trip by remember { mutableStateOf<com.kairos.app.data.remote.dto.BackpackTodayDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        trip = runCatching { container.sessionRepository.backpacking() }.getOrNull()
        loaded = true
    }
    val dayFmt = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backpacking trip") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(KairosIcons.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Icon(KairosIcons.Backpack, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            }
            val t = trip
            if (t != null) {
                Text(
                    "Day ${t.day} of ${t.of} \u00b7 ${String.format(Locale.US, "%.1f", t.miles)} mi today",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (loaded) {
                Text("Back on", style = MaterialTheme.typography.titleSmall)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (0L..13L).map { today.plusDays(it) }.forEach { d ->
                        val on = d == until
                        val label = when (d) {
                            today -> "Today"
                            today.plusDays(1) -> "Tomorrow"
                            else -> d.format(dayFmt)
                        }
                        if (on) {
                            Button(onClick = { until = d }) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { until = d }) { Text(label) }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val r = runCatching {
                                container.sessionRepository.answerBackpacking(
                                    com.kairos.app.data.remote.dto.BackpackRequest(start = true, until = until.toString()),
                                )
                            }
                            busy = false
                            if (r.isSuccess) onStarted() else error = r.exceptionOrNull()?.message ?: "Couldn't start the trip."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Start trip") }
            }
        }
    }
}

/**
 * On a backpacking trip today (v0.558): the day and miles so far, and
 * "Hiking today?" until it's answered. Shown on Workouts.
 */
@Composable
internal fun BackpackCard(refreshKey: Int) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var trip by remember { mutableStateOf<com.kairos.app.data.remote.dto.BackpackTodayDto?>(null) }
    LaunchedEffect(refreshKey) {
        trip = runCatching { container.sessionRepository.backpacking() }.getOrNull()
    }
    val t = trip ?: return
    fun answer(hiking: Boolean) {
        scope.launch {
            runCatching {
                container.sessionRepository.answerBackpacking(com.kairos.app.data.remote.dto.BackpackRequest(hiking = hiking))
            }.onSuccess { trip = it }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(KairosIcons.Backpack, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    "Backpacking \u00b7 Day ${t.day} of ${t.of}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                Text(String.format(Locale.US, "%.1f mi", t.miles), style = MaterialTheme.typography.bodyMedium)
            }
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
    }
}
