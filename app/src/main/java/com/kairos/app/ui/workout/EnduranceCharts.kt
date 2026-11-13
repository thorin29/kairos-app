package com.kairos.app.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairos.app.data.remote.dto.CardioActivityDto
import com.kairos.app.data.remote.dto.CardioChartsDto
import com.kairos.app.data.remote.dto.CondRepeatDto
import com.kairos.app.data.remote.dto.ConditioningChartsDto
import com.kairos.app.ui.nav.KairosIcons
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import com.kairos.app.ui.common.AnimatedDialog

/** The weekly-training colours (v0.481), so a kind reads the same everywhere. */
// v0.496: Weekly training's Cardio and Conditioning colours.
internal val CARDIO_COLOR = Color(0xFF0EA5E9)
internal val CONDITIONING_COLOR = Color(0xFFC026D3)
private val EASY_SHADE = Color(0xFF9FD9F6)
private val HARD_SHADE = Color(0xFF075985)
private val NO_EFFORT = Color(0xFFC9CED6)
private val BEST_DOT = Color(0xFF7A4A00)
private val DOT = Color(0xFFF3C76A)
private val GREEN = Color(0xFF047857)
private val AMBER = Color(0xFFB45309)

private fun effortShade(e: String?): Color = when (e) {
    "EASY" -> EASY_SHADE
    "SOLID" -> CARDIO_COLOR
    "HARD", "TOO_HARD" -> HARD_SHADE
    else -> NO_EFFORT
}

private fun md(iso: String): String = try {
    val d = LocalDate.parse(iso)
    d.month.getDisplayName(TextStyle.SHORT, Locale.US) + " " + d.dayOfMonth
} catch (_: Exception) {
    iso
}

private fun dayLong(iso: String): String = try {
    val d = LocalDate.parse(iso)
    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) + ", " + md(iso)
} catch (_: Exception) {
    iso
}

private fun clock(sec: Double): String {
    val t = sec.roundToInt().coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "$h:" + m.toString().padStart(2, '0') + ":" + s.toString().padStart(2, '0') else "$m:" + s.toString().padStart(2, '0')
}

/** A rowing split with tenths: "1:56.3" (the web's split). */
private fun split(sec: Double): String {
    val t = (sec * 10).roundToInt() / 10.0
    val m = (t / 60).toInt()
    val s = t - m * 60
    return "$m:" + (if (s < 10) "0" else "") + String.format(Locale.US, "%.1f", s)
}

private fun num(v: Double): String =
    if (v % 1.0 == 0.0) String.format(Locale.US, "%,d", v.toLong()) else String.format(Locale.US, "%,.1f", v)

private fun hoursText(min: Int): String = if (min >= 60) "${min / 60} h ${min % 60} m" else "$min m"

@Composable
private fun Chevron() {
    Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
}

@Composable
private fun SmallCaps(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Twelve weekly bars with dates under them. */
@Composable
private fun WeekBars(values: List<Double>, starts: List<String>, color: Color, height: Dp) {
    val max = maxOf(1.0, values.maxOrNull() ?: 1.0)
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val gap = 4.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            values.forEachIndexed { i, v ->
                val h = (v / max).toFloat() * size.height
                if (v > 0) drawRect(color, topLeft = Offset(i * (bw + gap), size.height - h), size = androidx.compose.ui.geometry.Size(bw, maxOf(h, 2f)))
            }
            drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.5f)
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 4, 8).forEach { i -> starts.getOrNull(i)?.let { Text(md(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            Text("This week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartTile(key: String, value: String, sub: String, modifier: Modifier, tone: Color? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(key.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tone ?: MaterialTheme.colorScheme.onSurface)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FullPage(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // The app's one full page (v0.491).
    com.kairos.app.ui.common.FullPage(title = title, onBack = onDismiss) { content() }
}

// --- cardio ------------------------------------------------------------------

/** Cardio on the Workouts screen (v0.484): this week's minutes, a line per
 *  activity, twelve weeks of minutes. Opens the Cardio page. */
@Composable
internal fun CardioChartCard(data: CardioChartsDto, onOpen: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen() }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SmallCaps("This week")
                    Text("${data.thisWeek.minutes} min", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (data.thisWeek.lines.isNotEmpty()) {
                        data.thisWeek.lines.forEach { l ->
                            Text(l.label + " · " + l.detail, style = MaterialTheme.typography.labelMedium, color = muted)
                        }
                    } else {
                        data.last?.let { Text("Last: " + it.label + " · " + dayLong(it.date), style = MaterialTheme.typography.labelMedium, color = muted) }
                    }
                }
                Chevron()
            }
            WeekBars(values = data.weeks.map { it.minutes.toDouble() }, starts = data.weeks.map { it.start }, color = CARDIO_COLOR, height = 48.dp)
        }
    }
}

