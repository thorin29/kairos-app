package com.kairos.app.ui.chores

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.AlwaysOpenChoreDto
import com.kairos.app.data.remote.dto.ChorePersonDto
import com.kairos.app.data.remote.dto.ChoresDto
import com.kairos.app.data.remote.dto.PoolChoreDto
import com.kairos.app.data.remote.dto.PoolTallyDto
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.PersonAvatar
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ACCENT = Color(0xFF0F5C63)
private val DONE = Color(0xFF15803D)
private val MISS = Color(0xFFB91C1C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoresScreen(onOpenDrawer: () -> Unit) {
    val container = rememberContainer()
    val vm: ChoresViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ChoresViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chores") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val data = ui.data
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.loadError ?: "Couldn't load chores.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> ChoresContent(data)
            }
        }
    }
}

@Composable
private fun ChoresContent(data: ChoresDto) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        data.pause?.let { PauseBanner(it.name) }

        val people = data.people.filter { it.person != null }
        if (people.size > 1) {
            ThisWeekTable(people)
            RotationGrid(people)
        } else if (people.size == 1) {
            FocusedSummary(people.first())
            RotationCard(people.first())
        }

        if (data.alwaysOpen.isNotEmpty()) AlwaysOpenSection(data.alwaysOpen)
        if (data.pool.chores.isNotEmpty()) SharedChoresSection(data.pool.chores, data.pool.tally)
    }
}

@Composable
private fun PauseBanner(name: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ACCENT.copy(alpha = 0.10f))
            .border(1.dp, ACCENT.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(KairosIcons.Moon, contentDescription = null, tint = ACCENT, modifier = Modifier.size(20.dp))
        Column {
            Text("Chores are paused for $name", fontWeight = FontWeight.SemiBold, color = ACCENT)
            Text(
                "Nothing's due while you're away. Chores pick back up the day after the break ends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Section heading ----

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

// ---- This week (household) ----

@Composable
private fun ThisWeekTable(people: List<ChorePersonDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading("This week")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Person", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    NumHead("Due"); NumHead("Done"); NumHead("Open"); NumHead("Miss")
                }
                people.forEach { row ->
                    Divider()
                    val p = row.person!!
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PersonAvatar(p, size = 28.dp)
                            Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        NumCell(row.stats.due, muted = true)
                        NumCell(row.stats.done, weight = FontWeight.SemiBold)
                        NumCell(row.stats.open, muted = true)
                        NumCell(row.stats.missed, color = if (row.stats.missed > 0) MISS else null, muted = row.stats.missed == 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumHead(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(40.dp),
    )
}

@Composable
private fun NumCell(value: Int, muted: Boolean = false, color: Color? = null, weight: FontWeight? = null) {
    Text(
        value.toString(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = weight,
        color = color ?: if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
        modifier = Modifier.width(40.dp),
    )
}

// ---- Focused summary (single person) ----

@Composable
private fun FocusedSummary(row: ChorePersonDto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading("This week")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatTile("Due", row.stats.due, null)
                StatTile("Done", row.stats.done, DONE)
                StatTile("Open", row.stats.open, null)
                StatTile("Missed", row.stats.missed, if (row.stats.missed > 0) MISS else null)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, color: Color?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Weekly rotation ----

@Composable
private fun RotationGrid(people: List<ChorePersonDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading("Weekly rotation")
        people.forEach { RotationCardInner(it) }
    }
}

@Composable
private fun RotationCard(row: ChorePersonDto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading("Weekly rotation")
        RotationCardInner(row)
    }
}

@Composable
private fun RotationCardInner(row: ChorePersonDto) {
    val p = row.person ?: return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PersonAvatar(p, size = 28.dp)
                Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.rotation.size.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            if (row.rotation.isEmpty()) {
                Text("No chores assigned.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                row.rotation.forEachIndexed { i, a ->
                    if (i > 0) Spacer(Modifier.height(2.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            a.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(36.dp),
                        )
                        Text(
                            a.chore,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (a.pastDue) MISS else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (a.pastDue) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (a.complete) {
                            Icon(KairosIcons.Check, contentDescription = "Done", tint = DONE, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---- Always open ----

@Composable
private fun AlwaysOpenSection(items: List<AlwaysOpenChoreDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading("Always open")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Chore", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
                    Text("Week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
                }
                items.forEach { c ->
                    Divider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.today.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
                        Text(c.week.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
                    }
                }
            }
        }
    }
}

// ---- Shared chores ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharedChoresSection(chores: List<PoolChoreDto>, tally: List<PoolTallyDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading("Shared chores")
        if (tally.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tally.forEach { t ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(parseTallyColor(t.color)))
                        Text(t.name, style = MaterialTheme.typography.labelMedium)
                        Text(t.count.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column {
                chores.forEachIndexed { i, c ->
                    if (i > 0) Divider()
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(c.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            if (c.isPaused) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text("paused", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            poolStatus(c),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun poolStatus(c: PoolChoreDto): String {
    val base = "every ${c.intervalDays} days"
    val suffix = when {
        c.isPaused -> ""
        c.outstanding -> " \u00b7 up for grabs now"
        c.claimedByName != null -> " \u00b7 ${c.claimedByName} is on it"
        c.nextDueISO != null -> " \u00b7 next ${shortDate(c.nextDueISO)}"
        else -> ""
    }
    return base + suffix
}

// ---- shared bits ----

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

private val SHORT_DATE = DateTimeFormatter.ofPattern("MMM d")

private fun shortDate(iso: String): String =
    try {
        LocalDate.parse(iso).format(SHORT_DATE)
    } catch (_: Exception) {
        iso
    }

private fun parseTallyColor(hex: String?): Color {
    val s = hex?.trim()?.removePrefix("#") ?: return Color(0xFF64748B)
    return try {
        when (s.length) {
            6 -> Color(("FF$s").toLong(16))
            8 -> Color(s.toLong(16))
            else -> Color(0xFF64748B)
        }
    } catch (_: NumberFormatException) {
        Color(0xFF64748B)
    }
}
