package com.kairos.app.ui.workout

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairos.app.data.remote.dto.WarmupStepDto
import kotlinx.coroutines.delay

/*
 * The warm-up countdown (v0.433), mirroring warmup-library.ts on the web:
 * a timed step's timer runs it and the timed steps straight after it, one
 * interval each, two for an each-side step ("Side plank · left", "· right"),
 * with a get-ready before the first and a chosen pause between the rest.
 */

internal data class WarmupInterval(val step: Int, val name: String, val seconds: Int)

/** One stretch of the countdown: READY (get ready, or the pause) or WORK. */
internal data class WarmupSegment(val kind: String, val interval: Int, val seconds: Int)

/** Choices for the pause between intervals, in seconds (WARMUP_GAPS). */
internal val WARMUP_GAPS = listOf(0, 1, 2, 3, 5, 10)

/** [rightFirst]: an each-side step starts on the right (v0.437, chosen on the timer). */
internal fun warmupIntervals(steps: List<WarmupStepDto>, from: Int, rightFirst: Boolean = false): List<WarmupInterval> {
    val (a, b) = if (rightFirst) "right" to "left" else "left" to "right"
    val out = mutableListOf<WarmupInterval>()
    for (i in from until steps.size) {
        val s = steps[i]
        val sec = s.seconds ?: 0
        if (s.kind != "TIME" || sec <= 0) break
        if (s.sides) {
            out += WarmupInterval(step = i, name = "${s.name} · $a", seconds = sec)
            out += WarmupInterval(step = i, name = "${s.name} · $b", seconds = sec)
        } else {
            out += WarmupInterval(step = i, name = s.name, seconds = sec)
        }
    }
    return out
}

/** [sideGap] (v0.585): the pause when an each-side step switches sides,
 *  the same as the each-side timer's; [gap] between steps. */
internal fun warmupSchedule(intervals: List<WarmupInterval>, lead: Int, gap: Int, sideGap: Int = gap): List<WarmupSegment> {
    val out = mutableListOf<WarmupSegment>()
    intervals.forEachIndexed { i, iv ->
        val wait = when {
            i == 0 -> lead
            intervals[i - 1].step == iv.step -> sideGap
            else -> gap
        }
        if (wait > 0) out += WarmupSegment(kind = "READY", interval = i, seconds = wait)
        out += WarmupSegment(kind = "WORK", interval = i, seconds = iv.seconds)
    }
    return out
}

/** Where the countdown is after [elapsed] whole seconds. */
internal data class WarmupPos(val segment: Int, val left: Int, val done: Boolean)

internal fun warmupAt(schedule: List<WarmupSegment>, elapsed: Int): WarmupPos {
    var t = elapsed
    schedule.forEachIndexed { i, s ->
        if (t < s.seconds) return WarmupPos(segment = i, left = s.seconds - t, done = false)
        t -= s.seconds
    }
    return WarmupPos(segment = schedule.size - 1, left = 0, done = true)
}

/**
 * The full-screen warm-up countdown on the shared timer face (v0.585,
 * TimerKit): the step and its time, a bar of steps, now and next, 3-2-1
 * tones and a buzz at each change, Reset, Pause and Skip. Get ready, the
 * side to start on and the pause between sides come from Timer settings;
 * the rest between steps is chosen here. [onFinished] gets the steps it ran.
 */
