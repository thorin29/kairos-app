package com.kairos.app.ui.workout

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay

/**
 * A timed movement done a side at a time (v0.449), like a side plank: a
 * stopwatch with Switch. Switch keeps this side's time, counts down the pause,
 * then times the next side, until each of the movement's open sets has a
 * time — then they fill its sets in order. Only this movement: it never runs
 * on into the next one. v0.585: the shared timer face, cues and settings — a
 * get-ready before the first side, 3-2-1 into each side, the floating timer.
 */
@Composable
internal fun SidesTimer(name: String, sets: Int, onDone: (List<Int>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val settings = rememberTimerSettings()
    val cues = rememberTimerCues(settings)
    val gap = settings.sideGap
    val lead = settings.lead
    val leftFirst = !settings.rightFirst
    // idle, lead, run, gap, done
    var phase by remember { mutableStateOf("idle") }
    val times = remember { mutableListOf<Int>() }
    var count by remember { mutableIntStateOf(0) }
    var start by remember { mutableLongStateOf(0L) }
    var shown by remember { mutableIntStateOf(0) }

    fun secs(): Int = ((SystemClock.elapsedRealtime() - start) / 1000).toInt()
    fun finish() {
        phase = "done"
        cues.finish()
        onDone(times.toList())
    }
    fun go() {
        cues.work()
        start = SystemClock.elapsedRealtime()
        shown = 0
        phase = "run"
    }

    LaunchedEffect(phase) {
        var lastLeft = -1
        while (phase == "run" || phase == "gap" || phase == "lead") {
            delay(200)
            if (phase == "run") {
                shown = secs()
            } else {
                val wait = if (phase == "lead") lead else gap
                val left = wait - secs()
                shown = left.coerceAtLeast(0)
                if (left <= 0) {
                    go()
                } else if (left != lastLeft) {
                    if (left <= 3) cues.tick()
                    lastLeft = left
                }
            }
        }
    }
    val side = { i: Int -> if ((i % 2 == 0) == leftFirst) "Left" else "Right" }
    val n = count + 1
    val status = when (phase) {
        "idle" -> "Ready"
        "lead" -> "Get ready"
        "gap" -> "Switch to ${side(count)}"
        "done" -> "Done"
        else -> side(count)
    }

    LaunchedEffect(phase, count) {
        if (phase == "idle" || phase == "done") {
            TimerLive.clear(context)
            return@LaunchedEffect
        }
        val wait = if (phase == "lead") lead else gap
        TimerLive.publish(
            context,
            LiveTimer(
                title = name,
                status = status,
                clock = clockText(if (phase == "run") secs() else wait - secs()),
                endAt = if (phase == "run") null else TimerLive.wallAt(start + wait * 1000L),
                startedAt = if (phase == "run") System.currentTimeMillis() - (SystemClock.elapsedRealtime() - start) else null,
                running = true,
            ),
        )
    }
    DisposableEffect(Unit) { onDispose { TimerLive.clear(context) } }

    val buttons = when (phase) {
        "idle" -> listOf(
            TimerButton(label = "Start", filled = true, onClick = {
                if (lead > 0) {
                    start = SystemClock.elapsedRealtime()
                    shown = lead
                    phase = "lead"
                } else {
                    go()
                }
            }),
        )
        "done" -> listOf(TimerButton(label = "Close", filled = true, onClick = onClose))
        else -> listOf(
            TimerButton(label = "Done", onClick = {
                if (phase == "run") times.add(maxOf(1, secs()))
                count = times.size
                finish()
            }),
            TimerButton(
                label = if (n >= sets) "Finish" else "Switch",
                filled = true,
                enabled = phase == "run",
                weight = 1.4f,
                onClick = {
                    times.add(maxOf(1, secs()))
                    count = times.size
                    if (times.size >= sets) {
                        finish()
                    } else {
                        cues.rest()
                        start = SystemClock.elapsedRealtime()
                        shown = gap
                        phase = "gap"
                    }
                },
            ),
        )
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TimerFace(
            family = false,
            caption = "Set ${minOf(n, sets)} of $sets",
            heading = name,
            clock = clockText(shown),
            state = when (phase) {
                "idle" -> TimerState.IDLE
                "lead", "gap" -> TimerState.READY
                "done" -> TimerState.DONE
                else -> TimerState.WORK
            },
            word = status,
            progress = when (phase) {
                "lead" -> if (lead > 0) shown / lead.toFloat() else 0f
                "gap" -> if (gap > 0) shown / gap.toFloat() else 0f
                else -> null
            },
            bar = if (sets > 1) (if (phase == "done") sets else count) to sets else null,
            now = if (phase == "run" || phase == "gap" || phase == "lead") "${side(count)} side" else null,
            next = if ((phase == "run" || phase == "gap" || phase == "lead") && n < sets) "${side(count + 1)} side" else null,
            buttons = buttons,
            onBack = onClose,
        ) { ink ->
            if (count > 0) {
                Text(
                    times.mapIndexed { i, t -> "${side(i)} ${clockText(t)}" }.joinToString(" \u00b7 "),
                    color = ink.soft,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
