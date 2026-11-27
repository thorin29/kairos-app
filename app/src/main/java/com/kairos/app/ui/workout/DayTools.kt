package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.data.remote.dto.ConditioningPlanDto
import com.kairos.app.data.remote.dto.WarmupStepDto
import com.kairos.app.ui.common.FullPage
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons

/**
 * The workout day's buttons (v0.585), as on the Workouts page: Train (Start
 * a walk or run, the day's Family WOD, Log another) and Tools (Timer,
 * Routines, Calculator).
 */
@Composable
internal fun DayTiles(
    /** The day has a Family WOD: the tile says Today (0.585.2: always shown). */
    hasFamilyWod: Boolean,
    onStart: () -> Unit,
    onFamilyWod: () -> Unit,
    onLogAnother: () -> Unit,
    onTimer: () -> Unit,
    onRoutines: () -> Unit,
    onCalculator: () -> Unit,
) {
    val going by ActivityTrack.session.collectAsState()
    val g = going
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionGroup(kind = "Train", title = "Today") {
            ActionCard(
                if (g != null) trackIcon(g.activity) else KairosIcons.Run,
                if (g != null) trackLabel(g.activity) else "Start a…",
                Modifier.weight(1f),
                highlighted = g != null,
                subtitle = when {
                    g == null -> "Walk · Run · Ruck"
                    g.endedAt != null -> "Finished"
                    g.paused -> "Paused"
                    else -> "In progress"
                },
                onClick = onStart,
            )
            ActionCard(KairosIcons.Users, "Family WOD", Modifier.weight(1f), subtitle = if (hasFamilyWod) "Today" else null, onClick = onFamilyWod)
            ActionCard(KairosIcons.Plus, "Log another", Modifier.weight(1f), highlighted = true, onClick = onLogAnother)
        }
        ActionGroup(kind = "Tools", title = "") {
            ActionCard(KairosIcons.Timer, "Timer", Modifier.weight(1f), onClick = onTimer)
            ActionCard(KairosIcons.Flame, "Routines", Modifier.weight(1f), onClick = onRoutines)
            ActionCard(KairosIcons.Calculator, "Calculator", Modifier.weight(1f), onClick = onCalculator)
        }
    }
}

/** Log another workout's choices (v0.585): key, name, what it's for, icon. */
private data class LogChoice(val key: String, val name: String, val sub: String, val icon: ImageVector)

private val LOG_CHOICES = listOf(
    LogChoice(key = "strength", name = "Strength", sub = "Weights, sets and reps", icon = KairosIcons.Dumbbell),
    LogChoice(key = "cardio", name = "Cardio", sub = "Run, row, ruck or bike · distance and time", icon = KairosIcons.Run),
    LogChoice(key = "sport", name = "Sport", sub = "Practice, game or open skate", icon = KairosIcons.Hockey),
    LogChoice(key = "conditioning", name = "Conditioning", sub = "Pick a WOD or make one, with the timer", icon = KairosIcons.Bolt),
)

/**
 * Log another workout (v0.585): a full page of choices instead of the
 * pop-up (0.585.2: four; Something else only repeated them). Starting a walk or run and the Family WOD are their own buttons;
 * Mobility, Stretching and Isometrics are under Routines.
 */