/** Pace per session: faster is higher, shaded by how it felt, the best ringed. */
@Composable
private fun PaceChart(a: CardioActivityDto) {
    val p = a.pace ?: return
    val pts = p.points
    if (pts.isEmpty()) return
    val ink = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val lo = pts.minOf { it.sec }
    val hi = pts.maxOf { it.sec }
    val pad = maxOf(2.0, (hi - lo) * 0.12)
    val a0 = lo - pad
    val a1 = hi + pad
    val ticks = listOf(a0 + (a1 - a0) * 0.2, (a0 + a1) / 2, a0 + (a1 - a0) * 0.8)
    val fmt: (Double) -> String = { s -> if (a.measure == "m") split(s).substringBefore('.') else clock(s) }
    val bi = p.best?.let { b -> pts.indexOfFirst { it.date == b.date && abs(it.sec - b.sec) < 0.05 } } ?: -1
    Row(Modifier.fillMaxWidth().height(170.dp)) {
        Box(Modifier.width(44.dp).fillMaxHeight()) {
            ticks.forEach { t ->
                val frac = ((t - a0) / (a1 - a0)).toFloat()
                Text(
                    fmt(t),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = (12 + frac * 146 - 7).coerceAtLeast(0f).dp),
                )
            }
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val w = size.width
            val h = size.height
            val m = 12.dp.toPx()
            fun y(s: Double) = m + ((s - a0) / (a1 - a0)).toFloat() * (h - 2 * m)
            fun x(i: Int) = if (pts.size == 1) w / 2 else 8f + i * ((w - 16f) / (pts.size - 1))
            ticks.forEach { t -> drawLine(grid, Offset(0f, y(t)), Offset(w, y(t)), 1.5f) }
            pts.forEachIndexed { i, pt ->
                drawCircle(surface, radius = 11f, center = Offset(x(i), y(pt.sec)))
                drawCircle(effortShade(pt.effort), radius = 9f, center = Offset(x(i), y(pt.sec)))
            }
            if (bi >= 0) drawCircle(ink, radius = 17f, center = Offset(x(bi), y(pts[bi].sec)), style = Stroke(width = 3f))
        }
    }
}

