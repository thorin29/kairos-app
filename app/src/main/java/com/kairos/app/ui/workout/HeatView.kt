package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.HeatGroupDto
import com.kairos.app.data.remote.dto.TrainGroupDto
import com.kairos.app.data.remote.dto.TrainingHeatDto
import com.kairos.app.ui.nav.KairosIcons
import com.kairos.app.ui.common.AnimatedDialog

/**
 * The body map's heat views. The training heat map (v0.664, rules in the
 * server's training-heat.ts) is drawn from `training`; a muscle with no weight
 * charts shows its 12-week summary (`HeatGroupSummary`) from the server's
 * "12w" range. The old range view (chips, key, detail pop-up) was removed in
 * 0.502.0. The server does the counting, so the phone and the web match.
 */

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
        AnimatedDialog(
            onDismissRequest = { about = false },
            title = "Heat map",
            confirmButton = { TextButton(onClick = { about = false }) { Text("Close") } },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pale yellow: trained regularly, 4 of the last 6 weeks with the muscle's own exercises.")
                Text("It warms as you get stronger: up 3% over 12 weeks is yellow, 6% orange. Red is a strong run over about 6 months.")
                Text("When progress stalls it eases back, but not below pale yellow while you keep training.")
                Text("A missed week cools it a step. Blue after about a month off.")
                Text("The heart is endurance: sport, rowing, running, rucking and conditioning, by minutes and how hard they felt. About 120 hard minutes a week is yellow; doing more than you used to warms it.")
            }
        }
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
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    ) {
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
            // Why it's this colour (v0.497: shown with the week's rows too).
            if (week != null && why.isNotBlank()) {
                Text(why, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // "+31% since you started" (v0.499).
            data?.since?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    }
}

internal fun trainTitle(nav: String): String = if (nav == "ENDURANCE") "Endurance" else navLabel(nav)
