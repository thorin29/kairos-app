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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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

/** The timer's colours (v0.430): dark (the gym screen), or light from the
 *  app's own surfaces and accent, with amber for get-ready and red for rest. */
internal data class TimerPalette(
    val bg: Color,
    val panel: Color,
    val line: Color,
    val soft: Color,
    val accent: Color,
    val ready: Color,
    val rest: Color,
    val ink: Color,
    val strong: Color,
    val onAccent: Color,
)

private val DARK_TIMER = TimerPalette(
    bg = Color(0xFF10202F),
    panel = Color(0xFF1B3044),
    line = Color(0xFF3B5468),
    soft = Color(0xFFC9D4DE),
    accent = Color(0xFF9FD3D8),
    ready = Color(0xFFF6C66B),
    rest = Color(0xFFF0A3A3),
    ink = Color.White,
    strong = Color(0xFF16384A),
    onAccent = Color(0xFF10202F),
)

/** Settings → Appearance → Workout timer: DARK (default), LIGHT, or APP
 *  (the app's light or dark mode). */
@Composable
internal fun timerPalette(): TimerPalette {
    val theme by com.kairos.app.ui.common.rememberContainer().settingsStore.timerTheme.collectAsState(initial = "DARK")
    val light = theme == "LIGHT" || (theme == "APP" && !com.kairos.app.ui.theme.KairosThemeState.dark)
    if (!light) return DARK_TIMER
    val c = MaterialTheme.colorScheme
    return TimerPalette(
        bg = c.surface,
        panel = c.surfaceVariant,
        line = c.outlineVariant,
        soft = c.onSurfaceVariant,
        accent = c.primary,
        ready = Color(0xFFB45309),
        rest = Color(0xFFB91C1C),
        ink = c.onSurface,
        strong = c.primary.copy(alpha = 0.14f),
        onAccent = c.onPrimary,
    )
}

/**
 * The full-screen conditioning timer (the web's ConditioningTimer): get ready,
 * then the format's clock with the shared face, cues and settings (v0.585,
 * TimerKit), "+1 round" on an AMRAP, Done on a stopwatch, and the result
 * filled in at the end. [custom] runs a timer of its own (the free Timer) and
 * [logging] false leaves out the result. A Family WOD uses its own look.
 */
