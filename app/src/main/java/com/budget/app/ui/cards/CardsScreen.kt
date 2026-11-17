package com.budget.app.ui.cards

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.AppContainer
import com.budget.app.data.CardLogic
import com.budget.app.data.CardReminderLead
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.CardDto
import com.budget.app.data.reminders.BillReminderWorker
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import com.budget.app.ui.today.ExtrasUi
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The Cards tab: the stack, each card's payment, and the reminder setting. */
@Composable
fun CardsScreen(container: AppContainer, extras: ExtrasUi) {
    val c = Budget.colors
    val lead by container.cardReminderLead.collectAsState(initial = CardReminderLead.OFF)
    var picking by remember { mutableStateOf(false) }
    val setLead = rememberCardReminderSetter(container)
    val cards = extras.cards

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "Cards",
                color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
                modifier = Modifier.statusBarsPadding().padding(start = 4.dp, top = 16.dp),
            )
        }
        when {
            cards == null -> item {
                Text("Cards show once the server is reachable.", color = c.muted, modifier = Modifier.padding(4.dp))
            }
            cards.cards.isEmpty() -> item {
                Text("No credit cards from the bank yet, or every card is paid off.", color = c.muted, modifier = Modifier.padding(4.dp))
            }
            else -> {
                item { CardStack(cards) }
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(c.surface, RoundedCornerShape(20.dp))
                            .border(1.dp, c.line, RoundedCornerShape(20.dp)),
                    ) {
                        cards.cards.forEachIndexed { i, card ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                            CardRow(card)
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.surface, RoundedCornerShape(18.dp))
                    .border(1.dp, c.line, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { picking = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhIcon(Ph.BELL, null, c.muted)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Payment reminders", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (lead == CardReminderLead.OFF) "Off · a notification before each card is due" else lead.label,
                        color = c.muted, fontSize = 13.sp,
                    )
                }
                PhIcon(Ph.CARET_RIGHT, null, c.muted, size = 20.dp)
            }
        }
    }

    if (picking) {
        CardReminderSheet(
            current = lead,
            onPick = {
                picking = false
                setLead(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun CardRow(card: CardDto) {
    val c = Budget.colors
    val s = card.status
    val today = LocalDate.now()
    val dueIn = s?.nextDue?.let { runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(it)) }.getOrNull() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 34.dp, height = 22.dp)
                .background(
                    Brush.linearGradient(listOf(Color(CardLogic.argb(card.color.from)), Color(CardLogic.argb(card.color.to)))),
                    RoundedCornerShape(5.dp),
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(CardLogic.faceName(card.name), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    s == null -> "Balance ${Money.whole(card.owedNowCents)} · add its days on the web"
                    s.plan == "payoff" || s.paymentsLeft != null -> "Payoff plan · ${s.paymentsLeft ?: "?"} payments left"
                    else -> "Statement ${Money.whole(s.dueCents)} · new ${Money.whole(s.newChargesCents)}"
                },
                color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                s?.let { Money.format(it.suggestedCents) } ?: Money.format(card.owedNowCents),
                color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
            if (s != null) {
                val paid = s.suggestedCents <= 0L
                Text(
                    if (paid) "Paid this cycle" else "Due ${Dates.short(s.nextDue) ?: s.nextDue}",
                    color = when {
                        paid -> c.accent
                        dueIn != null && dueIn <= 7 -> c.warn
                        else -> c.muted
                    },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Saves the card reminder choice, asking for notifications first on Android 13+
 * when they aren't allowed yet.
 */
@Composable
fun rememberCardReminderSetter(container: AppContainer): (CardReminderLead) -> Unit {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pending by remember { mutableStateOf<CardReminderLead?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.let { lead -> scope.launch { container.setCardReminderLead(lead) } }
        pending = null
    }
    return { lead ->
        if (lead != CardReminderLead.OFF && Build.VERSION.SDK_INT >= 33 && !BillReminderWorker.canPost(context)) {
            pending = lead
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scope.launch { container.setCardReminderLead(lead) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardReminderSheet(current: CardReminderLead, onPick: (CardReminderLead) -> Unit, onDismiss: () -> Unit) {
    val c = Budget.colors
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Card payment reminders", color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "A notification around 8 AM with what to pay. A card already paid for this cycle is skipped.",
                color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp),
            )
            CardReminderLead.entries.forEach { lead ->
                val on = lead == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(if (on) c.accentSoft else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { onPick(lead) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhIcon(if (on) Ph.CHECK_CIRCLE_FILL else Ph.CIRCLE, null, if (on) c.accent else c.muted)
                    Spacer(Modifier.width(12.dp))
                    Text(lead.label, color = c.ink, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold)
                }
            }
            if (current != CardReminderLead.OFF && !BillReminderWorker.canPost(context)) {
                Text(
                    "Notifications are off for Budget, so reminders can't show. Turn them on in the phone's settings.",
                    color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
