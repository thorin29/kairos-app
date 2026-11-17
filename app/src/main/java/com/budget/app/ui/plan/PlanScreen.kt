package com.budget.app.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.PlanLogic
import com.budget.app.data.remote.Projection
import com.budget.app.ui.common.Banner
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import androidx.compose.ui.Alignment
import kotlin.math.roundToLong

/** "What can I pay?": the bills balance walked forward, and what a payment today does to it. */
@Composable
fun PlanScreen(vm: PlanViewModel, onEditBalance: () -> Unit, onBack: (() -> Unit)? = null) {
    val c = Budget.colors
    val ui by vm.ui.collectAsState()
    val p = ui.projection

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            Row(
                Modifier.height(44.dp).clickable(onClick = onBack).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhIcon(Ph.CARET_LEFT, "Back", c.ink)
                Text("Back", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            }
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text("Bills account, walked forward", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("What can I pay?", color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        }

        if (ui.offline) Banner("Offline. Showing the last projection.", c.warn, c.warnSoft, icon = Ph.CLOUD_SLASH)
        ui.error?.let { Banner(it, c.danger, c.dangerSoft, icon = Ph.WARNING) }

        when {
            p == null -> Text(if (ui.loading) "Loading…" else "Nothing loaded yet.", color = c.muted)
            p.balanceMissing -> Banner(
                "Add what's in the bills account first; the projection starts from it. Tap here to add it.",
                c.warn, c.warnSoft, icon = Ph.PENCIL, modifier = Modifier.clickable(onClick = onEditBalance),
            )
            else -> PlanBody(p, ui.horizonDays, ui.payCents, onHorizon = vm::setHorizon, onPay = vm::setPay)
        }
    }
}

@Composable
private fun PlanBody(p: Projection, horizon: Int, payCents: Long, onHorizon: (Int) -> Unit, onPay: (Long) -> Unit) {
    val c = Budget.colors
    val outcome = PlanLogic.ifPaid(p, payCents)
    val lowDate = Dates.short(p.lowPoint.date) ?: "—"
    val lowColor = when (outcome.verdict) {
        PlanLogic.Verdict.BELOW_ZERO -> c.danger
        PlanLogic.Verdict.INTO_CUSHION -> c.warn
        PlanLogic.Verdict.FINE -> c.ink
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.weight(1f).background(c.accent, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Safe to send today", color = c.onAccent.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(Money.format(outcome.safeCents), color = c.onAccent, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(
            Modifier.weight(1f).background(c.surface, RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Lowest point · $lowDate", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(Money.format(outcome.lowCents), color = lowColor, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
    }

    Column(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(20.dp)).border(1.dp, c.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HORIZONS.forEach { d ->
                val on = d == horizon
                Text(
                    "$d days",
                    color = if (on) c.accent else c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(if (on) c.accentSoft else Color.Transparent, RoundedCornerShape(16.dp))
                        .border(1.dp, if (on) c.accent else c.line, RoundedCornerShape(16.dp))
                        .clickable { onHorizon(d) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        BalanceChart(
            values = PlanLogic.series(p, payCents),
            lowIndex = p.days.indexOfFirst { it.date.take(10) == p.lowPoint.date.take(10) }.let { if (it >= 0) it + 1 else -1 },
            line = c.accent, fill = c.accentSoft, zero = c.warn, dot = lowColor, background = c.surface,
            modifier = Modifier.fillMaxWidth().height(170.dp).semantics {
                contentDescription = "Bills account balance over the next $horizon days, lowest on $lowDate"
            },
        )
        if (p.days.isNotEmpty()) {
            Row {
                val first = Dates.short(p.days.first().date) ?: ""
                val mid = Dates.short(p.days[p.days.size / 2].date) ?: ""
                val last = Dates.short(p.days.last().date) ?: ""
                Text(first, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(mid, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(last, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    val max = PlanLogic.sliderMaxCents(p)
    Column(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(20.dp)).border(1.dp, c.line, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row {
            Text("If I pay today", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(Money.format(payCents), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Slider(
            value = payCents.toFloat().coerceIn(0f, max.toFloat()),
            onValueChange = { v -> onPay(((v / 1_000f).roundToLong()) * 1_000L) },
            valueRange = 0f..max.toFloat(),
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.raised),
        )
        Text(
            when (outcome.verdict) {
                PlanLogic.Verdict.BELOW_ZERO -> "Too much: the account would drop below zero by $lowDate."
                PlanLogic.Verdict.INTO_CUSHION -> "It clears, but dips into the cushion you set by $lowDate."
                PlanLogic.Verdict.FINE -> if (payCents == 0L) "Slide to see what a payment today does." else "Fine. The lowest point stays covered on $lowDate."
            },
            color = when (outcome.verdict) {
                PlanLogic.Verdict.BELOW_ZERO -> c.danger
                PlanLogic.Verdict.INTO_CUSHION -> c.warn
                PlanLogic.Verdict.FINE -> c.muted
            },
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp,
        )
    }
}

/** A step line of the closing balance per day, with zero dashed and the low point ringed. */
@Composable
private fun BalanceChart(
    values: List<Long>,
    lowIndex: Int,
    line: Color,
    fill: Color,
    zero: Color,
    dot: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val pad = 8.dp.toPx()
        val top = values.max().coerceAtLeast(0L).toFloat()
        val bottom = values.min().coerceAtMost(0L).toFloat()
        val span = (top - bottom).takeIf { it > 0f } ?: 1f
        val w = size.width
        val h = size.height
        fun x(i: Int) = i * w / (values.size - 1)
        fun y(v: Long) = pad + (1f - (v - bottom) / span) * (h - 2 * pad)

        val path = Path().apply {
            moveTo(0f, y(values[0]))
            for (i in 1 until values.size) {
                lineTo(x(i), y(values[i - 1]))
                if (values[i] != values[i - 1]) lineTo(x(i), y(values[i]))
            }
        }
        val area = Path().apply {
            addPath(path)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(area, fill)
        val zeroY = y(0)
        drawLine(zero, Offset(0f, zeroY), Offset(w, zeroY), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx()))
        if (lowIndex in values.indices) {
            val center = Offset(x(lowIndex), y(values[lowIndex]))
            drawCircle(background, radius = 6.dp.toPx(), center = center)
            drawCircle(dot, radius = 6.dp.toPx(), center = center, style = Stroke(width = 3.dp.toPx()))
        }
    }
}
