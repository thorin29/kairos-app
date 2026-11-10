package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.GoPickDto
import com.kairos.app.ui.common.AccentOutlinedButton

/**
 * Go light (v0.464): up to three answers, then one suggestion with
 * alternatives. "Use this" makes today light; the day shows it with Undo.
 */
@Composable
internal fun GoLightScreen(
    busy: Boolean,
    onAsk: (why: String, minutes: Int, hurt: Boolean, neck: String?, onPick: (GoPickDto) -> Unit) -> Unit,
    onUse: (key: String, minutes: Int) -> Unit,
    onRest: () -> Unit,
    onDismiss: () -> Unit,
) {
    var why by remember { mutableStateOf<String?>(null) }
    var minutes by remember { mutableStateOf<Int?>(null) }
    var hurt by remember { mutableStateOf<Boolean?>(null) }
    var neck by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf<GoPickDto?>(null) }
    var chosen by remember { mutableStateOf<String?>(null) }
    val ready = why != null && minutes != null && hurt != null && (why != "sick" || neck != null)
    CoreScreen(title = "Go light", onDismiss = onDismiss, footer = {
        Button(
            onClick = {
                val p = pick
                if (p == null) {
                    onAsk(why!!, minutes!!, hurt!!, neck) { got -> pick = got; chosen = got.suggest.key }
                } else {
                    chosen?.let { onUse(it, minutes ?: 20) }
                }
            },
            enabled = !busy && (if (pick == null) ready else chosen != null),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text(if (pick == null) "See my workout" else "Use this") }
    }) {
        val p = pick
        if (p == null) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Question("What's going on?", listOf("energy" to "Low energy", "sore" to "Sore", "time" to "Short on time", "sick" to "Sick"), why) { why = it }
                if (why == "sick") {
                    Question("Where is it?", listOf("above" to "Above the neck", "below" to "Below the neck"), neck) { neck = it }
                }
                Question("How long have you got?", listOf(10 to "10 min", 20 to "20 min", 30 to "30 min"), minutes) { minutes = it }
                Question("Anything hurt?", listOf(false to "No", true to "Yes"), hurt) { hurt = it }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (p.rest) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Rest might be best today.", fontWeight = FontWeight.SemiBold)
                        AccentOutlinedButton(onClick = onRest) { Text("Rest / skip day") }
                    }
                }
                (listOf(p.suggest) + p.others).forEachIndexed { i, o ->
                    val on = chosen == o.key
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent,
                                RoundedCornerShape(12.dp),
                            )
                            .border(
                                width = if (on) 1.5.dp else 1.dp,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { chosen = o.key }
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(o.title, fontWeight = FontWeight.SemiBold)
                            if (i == 0) {
                                Text(
                                    "SUGGESTED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Text(o.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Question(label: String, values: List<Pair<T, String>>, chosen: T?, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
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
