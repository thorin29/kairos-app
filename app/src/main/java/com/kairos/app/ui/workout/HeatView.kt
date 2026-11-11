package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.HeatGroupDto
import com.kairos.app.data.remote.dto.HeatRangeDto
import com.kairos.app.data.remote.dto.TrainGroupDto
import com.kairos.app.data.remote.dto.TrainingHeatDto
import com.kairos.app.ui.nav.KairosIcons

/**
 * The body map's heat view (v0.442): one hue, three strengths, by hard sets a
 * week over the chosen range. The server does the counting, so the phone and
 * the web show the same numbers.
 */

internal val HEAT_RANGES = listOf(
    "1w" to "1 wk",
    "4w" to "4 wk",
    "12w" to "12 wk",
    "6m" to "6 mo",
    "all" to "All",
)

internal fun heatShade(level: Int, accent: Color, fade: Color): Color? = when (level) {
    1 -> lerp(start = fade, stop = accent, fraction = 0.40f)
    2 -> lerp(start = fade, stop = accent, fraction = 0.70f)
    3 -> accent
    else -> null
}

internal fun heatFills(range: HeatRangeDto?, accent: Color, fade: Color): Map<String, Color> = buildMap {
    range?.groups.orEmpty().forEach { g ->
        heatShade(level = g.level, accent = accent, fade = fade)?.let { put(g.group, it) }
    }
}

/** The tap targets with something behind them in this range. */
internal fun heatAvailable(range: HeatRangeDto?): List<String> =
    range?.groups.orEmpty()
        .filter { it.level > 0 }
        .map { if (it.group == "DEADLIFT") "POSTERIOR_HIP" else it.group }

internal fun heatGroupOf(nav: String): String = if (nav == "POSTERIOR_HIP") "DEADLIFT" else nav

private fun navLabel(nav: String): String =
    if (nav == "POSTERIOR_HIP") "Deadlift"
    else nav.split('_').joinToString(" ") { it.lowercase() }.replaceFirstChar { it.uppercase() }

private fun clock(sec: Double): String {
    val s = Math.round(sec).toInt()
    return "${s / 60}:" + (s % 60).toString().padStart(2, '0')
}

private fun fmt(n: Double): String =
    if (n == Math.floor(n)) n.toLong().toString() else String.format(java.util.Locale.US, "%.1f", n)

/** The flame button that turns the heat view on and off. */
@Composable
internal fun HeatToggle(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (on) accent else Color.Transparent)
            .border(width = 1.dp, color = accent, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            KairosIcons.Flame,
            contentDescription = "Heat map",
            tint = if (on) MaterialTheme.colorScheme.onPrimary else accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeatRangeChips(value: String, onChange: (String) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp, alignment = Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HEAT_RANGES.forEach { (key, label) ->
            val on = key == value
            Box(
                Modifier
                    .height(32.dp)
                    .clip(shape)
                    .background(if (on) accent else Color.Transparent)
                    .border(width = 1.dp, color = accent, shape = shape)
                    .clickable { onChange(key) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else accent,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeatKey(accent: Color, fade: Color) {
    var about by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp, alignment = Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.Center,
    ) {
        listOf(1 to "Under 4", 2 to "4–9", 3 to "10+").forEach { (lvl, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(heatShade(level = lvl, accent = accent, fade = fade) ?: fade),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
        }
        Text("sets a week", style = MaterialTheme.typography.labelSmall, color = muted)
        Icon(
            KairosIcons.Info,
            contentDescription = "About the heat map",
            tint = muted,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable { about = true }
                .padding(4.dp),
        )
    }
    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text("Heat map") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hard sets per muscle, averaged per week over the range you pick.")
                    Text(
                        "A set counts in full when it ended within about 5 reps of what you could do, " +
                            "and half when 6–9 short. Warm-ups and easy sets don't count. Marking a lift " +
                            "Hard counts it in full; Easy counts it half.",
                    )
                    Text("Muscles a lift only helps with get half a set.")
                    Text(
                        "Holds count in full at two-thirds of your longest or more, half at 40%. A plank counts " +
                            "in full at 1:10, the Marine Corps PFT minimum, and shows its PFT points (3:45 is 100).",
                    )
                    Text("Heavy sets are 6 reps or fewer near your best weight. They're what builds strength most.")
                    Text("Around 10 or more hard sets a week is a strong week for a muscle.")
                }
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text("Close") } },
        )
    }
}

