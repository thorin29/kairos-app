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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.AdjustPreviewDto
import com.kairos.app.data.remote.dto.AdjustRequest
import com.kairos.app.data.remote.dto.ShortAnswerDto
import com.kairos.app.ui.common.AccentOutlinedButton

private val TIME_CHOICES = listOf(15, 30, 45)
private val CARDIO_CHOICES = listOf(10, 15, 20)
private val PICKS = listOf("weights" to "Short weights", "cardio" to "Cardio", "conditioning" to "Conditioning")
private val ADJUST_AREAS = listOf(
    "shoulder" to "Shoulder",
    "elbow" to "Elbow / wrist",
    "back" to "Back",
    "hip" to "Hip",
    "knee" to "Knee / ankle",
    "legs" to "Legs",
)

/**
 * Adjust today (v0.468; replaces Go light): Low energy, Short on time, Sick,
 * or Hurt. Confirm changes today's plan; the day shows it with Undo.
 */
@Composable
internal fun AdjustTodayScreen(
    busy: Boolean,
    onShort: (minutes: Int, picks: List<String>, cardioMin: Int, onAnswer: (ShortAnswerDto) -> Unit) -> Unit,
    onPreview: (mode: String, how: String?, area: String?, onPreview: (AdjustPreviewDto) -> Unit) -> Unit,
    onConfirm: (AdjustRequest) -> Unit,
    onRest: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf("start") }
    var preview by remember { mutableStateOf<AdjustPreviewDto?>(null) }
    var minutes by remember { mutableStateOf(30) }
    var picks by remember { mutableStateOf(listOf("weights")) }
    var cardioMin by remember { mutableStateOf(10) }
    var short by remember { mutableStateOf<ShortAnswerDto?>(null) }
    var how by remember { mutableStateOf<String?>(null) }
    var area by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(step, minutes, picks, cardioMin) {
        if (step == "time" || step == "timePreview") {
            onShort(minutes, picks, cardioMin) { got ->
                short = got
                // Nothing to lift today: start from what there is.
                if (picks.isNotEmpty() && picks.none { got.available[it] == true }) {
                    PICKS.map { it.first }.firstOrNull { got.available[it] == true }?.let { picks = listOf(it) }
                }
            }
        }
    }

    val title = when (step) {
        "start" -> "Adjust today"
        "low" -> "Low energy"
        "sick" -> "Sick"
        "hurt" -> "Hurt or injured"
        else -> "Short on time"
    }
    val s = short
    val p = preview
    CoreScreen(title = title, onDismiss = onDismiss, footer = {
        val pad = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        when (step) {
            "low" -> Button(onClick = { onConfirm(AdjustRequest(action = "apply", mode = "low")) }, enabled = !busy && p != null, modifier = pad) {
                Text("Confirm")
            }
            "sick" -> Button(
                onClick = { onConfirm(AdjustRequest(action = "apply", mode = "sick", how = how)) },
                enabled = !busy && how != null,
                modifier = pad,
            ) { Text("Confirm") }
            "hurt" -> if (p?.empty == true) {
                AccentOutlinedButton(onClick = onRest, modifier = pad) { Text("Rest / skip day") }
            } else {
                Button(
                    onClick = { onConfirm(AdjustRequest(action = "apply", mode = "hurt", area = area)) },
                    enabled = !busy && area != null && p != null,
                    modifier = pad,
                ) { Text("Confirm") }
            }
            "time" -> Button(
                onClick = { step = "timePreview" },
                enabled = s != null && s.fits && picks.isNotEmpty(),
                modifier = pad,
            ) { Text("See my workout") }
            "timePreview" -> Column(pad, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { onConfirm(AdjustRequest(action = "apply", mode = "time", minutes = minutes, picks = picks, cardioMin = cardioMin)) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Confirm") }
                AccentOutlinedButton(onClick = { step = "time" }, modifier = Modifier.fillMaxWidth()) { Text("Change") }
            }
            else -> {}
        }
    }) {
        when (step) {
            "start" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustOption(title = "Low energy", detail = "Same workout, lighter") {
                    step = "low"
                    onPreview("low", null, null) { preview = it }
                }
                AdjustOption(title = "Short on time", detail = "Fit a workout into the time you have") { step = "time" }
                AdjustOption(title = "Sick", detail = "A lighter version or an easy day") { step = "sick" }
                AdjustOption(title = "Hurt or injured", detail = "Work around it") { step = "hurt" }
            }
            "low" -> if (p != null) PreviewCard(p) else Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            "sick" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustOption(title = "Lighter version", detail = "About 50%, main lifts only", on = how == "light") { how = "light" }
                AdjustOption(title = "Easy day", detail = "Warm-up and easy cardio, no lifting", on = how == "easy") { how = "easy" }
            }
            "hurt" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Chips(label = "Where?", values = ADJUST_AREAS, chosen = area) { a ->
                    area = a
                    preview = null
                    onPreview("hurt", null, a) { preview = it }
                }
                if (area != null && p != null) PreviewCard(p)
            }
            "time" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Chips(label = "How long have you got?", values = TIME_CHOICES.map { it to "$it min" }, chosen = minutes) { minutes = it }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What do you want to fit in?", fontWeight = FontWeight.SemiBold)
                    PICKS.filter { s == null || s.available[it.first] == true }.forEach { (key, label) ->
                        val on = key in picks
                        val est = when (key) {
                            "weights" -> "~${s?.weightsMin ?: "…"} min"
                            "conditioning" -> "~${s?.conditioningMin ?: "…"} min"
                            else -> "$cardioMin min"
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent, RoundedCornerShape(12.dp))
                                .border(
                                    width = if (on) 1.5.dp else 1.dp,
                                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { picks = if (on) picks - key else picks + key }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(5.dp))
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(est, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if ("cardio" in picks) {
                        Chips(label = null, values = CARDIO_CHOICES.map { it to "$it min" }, chosen = cardioMin) { cardioMin = it }
                    }
                }
                Text(
                    "Warm-up first · ~${if ("weights" in picks) 6 else 4} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s != null && !s.fits && picks.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0x33F59E0B), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val what = (listOf("Warm-up") + picks.filter { s.available[it] == true }).joinToString(" + ")
                        Text("$what needs about ${s.need} min.", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.fixes.forEach { f ->
                                AccentOutlinedButton(onClick = {
                                    val drop = f.drop
                                    val longer = f.minutes
                                    if (drop != null) picks = picks - drop else if (longer != null) minutes = longer
                                }) { Text(f.label) }
                            }
                        }
                    }
                }
            }
            "timePreview" -> if (s != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Today, $minutes minutes", fontWeight = FontWeight.SemiBold)
                        Tag("SHORT")
                    }
                    s.items.forEach { item ->
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.minutes == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            item.minutes?.let { m -> Text("$m min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth()) {
                        Text("Total", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("about ${s.total} min", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun PreviewCard(p: AdjustPreviewDto) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.title, fontWeight = FontWeight.SemiBold)
            if (p.tag.isNotEmpty()) Tag(p.tag)
        }
        p.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        p.lines.forEach { l ->
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    l.label,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (l.dropped) TextDecoration.LineThrough else null,
                    color = if (l.dropped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (l.dropped) "dropped" else l.detail.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AdjustOption(title: String, detail: String, on: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent, RoundedCornerShape(12.dp))
            .border(
                width = if (on) 1.5.dp else 1.dp,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(label: String?, values: List<Pair<T, String>>, chosen: T?, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        label?.let { Text(it, fontWeight = FontWeight.SemiBold) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { (v, text) ->
                val on = chosen == v
                Text(
                    text,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(999.dp))
                        .border(
                            width = 1.dp,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(999.dp),
                        )
                        .clickable { onPick(v) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
