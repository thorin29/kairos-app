package com.kairos.app.ui.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.SchoolAheadDto
import com.kairos.app.data.remote.dto.SchoolAheadItemDto
import com.kairos.app.data.remote.dto.SchoolCardProgressDto
import com.kairos.app.data.remote.dto.SchoolDto
import com.kairos.app.data.remote.dto.SchoolItemDto
import com.kairos.app.data.remote.dto.SchoolPersonDto
import com.kairos.app.data.remote.dto.SchoolProgressDto
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.OverlayDialog
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons

private val Emerald = Color(0xFF059669)
private val Amber = Color(0xFFD97706)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolScreen(onOpenDrawer: () -> Unit, onOpenAdd: () -> Unit, refreshKey: Int = 0) {
    val container = rememberContainer()
    val vm: SchoolViewModel = viewModel(
        factory = viewModelFactory { initializer { SchoolViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()
    androidx.compose.runtime.LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }

    val data = ui.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("School") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
                actions = {
                    IconButton(onClick = onOpenAdd) {
                        Icon(KairosIcons.Plus, "Add school work", modifier = Modifier.size(22.dp))
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.error ?: "Couldn't load school.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> SchoolContent(data, ui.message)
            }
        }
    }
}

@Composable
private fun SchoolContent(
    data: SchoolDto,
    message: String?,
) {
    val myPerson = data.people.firstOrNull { it.id == data.meId }
    val others = data.people.filter { it.id != data.meId }
    val progressById = remember(data.progress) { data.progress.associateBy { it.id } }
    var detailId by remember { mutableStateOf<String?>(null) }

    // Own card first (a parent rarely has school work; a child sees just theirs),
    // then each child. Tapping a card opens the detail in a pop-up window.
    val ownFirst = if (myPerson != null && hasSchool(myPerson)) listOf(myPerson) else emptyList()
    val cards = ownFirst + others.filter { hasSchool(it) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        message?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = Emerald, modifier = Modifier.fillMaxWidth())
        }
        if (cards.isEmpty()) {
            EmptyNote("Nothing due right now.")
        } else {
            cards.forEach { p ->
                SchoolSummaryCard(p, data.today, progressById[p.id]) { detailId = p.id }
            }
        }
    }

    detailId?.let { id ->
        data.people.firstOrNull { it.id == id }?.let { p ->
            OverlayDialog(
                onDismiss = { detailId = null },
                icon = KairosIcons.School,
                iconColor = Color(0xFF4F46E5),
                title = p.name,
            ) {
                SchoolOverlayBody(p, data.today)
            }
        }
    }
}

private fun hasSchool(p: SchoolPersonDto): Boolean =
    p.items.isNotEmpty() || p.classes.isNotEmpty() ||
        (p.card?.progress?.isNotEmpty() == true) || (p.card?.getAhead?.isNotEmpty() == true)