@Composable
internal fun HeatDetailDialog(nav: String, data: HeatGroupDto?, unit: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(navLabel(nav)) },
        text = {
            if (data == null || data.perWeek <= 0.0) {
                Text("No hard sets in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeatStat(value = fmt(data.perWeek), label = "sets a week", modifier = Modifier.weight(1f))
                        HeatStat(value = fmt(data.heavyPerWeek), label = "heavy sets a week", modifier = Modifier.weight(1f))
                    }
                    HeatBests(bests = data.bests, unit = unit)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun HeatStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Each best, then → now: weight at a rep count, most reps, or longest hold (with PFT points). */
@Composable
internal fun HeatBests(bests: List<com.kairos.app.data.remote.dto.HeatBestDto>, unit: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        bests.forEach { b ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val what = when (b.kind) {
                    "weight" -> "${b.reps} reps"
                    "time" -> "longest hold"
                    else -> "most reps"
                }
                Text(
                    b.name + " · " + what,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val show = { n: Double -> if (b.kind == "time") clock(n) else fmt(n) }
                val value = if (b.from == b.to) show(b.to) else "${show(b.from)} → ${show(b.to)}"
                val suffix = when {
                    b.kind == "weight" && unit.isNotEmpty() -> " $unit"
                    b.pft != null -> " · ${b.pft} pts"
                    else -> ""
                }
                Text(
                    value + suffix,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** A muscle with no weight charts (a core of holds and reps): its hard sets a
 *  week over the last 12 weeks and its bests (v0.445). */
@Composable
internal fun HeatGroupSummary(data: HeatGroupDto?, unit: String) {
    if (data == null || (data.perWeek <= 0.0 && data.bests.isEmpty())) {
        Text(
            "Nothing logged yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${fmt(data.perWeek)} hard sets a week · last 12 weeks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HeatBests(bests = data.bests, unit = unit)
    }
}

// --- the training heat map (v0.474) -------------------------------------------

/** Warmth 1–5 pale yellow to red, −1 to −3 light to deep blue (the web's
 *  TRAIN_WARM / TRAIN_COLD); the same on every theme. Neutral is the figure's grey. */
private val TRAIN_WARM = listOf(Color(0xFFFDE68A), Color(0xFFFACC15), Color(0xFFFB923C), Color(0xFFEA580C), Color(0xFFDC2626))
private val TRAIN_COLD = listOf(Color(0xFFBFDBFE), Color(0xFF60A5FA), Color(0xFF2563EB))

internal fun trainColor(level: Int): Color? = when {
    level > 0 -> TRAIN_WARM[minOf(5, level) - 1]
    level < 0 -> TRAIN_COLD[minOf(3, -level) - 1]
    else -> null
}

internal fun trainFills(t: TrainingHeatDto?): Map<String, Color> = buildMap {
    t?.groups.orEmpty().forEach { g -> trainColor(g.level)?.let { put(g.group, it) } }
}

internal fun trainAvailable(t: TrainingHeatDto?): List<String> =
    t?.groups.orEmpty().map { if (it.group == "DEADLIFT") "POSTERIOR_HIP" else it.group }

@Composable
internal fun TrainKey(neutral: Color) {
    var about by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("cooled", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(end = 4.dp))
        (TRAIN_COLD.reversed() + neutral + TRAIN_WARM).forEach { c ->
            Box(
                Modifier
                    .padding(horizontal = 1.5.dp)
                    .size(width = 16.dp, height = 11.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c),
            )
        }
        Text("hot", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(start = 4.dp))
        Icon(
            KairosIcons.Info,
            contentDescription = "About the heat map",
            tint = muted,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .clickable { about = true }
                .padding(4.dp),
        )
    }
    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text("Heat map") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Each muscle warms a step for every week you give it real work, and fastest when you're coming back to where you were.")
                    Text("The hottest step is for training it steadily and setting a new best in the last six weeks.")
                    Text("A missed week cools it a step. It only turns blue after it's cooled to grey and you've missed it about a month.")
                    Text(
                        "Strength that keeps slipping for two weeks cools it too. Light, deload and test weeks, " +
                            "adjusted days, and your first session back aren't judged.",
                    )
                    Text("The heart is endurance: sport, rowing, running, rucking and conditioning, by minutes and how hard they felt.")
                }
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text("Close") } },
        )
    }
}

private const val HEART_D =
    "M12 21s-8.5-5.4-10.6-10.3C-0.2 6.7 2.3 2 6.6 2c2.2 0 3.9 1.2 5.4 3 1.5-1.8 3.2-3 5.4-3C21.7 2 24.2 6.7 22.6 10.7 20.5 15.6 12 21 12 21z"

/** The endurance gauge: a heart in the same colours. */
@Composable
internal fun EnduranceHeart(data: TrainGroupDto?, neutral: Color, onOpen: () -> Unit) {
    val path = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString(HEART_D).toPath() }
    val c = data?.let { trainColor(it.level) } ?: neutral
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen() }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 36.dp, height = 33.dp)) {
            val s = size.width / 24f
            withTransform({ scale(scaleX = s, scaleY = s, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
                drawPath(path = path, color = c)
            }
        }
        Text("Endurance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A tapped muscle (or the heart): why it's this colour, and its bests. */
@Composable
internal fun TrainDetailDialog(
    title: String,
    data: TrainGroupDto?,
    neutral: Color,
    onDismiss: () -> Unit,
    /** The heart's Cardio and Conditioning rows open their pages (v0.484). */
    onOpenRow: ((String) -> Unit)? = null,
) {
    // This week (v0.483): exact sets or minutes, and a row per movement or
    // kind. With nothing this week, why it's this colour instead.
    val week = data?.week
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(data?.let { trainColor(it.level) } ?: neutral),
                    )
                    Text(
                        week?.headline ?: data?.line ?: "Nothing logged yet.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val why = data?.line.orEmpty()
                if (week != null && week.rows.isEmpty() && why.isNotBlank()) {
                    Text(why, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                week?.rows?.forEach { r ->
                    androidx.compose.material3.HorizontalDivider()
                    val opens = onOpenRow != null && (r.name == "Cardio" || r.name == "Conditioning")
                    Row(
                        Modifier.fillMaxWidth().then(if (opens) Modifier.clickable { onOpenRow?.invoke(r.name) } else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(r.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(r.amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (opens) Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

internal fun trainTitle(nav: String): String = if (nav == "ENDURANCE") "Endurance" else navLabel(nav)
