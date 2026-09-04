package com.kairos.app.ui.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.ReadingGroupDto
import com.kairos.app.data.remote.dto.ReadingStatsDto
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FAMILY_COLOR = Color(0xFF0F5C63)

/** "#rrggbb" -> Color, defaulting to the family teal on anything unparseable. */
fun parseHexColor(hex: String?): Color {
    val s = hex?.trim()?.removePrefix("#") ?: return FAMILY_COLOR
    return try {
        when (s.length) {
            6 -> Color(("FF$s").toLong(16))
            8 -> Color(s.toLong(16))
            else -> FAMILY_COLOR
        }
    } catch (_: NumberFormatException) {
        FAMILY_COLOR
    }
}

private val SHORT_DATE = DateTimeFormatter.ofPattern("MMM d")

private fun formatShortISO(iso: String): String =
    try {
        LocalDate.parse(iso).format(SHORT_DATE)
    } catch (_: Exception) {
        iso
    }

/** A day name relative to today, matching the web ReadingCards.relativeLabel. */
private fun relativeLabel(offset: Int): String = when {
    offset == 0 -> "Today"
    offset == 1 -> "Tomorrow"
    offset == -1 -> "Yesterday"
    offset > 1 -> "In $offset days"
    else -> "${-offset} days ago"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleScreen(onOpenDrawer: () -> Unit) {
    val container = rememberContainer()
    val vm: BibleViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BibleViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.savedTick) {
        if (ui.savedTick > 0) snackbar.showSnackbar("Updated")
    }
    LaunchedEffect(ui.actionError) {
        ui.actionError?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bible reading") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val data = ui.data
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.loadError ?: "Couldn't load reading.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> {
                    val personal = data.personal
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (personal != null) {
                            ProgressTabs(ui.tab, onSelect = vm::setTab)
                        }
                        if (personal == null || ui.tab == BibleTab.FAMILY) {
                            FamilyContent(data)
                        } else {
                            PersonalContent(vm, ui)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressTabs(tab: BibleTab, onSelect: (BibleTab) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabPill("Family Progress", tab == BibleTab.FAMILY) { onSelect(BibleTab.FAMILY) }
        TabPill("Personal Progress", tab == BibleTab.PERSONAL) { onSelect(BibleTab.PERSONAL) }
    }
}

@Composable
private fun TabPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- Family ----

@Composable
private fun FamilyContent(data: com.kairos.app.data.remote.dto.ReadingDto) {
    val family = data.family
    if (family.havePlan && family.cards.isNotEmpty()) {
        ReadingDeck(family)
        family.lastDayISO?.let { last ->
            Text(
                "${family.remaining} days left \u00b7 plan runs out ${formatShortISO(last)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SectionRow("Family reading", if (family.stats.wholeBible) "Whole Bible read" else null, FAMILY_COLOR)
    CoverageCards(family.stats, FAMILY_COLOR)
    GroupsCard(family.stats.groups, FAMILY_COLOR)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingDeck(family: com.kairos.app.data.remote.dto.ReadingFamilyDto) {
    val cards = family.cards
    val start = family.todayIndex.coerceIn(0, (cards.size - 1).coerceAtLeast(0))
    val pager = rememberPagerState(initialPage = start) { cards.size }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // "Back to today" only when off the today card.
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.CenterEnd) {
            if (pager.currentPage != family.todayIndex) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(family.todayIndex) } }) {
                    Text("Back to today")
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
                enabled = pager.currentPage > 0,
            ) {
                Icon(KairosIcons.ChevronLeft, contentDescription = "Previous day")
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).height(176.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp),
                pageSpacing = 10.dp,
            ) { page ->
                val card = cards[page]
                val isActive = page == pager.currentPage
                OutlinedCard(
                    modifier = Modifier.fillMaxSize(),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isActive) 1.5.dp else 1.dp,
                        if (isActive) FAMILY_COLOR else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(18.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            relativeLabel(page - family.todayIndex).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) FAMILY_COLOR else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            card.passage,
                            style = if (isActive) MaterialTheme.typography.headlineSmall
                            else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            card.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            IconButton(
                onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(cards.size - 1)) } },
                enabled = pager.currentPage < cards.size - 1,
            ) {
                Icon(KairosIcons.ChevronRight, contentDescription = "Next day")
            }
        }
    }
}

// ---- Personal ----

@Composable
private fun PersonalContent(vm: BibleViewModel, ui: BibleUiState) {
    val data = ui.data ?: return
    val personal = data.personal ?: return
    val color = parseHexColor(personal.color)

    SectionRow("Your reading", if (personal.stats.wholeBible) "Whole Bible read" else null, color)
    CoverageCards(personal.stats, color)
    GroupsCard(personal.stats.groups, color)

    Spacer(Modifier.height(4.dp))
    SectionHeading("Your plan")
    PersonalPlanSection(vm, personal.plan, data.today, ui.busy, ui.actionError)

    Spacer(Modifier.height(4.dp))
    SectionHeading("Manual checklist")
    BookProgress(vm, personal.readKeys, color, ui.busy)
}

// ---- Shared pieces ----

@Composable
fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SectionRow(title: String, trophy: String?, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeading(title)
        if (trophy != null) TrophyPill(trophy, color)
    }
}

@Composable
private fun TrophyPill(text: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(KairosIcons.Trophy, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ProgressBar(percent: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth((percent.coerceIn(0, 100)) / 100f)
                .clip(RoundedCornerShape(999.dp))
                .background(color),
        )
    }
}

@Composable
private fun CoverageCards(stats: ReadingStatsDto, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CoverageCard(stats.ot, color)
        CoverageCard(stats.nt, color)
    }
}

@Composable
private fun CoverageCard(g: ReadingGroupDto, color: Color) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(g.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("${g.percent}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(g.percent, color)
            Spacer(Modifier.height(8.dp))
            Text(
                "${g.read} of ${g.chapters} chapters",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupsCard(groups: List<ReadingGroupDto>, color: Color) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            groups.forEachIndexed { i, g ->
                if (i > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        g.label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(104.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ProgressBar(g.percent, color, Modifier.weight(1f))
                    Text(
                        "${g.percent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(38.dp),
                        maxLines = 1,
                    )
                    Text(
                        "${g.read}/${g.chapters}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(52.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
