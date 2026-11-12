package com.kairos.app.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.common.AccentOutlinedButton
import com.kairos.app.ui.common.FullPage
import com.kairos.app.ui.common.PageCard
import com.kairos.app.ui.common.rememberContainer

/** The routine kinds "+ Additional" adds to the day (v0.477). */
internal val EXTRA_KINDS = listOf("mobility" to "Mobility", "stretching" to "Stretching", "iso" to "Isometrics")

/** What "+ Additional" logs through the log flow (v0.491): its categories. */
internal val LOG_KINDS = listOf(
    "strength" to "Strength",
    "cardio" to "Cardio",
    "sport" to "Sport",
)

/** The log flow's categories for each of [LOG_KINDS]; "other" is all of them. */
internal fun logCategoriesFor(kind: String): List<String>? = when (kind) {
    "strength" -> listOf("WEIGHTS")
    "cardio" -> listOf("RUNNING", "ROWING", "RUCKING", "CARDIO")
    "sport" -> listOf("SPORT")
    else -> null
}

/**
 * "+ Additional" (v0.477; v0.491 the day's one Add button): a short pop-up of
 * kinds. Each opens a full page: Conditioning (Pick · Generator), the Family
 * WOD, Strength / Cardio / Sport (the log flow), Mobility / Stretching /
 * Isometrics (a routine for the day), or Something else (every category).
 */
@Composable
internal fun AdditionalMenu(
    onConditioning: () -> Unit,
    onKind: (String) -> Unit,
    onDismiss: () -> Unit,
    /** "Family WOD" (v0.489): set one for the day, or suggest one. */
    onFamilyWod: (() -> Unit)? = null,
    /** Strength, Cardio, Sport or "other": the log flow (v0.491). */
    onLog: ((String) -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Additional") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentOutlinedButton(onClick = onConditioning, modifier = Modifier.fillMaxWidth()) { Text("Conditioning") }
                if (onFamilyWod != null) {
                    AccentOutlinedButton(onClick = onFamilyWod, modifier = Modifier.fillMaxWidth()) { Text("Family WOD") }
                }
                if (onLog != null) {
                    LOG_KINDS.forEach { (k, label) ->
                        AccentOutlinedButton(onClick = { onLog(k) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                }
                EXTRA_KINDS.forEach { (k, label) ->
                    AccentOutlinedButton(onClick = { onKind(k) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
                if (onLog != null) {
                    TextButton(onClick = { onLog("other") }, modifier = Modifier.fillMaxWidth()) { Text("Something else") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class ExtraChoice(val id: String, val name: String, val sub: String, val steps: List<String>)

/**
 * Pick a routine or an isometric workout, a full page (v0.491; was a pop-up):
 * search on the top card, full names, ⓘ for its steps; tapping one adds it
 * to the day.
 */
@Composable
internal fun ExtraPickerPage(kind: String, onPick: (String) -> Unit, onBack: () -> Unit) {
    val session = rememberContainer().sessionRepository
    var choices by remember(kind) { mutableStateOf<List<ExtraChoice>?>(null) }
    var q by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<ExtraChoice?>(null) }
    LaunchedEffect(kind) {
        choices = runCatching {
            if (kind == "iso") {
                session.coreWorkouts(kind = "iso").map { w ->
                    ExtraChoice(
                        id = w.id,
                        name = w.name,
                        sub = listOf("${w.minutes} min", w.gear).filter { it.isNotBlank() }.joinToString(" · "),
                        steps = emptyList(),
                    )
                }
            } else {
                session.warmupRoutines(kind = kind).map { r ->
                    ExtraChoice(id = r.id, name = r.name, sub = "${r.steps.size} steps", steps = r.steps.map { it.name })
                }
            }
        }.getOrDefault(emptyList())
    }
    val title = EXTRA_KINDS.firstOrNull { it.first == kind }?.second ?: "Additional"
    FullPage(title = title, onBack = onBack) {
        PageCard {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val list = choices
        if (list == null) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val t = q.trim().lowercase()
            val shown = list.filter { t.isEmpty() || it.name.lowercase().contains(t) }
            if (shown.isEmpty()) Text("Nothing matches.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (shown.isNotEmpty()) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column {
                        shown.forEachIndexed { i, c ->
                            if (i > 0) HorizontalDivider()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { onPick(c.id) }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                    Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    if (c.sub.isNotBlank()) {
                                        Text(c.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (c.steps.isNotEmpty()) {
                                    TextButton(onClick = { info = c }) { Text("ⓘ", style = MaterialTheme.typography.titleMedium) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    info?.let { c ->
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text(c.name) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { c.steps.forEach { Text(it) } } },
            confirmButton = { TextButton(onClick = { info = null }) { Text("Close") } },
        )
    }
}
