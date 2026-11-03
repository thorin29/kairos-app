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

internal fun warmupIntervals(steps: List<WarmupStepDto>, from: Int): List<WarmupInterval> {
    val out = mutableListOf<WarmupInterval>()
    for (i in from until steps.size) {
        val s = steps[i]
        val sec = s.seconds ?: 0
        if (s.kind != "TIME" || sec <= 0) break
        if (s.sides) {
            out += WarmupInterval(step = i, name = "${s.name} · left", seconds = sec)
            out += WarmupInterval(step = i, name = "${s.name} · right", seconds = sec)
        } else {
            out += WarmupInterval(step = i, name = s.name, seconds = sec)
        }
    }
    return out
}

internal fun warmupSchedule(intervals: List<WarmupInterval>, lead: Int, gap: Int): List<WarmupSegment> {
    val out = mutableListOf<WarmupSegment>()
    intervals.forEachIndexed { i, iv ->
        val wait = if (i == 0) lead else gap
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
 * The full-screen warm-up countdown in the timer's colours: the step's name
 * and time, "Next: …", 3-2-1 tones and a buzz at each change, Pause and
 * Skip. [onFinished] gets the steps it ran, to tick off.
 */
@Composable
internal fun WarmupTimer(
    steps: List<WarmupStepDto>,
    from: Int,
    onFinished: (List<Int>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val pal = timerPalette()
    val prefs = remember { context.getSharedPreferences("kairos_timer", Context.MODE_PRIVATE) }
    val intervals = remember(steps, from) { warmupIntervals(steps = steps, from = from) }
    var sound by remember { mutableStateOf(prefs.getBoolean("warmup_sound", true)) }
    var lead by remember { mutableIntStateOf(prefs.getInt("warmup_lead", 5).takeIf { it in TIMER_LEADS } ?: 5) }
    var gap by remember { mutableIntStateOf(prefs.getInt("warmup_gap", 10).takeIf { it in WARMUP_GAPS } ?: 10) }
    // idle, run, paused, done
    var phase by remember { mutableStateOf("idle") }
    var elapsed by remember { mutableIntStateOf(0) }
    var runStart by remember { mutableLongStateOf(0L) }
    var banked by remember { mutableLongStateOf(0L) }
    val schedule = remember(intervals, lead, gap) { warmupSchedule(intervals = intervals, lead = lead, gap = gap) }
    val total = schedule.sumOf { it.seconds }

    val tones = remember { runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90) }.getOrNull() }
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    DisposableEffect(Unit) { onDispose { tones?.release() } }
    fun tone(kind: Int, ms: Int) {
        if (sound) runCatching { tones?.startTone(kind, ms) }
    }
    fun buzz(ms: Long) {
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    val view = LocalView.current
    DisposableEffect(phase) {
        view.keepScreenOn = phase == "run"
        onDispose { view.keepScreenOn = false }
    }

    fun finish() {
        phase = "done"
        elapsed = total
        tone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1400)
        buzz(700)
        onFinished(intervals.map { it.step }.distinct())
    }
    // A second's cues: a high tone and a buzz into work, a low one out of it,
    // and a tick for each of the last three seconds.
    fun cuesFor(sec: Int) {
        val now = warmupAt(schedule = schedule, elapsed = sec)
        val prev = warmupAt(schedule = schedule, elapsed = sec - 1)
        if (now.done) return
        if (now.segment != prev.segment) {
            if (schedule[now.segment].kind == "WORK") {
                tone(ToneGenerator.TONE_PROP_ACK, 600)
                buzz(250)
            } else {
                tone(ToneGenerator.TONE_PROP_BEEP2, 700)
                buzz(250)
            }
        } else if (now.left <= 3) {
            tone(ToneGenerator.TONE_PROP_BEEP, 150)
        }
    }

    LaunchedEffect(phase, schedule) {
        if (phase != "run") return@LaunchedEffect
        while (phase == "run") {
            val sec = ((banked + (SystemClock.elapsedRealtime() - runStart)) / 1000).toInt()
            if (sec != elapsed) {
                for (s in (elapsed + 1)..sec) cuesFor(s)
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
            if (schedule.firstOrNull()?.kind == "WORK") {
                tone(ToneGenerator.TONE_PROP_ACK, 600)
                buzz(250)
            } else {
                tone(ToneGenerator.TONE_PROP_BEEP, 80)
            }
        }
        runStart = SystemClock.elapsedRealtime()
        phase = "run"
    }
    fun pause() {
        banked += SystemClock.elapsedRealtime() - runStart
        phase = "paused"
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
        tone(ToneGenerator.TONE_PROP_ACK, 600)
    }

    if (intervals.isEmpty()) return
    val at = warmupAt(schedule = schedule, elapsed = elapsed)
    val seg = schedule[at.segment]
    val current = intervals[seg.interval]
    val ready = (phase == "run" || phase == "paused") && seg.kind == "READY"
    val next = when {
        phase == "idle" -> intervals.getOrNull(1)
        seg.kind == "READY" -> null
        else -> intervals.getOrNull(seg.interval + 1)
    }
    val shown = when (phase) {
        "idle" -> intervals[0].seconds
        "done" -> 0
        else -> at.left
    }
    val status = when {
        phase == "idle" -> "Ready"
        phase == "done" -> "Done"
        phase == "paused" -> "Paused"
        ready -> "Get ready"
        else -> "Go"
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(pal.bg)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text("‹ Back", color = pal.accent, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        sound = !sound
                        prefs.edit().putBoolean("warmup_sound", sound).apply()
                    },
                    border = BorderStroke(1.dp, pal.line),
                ) { Text(if (sound) "Sound on" else "Sound off", color = pal.soft) }
            }

            Column(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                if (intervals.size > 1 && phase != "done") {
                    Text("${seg.interval + 1} of ${intervals.size}", color = pal.soft, fontSize = 14.sp)
                }
                Text(
                    if (phase == "done") "Warm-up done" else current.name,
                    color = pal.ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    clockText(shown),
                    color = if (ready) pal.ready else pal.ink,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(status, color = if (ready) pal.ready else pal.soft, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (next != null && phase != "done") {
                    Text(
                        "Next: ${next.name} · ${clockText(next.seconds)}",
                        color = pal.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(pal.panel, RoundedCornerShape(999.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            if (phase == "idle") {
                ChipRow(label = "Get ready", values = TIMER_LEADS, chosen = lead, pal = pal) { v ->
                    lead = v
                    prefs.edit().putInt("warmup_lead", v).apply()
                }
                if (intervals.size > 1) {
                    ChipRow(label = "Between", values = WARMUP_GAPS, chosen = gap, pal = pal) { v ->
                        gap = v
                        prefs.edit().putInt("warmup_gap", v).apply()
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (phase == "done") {
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = pal.accent, contentColor = pal.onAccent),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("Close", fontWeight = FontWeight.Bold) }
                } else {
                    Button(
                        onClick = { if (phase == "run") pause() else start() },
                        colors = ButtonDefaults.buttonColors(containerColor = pal.accent, contentColor = pal.onAccent),
                        modifier = Modifier.weight(2f).heightIn(min = 52.dp),
                    ) {
                        Text(
                            when (phase) {
                                "idle" -> "Start"
                                "paused" -> "Resume"
                                else -> "Pause"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (phase != "idle") {
                        OutlinedButton(
                            onClick = { skip() },
                            border = BorderStroke(1.dp, pal.accent),
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) { Text("Skip", color = pal.accent, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

/** A label over a row of second choices ("0s", "5s" …), the chosen one filled. */
@Composable
private fun ChipRow(label: String, values: List<Int>, chosen: Int, pal: TimerPalette, onPick: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = pal.soft, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEach { v ->
                val on = chosen == v
                Box(
                    Modifier
                        .heightIn(min = 36.dp)
                        .width(46.dp)
                        .background(if (on) pal.accent else Color.Transparent, RoundedCornerShape(999.dp))
                        .border(1.dp, if (on) pal.accent else pal.line, RoundedCornerShape(999.dp))
                        .clickable { onPick(v) },
                    contentAlignment = Alignment.Center,
                ) { Text("${v}s", color = if (on) pal.onAccent else pal.soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
