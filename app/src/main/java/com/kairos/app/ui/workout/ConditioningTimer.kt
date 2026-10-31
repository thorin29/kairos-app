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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairos.app.data.remote.dto.ConditioningPlanDto
import kotlinx.coroutines.delay

private val TBg = Color(0xFF10202F)
private val TPanel = Color(0xFF1B3044)
private val TLine = Color(0xFF3B5468)
private val TSoft = Color(0xFFC9D4DE)
private val TAccent = Color(0xFF9FD3D8)
private val TReady = Color(0xFFF6C66B)
private val TRest = Color(0xFFF0A3A3)

/**
 * The full-screen conditioning timer (the web's ConditioningTimer and the
 * mockup's Timer): get ready, then the format's clock with tones on the alarm
 * stream (audible over music) and vibration, "+1 round" on an AMRAP, Done on a
 * stopwatch, and the result filled in at the end. The screen stays on while
 * it runs. Sound and the get-ready length are kept on this phone.
 */
@Composable
internal fun ConditioningTimer(
    workout: ConditioningPlanDto,
    onLog: (Int, String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kairos_timer", Context.MODE_PRIVATE) }
    val plan = remember(workout) { timerPlanFor(workout.type, workout.capSec, workout.movements.map { it.name }) }
    var sound by remember { mutableStateOf(prefs.getBoolean("sound", true)) }
    var lead by remember { mutableIntStateOf(prefs.getInt("lead", 10).takeIf { it in TIMER_LEADS } ?: 10) }
    // idle, lead, run, paused, done
    var phase by remember { mutableStateOf("idle") }
    var elapsed by remember { mutableIntStateOf(0) }
    var leadLeft by remember { mutableIntStateOf(lead) }
    var rounds by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Measured from the clock, so a busy phone never drifts: [banked] is the
    // time run before the last start.
    var runStart by remember { mutableLongStateOf(0L) }
    var banked by remember { mutableLongStateOf(0L) }
    var leadStart by remember { mutableLongStateOf(0L) }

    val tones = remember { runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90) }.getOrNull() }
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    DisposableEffect(Unit) { onDispose { tones?.release() } }
    fun tone(kind: Int, ms: Int) {
        if (sound) runCatching { tones?.startTone(kind, ms) }
    }
    fun buzz(ms: Long) {
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
    fun cue(c: String) = when (c) {
        "TICK" -> tone(ToneGenerator.TONE_PROP_BEEP, 150)
        "CHANGE" -> { tone(ToneGenerator.TONE_PROP_ACK, 450); buzz(250) }
        "HALF" -> { tone(ToneGenerator.TONE_PROP_BEEP2, 300); buzz(150) }
        else -> { tone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1400); buzz(700) }
    }

    val view = LocalView.current
    val running = phase == "run" || phase == "lead"
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    fun finish(at: Int) {
        phase = "done"
        val r = resultFromTimer(plan, at, rounds)
        result = when {
            r == null -> ""
            plan.result == "TIME" -> clockText(r)
            else -> r.toString()
        }
    }
    fun nowRun(): Int = ((banked + (SystemClock.elapsedRealtime() - runStart)) / 1000).toInt()

    LaunchedEffect(phase) {
        var lastSecond = elapsed
        while (phase == "lead" || phase == "run") {
            delay(250)
            if (phase == "lead") {
                val left = lead - ((SystemClock.elapsedRealtime() - leadStart) / 1000).toInt()
                if (left != leadLeft) {
                    if (left <= 0) {
                        tone(ToneGenerator.TONE_PROP_ACK, 700)
                        buzz(300)
                        runStart = SystemClock.elapsedRealtime()
                        banked = 0L
                        leadLeft = 0
                        phase = "run"
                    } else {
                        if (left <= 3) tone(ToneGenerator.TONE_PROP_BEEP, 150)
                        leadLeft = left
                    }
                }
            } else {
                val sec = nowRun()
                if (sec != lastSecond) {
                    for (s in (lastSecond + 1)..sec) cuesAt(plan, s).forEach { cue(it) }
                    lastSecond = sec
                    elapsed = sec
                    val total = plan.totalSec
                    if (total != null && sec >= total) finish(total)
                }
            }
        }
    }

    fun start() {
        when (phase) {
            "idle" -> if (lead > 0) {
                leadStart = SystemClock.elapsedRealtime()
                leadLeft = lead
                phase = "lead"
            } else {
                tone(ToneGenerator.TONE_PROP_ACK, 700)
                runStart = SystemClock.elapsedRealtime()
                banked = 0L
                phase = "run"
            }
            "paused" -> if (leadLeft > 0 && elapsed == 0) {
                leadStart = SystemClock.elapsedRealtime() - (lead - leadLeft) * 1000L
                phase = "lead"
            } else {
                runStart = SystemClock.elapsedRealtime()
                phase = "run"
            }
        }
    }
    fun pause() {
        if (phase == "run") banked += SystemClock.elapsedRealtime() - runStart
        phase = "paused"
    }
    fun reset() {
        phase = "idle"
        elapsed = 0
        banked = 0L
        leadLeft = lead
        rounds = 0
        result = ""
        extra = ""
        error = null
    }
    fun save() {
        val raw = result.trim()
        val value: Int? = if (plan.result == "TIME") {
            Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})$""").find(raw)?.let { m ->
                m.groupValues[1].ifEmpty { "0" }.toInt() * 3600 + m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt()
            }
        } else {
            raw.toIntOrNull()?.takeIf { it > 0 }
        }
        if (value == null || value <= 0) {
            error = if (plan.result == "TIME") "Time as mm:ss." else "Enter the ${workout.resultLabel.lowercase()}."
            return
        }
        val x = extra.toIntOrNull() ?: 0
        onLog(value, if (plan.result == "ROUNDS" && x > 0) "+$x reps" else "")
    }

    val at = timerAt(plan, elapsed)
    val leading = phase == "lead" || (phase == "paused" && leadLeft > 0 && elapsed == 0 && lead > 0)
    val status = when {
        phase == "idle" -> "Ready"
        leading -> "Get ready"
        phase == "paused" -> "Paused"
        phase == "done" -> "Time"
        else -> at.label.ifBlank { "Go" }
    }
    val restNow = phase == "run" && !at.work

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(TBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text("‹ Back", color = TAccent, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        sound = !sound
                        prefs.edit().putBoolean("sound", sound).apply()
                    },
                    border = BorderStroke(1.dp, TLine),
                ) { Text(if (sound) "Sound on" else "Sound off", color = TSoft) }
            }

            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("${workout.name} · ${plan.title}", color = TSoft, fontSize = 13.sp, textAlign = TextAlign.Center)
                Text(
                    if (leading) leadLeft.toString() else clockText(at.clock),
                    color = if (leading) TReady else if (restNow) TRest else Color.White,
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(status, color = if (leading) TReady else TSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            if (phase == "idle") {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Get ready", color = TSoft, fontSize = 13.sp)
                    TIMER_LEADS.forEach { v ->
                        val on = lead == v
                        Box(
                            Modifier
                                .padding(start = 6.dp)
                                .heightIn(min = 36.dp)
                                .width(48.dp)
                                .background(if (on) TAccent else Color.Transparent, RoundedCornerShape(999.dp))
                                .border(1.dp, if (on) TAccent else TLine, RoundedCornerShape(999.dp))
                                .clickable {
                                    lead = v
                                    leadLeft = v
                                    prefs.edit().putInt("lead", v).apply()
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text("${v}s", color = if (on) TBg else TSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            if (workout.movements.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(TPanel, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    workout.movements.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                m.reps?.toString() ?: m.distance?.let { MovementSetsViewModel.fmt(it) } ?: "",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.width(40.dp),
                            )
                            Text(m.name, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            m.weight?.let { Text("${MovementSetsViewModel.fmt(it)} lb", color = TSoft, fontSize = 13.sp) }
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (plan.rounds) {
                    Text(
                        ("Rounds" + (workout.last?.let { " · last time $it" } ?: "")).uppercase(),
                        color = TSoft,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                    )
                    Box(
                        Modifier
                            .size(160.dp)
                            .background(Color(0xFF16384A), CircleShape)
                            .border(3.dp, TAccent, CircleShape)
                            .clickable { rounds += 1 },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(rounds.toString(), color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Bold)
                            Text("+1 round", color = TAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(onClick = { rounds = maxOf(0, rounds - 1) }) { Text("Undo round", color = TSoft) }
                } else {
                    workout.last?.let { Text("Last time $it", color = TSoft, fontSize = 14.sp) }
                }
            }

            if (phase == "done") {
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = TAccent,
                    unfocusedBorderColor = TLine,
                    focusedLabelColor = TSoft,
                    unfocusedLabelColor = TSoft,
                    focusedContainerColor = TPanel,
                    unfocusedContainerColor = TPanel,
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = result,
                            onValueChange = { result = it.filter { ch -> ch.isDigit() || ch == ':' } },
                            label = { Text(if (plan.result == "ROUNDS") "Rounds" else workout.resultLabel) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                        )
                        if (plan.result == "ROUNDS") {
                            OutlinedTextField(
                                value = extra,
                                onValueChange = { extra = it.filter(Char::isDigit) },
                                label = { Text("Extra reps") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = fieldColors,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    error?.let { Text(it, color = TRest, fontSize = 13.sp) }
                    Button(
                        onClick = { save() },
                        colors = ButtonDefaults.buttonColors(containerColor = TAccent, contentColor = TBg),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Log", fontWeight = FontWeight.Bold) }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { reset() },
                    border = BorderStroke(1.dp, TLine),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text("Reset", color = Color.White) }
                if (phase != "done") {
                    Button(
                        onClick = { if (phase == "run" || phase == "lead") pause() else start() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TBg),
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
                }
                if (phase == "run" || phase == "paused") {
                    Button(
                        onClick = {
                            val now = if (phase == "run") nowRun() else elapsed
                            if (plan.kind == "STOPWATCH") tone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900)
                            elapsed = now
                            finish(now)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (plan.kind == "STOPWATCH") TAccent else Color.Transparent,
                            contentColor = if (plan.kind == "STOPWATCH") TBg else Color.White,
                        ),
                        border = if (plan.kind == "STOPWATCH") null else BorderStroke(1.dp, TLine),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(if (plan.kind == "STOPWATCH") "Done" else "End", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
