package com.budget.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Money
import com.budget.app.data.PaceLogic
import com.budget.app.data.remote.InsightsResponse
import com.budget.app.data.remote.PaceDto
import com.budget.app.data.remote.SuggestionDto
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import com.budget.app.ui.today.ExtrasUi

/** The Insights tab: this month's pace, suggestions, and where the money went. */
@Composable
fun InsightsScreen(extras: ExtrasUi) {
    val c = Budget.colors
    val s = extras.insights
    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "Insights",
                color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
                modifier = Modifier.statusBarsPadding().padding(start = 4.dp, top = 16.dp),
            )
        }
        if (s == null) {
            item { Text("Insights show once the server is reachable.", color = c.muted, modifier = Modifier.padding(4.dp)) }
            return@LazyColumn
        }
        s.pace?.let { p -> item { PaceCard(p) } }
        if (s.suggestions.isNotEmpty()) {
            item {
                val keep = s.suggestions.sumOf { it.monthlyCents }
                Row(Modifier.padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Suggestions", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (keep > 0) Text("up to ${Money.whole(keep)} a month", color = c.muted, fontSize = 13.sp)
                }
            }
            items(s.suggestions, key = { "${it.kind}-${it.title}" }) { SuggestionRow(it) }
            item {
                Text(
                    "From the last six complete months of bank history.",
                    color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        if (s.everydayCents > 0 || s.categories.isNotEmpty()) item { SpendingCard(s) }
    }
}

@Composable
private fun PaceCard(p: PaceDto) {
    val c = Budget.colors
    val (spent, projected, usual) = PaceLogic.bar(p)
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(20.dp))
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pace · everyday spending", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Day ${p.day} of ${p.daysInMonth}", color = c.muted, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Money.whole(p.soFarCents), color = c.ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(" so far", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .semantics {
                    contentDescription = "Spent ${Money.whole(p.soFarCents)} so far. A usual month has spent ${Money.whole(p.usualByNowCents)} by now."
                },
        ) {
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(10.dp).align(Alignment.Center).background(c.raised, RoundedCornerShape(5.dp)))
            Box(Modifier.width(w * projected).height(10.dp).align(Alignment.CenterStart).background(c.accentSoft, RoundedCornerShape(5.dp)))
            Box(Modifier.width(w * spent).height(10.dp).align(Alignment.CenterStart).background(c.accent, RoundedCornerShape(5.dp)))
            Box(Modifier.offset(x = (w * usual) - 1.dp).width(2.dp).fillMaxHeight().background(c.ink))
        }
        Text(
            "On pace for ${Money.whole(p.projectedCents)}. ${PaceLogic.verdict(p)}. The line is where a usual month is by today.",
            color = c.muted, fontSize = 13.sp,
        )
    }
}

@Composable
private fun SuggestionRow(s: SuggestionDto) {
    val c = Budget.colors
    var open by rememberSaveable(s.kind, s.title) { mutableStateOf(false) }
    val (icon, tint) = when (s.kind) {
        "rising", "price-up", "new-repeat" -> Ph.TREND_UP to c.warn
        "interest", "fees" -> Ph.WARNING to c.danger
        "payoff" -> Ph.CHECK_CIRCLE to c.accent
        "repeating" -> Ph.SYNC to c.accent
        else -> Ph.LIGHTBULB to c.accent
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(18.dp))
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button) { open = !open }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PhIcon(icon, null, tint, size = 20.dp)
            Spacer(Modifier.width(12.dp))
            Text(s.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PhIcon(if (open) Ph.CARET_DOWN else Ph.CARET_RIGHT, null, c.muted, size = 18.dp)
        }
        if (open) {
            Text(s.detail, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 32.dp))
            s.items.forEach { it ->
                Row(Modifier.padding(start = 32.dp)) {
                    Text(it.name, color = c.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        (it.fromCents?.let { f -> "${Money.format(f)} → " } ?: "") + Money.format(it.cents),
                        color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** This month's everyday spending against the average, and where most of it went. */
@Composable
private fun SpendingCard(s: InsightsResponse) {
    val c = Budget.colors
    val max = (s.categories.maxOfOrNull { maxOf(it.cents, it.averageCents) } ?: 1L).coerceAtLeast(1L)
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(20.dp))
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Where it went this month", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (s.everydayAverageCents > 0) {
            val diff = s.everydayCents - s.everydayAverageCents
            Text(
                "${Money.format(s.everydayCents)} everyday spending · " +
                    if (kotlin.math.abs(diff) < 1_000) "about your ${Money.whole(s.everydayAverageCents)} average"
                    else "${Money.whole(kotlin.math.abs(diff))} ${if (diff > 0) "more" else "less"} than your ${Money.whole(s.everydayAverageCents)} average",
                color = c.muted, fontSize = 13.sp,
            )
        }
        s.categories.forEach { cat ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(cat.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(Money.format(cat.cents), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).background(c.raised, RoundedCornerShape(3.dp))) {
                    Box(
                        Modifier
                            .fillMaxWidth((cat.cents.toFloat() / max).coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(c.accent, RoundedCornerShape(3.dp)),
                    )
                }
                if (cat.averageCents > 0) {
                    Text("Usually ${Money.whole(cat.averageCents)}", color = c.muted, fontSize = 12.sp)
                }
            }
        }
        if (s.uncategorizedCount > 0) {
            Text("${s.uncategorizedCount} charge${if (s.uncategorizedCount == 1) "" else "s"} without a category. Sort them on the web.", color = c.muted, fontSize = 12.sp)
        }
    }
}