@Composable
internal fun ConditioningTimer(
    workout: ConditioningPlanDto,
    onLog: (Int, String) -> Unit,
    onClose: () -> Unit,
    custom: TimerPlan? = null,
    logging: Boolean = true,
) {
    val context = LocalContext.current
    val pal = timerPalette()
    val settings = rememberTimerSettings()
    val cues = rememberTimerCues(settings)
    val plan = remember(workout, custom) { custom ?: timerPlanFor(workout.type, workout.capSec, workout.movements.map { it.name }) }
    val family = workout.plannedWorkoutId.startsWith("fwod_")
    val lead = settings.lead
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

    fun cue(c: String, second: Int) = when (c) {
        "TICK" -> cues.tick()
        // Into work high, into rest low (v0.585, as the warm-up timer).
        "CHANGE" -> if (timerAt(plan, second).work) cues.work() else cues.rest()
        "HALF" -> cues.half()
        else -> cues.finish()
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
                        cues.work()
                        runStart = SystemClock.elapsedRealtime()
                        banked = 0L
                        leadLeft = 0
                        phase = "run"
                    } else {
                        if (left <= 3) cues.tick()
                        leadLeft = left
                    }
                }
            } else {
                val sec = nowRun()
                if (sec != lastSecond) {
                    for (s in (lastSecond + 1)..sec) cuesAt(plan, s).forEach { cue(it, s) }
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
                cues.work()
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
        phase == "done" -> if (plan.kind == "STOPWATCH") "Done" else "Time"
        plan.kind == "INTERVALS" -> if (at.work) "Work" else "Rest"
        else -> "Go"
    }
    val clock = if (leading) leadLeft.toString() else clockText(at.clock)

    // Outside the app (v0.438): the notification and floating window show the
    // timer; published when its part changes, as the notification ticks itself.
    LaunchedEffect(phase, at.index, leading) {
        if (phase == "idle" || phase == "done") {
            TimerLive.clear(context)
            return@LaunchedEffect
        }
        val now = SystemClock.elapsedRealtime()
        val runMs = banked + (if (phase == "run") now - runStart else 0L)
        val endAt: Long? = when {
            phase == "lead" -> TimerLive.wallAt(leadStart + lead * 1000L)
            phase != "run" -> null
            plan.kind == "STOPWATCH" -> null
            plan.kind == "COUNTDOWN" -> System.currentTimeMillis() + ((plan.totalSec ?: 0) * 1000L - runMs)
            else -> System.currentTimeMillis() + (plan.segments.take(at.index + 1).sumOf { it.sec } * 1000L - runMs)
        }
        TimerLive.publish(
            context,
            LiveTimer(
                title = workout.name,
                status = status,
                clock = clock,
                endAt = endAt,
                startedAt = if (phase == "run" && plan.kind == "STOPWATCH") System.currentTimeMillis() - runMs else null,
                running = phase == "run" || phase == "lead",
            ),
        )
    }
    DisposableEffect(Unit) { onDispose { TimerLive.clear(context) } }

    // What the face shows: how much of this part is left, the bar of work
    // parts, and what's now and next (v0.585).
    val seg = plan.segments.getOrNull(at.index)
    val progress: Float? = when {
        leading -> if (lead > 0) leadLeft / lead.toFloat() else 1f
        phase == "idle" -> 1f
        plan.kind == "STOPWATCH" -> null
        plan.kind == "COUNTDOWN" -> plan.totalSec?.takeIf { it > 0 }?.let { at.clock / it.toFloat() }
        else -> seg?.sec?.takeIf { it > 0 }?.let { at.clock / it.toFloat() }
    }
    val works = plan.segments.count { it.work }
    val bar = if (plan.kind == "INTERVALS" && works > 1) {
        val before = plan.segments.take(at.index).count { it.work }
        (if (phase == "done") works else before) to works
    } else {
        null
    }
    val showsList = plan.kind == "INTERVALS" && phase != "done"
    val nowText = if (showsList) (if (phase == "idle") plan.segments.firstOrNull()?.label else at.label)?.takeIf { it.isNotBlank() } else null
    val nextText = if (showsList) plan.segments.getOrNull(if (phase == "idle") 1 else at.index + 1)?.label?.takeIf { it.isNotBlank() } else null
    val state = when {
        phase == "idle" -> TimerState.IDLE
        leading -> TimerState.READY
        phase == "paused" -> TimerState.PAUSED
        phase == "done" -> TimerState.DONE
        at.work -> TimerState.WORK
        else -> TimerState.REST
    }
    val buttons = buildList {
        add(TimerButton(label = "Reset", onClick = { reset() }))
        if (phase != "done") {
            add(
                TimerButton(
                    label = when (phase) {
                        "idle" -> "Start"
                        "paused" -> "Resume"
                        else -> "Pause"
                    },
                    filled = true,
                    weight = 1.4f,
                    onClick = { if (phase == "run" || phase == "lead") pause() else start() },
                ),
            )
        }
        if (phase == "run" || phase == "paused") {
            add(
                TimerButton(
                    label = if (plan.kind == "STOPWATCH") "Done" else "End",
                    onClick = {
                        val now = if (phase == "run") nowRun() else elapsed
                        if (plan.kind == "STOPWATCH") cues.finish()
                        elapsed = now
                        finish(now)
                    },
                ),
            )
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        TimerFace(
            family = family,
            caption = if (logging) "${workout.name} \u00b7 ${plan.title}" else plan.title,
            heading = if (logging) null else workout.name.takeIf { it.isNotBlank() && it != plan.title },
            clock = clock,
            state = state,
            word = status,
            progress = progress,
            bar = bar,
            now = nowText,
            next = nextText,
            buttons = buttons,
            onBack = onClose,
        ) { ink ->
            if (workout.movements.isNotEmpty() && !showsList) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .border(1.dp, ink.line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    workout.movements.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                m.reps?.toString() ?: m.distance?.let { MovementSetsViewModel.fmt(it) } ?: "",
                                color = ink.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.width(40.dp),
                            )
                            Text(m.name, color = ink.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            m.weight?.let { Text("${MovementSetsViewModel.fmt(it)} lb", color = ink.soft, fontSize = 13.sp) }
                        }
                    }
                }
            }
            if (plan.rounds) {
                Spacer(Modifier.heightIn(min = 14.dp))
                Text(
                    ("Rounds" + (workout.last?.takeIf { logging }?.let { " \u00b7 last time $it" } ?: "")).uppercase(),
                    color = ink.soft,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                )
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(150.dp)
                        .background(ink.text.copy(alpha = 0.08f), CircleShape)
                        .border(3.dp, ink.state, CircleShape)
                        .clickable { rounds += 1 },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(rounds.toString(), color = ink.text, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                        Text("+1 round", color = ink.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = { rounds = maxOf(0, rounds - 1) }) { Text("Undo round", color = ink.soft) }
            } else if (logging) {
                workout.last?.let { Text("Last time $it", color = ink.soft, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
            }

            if (logging && phase == "done") {
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ink.text,
                    unfocusedTextColor = ink.text,
                    focusedBorderColor = ink.state,
                    unfocusedBorderColor = ink.line,
                    focusedLabelColor = ink.soft,
                    unfocusedLabelColor = ink.soft,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                )
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    workout.today?.let { Text("Logging replaces today's $it.", color = ink.soft, fontSize = 13.sp) }
                    error?.let { Text(it, color = pal.rest, fontSize = 13.sp) }
                    Button(
                        onClick = { save() },
                        colors = ButtonDefaults.buttonColors(containerColor = ink.text, contentColor = ink.bg),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Log", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
