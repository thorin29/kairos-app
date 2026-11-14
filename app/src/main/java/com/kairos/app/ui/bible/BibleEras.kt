package com.kairos.app.ui.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.EraSummaryDto
import com.kairos.app.data.remote.dto.ErasDto
import com.kairos.app.ui.nav.KairosIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val OT_COLOR = Color(0xFF7C3AED)
private val NT_COLOR = Color(0xFF2563EB)
private val MEDIUM_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun medium(iso: String?): String? =
    iso?.let { runCatching { LocalDate.parse(it).format(MEDIUM_DATE) }.getOrDefault(it) }

private fun sectionLabel(label: String) = if (label == "General Epistles") "General letters" else label

private fun pct(read: Int, of: Int) = if (of > 0) Math.round(read * 100f / of) else 0

/**
 * Coverage by reading era (mirrors the web ReadingEras): this era's sections
 * against an earlier era's, the full-pass count and the list of eras.
 */
@Composable
fun ErasCard(eras: ErasDto) {
    var compareId by remember(eras) { mutableStateOf(eras.past.firstOrNull()?.id) }
    var picking by remember { mutableStateOf(false) }
    val compare = eras.past.firstOrNull { it.id == compareId }
    val cur = eras.current

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    cur.label + (medium(cur.startISO)?.let { " · since $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (eras.passes > 0) {
                    Text(
                        "${eras.passes} ${if (eras.passes == 1) "pass" else "passes"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }

            if (eras.past.isNotEmpty()) {
                Box {
                    TextButton(onClick = { picking = true }) {
                        Text(compare?.let { "By section · vs ${it.label}" } ?: "By section")
                        Icon(KairosIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                        DropdownMenuItem(text = { Text("No comparison") }, onClick = { compareId = null; picking = false })
                        eras.past.forEach { p ->
                            DropdownMenuItem(text = { Text("vs ${p.label}") }, onClick = { compareId = p.id; picking = false })
                        }
                    }
                }
            }

            cur.sections.forEachIndexed { i, s ->
                val color = if (s.testament == "OT") OT_COLOR else NT_COLOR
                val now = pct(s.read, s.chapters)
                val then = compare?.sections?.getOrNull(i)?.let { pct(it.read, it.chapters) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        sectionLabel(s.label),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(104.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        EraBar(percent = now, color = color, height = 8.dp)
                        if (then != null) EraBar(percent = then, color = color, height = 4.dp, faded = true)
                    }
                    Text(
                        "$now%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(38.dp),
                    )
                }
            }

            if (eras.past.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                (listOf(cur) + eras.past).forEach { e -> EraRow(e) }
            }
        }
    }
}

@Composable
private fun EraBar(percent: Int, color: Color, height: Dp, faded: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .clip(RoundedCornerShape(999.dp))
                .alpha(if (faded) 0.35f else 1f)
                .background(color),
        )
    }
}

@Composable
private fun EraRow(e: EraSummaryDto) {
    val from = medium(e.startISO)
    val to = medium(e.endISO) ?: "now"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (from != null) "$from – $to" else "until $to",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${e.percent}%", style = MaterialTheme.typography.bodyMedium)
        if (e.full) Icon(KairosIcons.Trophy, contentDescription = "Whole Bible", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}
