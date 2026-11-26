package com.kairos.app.ui.workout

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.launch

/**
 * One timer for the whole app (v0.585): the Conditioning timer, warm-ups and
 * routines, the each-side timer and the free Timer all use the same face,
 * cues and settings.
 *
 * Two looks: Ring (a ring empties as each part runs) and Full color (the whole
 * screen green for work, red for rest, amber to get ready). Each is picked
 * separately for your own workouts and the Family WOD. Under either, the bar
 * of rounds and what's now and next.
 */

/** The pause between sides, in seconds. */
internal val SIDE_GAPS = listOf(1, 2, 3, 5)

/** The timer's settings, kept on this phone (shared by every timer). */
internal class TimerSettings(private val prefs: SharedPreferences) {
    // The old per-timer keys are read once as the starting values (v0.585).
    var sound by mutableStateOf(prefs.getBoolean("t_sound", prefs.getBoolean("sound", prefs.getBoolean("warmup_sound", true))))
        private set
    var vibrate by mutableStateOf(prefs.getBoolean("t_vibrate", true))
        private set
    var lead by mutableIntStateOf(
        prefs.getInt("t_lead", prefs.getInt("lead", 10)).takeIf { it in TIMER_LEADS } ?: 10,
    )
        private set
    var sideGap by mutableIntStateOf(
        prefs.getInt("t_side_gap", prefs.getInt("sides_gap", 3)).takeIf { it in SIDE_GAPS } ?: 3,
    )
        private set
    var rightFirst by mutableStateOf(
        if (prefs.contains("t_right_first")) prefs.getBoolean("t_right_first", false)
        else prefs.getBoolean("warmup_right_first", false) || prefs.getString("warmup_side", "left") == "right",
    )
        private set
    var look by mutableStateOf(prefs.getString("t_look", "RING") ?: "RING")
        private set
    var lookFamily by mutableStateOf(prefs.getString("t_look_family", "COLOR") ?: "COLOR")
        private set

    fun setSound(v: Boolean) { sound = v; prefs.edit().putBoolean("t_sound", v).apply() }
    fun setVibrate(v: Boolean) { vibrate = v; prefs.edit().putBoolean("t_vibrate", v).apply() }
    fun setLead(v: Int) { lead = v; prefs.edit().putInt("t_lead", v).apply() }
    fun setSideGap(v: Int) { sideGap = v; prefs.edit().putInt("t_side_gap", v).apply() }
    fun setRightFirst(v: Boolean) { rightFirst = v; prefs.edit().putBoolean("t_right_first", v).apply() }
    fun setLook(v: String) { look = v; prefs.edit().putString("t_look", v).apply() }
    fun setLookFamily(v: String) { lookFamily = v; prefs.edit().putString("t_look_family", v).apply() }

    fun lookFor(family: Boolean) = if (family) lookFamily else look
}

@Composable
internal fun rememberTimerSettings(): TimerSettings {
    val context = LocalContext.current
    return remember { TimerSettings(context.getSharedPreferences("kairos_timer", Context.MODE_PRIVATE)) }
}

/**
 * The same sounds everywhere (v0.585): a short tick for each of the last three
 * seconds, a high tone and a buzz into work, a low one into rest, a long one at
 * the end. Sound and vibration follow the settings.
 */
internal data class TimerCues(
    private val settings: TimerSettings,
    private val tones: ToneGenerator?,
    private val vibrator: Vibrator?,
) {
    private fun tone(kind: Int, ms: Int) {
        if (settings.sound) runCatching { tones?.startTone(kind, ms) }
    }
    private fun buzz(ms: Long) {
        if (settings.vibrate) runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
    fun tick() = tone(ToneGenerator.TONE_PROP_BEEP, 150)
    fun work() { tone(ToneGenerator.TONE_PROP_ACK, 600); buzz(250) }
    fun rest() { tone(ToneGenerator.TONE_PROP_BEEP2, 600); buzz(250) }
    fun half() { tone(ToneGenerator.TONE_PROP_BEEP2, 300); buzz(150) }
    fun finish() { tone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1400); buzz(700) }
}

@Composable
internal fun rememberTimerCues(settings: TimerSettings): TimerCues {
    val context = LocalContext.current
    val tones = remember { runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tones?.release() } }
    return remember(settings) { TimerCues(settings = settings, tones = tones, vibrator = context.getSystemService(Vibrator::class.java)) }
}

/** IDLE, READY (get ready / switch sides), WORK, REST, PAUSED or DONE. */
internal enum class TimerState { IDLE, READY, WORK, REST, PAUSED, DONE }

