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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.SoreSpotDto
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

private val SORE_RED = Color(0xFFC0392B)
private val SORE_PINK = Color(0xFFF6C9C6)

private fun soreLabel(area: String): String = ADJUST_AREAS.firstOrNull { it.first == area }?.second ?: area

private fun soreDay(iso: String): String = try {
    val d = LocalDate.parse(iso)
    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) + ", " + d.month.getDisplayName(TextStyle.SHORT, Locale.US) + " " + d.dayOfMonth
} catch (_: Exception) {
    iso
}

/** Every sore spot's regions on the two figures. */
internal fun soreRegions(spots: List<SoreSpotDto>): Pair<List<Int>, List<Int>> {
    val front = spots.flatMap { SORE_PATHS[it.area]?.first.orEmpty() }.distinct()
    val back = spots.flatMap { SORE_PATHS[it.area]?.second.orEmpty() }.distinct()
    return front to back
}

/**
 * Sore spots under the body map (v0.485): each marked area with Better, and a
 * button to mark one. A spot stays until Better, never on its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SoreSpots(spots: List<SoreSpotDto>, onChange: (List<SoreSpotDto>) -> Unit) {
    val session = com.kairos.app.ui.common.rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val free = ADJUST_AREAS.map { it.first }.filter { a -> spots.none { it.area == a } }
    fun save(area: String, sore: Boolean) {
        busy = true
        scope.launch {
            runCatching { session.setSore(area = area, sore = sore) }.onSuccess { onChange(it) }
            busy = false
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        spots.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SORE_PINK.copy(alpha = 0.35f))
                    .border(1.dp, SORE_RED.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.linearGradient(listOf(SORE_RED, SORE_PINK, SORE_RED, SORE_PINK))),
                )
                Column(Modifier.weight(1f)) {
                    Text(soreLabel(s.area) + " sore", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Since " + soreDay(s.since) + (s.from?.let { " · from $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { save(area = s.area, sore = false) }, enabled = !busy) { Text("Better") }
            }
        }
        if (free.isNotEmpty()) {
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, SORE_RED.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                    .clickable {
                        pick = null
                        open = true
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("+ Sore spot", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = SORE_RED)
            }
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Sore spot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Where?", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        free.forEach { a ->
                            val on = pick == a
                            val shape = RoundedCornerShape(20.dp)
                            Box(
                                Modifier
                                    .heightIn(min = 40.dp)
                                    .clip(shape)
                                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                                    .clickable { pick = a }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    soreLabel(a),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pick?.let { save(area = it, sore = true) }
                        open = false
                    },
                    enabled = pick != null && !busy,
                ) { Text("Mark sore") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}