@Composable
private fun EmptyNote(text: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun SchoolSummaryCard(
    p: SchoolPersonDto,
    today: String,
    metrics: SchoolProgressDto?,
    onOpen: () -> Unit,
) {
    val overdue = p.items.filter { it.overdue }
    val todayItems = p.items.filter { !it.overdue && it.dueISO == today }
    val isStudent = (p.card?.progress?.isNotEmpty() == true) || p.classes.isNotEmpty()
    val completeForToday = overdue.isEmpty() && todayItems.isEmpty() && isStudent
    val summary = buildList {
        if (overdue.isNotEmpty()) add("${overdue.size} overdue")
        if (todayItems.isNotEmpty()) add("${todayItems.size} today")
    }.joinToString(" \u00b7 ").ifEmpty { "All caught up" }

    OutlinedCard(Modifier.fillMaxWidth().clickable { onOpen() }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(p.color, 12)
                Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (metrics != null && metrics.dueSoFar > 0) {
                    Text("${metrics.pct}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (completeForToday) "Complete for today!" else summary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (completeForToday) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        completeForToday -> Emerald
                        overdue.isNotEmpty() -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                Text("Open", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** The pop-up body for one student: school-year end, then Overdue / Today /
 *  Progress / Coming up — mirroring the web School overlay. */
@Composable
private fun SchoolOverlayBody(p: SchoolPersonDto, today: String) {
    val overdue = p.items.filter { it.overdue }.sortedBy { it.dueISO }
    val todayItems = p.items.filter { !it.overdue && it.dueISO == today }
    val card = p.card
    val isStudent = (card?.progress?.isNotEmpty() == true) || p.classes.isNotEmpty()

    if (card?.targetISO != null) {
        Text(
            "School year ends ${shortDate(card.targetISO)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (overdue.isEmpty() && todayItems.isEmpty() && isStudent) {
        Text("Complete for today!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Emerald)
    }
    if (overdue.isNotEmpty()) {
        OverlaySection("Overdue", MaterialTheme.colorScheme.error) { overdue.forEach { ItemRow(it) } }
    }
    if (todayItems.isNotEmpty()) {
        OverlaySection("Today", MaterialTheme.colorScheme.onSurfaceVariant) { todayItems.forEach { ItemRow(it) } }
    }
    if (card?.progress?.isNotEmpty() == true) {
        OverlaySection("Progress", MaterialTheme.colorScheme.onSurfaceVariant) { card.progress.forEach { ProgressRow(it) } }
    }
    if (card?.getAhead?.isNotEmpty() == true) {
        OverlaySection("Coming up", MaterialTheme.colorScheme.onSurfaceVariant) {
            card.getAhead.forEach { subj -> subj.items.firstOrNull()?.let { AheadRow(subj, it) } }
        }
    }
}

@Composable
private fun OverlaySection(label: String, color: Color, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(label, color)
        content()
    }
}

@Composable
private fun SectionLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ItemRow(item: SchoolItemDto) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(item.className ?: item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            "${item.title} \u00b7 ${item.typeLabel} \u00b7 due ${shortDate(item.dueISO)}",
            style = MaterialTheme.typography.labelSmall,
            color = if (item.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgressRow(pr: SchoolCardProgressDto) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Dot(pr.color, 10)
                Text(
                    pr.className,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                when (pr.pace) {
                    "behind" -> Tag("falling behind", Amber)
                    "ahead" -> Tag("getting ahead!", Emerald)
                }
            }
            if (pr.finishISO != null) {
                Text(
                    "finishes ${shortDate(pr.finishISO)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pr.onTrack) MaterialTheme.colorScheme.onSurfaceVariant else Amber,
                )
            }
        }
        if (pr.catchUpRate != null) {
            val note = if (pr.catchUpDays != null) {
                "Do ${pr.catchUpRate} a day for the next ${pr.catchUpDays} school day" + (if (pr.catchUpDays == 1) "" else "s") + " to finish on time."
            } else {
                "Do ${pr.catchUpRate} a day to finish on time."
            }
            Text(note, style = MaterialTheme.typography.labelSmall, color = Amber, modifier = Modifier.padding(start = 18.dp))
        }
    }
}

@Composable
private fun AheadRow(subj: SchoolAheadDto, first: SchoolAheadItemDto) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(subj.subject, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            "${first.title} \u00b7 next up",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun Dot(color: String?, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(999.dp)).background(parseColor(color)))
}

/** "2027-05-21" -> "5/21". */
private fun shortDate(iso: String): String {
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val m = parts[1].toIntOrNull() ?: return iso
    val d = parts[2].toIntOrNull() ?: return iso
    return "$m/$d"
}

private fun parseColor(hex: String?): Color {
    val s = hex?.trim()?.removePrefix("#") ?: return Color(0xFF94A3B8)
    return try {
        when (s.length) {
            6 -> Color(("FF$s").toLong(16))
            8 -> Color(s.toLong(16))
            else -> Color(0xFF94A3B8)
        }
    } catch (_: NumberFormatException) {
        Color(0xFF94A3B8)
    }
}