/** A button along the bottom of the timer. */
internal data class TimerButton(
    val label: String,
    val filled: Boolean = false,
    val enabled: Boolean = true,
    val weight: Float = 1f,
    val onClick: () -> Unit,
)

private val WORK_FULL = Color(0xFF16A34A)
private val REST_FULL = Color(0xFFDC2626)
private val READY_FULL = Color(0xFFD97706)

/**
 * The timer's screen. [progress] is how much of the current part is left
 * (0–1; null for a stopwatch, which shows a full ring). [bar] is the current
 * part and how many there are. [now] and [next] are what's being done and
 * what comes after. [extra] sits under them (rounds, a result to log).
 */
@Composable
internal fun TimerFace(
    family: Boolean,
    caption: String,
    heading: String?,
    clock: String,
    state: TimerState,
    word: String,
    progress: Float?,
    bar: Pair<Int, Int>?,
    now: String?,
    next: String?,
    buttons: List<TimerButton>,
    onBack: () -> Unit,
    extra: @Composable ColumnScope.(TimerInk) -> Unit = {},
) {
    val settings = rememberTimerSettings()
    val pal = timerPalette()
    val digits = timerDigits(pal)
    var showSettings by remember { mutableStateOf(false) }
    val running = state == TimerState.WORK || state == TimerState.REST || state == TimerState.READY
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    val ink = timerInk(pal, settings.lookFor(family), state)
    val inPip by TimerLive.inPip.collectAsState()
    if (inPip) {
        PipTimer(clock = clock, status = word, color = ink.state, pal = pal, digits = digits)
        return
    }
    val ring = settings.lookFor(family) != "COLOR"

    Column(
        Modifier
            .fillMaxSize()
            .background(ink.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = ink.soft, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { settings.setSound(!settings.sound) }) {
                Text(if (settings.sound) "Sound on" else "Sound off", color = ink.soft)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(KairosIcons.Settings, contentDescription = "Timer settings", tint = ink.soft)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (caption.isNotBlank()) Text(caption, color = ink.soft, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
            if (!heading.isNullOrBlank()) {
                Text(
                    heading,
                    color = ink.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (ring) {
                Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                    val track = ink.text.copy(alpha = 0.12f)
                    val arc = ink.state
                    val p = (progress ?: 1f).coerceIn(0f, 1f)
                    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                        val w = 13.dp.toPx()
                        val d = size.minDimension - w
                        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                        drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = tl, size = Size(d, d), style = Stroke(w))
                        if (p > 0f) drawArc(color = arc, startAngle = -90f, sweepAngle = 360f * p, useCenter = false, topLeft = tl, size = Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            clock,
                            color = digits.color ?: ink.text,
                            fontFamily = digits.font,
                            fontSize = if (clock.length > 5) 48.sp else 64.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(word.uppercase(), color = ink.state, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, maxLines = 1)
                    }
                }
            } else {
                Text(
                    clock,
                    color = if (state == TimerState.IDLE || state == TimerState.DONE) digits.color ?: ink.text else ink.text,
                    fontFamily = digits.font,
                    fontSize = if (clock.length > 5) 84.sp else 120.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(word.uppercase(), color = ink.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
            }
            bar?.takeIf { it.second in 2..60 }?.let { (cur, total) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (i in 0 until total) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(7.dp)
                                .background(
                                    when {
                                        i < cur -> ink.state
                                        i == cur -> ink.state.copy(alpha = 0.45f)
                                        else -> ink.text.copy(alpha = 0.14f)
                                    },
                                    RoundedCornerShape(3.dp),
                                ),
                        )
                    }
                }
            } ?: Spacer(Modifier.height(14.dp))
            if (!now.isNullOrBlank()) {
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .border(1.dp, ink.line, shape)
                        .background(Color.Transparent, shape),
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(ink.state.copy(alpha = if (ring) 0.10f else 0.18f), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = if (next.isNullOrBlank()) 14.dp else 0.dp, bottomEnd = if (next.isNullOrBlank()) 14.dp else 0.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(now, color = ink.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("now", color = ink.soft, fontSize = 13.sp)
                    }
                    if (!next.isNullOrBlank()) {
                        HorizontalDivider(color = ink.line)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(next, color = ink.soft, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("next", color = ink.soft, fontSize = 13.sp)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { extra(ink) }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            buttons.forEach { b ->
                val shape = RoundedCornerShape(999.dp)
                Box(
                    Modifier
                        .weight(b.weight)
                        .heightIn(min = 54.dp)
                        .background(if (b.filled) ink.text.copy(alpha = if (b.enabled) 1f else 0.4f) else Color.Transparent, shape)
                        .border(if (b.filled) 0.dp else 1.5.dp, if (b.filled) Color.Transparent else ink.line, shape)
                        .clickable(enabled = b.enabled, onClick = b.onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(b.label, color = if (b.filled) ink.bg else ink.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
        }
    }
    if (showSettings) TimerSettingsSheet(settings = settings, onDismiss = { showSettings = false })
}

/** The colours the timer draws in for a look and state. */
internal data class TimerInk(val bg: Color, val text: Color, val soft: Color, val line: Color, val state: Color)

internal fun timerInk(pal: TimerPalette, look: String, state: TimerState): TimerInk {
    val dark = pal.bg.luminance() < 0.4f
    val work = if (dark) Color(0xFF4ADE80) else WORK_FULL
    val stateColor = when (state) {
        TimerState.READY -> pal.ready
        TimerState.REST -> pal.rest
        TimerState.WORK -> work
        else -> pal.soft
    }
    if (look == "COLOR") {
        val full = when (state) {
            TimerState.WORK -> WORK_FULL
            TimerState.REST -> REST_FULL
            TimerState.READY -> READY_FULL
            else -> null
        }
        if (full != null) {
            return TimerInk(bg = full, text = Color.White, soft = Color.White.copy(alpha = 0.8f), line = Color.White.copy(alpha = 0.5f), state = Color.White)
        }
    }
    return TimerInk(bg = pal.bg, text = pal.ink, soft = pal.soft, line = pal.line, state = stateColor)
}

/** ⚙ Timer settings (v0.585): one sheet for every timer. */
@Composable
internal fun TimerSettingsSheet(settings: TimerSettings, onDismiss: () -> Unit) {
    val store = rememberContainer().settingsStore
    val scope = rememberCoroutineScope()
    val font by store.timerFont.collectAsState(initial = "STANDARD")
    val color by store.timerColor.collectAsState(initial = "PLAIN")
    val theme by store.timerTheme.collectAsState(initial = "DARK")
    val primary = MaterialTheme.colorScheme.primary
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "Timer settings",
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TkLabel("Look for my workouts")
            TkSegment(listOf("RING" to "Ring", "COLOR" to "Full color"), settings.look) { settings.setLook(it) }
            TkLabel("Look for Family WOD")
            TkSegment(listOf("RING" to "Ring", "COLOR" to "Full color"), settings.lookFamily) { settings.setLookFamily(it) }
            TkLabel("Background")
            TkSegment(listOf("DARK" to "Dark", "LIGHT" to "Light", "APP" to "Match app"), theme) { v -> scope.launch { store.setTimerTheme(v) } }
            TkSwitchRow("Sound", settings.sound) { settings.setSound(it) }
            TkSwitchRow("Vibration", settings.vibrate) { settings.setVibrate(it) }
            TkLabel("Get ready")
            TkSegment(TIMER_LEADS.map { it.toString() to "${it}s" }, settings.lead.toString()) { settings.setLead(it.toInt()) }
            TkLabel("Pause between sides")
            TkSegment(SIDE_GAPS.map { it.toString() to "${it}s" }, settings.sideGap.toString()) { settings.setSideGap(it.toInt()) }
            TkLabel("Start side")
            TkSegment(listOf("L" to "Left", "R" to "Right"), if (settings.rightFirst) "R" else "L") { settings.setRightFirst(it == "R") }
            TkLabel("Numbers")
            TimerLookPicker(
                options = TIMER_FONTS,
                selected = font,
                font = { timerFontFor(it) },
                color = { timerColorFor(color, primary) ?: Color.White },
                onPick = { v -> scope.launch { store.setTimerFont(v) } },
            )
            TkLabel("Number color")
            TimerLookPicker(
                options = TIMER_COLORS,
                selected = color,
                font = { timerFontFor(font) },
                color = { timerColorFor(it, primary) ?: Color.White },
                onPick = { v -> scope.launch { store.setTimerColor(v) } },
            )
        }
    }
}

@Composable
private fun TkLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun TkSwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun TkSegment(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(c.surfaceVariant, RoundedCornerShape(10.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (k, l) ->
            val on = k == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .background(if (on) c.surface else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onPick(k) },
                contentAlignment = Alignment.Center,
            ) {
                Text(l, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.onSurface else c.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
