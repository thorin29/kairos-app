package com.kairos.app.ui.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.ManualRunDto
import com.kairos.app.data.remote.dto.MilestoneDto
import com.kairos.app.data.remote.dto.TrackerDto
import com.kairos.app.data.remote.dto.TrackerLevelDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val OT_COLOR = Color(0xFF7C3AED)
private val NT_COLOR = Color(0xFF2563EB)
private val BIBLE_COLOR = Color(0xFFB45309)
private val MEDIUM_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun medium(iso: String?): String? =
    iso?.let { runCatching { LocalDate.parse(it).format(MEDIUM_DATE) }.getOrDefault(it) }

private fun colorOf(l: TrackerLevelDto): Color = when {
    l.kind == "bible" -> BIBLE_COLOR
    l.testament == "OT" -> OT_COLOR
    else -> NT_COLOR
}

/** "3 weeks", "4 months", "1 year 2 months" (web formatSpan). */
internal fun formatSpan(days: Int): String {
    if (days < 14) return "$days ${if (days == 1) "day" else "days"}"
    if (days < 60) return "${Math.round(days / 7f)} weeks"
    val months = Math.round(days / 30.44f)
    if (months < 12) return "$months months"
    val y = months / 12
    val m = months % 12
    return "$y ${if (y == 1) "year" else "years"}" + if (m > 0) " $m ${if (m == 1) "month" else "months"}" else ""
}

@Composable
private fun TimesPill(n: Int, color: Color) {
    Text(
        "×$n",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun TrackBar(percent: Int, color: Color, height: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
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
                .background(color),
        )
    }
}

/**
 * How many times each book, section, testament and the whole Bible has been
 * read, the progress toward the next time, and the milestones (mirrors the web
 * BibleTracker, server 0.724).
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TrackerSection(tracker: TrackerDto) {
    // Books open in a dialog: the Bible card shows all 66, a testament card its
    // books, a section row its books.
    var books by remember { mutableStateOf<Pair<String, (BibleBook) -> Boolean>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(tracker.bible to "Bible", tracker.ot to "OT", tracker.nt to "NT").forEach { (l, short) ->
                val color = colorOf(l)
                OutlinedCard(
                    onClick = {
                        books = l.label to { b: BibleBook -> l.kind == "bible" || b.testament.name == l.testament }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(short, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text("×${l.times}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
                        }
                        TrackBar(percent = l.progress, color = color, height = 6.dp)
                        Text("${l.progress}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        medium(tracker.bible.sinceISO)?.let {
            Text(
                "This time through the Bible since $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tracker.sections.forEach { s ->
                    val color = colorOf(s)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { books = s.label to { b: BibleBook -> b.group == s.key } }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(s.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(104.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TrackBar(percent = s.progress, color = color, modifier = Modifier.weight(1f))
                        TimesPill(n = s.times, color = color)
                    }
                }
            }
        }

        MilestonesCard(tracker)
    }

    books?.let { (title, pick) ->
        com.kairos.app.ui.common.AnimatedDialog(
            onDismissRequest = { books = null },
            title = title,
            confirmButton = { androidx.compose.material3.TextButton(onClick = { books = null }) { Text("Close") } },
        ) {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                tracker.sections.forEach { s ->
                    val inSection = tracker.books.filter { b ->
                        val book = BIBLE_BOOKS.firstOrNull { it.name == b.key }
                        book != null && book.group == s.key && pick(book)
                    }
                    if (inSection.isNotEmpty()) {
                        Text(s.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            inSection.forEach { b -> BookTile(b) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookTile(b: TrackerLevelDto) {
    val color = colorOf(b)
    Column(
        Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(b.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("×${b.times}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        }
        TrackBar(percent = b.progress, color = color, height = 3.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MilestonesCard(tracker: TrackerDto) {
    val shown = tracker.milestones
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Milestones", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (shown.isEmpty()) {
                Text("Nothing finished yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            shown.forEach { m -> MilestoneRow(m) }
            if (tracker.bests.isNotEmpty() || tracker.plansCompleted > 0) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    tracker.bests.forEach { b ->
                        Column(Modifier.weight(1f)) {
                            Text(b.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatSpan(b.days), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (tracker.plansCompleted > 0) {
                        Column(Modifier.weight(1f)) {
                            Text("Plans completed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${tracker.plansCompleted}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestoneRow(m: MilestoneDto) {
    val strong = m.kind == "bible" || m.kind == "testament" || m.kind == "plan"
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(medium(m.atISO) ?: "Before tracking", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            m.days?.let { Text(formatSpan(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(m.label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
            if (m.detail.isNotEmpty()) Text(m.detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The manual checklist's run: since when, how many chapters, and Reset. */
@Composable
fun ManualRunRow(manual: ManualRunDto, busy: Boolean, onReset: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (manual.open) "${medium(manual.startedISO)?.let { "Since $it" } ?: "Untimed"} · ${manual.chapters} chapters" else "Open",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (manual.open) "Manual reading" else "Starts with your next mark",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onReset, enabled = manual.open && !busy) { Text("Reset") }
    }
}
