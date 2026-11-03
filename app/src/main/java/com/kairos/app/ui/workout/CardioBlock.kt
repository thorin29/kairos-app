package com.kairos.app.ui.workout

import com.kairos.app.ui.common.AccentOutlinedButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.CardioEntryDto
import com.kairos.app.data.remote.dto.Concept2DayDto
import com.kairos.app.data.remote.dto.SimplePlanDto
import com.kairos.app.ui.nav.KairosIcons

/** What can be logged as cardio (the web's cardio.ts CARDIO_ACTIVITIES). */
private data class CardioKind(
    val key: String,
    val label: String,
    val meters: Boolean = false,
    val distance: Boolean = false,
    val load: Boolean = false,
)

private val CARDIO_KINDS = listOf(
    CardioKind(key = "row", label = "Row", meters = true),
    CardioKind(key = "run", label = "Run", distance = true),
    CardioKind(key = "ruck", label = "Ruck", distance = true, load = true),
    CardioKind(key = "jump_rope", label = "Jump rope"),
    CardioKind(key = "bike", label = "Bike", distance = true),
    CardioKind(key = "other", label = "Other"),
)

/** "7:45" → 465, "1:02:05" → 3725, "20" → 1200 (cardio.ts parseClock). */
internal fun parseClock(raw: String): Int? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    t.toDoubleOrNull()?.let { min -> return if (min > 0 && min <= 24 * 60) Math.round(min * 60).toInt() else null }
    val m = Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})$""").find(t) ?: return null
    val h = m.groupValues[1].ifEmpty { "0" }.toInt()
    val mm = m.groupValues[2].toInt()
    val ss = m.groupValues[3].toInt()
    if (ss > 59 || (m.groupValues[1].isNotEmpty() && mm > 59)) return null
    val total = h * 3600 + mm * 60 + ss
    return if (total in 1..(24 * 3600)) total else null
}

/** A section heading with nothing from a plan under it (Warm-up, Cardio). */
@Composable
internal fun SectionLabel(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = TextUnit(0.08f, TextUnitType.Em),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, start = 2.dp),
    )
}

/** A Warm-up in the plan: Mark done (tap again to undo), or Skip. */
@Composable
internal fun WarmupCard(
    plan: SimplePlanDto,
    onDone: (Boolean) -> Unit,
    onSkip: (Boolean) -> Unit,
    /** The routine was switched (v0.430): reload the day. */
    onChanged: () -> Unit = {},
) {
    val finished = plan.done && !plan.skipped
    // Tapping the card opens its routine (steps and timers); the check
    // completes it without opening (v0.430).
    var open by remember { mutableStateOf(false) }
    if (open) {
        WarmupWindow(
            plannedWorkoutId = plan.plannedWorkoutId,
            routine = plan.routine,
            done = finished,
            onDone = {
                onDone(true)
                open = false
            },
            onClose = { open = false },
            onChanged = onChanged,
        )
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f).clickable { open = true }.padding(vertical = 4.dp)) {
                Text(
                    plan.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                plan.routine?.let {
                    Text(
                        it.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            if (!finished) {
                TextButton(onClick = { onSkip(!plan.skipped) }) { Text(if (plan.skipped) "Skipped" else "Skip") }
            }
            if (!plan.skipped) {
                if (finished) {
                    AccentOutlinedButton(onClick = { onDone(false) }) {
                        Icon(KairosIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Done", modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    Button(onClick = { onDone(true) }) {
                        Icon(KairosIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Mark done", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

/**
 * The day's Cardio (the web's CardioCard): every row, run, ruck or other
 * cardio done that day, Concept2 rows included, how each felt, and adding
 * one. Any of them completes a Cardio plan.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CardioBlock(
    /** The card's title: the Cardio plan's name. */
    name: String,
    entries: List<CardioEntryDto>,
    concept2: Concept2DayDto?,
    checking: Boolean,
    saving: Boolean,
    error: String?,
    skippedPlan: SimplePlanDto?,
    onCheck: () -> Unit,
    onUndo: (String) -> Unit,
    onRemove: (String) -> Unit,
    onFeedback: (String, String?, String?) -> Unit,
    onLog: (String, Double?, Double?, Int?, Double?, () -> Unit) -> Unit,
    onSkip: ((Boolean) -> Unit)?,
) {
    var adding by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("row") }
    var meters by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var load by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<CardioEntryDto?>(null) }
    val k = CARDIO_KINDS.first { it.key == kind }

    // One card for the day's cardio: the title with Concept2 (sync) and Add
    // on it, then each entry with how it felt.
    val skipped = skippedPlan != null && entries.isEmpty()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (entries.size == 1) entries[0].name else name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (concept2?.connected == true) {
                    TextButton(onClick = onCheck, enabled = !checking) {
                        Icon(KairosIcons.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(if (checking) "Checking\u2026" else "Concept2", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                if (entries.isNotEmpty()) {
                    IconButton(onClick = { adding = true }) {
                        Icon(KairosIcons.Plus, contentDescription = "Add cardio", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // Nothing logged yet: Skip and Add cardio on their own row, so the
            // title keeps its line on a phone (v0.429).
            if (entries.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (onSkip != null) {
                        TextButton(onClick = { onSkip(skippedPlan == null) }) { Text(if (skipped) "Skipped" else "Skip") }
                    }
                    if (!skipped) {
                        Button(onClick = { adding = true }) {
                            Icon(KairosIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Add cardio", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            concept2?.error?.takeIf { concept2.connected }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            entries.forEachIndexed { i, e ->
                if (i > 0 || entries.size > 1) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (entries.size > 1) {
                            Text(e.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            if (e.concept2 != null) "From Concept2 · ${e.summary}" else e.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        e.concept2?.replaced?.let {
                            Text(
                                "Replaced $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    e.concept2?.takeIf { it.replaced != null }?.let { c ->
                        TextButton(onClick = { onUndo(c.resultId) }) { Text("Undo") }
                    }
                    // A Concept2 row comes from the rower: nothing to delete here.
                    if (e.concept2 == null) {
                        IconButton(onClick = { removing = e }) {
                            Icon(
                                KairosIcons.Trash,
                                contentDescription = "Remove ${e.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                EffortChooser(
                    question = "How did it feel?",
                    effort = e.effort,
                    hurt = e.hurt,
                    onChange = { eff, h -> onFeedback(e.sessionId, eff, h) },
                )
            }
        }
    }

    // Adding cardio opens a sheet, like Add workout, rather than growing the page.
    if (adding) {
        ModalBottomSheet(
            onDismissRequest = { adding = false; formError = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Add cardio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CARDIO_KINDS.forEach { c ->
                        ChoicePill(label = c.label, selected = kind == c.key) { kind = c.key }
                    }
                }
                if (k.meters) {
                    OutlinedTextField(
                        value = meters,
                        onValueChange = { meters = it.filter(Char::isDigit) },
                        label = { Text("Meters") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (k.distance) {
                    OutlinedTextField(
                        value = distance,
                        onValueChange = { distance = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Distance (mi)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it.filter { ch -> ch.isDigit() || ch == ':' } },
                    label = { Text("Time (mm:ss)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (k.load) {
                    OutlinedTextField(
                        value = load,
                        onValueChange = { load = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Load (lb)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                (formError ?: error)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !saving,
                        onClick = {
                            val secs = if (time.isBlank()) null else parseClock(time)
                            val m = if (k.meters) meters.toDoubleOrNull()?.takeIf { it > 0 } else null
                            val d = if (k.distance) distance.toDoubleOrNull()?.takeIf { it > 0 } else null
                            val l = if (k.load) load.toDoubleOrNull()?.takeIf { it > 0 } else null
                            formError = when {
                                time.isNotBlank() && secs == null -> "Time as mm:ss, like 7:45."
                                m == null && d == null && secs == null -> "Enter a time or a distance."
                                else -> null
                            }
                            if (formError == null) {
                                onLog(kind, m, d, secs, l) {
                                    meters = ""
                                    distance = ""
                                    time = ""
                                    load = ""
                                    adding = false
                                }
                            }
                        },
                    ) { Text(if (saving) "Saving…" else "Save") }
                    TextButton(onClick = { adding = false; formError = null }) { Text("Cancel") }
                }
            }
        }
    }

    removing?.let { e ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${e.name}?") },
            text = { Text(e.summary) },
            confirmButton = { TextButton(onClick = { onRemove(e.sessionId); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
