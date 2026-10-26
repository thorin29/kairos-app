package com.kairos.app.ui.hockey

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.HockeyCountDto
import com.kairos.app.data.remote.dto.HockeyDto
import com.kairos.app.data.remote.dto.HockeyRowDto
import com.kairos.app.data.remote.dto.HockeySectionDto
import com.kairos.app.ui.calendar.IcsDraft
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.TimeFmt
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import com.kairos.app.ui.theme.KairosThemeState
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Hockey: the week's ice times from ice-time, by section, with sign-up counts.
 * Empty days are left out (the web lists all seven). "Add" opens the calendar's
 * New event filled in — name, times, type and location come from the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HockeyScreen(
    onOpenDrawer: () -> Unit,
    onAdd: (IcsDraft, String?) -> Unit,
    refreshKey: Int = 0,
) {
    val container = rememberContainer()
    val vm: HockeyViewModel = viewModel(
        factory = viewModelFactory { initializer { HockeyViewModel(container.sessionRepository, container.payloadCache) } },
    )
    val ui by vm.ui.collectAsState()
    LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Hockey") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
                actions = {
                    if (ui.data?.state == "ok" && (ui.data?.sections?.size ?: 0) > 1) {
                        TextButton(onClick = { vm.setArranging(!ui.arranging) }) {
                            Text(if (ui.arranging) "Done" else "Arrange")
                        }
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = ui.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val data = ui.data
                when {
                    ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    data == null || data.state != "ok" -> Message(
                        text = when {
                            data?.state == "off" -> "Hockey isn’t set up. An admin can connect ice-time on the web under Admin → Hockey."
                            data?.state == "error" -> data.error ?: "Couldn’t reach ice-time."
                            else -> ui.loadError ?: "Couldn’t load hockey."
                        },
                        onRetry = { vm.load() },
                    )
                    else -> Week(
                        data, ui.loadError, ui.arranging, onAdd,
                        onToggle = vm::toggle,
                        onMove = vm::move,
                    )
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(120.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun Week(
    data: HockeyDto,
    staleError: String?,
    arranging: Boolean,
    onAdd: (IcsDraft, String?) -> Unit,
    onToggle: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val sub = listOfNotNull(
            data.rinks.joinToString(", ").ifBlank { null },
            "next 7 days",
            updatedText(data.checkedAt),
        ).joinToString(" · ")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (listOfNotNull(staleError) + data.warnings).forEach { w ->
                Text(w, style = MaterialTheme.typography.bodySmall, color = warnColor())
            }
        }
        if (data.sections.isEmpty()) {
            Text("No ice times this week.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val last = data.sections.lastIndex
        data.sections.forEachIndexed { i, s ->
            SectionBlock(
                s,
                arranging = arranging,
                onToggle = { onToggle(s.name) },
                onUp = if (i > 0) ({ onMove(i, -1) }) else null,
                onDown = if (i < last) ({ onMove(i, 1) }) else null,
                onAdd = { r -> onAdd(draftFor(r), data.eventTypeId) },
                onOpen = { link -> runCatching { uri.openUri(link) } },
            )
        }
    }
}

@Composable
private fun SectionBlock(
    s: HockeySectionDto,
    arranging: Boolean,
    onToggle: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onAdd: (HockeyRowDto) -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggle)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    KairosIcons.ChevronDown,
                    contentDescription = if (s.open) "Collapse ${s.name}" else "Expand ${s.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp).rotate(if (s.open) 0f else -90f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    s.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                val n = s.rows.size
                Text(
                    if (n == 0) "none this week" else if (n == 1) "1 session" else "$n sessions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (arranging) {
                IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null) {
                    Icon(KairosIcons.ChevronDown, "Move ${s.name} up", modifier = Modifier.rotate(180f))
                }
                IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null) {
                    Icon(KairosIcons.ChevronDown, "Move ${s.name} down")
                }
            } else {
                val link = s.link
                if (link != null && s.open) {
                    TextButton(onClick = { onOpen(link) }) { Text("Sign up ↗") }
                }
            }
        }
        if (!s.open) return@Column
        if (s.rows.isEmpty()) {
            Text(
                "Nothing scheduled this week.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        s.rows.forEach { r -> SessionCard(s, r, onAdd = { onAdd(r) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(s: HockeySectionDto, r: HockeyRowDto, onAdd: () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dayText(r.date), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(timeRange(r.start, r.end))
            Spacer(Modifier.width(6.dp))
            Text(
                lengthText(r.minutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val main = r.skaters ?: r.players
            if (main != null) {
                val label = if (r.skaters != null) "Skaters" else s.colA
                Pill("$label ${countText(main)}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
            }
            r.goalies?.let { g ->
                val open = spotsLeft(g)
                val (bg, fg) = goalieColors(open > 0)
                Pill(
                    "Goalies ${countText(g)} · " + if (open > 0) "$open open" else "Full",
                    bg, fg, bold = true,
                )
            }
        }
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KairosThemeState.accent.copy(alpha = 0.14f),
                contentColor = KairosThemeState.accent,
            ),
        ) {
            Icon(KairosIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(r.add.label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color, bold: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private fun goalieColors(open: Boolean): Pair<Color, Color> {
    val dark = KairosThemeState.dark
    return when {
        open && dark -> Color(0xFF14352A) to Color(0xFF6EE7B7)
        open -> Color(0xFFE3F4EA) to Color(0xFF16623A)
        dark -> Color(0xFF3B1D1F) to Color(0xFFFCA5A5)
        else -> Color(0xFFFDEAEA) to Color(0xFFA12A2A)
    }
}

private fun warnColor(): Color =
    if (KairosThemeState.dark) Color(0xFFFBBF24) else Color(0xFF8A4B00)

/** The session as New event values, on the household clock like a file. */
internal fun draftFor(r: HockeyRowDto): IcsDraft = IcsDraft(
    title = r.add.title,
    allDay = false,
    startDate = r.date,
    startTime = r.start,
    endDate = runCatching { LocalDate.parse(r.date).plusDays(r.endDayOffset.toLong()).toString() }.getOrDefault(r.date),
    endTime = r.end,
    location = r.add.location,
    notes = null,
    repeat = "NONE",
    repeatDropped = false,
)

internal fun countText(c: HockeyCountDto): String =
    if (c.max != null) "${c.enrolled}/${c.max}" else "${c.enrolled}"

internal fun spotsLeft(c: HockeyCountDto): Int =
    c.remaining ?: c.max?.let { (it - c.enrolled).coerceAtLeast(0) } ?: 1

private val DAY = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)

private fun dayText(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DAY) }.getOrDefault(iso)

private fun minutesOf(hhmm: String): Int? {
    val p = hhmm.split(":")
    val h = p.getOrNull(0)?.toIntOrNull() ?: return null
    val m = p.getOrNull(1)?.toIntOrNull() ?: return null
    return h * 60 + m
}

internal fun timeRange(start: String, end: String): String {
    val a = minutesOf(start) ?: return "$start – $end"
    val b = minutesOf(end) ?: return "$start – $end"
    if (TimeFmt.military) return "${TimeFmt.clock(a)} – ${TimeFmt.clock(b)}"
    val sa = TimeFmt.clock(a)
    val sb = TimeFmt.clock(b)
    // "6:00 – 7:45 AM" when both share AM/PM, else both halves in full.
    return if (sa.takeLast(2) == sb.takeLast(2)) "${sa.dropLast(3)} – $sb" else "$sa – $sb"
}

private fun lengthText(min: Int): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

private fun updatedText(iso: String?): String? {
    val at = iso?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: iso?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        ?: return null
    val min = ((System.currentTimeMillis() - at.toEpochMilli()) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "updated just now"
        min < 60 -> "updated $min min ago"
        else -> "updated ${min / 60} h ago"
    }
}