/**
 * The Cardio page (v0.484): an activity at a time — its pace per session (or
 * minutes for other cardio), its numbers, distance per week and recent
 * sessions. The same order as a lift's page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardioPageDialog(data: CardioChartsDto, onDismiss: () -> Unit) {
    var pick by remember { mutableStateOf(data.activities.firstOrNull()?.key) }
    val a = data.activities.firstOrNull { it.key == pick } ?: data.activities.firstOrNull() ?: return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val volTitle = when (a.measure) {
        "m" -> "Meters per week"
        "min" -> "Minutes per week"
        "km" -> "Kilometers per week"
        else -> "Miles per week"
    }
    val pace = a.pace
    FullPage(title = "Cardio", onDismiss = onDismiss) {
        if (data.activities.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                data.activities.forEach { x ->
                    val on = x.key == a.key
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier
                            .heightIn(min = 36.dp)
                            .clip(shape)
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                            .clickable { pick = x.key }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(x.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                if (pace != null && pace.points.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallCaps(if (a.measure == "m") "Split per 500 m" else "Pace per " + pace.per, Modifier.weight(1f))
                        Text("Faster is higher", style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                    PaceChart(a = a)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("Easy" to EASY_SHADE, "Solid" to CARDIO_COLOR, "Hard" to HARD_SHADE).forEach { (l, c) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                                Text(l, style = MaterialTheme.typography.labelSmall, color = muted)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(10.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
                            Text("Best", style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                    }
                } else {
                    SmallCaps(volTitle)
                    Spacer(Modifier.height(8.dp))
                    WeekBars(values = a.weeks.map { it.value }, starts = a.weeks.map { it.start }, color = CARDIO_COLOR, height = 90.dp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartTile(
                key = "This week",
                value = if (a.measure == "min") "${a.thisWeek.minutes} min" else num(a.thisWeek.value) + " " + a.measure,
                sub = "${a.thisWeek.count} " + (if (a.thisWeek.count == 1) "session" else "sessions") + if (a.measure == "min") "" else " · ${a.thisWeek.minutes} min",
                modifier = Modifier.weight(1f),
            )
            if (pace != null) {
                ChartTile(
                    key = if (a.measure == "m") "Best split" else "Best pace",
                    value = pace.best?.let { if (a.measure == "m") split(it.sec) else clock(it.sec) } ?: "—",
                    sub = pace.best?.let { md(it.date) } ?: if (a.measure == "m") "rows of 1,000 m+" else "a mile or more",
                    modifier = Modifier.weight(1f),
                )
                val ch = pace.change
                ChartTile(
                    key = if (a.measure == "m") "Split, 30 d" else "Pace, 30 d",
                    value = when {
                        ch == null -> "—"
                        ch == 0 -> "Same"
                        else -> (if (ch < 0) "−" else "+") + abs(ch) + " s"
                    },
                    sub = if (ch == null) "needs 2 months" else "per " + pace.per,
                    modifier = Modifier.weight(1f),
                    tone = when {
                        ch == null || ch == 0 -> null
                        ch < 0 -> GREEN
                        else -> AMBER
                    },
                )
            } else {
                ChartTile(key = "Longest", value = a.longest?.let { "${it.minutes} min" } ?: "—", sub = a.longest?.let { md(it.date) } ?: "", modifier = Modifier.weight(1f))
                ChartTile(key = "30 days", value = a.sessions30.toString(), sub = if (a.sessions30 == 1) "session" else "sessions", modifier = Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val wk = a.sinceBestWeeks
            ChartTile(
                key = "Since best",
                value = when {
                    wk == null -> "\u2014"
                    wk < 1 -> "This week"
                    else -> "$wk " + if (wk == 1) "week" else "weeks"
                },
                sub = pace?.best?.let { md(it.date) } ?: a.longest?.let { md(it.date) } ?: "",
                modifier = Modifier.weight(1f),
            )
            ChartTile(key = "Sessions", value = a.count.toString(), sub = if (a.firstDate.isNotBlank()) "since " + md(a.firstDate) else "", modifier = Modifier.weight(1f))
            ChartTile(key = "Last", value = a.last?.text?.substringBefore(" /") ?: "\u2014", sub = a.last?.let { md(it.date) } ?: "", modifier = Modifier.weight(1f))
        }
        if (pace != null) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SmallCaps(volTitle)
                    Spacer(Modifier.height(8.dp))
                    WeekBars(values = a.weeks.map { it.value }, starts = a.weeks.map { it.start }, color = CARDIO_COLOR, height = 90.dp)
                }
            }
        }
        SmallCaps("Recent sessions")
        a.recent.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(dayLong(r.date), style = MaterialTheme.typography.bodyMedium)
                Text(r.text, style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}

// --- conditioning --------------------------------------------------------------

/** Conditioning on the Workouts screen (v0.484): this month, new bests,
 *  twelve weeks of sessions. Opens the Conditioning page. */
@Composable
internal fun ConditioningChartCard(data: ConditioningChartsDto, onOpen: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen() }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SmallCaps("This month")
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(data.month.count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            (if (data.month.count == 1) "session" else "sessions") + " · " + hoursText(data.month.minutes),
                            style = MaterialTheme.typography.labelMedium,
                            color = muted,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text(
                        if (data.newBests30 > 0) "${data.newBests30} new " + (if (data.newBests30 == 1) "best" else "bests") + " in the last 30 days"
                        else "${data.repeats.size} " + (if (data.repeats.size == 1) "workout" else "workouts") + " done more than once",
                        style = MaterialTheme.typography.labelMedium,
                        color = muted,
                    )
                }
                Chevron()
            }
            WeekBars(values = data.weeks.map { it.count.toDouble() }, starts = data.weeks.map { it.start }, color = CONDITIONING_COLOR, height = 48.dp)
        }
    }
}