@Composable
internal fun WarmupTimer(
    steps: List<WarmupStepDto>,
    from: Int,
    onFinished: (List<Int>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kairos_timer", Context.MODE_PRIVATE) }
    val settings = rememberTimerSettings()
    val cues = rememberTimerCues(settings)
    val rightFirst = settings.rightFirst
    val intervals = remember(steps, from, rightFirst) { warmupIntervals(steps = steps, from = from, rightFirst = rightFirst) }
    val lead = settings.lead
    var gap by remember { mutableIntStateOf(prefs.getInt("warmup_gap", 10).takeIf { it in WARMUP_GAPS } ?: 10) }
    // idle, run, paused, done
    var phase by remember { mutableStateOf("idle") }
    var elapsed by remember { mutableIntStateOf(0) }
    var runStart by remember { mutableLongStateOf(0L) }
    var banked by remember { mutableLongStateOf(0L) }
    val schedule = remember(intervals, lead, gap, settings.sideGap) {
        warmupSchedule(intervals = intervals, lead = lead, gap = gap, sideGap = settings.sideGap)
    }
    val total = schedule.sumOf { it.seconds }

    fun finish() {
        phase = "done"
        elapsed = total
        cues.finish()
        onFinished(intervals.map { it.step }.distinct())
    }
    // A second's cues: high into work, low into a pause, a tick for each of
    // the last three seconds.
    fun cuesFor(sec: Int) {
        val now = warmupAt(schedule = schedule, elapsed = sec)
        val prev = warmupAt(schedule = schedule, elapsed = sec - 1)
        if (now.done) return
        if (now.segment != prev.segment) {
            if (schedule[now.segment].kind == "WORK") cues.work() else cues.rest()
        } else if (now.left <= 3) {
            cues.tick()
        }
    }

    LaunchedEffect(phase, schedule) {
        if (phase != "run") return@LaunchedEffect
        while (phase == "run") {
            val sec = ((banked + (SystemClock.elapsedRealtime() - runStart)) / 1000).toInt()
            if (sec != elapsed) {
                for (x in (elapsed + 1)..sec) cuesFor(x)
                elapsed = sec
                if (sec >= total) {
                    finish()
                    break
                }
            }
            delay(250)
        }
    }

    fun start() {
        if (phase == "idle") {
            banked = 0L
            elapsed = 0
            if (schedule.firstOrNull()?.kind == "WORK") cues.work()
        }
        runStart = SystemClock.elapsedRealtime()
        phase = "run"
    }
    fun pause() {
        banked += SystemClock.elapsedRealtime() - runStart
        phase = "paused"
    }
    fun reset() {
        phase = "idle"
        elapsed = 0
        banked = 0L
    }
    // Straight on to the next interval's work (from a get-ready, its own).
    fun skip() {
        val at = warmupAt(schedule = schedule, elapsed = elapsed)
        var j = at.segment + 1
        if (schedule[at.segment].kind == "WORK") {
            while (j < schedule.size && schedule[j].kind != "WORK") j++
        }
        if (j >= schedule.size) {
            finish()
            return
        }
        val to = schedule.take(j).sumOf { it.seconds }
        banked = to * 1000L
        runStart = SystemClock.elapsedRealtime()
        elapsed = to
        cues.work()
    }

    if (intervals.isEmpty()) return
    val at = warmupAt(schedule = schedule, elapsed = elapsed)
    val seg = schedule[at.segment]
    val current = intervals[seg.interval]
    val ready = (phase == "run" || phase == "paused") && seg.kind == "READY"
    val shown = when (phase) {
        "idle" -> intervals[0].seconds
        "done" -> 0
        else -> at.left
    }
    val sideSwitch = ready && seg.interval > 0 && intervals[seg.interval - 1].step == current.step
    val status = when {
        phase == "idle" -> "Ready"
        phase == "done" -> "Done"
        phase == "paused" -> "Paused"
        sideSwitch -> "Switch sides"
        ready -> "Get ready"
        else -> "Work"
    }
    val nextIv = when {
        phase == "idle" -> intervals.getOrNull(1)
        seg.kind == "READY" -> intervals.getOrNull(seg.interval + 1)
        else -> intervals.getOrNull(seg.interval + 1)
    }

    // Outside the app (v0.438): the notification and floating window show the
    // step; published when it changes, as the notification's clock ticks itself.
    LaunchedEffect(phase, at.segment, rightFirst) {
        if (phase == "idle" || phase == "done") {
            TimerLive.clear(context)
            return@LaunchedEffect
        }
        val segEnd = schedule.take(at.segment + 1).sumOf { it.seconds } * 1000L
        val runMs = banked + (if (phase == "run") SystemClock.elapsedRealtime() - runStart else 0L)
        TimerLive.publish(
            context,
            LiveTimer(
                title = current.name,
                status = status,
                next = nextIv?.name,
                clock = clockText(at.left),
                endAt = if (phase == "run") System.currentTimeMillis() + (segEnd - runMs) else null,
                running = phase == "run",
            ),
        )
    }
    DisposableEffect(Unit) { onDispose { TimerLive.clear(context) } }

    val state = when {
        phase == "idle" -> TimerState.IDLE
        phase == "done" -> TimerState.DONE
        phase == "paused" -> TimerState.PAUSED
        ready -> TimerState.READY
        else -> TimerState.WORK
    }
    val progress = when (phase) {
        "idle" -> 1f
        "done" -> 0f
        else -> seg.seconds.takeIf { it > 0 }?.let { at.left / it.toFloat() } ?: 0f
    }
    val buttons = if (phase == "done") {
        listOf(TimerButton(label = "Close", filled = true, onClick = onClose))
    } else {
        buildList {
            add(TimerButton(label = "Reset", enabled = phase != "idle", onClick = { reset() }))
            add(
                TimerButton(
                    label = when (phase) {
                        "idle" -> "Start"
                        "paused" -> "Resume"
                        else -> "Pause"
                    },
                    filled = true,
                    weight = 1.4f,
                    onClick = { if (phase == "run") pause() else start() },
                ),
            )
            if (phase != "idle") add(TimerButton(label = "Skip", onClick = { skip() }))
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        TimerFace(
            family = false,
            caption = if (intervals.size > 1 && phase != "done") "${seg.interval + 1} of ${intervals.size}" else "",
            heading = if (phase == "done") "Warm-up done" else current.name,
            clock = clockText(shown),
            state = state,
            word = status,
            progress = progress,
            bar = if (intervals.size > 1) (if (phase == "done") intervals.size else seg.interval) to intervals.size else null,
            now = if (phase == "done") null else "${current.name} \u00b7 ${clockText(current.seconds)}",
            next = if (phase == "done") null else nextIv?.let { "${it.name} \u00b7 ${clockText(it.seconds)}" },
            buttons = buttons,
            onBack = onClose,
        ) { ink ->
            if (phase == "idle" && intervals.size > 1) {
                Text("Rest between steps", color = ink.soft, fontSize = 13.sp)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WARMUP_GAPS.forEach { v ->
                        val on = gap == v
                        Box(
                            Modifier
                                .heightIn(min = 36.dp)
                                .width(46.dp)
                                .background(if (on) ink.text else Color.Transparent, RoundedCornerShape(999.dp))
                                .border(1.dp, if (on) ink.text else ink.line, RoundedCornerShape(999.dp))
                                .clickable {
                                    gap = v
                                    prefs.edit().putInt("warmup_gap", v).apply()
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text("${v}s", color = if (on) ink.bg else ink.soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}
