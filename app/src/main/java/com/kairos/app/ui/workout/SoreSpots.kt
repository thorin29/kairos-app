package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.SoreSpotDto
import com.kairos.app.ui.nav.KairosIcons
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
 * Sore spots under the body map (v0.485; v0.487 marked only from a workout's
 * band-aid, "Something hurt?"): each one with Better. A spot stays until
 * Better, never on its own. Nothing shows when nothing hurts.
 */
@Composable
internal fun SoreSpots(spots: List<SoreSpotDto>, onChange: (List<SoreSpotDto>) -> Unit) {
    if (spots.isEmpty()) return
    val session = com.kairos.app.ui.common.rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
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
                Icon(KairosIcons.Bandage, contentDescription = null, tint = SORE_RED, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(soreLabel(s.area), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Since " + soreDay(s.since) + (s.from?.let { " \u00b7 from $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { session.setSore(area = s.area, sore = false) }.onSuccess { onChange(it) }
                            busy = false
                        }
                    },
                    enabled = !busy,
                ) { Text("Better") }
            }
        }
    }
}
