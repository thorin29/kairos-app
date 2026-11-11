package com.kairos.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.common.AccentOutlinedButton
import com.kairos.app.ui.common.rememberContainer

/** The kinds "+ Additional" offers besides conditioning (v0.477). */
internal val EXTRA_KINDS = listOf("mobility" to "Mobility", "stretching" to "Stretching", "iso" to "Isometrics")

/**
 * "+ Additional" (v0.477; was + Conditioning): conditioning, or a mobility or
 * stretching routine or an isometric workout added to this day only.
 */
@Composable
internal fun AdditionalMenu(onConditioning: () -> Unit, onKind: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Additional") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentOutlinedButton(onClick = onConditioning, modifier = Modifier.fillMaxWidth()) { Text("Conditioning") }
                EXTRA_KINDS.forEach { (k, label) ->
                    AccentOutlinedButton(onClick = { onKind(k) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class ExtraChoice(val id: String, val name: String, val sub: String)

/** Pick a routine or an isometric workout; tapping one adds it to the day. */
@Composable
internal fun ExtraPickerDialog(kind: String, onPick: (String) -> Unit, onBack: () -> Unit, onDismiss: () -> Unit) {
    val session = rememberContainer().sessionRepository
    var choices by remember(kind) { mutableStateOf<List<ExtraChoice>?>(null) }
    LaunchedEffect(kind) {
        choices = runCatching {
            if (kind == "iso") {
                session.coreWorkouts(kind = "iso").map { w ->
                    ExtraChoice(id = w.id, name = w.name, sub = listOf("${w.minutes} min", w.gear).filter { it.isNotBlank() }.joinToString(" · "))
                }
            } else {
                session.warmupRoutines(kind = kind).map { r ->
                    ExtraChoice(id = r.id, name = r.name, sub = r.steps.joinToString(" · ") { it.name })
                }
            }
        }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(EXTRA_KINDS.firstOrNull { it.first == kind }?.second ?: "Additional") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val list = choices
                if (list == null) {
                    Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    list.forEach { c ->
                        OutlinedCard(onClick = { onPick(c.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (c.sub.isNotBlank()) {
                                    Text(
                                        c.sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onBack) { Text("Back") } },
    )
}
