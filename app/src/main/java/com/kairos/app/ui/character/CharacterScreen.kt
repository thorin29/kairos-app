package com.kairos.app.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.SubcomposeAsyncImage
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.dto.CharCompanionDto
import com.kairos.app.data.remote.dto.FamilyGoalDto
import com.kairos.app.data.remote.dto.CharacterDto
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.ui.common.PersonAvatar
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import com.kairos.app.ui.common.LogoMenuButton

private val ACCENT = Color(0xFF0F5C63)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterScreen(person: PersonDto, onOpenDrawer: () -> Unit, onOpenGallery: () -> Unit) {
    val container = rememberContainer()
    val vm: CharacterViewModel = viewModel(
        factory = viewModelFactory { initializer { CharacterViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.data?.seasonName?.ifBlank { null }?.let { "Season \u00b7 $it" } ?: "Season") },
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
                        Text(ui.error ?: "Couldn't load your character.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> CharacterContent(person, ui, vm, onOpenGallery)
            }
        }
    }
}

@Composable
private fun CharacterContent(person: PersonDto, ui: CharacterUiState, vm: CharacterViewModel, onOpenGallery: () -> Unit) {
    val data = ui.data ?: return
    val c = data.companion
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FamilyGoalCard(data.familyGoal)
        CompanionCard(c)

        // Actions below the card — only what applies. Same card design throughout.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (c.eggReady) {
                CharAction(KairosIcons.Plus, "Hatch", Modifier.weight(1f), highlighted = true, enabled = !ui.busy) { vm.hatch("new") }
                if (c.active) {
                    CharAction(KairosIcons.Palette, "Deepen", Modifier.weight(1f), enabled = !ui.busy) { vm.hatch("deepen") }
                }
            }
            CharAction(KairosIcons.Trophy, "Gallery", Modifier.weight(1f)) { onOpenGallery() }
        }

        ui.message?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF047857), modifier = Modifier.fillMaxWidth())
        }

        PersonCard(person, data)
    }
}

@Composable
private fun CharAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = modifier.height(76.dp)) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun FamilyGoalCard(goal: FamilyGoalDto) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("\uD83C\uDFC6", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f)) {
                Text("Family goal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    goal.text.ifBlank { "Propose and vote on a family reward" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (goal.kids != null) {
                Text(goal.kids, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CompanionCard(c: CharCompanionDto) {
    val container = rememberContainer()
    val base = container.sessionRepository.baseUrlRaw
    val glow = parseHex(c.color)

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(glow.copy(alpha = 0.08f))
            .border(1.5.dp, glow.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (base != null && c.image.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ApiClient.resolveUrl(base, c.image),
                imageLoader = container.imageLoader,
                contentDescription = c.speciesName ?: "Companion egg",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(120.dp),
                loading = {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                },
                error = {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("\uD83E\uDD5A", style = MaterialTheme.typography.displaySmall)
                    }
                },
            )
        }

        if (c.active && c.speciesName != null) {
            Row {
                Text(c.speciesName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (c.stageName != null) {
                    Text("  \u00b7 ${c.stageName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (c.shiny) Text("  \u2726", style = MaterialTheme.typography.titleSmall, color = glow)
            }
            XpCells(c.xpCells)
        } else {
            Text(if (c.eggReady) "Ready to hatch!" else "Egg", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Bar(c.incubationPct / 100f, glow, Modifier.width(180.dp))
            Text("${c.incubationPct}% incubated", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The web's segmented level bar: 20 little squares, filled ones coloured by
 *  which domain earned the XP. */
@Composable
private fun XpCells(cells: List<String>) {
    val slots = if (cells.isEmpty()) List(20) { "" } else cells
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        slots.forEach { hex ->
            val filled = hex.isNotBlank()
            Box(
                Modifier.size(10.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (filled) parseHex(hex) else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun PersonCard(person: PersonDto, data: CharacterDto) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonAvatar(person, size = 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(data.className, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("LEVEL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${data.level.level}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            // Character XP
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Bar(data.level.pct.toFloat() / 100f, ACCENT, Modifier.fillMaxWidth())
                Text(
                    "${data.level.toNext} XP to level ${data.level.level + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Season tier
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Season \u00b7 Tier ${data.season.tier} / ${data.season.maxTier}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (data.season.complete) "Complete" else "Next: Tier ${data.season.tier + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Bar(
                    data.season.pct.toFloat() / 100f,
                    if (data.season.complete) Color(0xFF10B981) else ACCENT,
                    Modifier.fillMaxWidth(),
                )
            }

            // Stats grid
            if (data.stats.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.stats.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            pair.forEach { s ->
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row {
                                        Text(s.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                        Text("Lv ${s.level}", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Bar(s.pct.toFloat() / 100f, ACCENT, Modifier.fillMaxWidth())
                                }
                            }
                            if (pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Streak + badges
            val chips = buildList {
                if (data.currentStreak > 0) add("\uD83D\uDD25 ${data.currentStreak}-day streak")
                if (data.season.complete) add("\uD83C\uDFC6 Season complete")
                if (data.perfectWeeks > 0) add("\u2B50 ${data.perfectWeeks} perfect ${if (data.perfectWeeks == 1) "week" else "weeks"}")
                data.milestones.forEach { add("\uD83D\uDD25 $it-day streak") }
                data.bestWeekPct?.let { add("\u2B50 Best week ${it.toInt()}%") }
            }
            if (chips.isNotEmpty()) {
                Chips(chips)
            }

            // Mastery
            if (data.masteries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("MASTERY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    data.masteries.forEach { m ->
                        Text(
                            "${m.title} of ${m.chore}  \u00d7${m.count}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(labels: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier.height(6.dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(999.dp)).background(color))
    }
}

private fun parseHex(hex: String?): Color {
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
