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

/** The pause between sides, in seconds. */
internal val SIDE_GAPS = listOf(1, 2, 3)

/**
 * A timed movement done a side at a time (v0.449), like a side plank: a
 * stopwatch with Switch. Switch keeps this side's time, counts down the pause
 * (1–3 s), then times the next side, until each of the movement's open sets
 * has a time — then they fill its sets in order. Only this movement: it never
 * runs on into the next one.
 */
@Composable
internal fun SidesTimer(name: String, sets: Int, onDone: (List<Int>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val pal = timerPalette()
    val digits = timerDigits(pal)
    val prefs = remember { context.getSharedPreferences("kairos_timer", Context.MODE_PRIVATE) }
    var gap by remember { mutableIntStateOf(prefs.getInt("sides_gap", 3).takeIf { it in SIDE_GAPS } ?: 3) }
    var leftFirst by remember { mutableStateOf(prefs.getString("warmup_side", "left") != "right") }
    // idle, run, gap, done
    var phase by remember { mutableStateOf("idle") }
    val times = remember { mutableListOf<Int>() }
    var count by remember { mutableIntStateOf(0) }
    var start by remember { mutableLongStateOf(0L) }
    var shown by remember { mutableIntStateOf(0) }

    val tones = remember { runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90) }.getOrNull() }
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    DisposableEffect(Unit) { onDispose { tones?.release() } }
    fun cue(kind: Int, ms: Int) {
        runCatching { tones?.startTone(kind, ms) }
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
    val view = LocalView.current
    DisposableEffect(phase) {
        view.keepScreenOn = phase == "run" || phase == "gap"
        onDispose { view.keepScreenOn = false }
    }
    fun secs(): Int = ((SystemClock.elapsedRealtime() - start) / 1000).toInt()
    fun finish() {
        phase = "done"
        cue(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900)
        onDone(times.toList())
    }

    LaunchedEffect(phase) {
        while (phase == "run" || phase == "gap") {
            delay(200)
            if (phase == "run") {
                shown = secs()
            } else {
                val left = gap - secs()
                shown = left.coerceAtLeast(0)
                if (left <= 0) {
                    cue(ToneGenerator.TONE_PROP_ACK, 500)
                    start = SystemClock.elapsedRealtime()
                    shown = 0
                    phase = "run"
                }
            }
        }
    }
    val side = { i: Int -> if ((i % 2 == 0) == leftFirst) "Left" else "Right" }
    val n = count + 1

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(pal.bg).statusBarsPadding().navigationBarsPadding(),
        ) {
            TextButton(onClick = onClose, modifier = Modifier.padding(4.dp)) {
                Text("‹ Back", color = pal.accent, fontWeight = FontWeight.SemiBold)
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Set ${minOf(n, sets)} of $sets" + if (phase == "run" || phase == "gap") " · ${side(count)}" else "",
                    color = pal.soft,
                    fontSize = 14.sp,
                )
                Text(name, color = pal.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    clockOf(shown),
                    color = if (phase == "gap") pal.ready else (digits.color ?: pal.ink),
                    fontFamily = digits.font,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when (phase) {
                        "gap" -> "Switch to ${side(count)}"
                        "done" -> "Done"
                        else -> ""
                    },
                    color = if (phase == "gap") pal.ready else pal.soft,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (count > 0) {
                    Text(
                        times.mapIndexed { i, t -> "${side(i)} ${clockOf(t)}" }.joinToString(" · "),
                        color = pal.soft,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (phase == "idle") {
                SidesChips(label = "Start on", values = listOf(true to "Left", false to "Right"), chosen = leftFirst, pal = pal) {
                    leftFirst = it
                    prefs.edit().putString("warmup_side", if (it) "left" else "right").apply()
                }
                SidesChips(label = "Between", values = SIDE_GAPS.map { it to "${it}s" }, chosen = gap, pal = pal) {
                    gap = it
                    prefs.edit().putInt("sides_gap", it).apply()
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (phase) {
                    "idle" -> BigButton(label = "Start", filled = true, pal = pal, modifier = Modifier.weight(1f)) {
                        cue(ToneGenerator.TONE_PROP_ACK, 500)
                        start = SystemClock.elapsedRealtime()
                        shown = 0
                        phase = "run"
                    }
                    "done" -> Unit
                    else -> {
                        BigButton(label = "Done", filled = false, pal = pal, modifier = Modifier.weight(1f)) {
                            if (phase == "run") times.add(maxOf(1, secs()))
                            count = times.size
                            finish()
                        }
                        BigButton(
                            label = if (n >= sets) "Finish" else "Switch",
                            filled = true,
                            enabled = phase == "run",
                            pal = pal,
                            modifier = Modifier.weight(1f),
                        ) {
                            times.add(maxOf(1, secs()))
                            count = times.size
                            if (times.size >= sets) {
                                finish()
                            } else {
                                cue(ToneGenerator.TONE_PROP_BEEP2, 400)
                                start = SystemClock.elapsedRealtime()
                                shown = gap
                                phase = "gap"
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> SidesChips(label: String, values: List<Pair<T, String>>, chosen: T, pal: TimerPalette, onPick: (T) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = pal.soft, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEach { (v, text) ->
                val on = chosen == v
                Box(
                    Modifier
                        .heightIn(min = 36.dp)
                        .width(64.dp)
                        .background(if (on) pal.accent else Color.Transparent, RoundedCornerShape(999.dp))
                        .border(1.dp, if (on) pal.accent else pal.line, RoundedCornerShape(999.dp))
                        .clickable { onPick(v) },
                    contentAlignment = Alignment.Center,
                ) { Text(text, color = if (on) pal.onAccent else pal.soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun BigButton(
    label: String,
    filled: Boolean,
    pal: TimerPalette,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .background(if (filled) pal.accent.copy(alpha = if (enabled) 1f else 0.5f) else Color.Transparent, shape)
            .border(1.dp, if (filled) pal.accent else pal.line, shape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) pal.onAccent else pal.soft, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