@Composable
internal fun LogAnotherPage(onPick: (String) -> Unit, onBack: () -> Unit) {
    FullPage(title = "Log another workout", onBack = onBack) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Text(
                "WHAT DID YOU DO?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            LOG_CHOICES.forEach { c ->
                HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(c.key) }
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(c.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(c.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** The kinds on the Routines page (v0.585). */
private val ROUTINE_KINDS = listOf("warmup" to "Warm-up", "mobility" to "Mobility", "stretching" to "Stretching", "iso" to "Isometrics")

private data class RoutinePick(val id: String, val name: String, val sub: String, val steps: List<WarmupStepDto>)

/**
 * Routines (v0.585): warm-ups, mobility, stretching and isometrics in one
 * place. A warm-up starts its timer now; the others are added to today.
 */
@Composable
internal fun RoutinesPage(onAdd: (kind: String, id: String) -> Unit, onBack: () -> Unit) {
    val session = rememberContainer().sessionRepository
    var kind by remember { mutableStateOf("mobility") }
    var rows by remember(kind) { mutableStateOf<List<RoutinePick>?>(null) }
    var running by remember { mutableStateOf<RoutinePick?>(null) }
    LaunchedEffect(kind) {
        rows = runCatching {
            if (kind == "iso") {
                session.coreWorkouts(kind = "iso").map { w ->
                    RoutinePick(id = w.id, name = w.name, sub = listOf("${w.minutes} min", w.gear).filter { it.isNotBlank() }.joinToString(" · "), steps = emptyList())
                }
            } else {
                session.warmupRoutines(kind = kind).map { r ->
                    val sec = r.steps.sumOf { (it.seconds ?: 0) * if (it.sides) 2 else 1 }
                    RoutinePick(
                        id = r.id,
                        name = r.name,
                        sub = listOfNotNull("${r.steps.size} steps", if (sec >= 60) "${sec / 60} min" else null).joinToString(" · "),
                        steps = r.steps,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
    FullPage(title = "Routines", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ROUTINE_KINDS.forEach { (k, label) ->
                val on = k == kind
                val c = MaterialTheme.colorScheme
                Box(
                    Modifier
                        .heightIn(min = 36.dp)
                        .background(if (on) c.onSurface else Color.Transparent, RoundedCornerShape(10.dp))
                        .border(1.dp, if (on) c.onSurface else c.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { kind = k }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.surface else c.onSurface, maxLines = 1) }
            }
        }
        val list = rows
        when {
            list == null -> Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.isEmpty() -> Text("None yet. Make them on the Workouts page under Routines.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> OutlinedCard(Modifier.fillMaxWidth()) {
                list.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            if (r.sub.isNotBlank()) Text(r.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val startNow = kind == "warmup" && r.steps.any { it.kind == "TIME" && (it.seconds ?: 0) > 0 }
                        com.kairos.app.ui.common.AccentOutlinedButton(onClick = { if (kind == "warmup") running = r else onAdd(kind, r.id) }, enabled = kind != "warmup" || startNow) {
                            Text(if (kind == "warmup") "Start" else "Add to today")
                        }
                    }
                }
            }
        }
        Text(
            "Warm-ups start now. Mobility, stretching and isometrics are added to today. Editing routines stays on the Workouts page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    running?.let { r ->
        WarmupTimer(steps = r.steps, from = 0, onFinished = {}, onClose = { running = null })
    }
}

/**
 * The free Timer (v0.585): pick a kind and a length, then the shared timer
 * runs it. Nothing is logged. The last choices are kept on this phone.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun FreeTimerPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kairos_timer", android.content.Context.MODE_PRIVATE) }
    var type by remember { mutableStateOf(prefs.getString("free_type", "EMOM") ?: "EMOM") }
    var minutes by remember { mutableIntStateOf(prefs.getInt("free_min", 12)) }
    var work by remember { mutableIntStateOf(prefs.getInt("free_work", 40)) }
    var rest by remember { mutableIntStateOf(prefs.getInt("free_rest", 20)) }
    var rounds by remember { mutableIntStateOf(prefs.getInt("free_rounds", 8)) }
    var running by remember { mutableStateOf(false) }
    fun save() = prefs.edit().putString("free_type", type).putInt("free_min", minutes).putInt("free_work", work).putInt("free_rest", rest).putInt("free_rounds", rounds).apply()
    val plan = freeTimerPlan(type = type, minutes = minutes, work = work, rest = rest, rounds = rounds)
    FullPage(title = "Timer", onBack = onBack) {
        Text("TYPE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FREE_TIMERS.forEach { (k, label) ->
                val on = k == type
                val c = MaterialTheme.colorScheme
                Box(
                    Modifier
                        .heightIn(min = 36.dp)
                        .background(if (on) c.onSurface else Color.Transparent, RoundedCornerShape(10.dp))
                        .border(1.dp, if (on) c.onSurface else c.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { type = k; save() }
                        .padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.surface else c.onSurface) }
            }
        }
        when (type) {
            "COUNTDOWN", "AMRAP", "EMOM" -> OutlinedCard(Modifier.fillMaxWidth()) {
                Stepper(label = "Length", value = minutes, text = "$minutes min", min = 1, max = 90) { minutes = it; save() }
                Text(
                    when (type) {
                        "EMOM" -> "A new minute every 60 seconds, $minutes rounds."
                        "AMRAP" -> "As many rounds as you can; tap +1 round as you go."
                        else -> "Counts down from $minutes:00."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                )
            }
            "INTERVALS" -> OutlinedCard(Modifier.fillMaxWidth()) {
                Stepper(label = "Work", value = work, text = "$work s", min = 5, max = 600, step = 5) { work = it; save() }
                HorizontalDivider()
                Stepper(label = "Rest", value = rest, text = "$rest s", min = 0, max = 600, step = 5) { rest = it; save() }
                HorizontalDivider()
                Stepper(label = "Rounds", value = rounds, text = "$rounds", min = 1, max = 50) { rounds = it; save() }
            }
            "TABATA" -> Text("8 rounds of 20 seconds on, 10 seconds off.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text("Counts up until you tap Done.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            clockText(plan.totalSec ?: 0),
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
        )
        Button(onClick = { running = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Start", fontWeight = FontWeight.Bold)
        }
        Text(
            "Same clock, beeps and get-ready as every timer. Nothing is logged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
    if (running) {
        ConditioningTimer(
            workout = ConditioningPlanDto(name = "Timer", type = type),
            onLog = { _, _ -> running = false },
            onClose = { running = false },
            custom = plan,
            logging = false,
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, text: String, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = value > min) { Text("−", fontSize = 22.sp) }
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
        IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = value < max) { Text("+", fontSize = 22.sp) }
    }
}