/** Each attempt as a dot, better higher; the best darker. */
@Composable
private fun Dots(r: CondRepeatDto, modifier: Modifier) {
    val vals = r.results.map { it.value }
    if (vals.isEmpty()) return
    val lo = vals.min()
    val hi = vals.max()
    Canvas(modifier) {
        val n = r.results.size
        r.results.forEachIndexed { i, x ->
            val score = if (hi == lo) 0.5f else if (r.timed) ((hi - x.value) / (hi - lo)).toFloat() else ((x.value - lo) / (hi - lo)).toFloat()
            val cx = if (n == 1) size.width / 2 else 10f + i * ((size.width - 20f) / (n - 1))
            val cy = size.height - 8f - score * (size.height - 16f)
            drawCircle(if (x.best) BEST_DOT else DOT, radius = if (x.best) 9f else 7f, center = Offset(cx, cy))
        }
    }
}

/**
 * The Conditioning page (v0.484): sessions per week, the numbers, the
 * workouts done more than once (each attempt a dot) and how many were done
 * once. A workout opens its attempts.
 */
@Composable
internal fun ConditioningPageDialog(data: ConditioningChartsDto, onDismiss: () -> Unit) {
    var open by remember { mutableStateOf<CondRepeatDto?>(null) }
    var onceOpen by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FullPage(title = "Conditioning", onDismiss = onDismiss) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                SmallCaps("Sessions per week")
                Spacer(Modifier.height(8.dp))
                WeekBars(values = data.weeks.map { it.count.toDouble() }, starts = data.weeks.map { it.start }, color = CONDITIONING_COLOR, height = 70.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartTile(key = "This month", value = data.month.count.toString(), sub = if (data.month.count == 1) "session" else "sessions", modifier = Modifier.weight(1f))
            ChartTile(key = "New bests", value = data.newBests30.toString(), sub = "last 30 days", modifier = Modifier.weight(1f), tone = if (data.newBests30 > 0) GREEN else null)
            ChartTile(key = "Time spent", value = hoursText(data.month.minutes), sub = "this month", modifier = Modifier.weight(1f))
        }
        if (data.repeats.isNotEmpty()) {
            SmallCaps("Done more than once")
            OutlinedCard(Modifier.fillMaxWidth()) {
                data.repeats.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { open = r }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text((if (r.kind.isNotBlank()) r.kind + " · " else "") + "${r.results.size} times", style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Dots(r = r, modifier = Modifier.width(80.dp).height(30.dp))
                        Column(Modifier.width(76.dp), horizontalAlignment = Alignment.End) {
                            Text(r.last, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (r.newBest) "New best" else "Best " + r.best,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (r.newBest) GREEN else muted,
                            )
                        }
                    }
                }
            }
        }
        if (data.once.isNotEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onceOpen = true }) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Done once", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${data.once.size} " + if (data.once.size == 1) "workout" else "workouts", style = MaterialTheme.typography.bodyMedium, color = muted)
                    Chevron()
                }
            }
        }
        if (data.recent.isNotEmpty()) {
            SmallCaps("Recent sessions")
            data.recent.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(dayLong(r.date), style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                    Text(r.text, style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }
        }
    }
    open?.let { r ->
        AnimatedDialog(
            onDismissRequest = { open = null },
            title = r.name,
            confirmButton = { TextButton(onClick = { open = null }) { Text("Close") } },
        ) {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text((if (r.kind.isNotBlank()) r.kind + " · " else "") + if (r.timed) "faster is better" else "more is better", color = muted)
                Dots(r = r, modifier = Modifier.fillMaxWidth().height(90.dp))
                r.results.reversed().forEach { x ->
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(dayLong(x.date), modifier = Modifier.weight(1f))
                        Text(x.text + if (x.best) " · best" else "", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    if (onceOpen) {
        AnimatedDialog(
            onDismissRequest = { onceOpen = false },
            title = "Done once",
            confirmButton = { TextButton(onClick = { onceOpen = false }) { Text("Close") } },
        ) {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                data.once.forEachIndexed { i, x ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(x.name, fontWeight = FontWeight.SemiBold)
                            Text(dayLong(x.date), style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                        Text(x.text, color = muted)
                    }
                }
            }
        }
    }
}
